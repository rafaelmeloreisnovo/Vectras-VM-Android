package com.vectras.vm.core;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CharsetDecoder;

/**
 * Thread-safe ring buffer with line and byte quotas.
 */
public class BoundedStringRingBuffer {
    public static final String TRUNCATED_MARKER = "...[TRUNCATED]";
    private static final String TAG = "BoundedStringRingBuffer";

    private static final int UTF8_REPLACEMENT_BYTES = 3;
    private static final ThreadLocal<DecodeState> DECODE_STATE = ThreadLocal.withInitial(DecodeState::new);

    private static final class DecodeState {
        private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE);
        private CharBuffer charBuffer = CharBuffer.allocate(256);

        private CharBuffer ensureCharCapacity(int capacity) {
            if (charBuffer.capacity() < capacity) {
                charBuffer = CharBuffer.allocate(capacity);
            }
            charBuffer.clear();
            return charBuffer;
        }
    }

    private final int maxLines;
    private final int maxBytes;

    private final int[] charOffsets;
    private final int[] charLengths;
    private final int[] utf8ByteCounts;

    private int head;
    private int size;

    private char[] storage = new char[256];
    private int storageHead;
    private int storageTail;
    private int storageUsed;

    private int totalBytes;
    private int totalChars;
    private boolean truncated;

    public BoundedStringRingBuffer(int maxLines, int maxBytes) {
        this.maxLines = Math.max(1, maxLines);
        this.maxBytes = Math.max(1, maxBytes);
        this.charOffsets = new int[this.maxLines];
        this.charLengths = new int[this.maxLines];
        this.utf8ByteCounts = new int[this.maxLines];
    }

    public synchronized void addLine(String line) {
        if (line == null) {
            appendLineRange("", 0, 0);
            return;
        }
        appendLineRange(line, 0, line.length());
    }

    public synchronized void addLine(char[] chars, int offset, int length) {
        if (chars == null || length <= 0) {
            appendLineRange("", 0, 0);
            return;
        }
        int safeOffset = Math.max(0, offset);
        int safeLength = Math.min(length, chars.length - safeOffset);
        if (safeLength <= 0) {
            appendLineRange("", 0, 0);
            return;
        }
        appendLineRange(CharBuffer.wrap(chars), safeOffset, safeLength);
    }

    public synchronized void addLineUtf8(byte[] bytes, int offset, int length) {
        if (bytes == null || length <= 0) {
            appendLineRange("", 0, 0);
            return;
        }
        int safeOffset = Math.max(0, offset);
        int safeLength = Math.min(length, bytes.length - safeOffset);
        if (safeLength <= 0) {
            appendLineRange("", 0, 0);
            return;
        }

        int maxLineBytes = maxBytes - 1;
        int acceptedBytes = Math.min(safeLength, Math.max(0, maxLineBytes));
        DecodeState state = DECODE_STATE.get();
        state.decoder.reset();
        CharBuffer out = state.ensureCharCapacity(acceptedBytes);
        ByteBuffer in = ByteBuffer.wrap(bytes, safeOffset, acceptedBytes);
        CoderResult result = state.decoder.decode(in, out, true);
        if (result.isError()) {
            try {
                result.throwException();
            } catch (CharacterCodingException e) {
                RuntimeErrorReporter.warn("VRT-BSR-0001", "decode_utf8_line", "acceptedBytes=" + acceptedBytes, e);
                Log.w(TAG, "Falling back to empty line due to UTF-8 decode error");
                appendLineRange("", 0, 0);
                return;
            }
        }
        state.decoder.flush(out);
        out.flip();
        appendLineRange(out, 0, out.remaining());
    }

    public synchronized String snapshot() {
        int markerExtra = truncated ? TRUNCATED_MARKER.length() + 1 : 0;
        StringBuilder sb = new StringBuilder(totalChars + size + markerExtra);
        boolean lastLineIsMarker = false;
        for (int i = 0; i < size; i++) {
            int idx = indexAt(i);
            lastLineIsMarker = equalsStoredLine(idx, TRUNCATED_MARKER);
            appendStoredLine(sb, idx);
            sb.append('\n');
        }
        if (truncated && !lastLineIsMarker) {
            sb.append(TRUNCATED_MARKER).append('\n');
        }
        return sb.toString();
    }

    private void appendLineRange(CharSequence value, int offset, int length) {
        int maxLineBytes = maxBytes - 1;
        Utf8Slice slice = fitUtf8Prefix(value, offset, length, Math.max(0, maxLineBytes));

        int lineCharLength = slice.charLength;
        int lineUtf8Bytes = slice.byteLength;
        int lineSize = lineUtf8Bytes + 1;

        if (lineCharLength > 0) {
            ensureStorageCapacity(storageUsed + lineCharLength);
            while (freeStorage() < lineCharLength) {
                if (!evictOldest()) {
                    break;
                }
            }
        }

        while (size >= maxLines || totalBytes + lineSize > maxBytes) {
            if (!evictOldest()) {
                break;
            }
        }

        if (lineCharLength > 0) {
            ensureStorageCapacity(storageUsed + lineCharLength);
        }

        int tailIndex = indexAt(size);
        charOffsets[tailIndex] = storageTail;
        charLengths[tailIndex] = lineCharLength;
        utf8ByteCounts[tailIndex] = lineUtf8Bytes;
        copyIntoStorage(value, offset, lineCharLength);

        size++;
        totalChars += lineCharLength;
        totalBytes += lineSize;

        trim();
    }

    private void trim() {
        while (size > maxLines || totalBytes > maxBytes) {
            if (!evictOldest()) {
                return;
            }
        }
    }

    private boolean evictOldest() {
        if (size == 0) {
            return false;
        }
        int idx = head;
        int len = charLengths[idx];
        int removedBytes = utf8ByteCounts[idx] + 1;

        storageHead = advance(storageHead, len);
        storageUsed -= len;

        totalChars -= len;
        totalBytes -= removedBytes;
        head = (head + 1) % maxLines;
        size--;
        truncated = true;
        return true;
    }

    private void ensureStorageCapacity(int required) {
        if (required <= storage.length) {
            return;
        }
        int newCapacity = storage.length;
        while (newCapacity < required) {
            newCapacity = Math.max(newCapacity * 2, required);
        }

        char[] newStorage = new char[newCapacity];
        int copied = 0;
        for (int i = 0; i < size; i++) {
            int idx = indexAt(i);
            int len = charLengths[idx];
            int oldOffset = charOffsets[idx];
            copyFromStorage(oldOffset, newStorage, copied, len);
            charOffsets[idx] = copied;
            copied += len;
        }

        storage = newStorage;
        storageHead = 0;
        storageTail = copied;
        storageUsed = copied;
    }

    private int freeStorage() {
        return storage.length - storageUsed;
    }

    private int indexAt(int logicalIndex) {
        return (head + logicalIndex) % maxLines;
    }

    private int advance(int index, int delta) {
        int next = index + delta;
        return next >= storage.length ? next % storage.length : next;
    }

    private void copyIntoStorage(CharSequence value, int offset, int len) {
        if (len <= 0) {
            return;
        }
        int first = Math.min(len, storage.length - storageTail);
        for (int i = 0; i < first; i++) {
            storage[storageTail + i] = value.charAt(offset + i);
        }
        int remaining = len - first;
        for (int i = 0; i < remaining; i++) {
            storage[i] = value.charAt(offset + first + i);
        }
        storageTail = advance(storageTail, len);
        storageUsed += len;
    }

    private void copyFromStorage(int sourceOffset, char[] target, int targetOffset, int len) {
        if (len <= 0) {
            return;
        }
        int first = Math.min(len, storage.length - sourceOffset);
        System.arraycopy(storage, sourceOffset, target, targetOffset, first);
        int remaining = len - first;
        if (remaining > 0) {
            System.arraycopy(storage, 0, target, targetOffset + first, remaining);
        }
    }

    private void appendStoredLine(StringBuilder sb, int idx) {
        int len = charLengths[idx];
        int offset = charOffsets[idx];
        int first = Math.min(len, storage.length - offset);
        sb.append(storage, offset, first);
        if (len > first) {
            sb.append(storage, 0, len - first);
        }
    }

    private boolean equalsStoredLine(int idx, String value) {
        int len = charLengths[idx];
        if (len != value.length()) {
            return false;
        }
        int offset = charOffsets[idx];
        for (int i = 0; i < len; i++) {
            if (storage[(offset + i) % storage.length] != value.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    private static Utf8Slice fitUtf8Prefix(CharSequence value, int offset, int length, int maxBytes) {
        if (maxBytes <= 0 || length <= 0) {
            return new Utf8Slice(0, 0);
        }

        int byteCount = 0;
        int consumedChars = 0;
        int end = offset + length;

        int i = offset;
        while (i < end) {
            char c = value.charAt(i);
            int charCount = 1;
            int codePoint;

            if (Character.isHighSurrogate(c) && i + 1 < end && Character.isLowSurrogate(value.charAt(i + 1))) {
                codePoint = Character.toCodePoint(c, value.charAt(i + 1));
                charCount = 2;
            } else if (Character.isSurrogate(c)) {
                codePoint = 0xFFFD;
            } else {
                codePoint = c;
            }

            int cpBytes = utf8Length(codePoint);
            if (byteCount + cpBytes > maxBytes) {
                break;
            }

            byteCount += cpBytes;
            consumedChars += charCount;
            i += charCount;
        }

        return new Utf8Slice(consumedChars, byteCount);
    }

    private static int utf8Length(int codePoint) {
        if (codePoint <= 0x7F) {
            return 1;
        }
        if (codePoint <= 0x7FF) {
            return 2;
        }
        if (codePoint <= 0xFFFF) {
            return UTF8_REPLACEMENT_BYTES;
        }
        return 4;
    }

    private static final class Utf8Slice {
        private final int charLength;
        private final int byteLength;

        private Utf8Slice(int charLength, int byteLength) {
            this.charLength = charLength;
            this.byteLength = byteLength;
        }
    }
}
