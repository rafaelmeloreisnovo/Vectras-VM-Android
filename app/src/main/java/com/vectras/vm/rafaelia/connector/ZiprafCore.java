package com.vectras.vm.rafaelia.connector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32C;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

/**
 * ZIPRAF Core — ZIPRAF/SHARDS/CORE connector.
 *
 * <p>Implements the compression, signature, and sharding layer from the RAFAELIA
 * connector map. Each payload is:
 * <pre>
 *   ZIPRAF Block format:
 *   [MAGIC:4B][VERSION:1B][FLAGS:1B][SHARD_ID:2B][TOTAL_SHARDS:2B][RESERVED:2B]
 *   [PAYLOAD_LEN:4B][COMPRESSED_LEN:4B][SHA256:32B][CRC32C:4B][PAYLOAD:N]
 * </pre>
 *
 * <p>Compression uses DEFLATE (best compression level by default).
 * Signature uses SHA-256 over uncompressed payload.
 * Sharding splits payloads larger than SHARD_MAX_BYTES into N shards.
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-ZIPRAF
 */
public final class ZiprafCore {

    // ─── Block header constants ───────────────────────────────────────────────
    static final int  MAGIC            = 0x5A495052;  // "ZIPR"
    static final byte VERSION          = 0x01;
    static final int  HEADER_SIZE      = 4+1+1+2+2+2+4+4+32+4;  // 52 bytes
    static final int  SHARD_MAX_BYTES  = 256 * 1024;             // 256 KB per shard
    static final int  MAX_PAYLOAD_BYTES = 64 * 1024 * 1024;      // 64 MB total

    // Flags
    static final byte FLAG_COMPRESSED  = 0x01;
    static final byte FLAG_SHARDED     = 0x02;
    static final byte FLAG_SIGNED      = 0x04;

    private static final ThreadLocal<CRC32C> CRC_POOL =
            ThreadLocal.withInitial(CRC32C::new);

    private ZiprafCore() {}

    // ─── Compress + sign + pack ───────────────────────────────────────────────

    /**
     * Pack a payload into one or more ZIPRAF blocks (auto-shard if needed).
     *
     * @param payload      raw bytes to pack
     * @param compressionLevel DEFLATE level 0-9
     * @return array of packed ZIPRAF blocks (length 1 if no sharding needed)
     */
    @NonNull
    public static byte[][] pack(@NonNull byte[] payload, int compressionLevel) throws IOException {
        if (payload.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("payload exceeds 64MB limit");
        }

        byte[] sha256 = sha256(payload);
        byte[] compressed = compress(payload, compressionLevel);
        boolean didCompress = compressed.length < payload.length;
        byte[] body = didCompress ? compressed : payload;

        if (body.length <= SHARD_MAX_BYTES) {
            return new byte[][]{ buildBlock(body, payload.length, sha256, (short) 0, (short) 1, didCompress) };
        }

        // Sharding
        int numShards = (body.length + SHARD_MAX_BYTES - 1) / SHARD_MAX_BYTES;
        if (numShards > Short.MAX_VALUE) throw new IllegalArgumentException("too many shards");

        byte[][] shards = new byte[numShards][];
        for (int i = 0; i < numShards; i++) {
            int off = i * SHARD_MAX_BYTES;
            int len = Math.min(SHARD_MAX_BYTES, body.length - off);
            byte[] shardBody = new byte[len];
            System.arraycopy(body, off, shardBody, 0, len);
            shards[i] = buildBlock(shardBody, payload.length, sha256,
                    (short) i, (short) numShards, didCompress);
        }
        return shards;
    }

    /** Pack with default best-compression level. */
    @NonNull
    public static byte[][] pack(@NonNull byte[] payload) throws IOException {
        return pack(payload, Deflater.BEST_COMPRESSION);
    }

    // ─── Unpack + verify ──────────────────────────────────────────────────────

    /**
     * Reassemble shards and return the original payload.
     * Verifies SHA-256 signature and CRC32C of each shard.
     */
    @NonNull
    public static byte[] unpack(@NonNull byte[][] shards) throws IOException {
        if (shards.length == 0) throw new IOException("empty shards");

        // Parse all shard headers and collect bodies
        byte[] expectedSha256 = null;
        int    origLen        = 0;
        boolean compressed    = false;
        byte[][] bodies       = new byte[shards.length][];

        for (int i = 0; i < shards.length; i++) {
            BlockHeader h = parseHeader(shards[i]);
            if (h.magic != MAGIC) throw new IOException("bad ZIPRAF magic at shard " + i);
            validateCrc(shards[i], h);

            if (i == 0) {
                expectedSha256 = h.sha256;
                origLen        = h.payloadLen;
                compressed     = (h.flags & FLAG_COMPRESSED) != 0;
            }
            bodies[i] = new byte[h.compressedLen];
            System.arraycopy(shards[i], HEADER_SIZE, bodies[i], 0, h.compressedLen);
        }

        // Reassemble body
        int totalBodyLen = 0;
        for (byte[] b : bodies) totalBodyLen += b.length;
        byte[] fullBody = new byte[totalBodyLen];
        int pos = 0;
        for (byte[] b : bodies) {
            System.arraycopy(b, 0, fullBody, pos, b.length);
            pos += b.length;
        }

        byte[] result = compressed ? decompress(fullBody, origLen) : fullBody;

        // Verify signature
        byte[] actualSha256 = sha256(result);
        if (!MessageDigest.isEqual(actualSha256, expectedSha256)) {
            throw new IOException("ZIPRAF SHA-256 signature mismatch — data corrupted");
        }

        return result;
    }

    /** Convenience: unpack single block. */
    @NonNull
    public static byte[] unpackSingle(@NonNull byte[] block) throws IOException {
        return unpack(new byte[][]{ block });
    }

    // ─── Signature / hash utilities ───────────────────────────────────────────

    @NonNull
    public static byte[] sha256(@NonNull byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @NonNull
    public static String sha256Hex(@NonNull byte[] data) {
        byte[] raw = sha256(data);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : raw) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    static long crc32c(@NonNull byte[] data, int off, int len) {
        CRC32C crc = CRC_POOL.get();
        crc.reset();
        crc.update(data, off, len);
        return crc.getValue();
    }

    // ─── Internal block builder ───────────────────────────────────────────────

    private static byte[] buildBlock(byte[] body, int origLen, byte[] sha256,
                                     short shardId, short totalShards,
                                     boolean compressed) {
        byte flags = FLAG_SIGNED;
        if (compressed) flags |= FLAG_COMPRESSED;
        if (totalShards > 1) flags |= FLAG_SHARDED;

        // CRC32C over body
        long crcVal = crc32c(body, 0, body.length);

        ByteBuffer buf = ByteBuffer.allocate(HEADER_SIZE + body.length)
                .order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(MAGIC);
        buf.put(VERSION);
        buf.put(flags);
        buf.putShort(shardId);
        buf.putShort(totalShards);
        buf.putShort((short) 0);           // reserved
        buf.putInt(origLen);
        buf.putInt(body.length);
        buf.put(sha256);                   // 32 bytes
        buf.putInt((int) crcVal);
        buf.put(body);
        return buf.array();
    }

    private static BlockHeader parseHeader(byte[] block) throws IOException {
        if (block.length < HEADER_SIZE) throw new IOException("block too small");
        ByteBuffer buf = ByteBuffer.wrap(block).order(ByteOrder.LITTLE_ENDIAN);
        BlockHeader h = new BlockHeader();
        h.magic        = buf.getInt();
        h.version      = buf.get();
        h.flags        = buf.get();
        h.shardId      = buf.getShort();
        h.totalShards  = buf.getShort();
        buf.getShort();                    // reserved
        h.payloadLen   = buf.getInt();
        h.compressedLen = buf.getInt();
        h.sha256       = new byte[32];
        buf.get(h.sha256);
        h.crc32c       = buf.getInt() & 0xFFFFFFFFL;
        return h;
    }

    private static void validateCrc(byte[] block, BlockHeader h) throws IOException {
        long actual = crc32c(block, HEADER_SIZE, h.compressedLen);
        if (actual != h.crc32c) {
            throw new IOException("ZIPRAF CRC32C mismatch shard=" + h.shardId
                    + " expected=" + h.crc32c + " got=" + actual);
        }
    }

    private static byte[] compress(byte[] data, int level) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(data.length / 2);
        Deflater deflater = new Deflater(level, true);
        try (DeflaterOutputStream dos = new DeflaterOutputStream(baos, deflater)) {
            dos.write(data);
        } finally {
            deflater.end();
        }
        return baos.toByteArray();
    }

    private static byte[] decompress(byte[] compressed, int expectedLen) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(expectedLen);
        Inflater inflater = new Inflater(true);
        try (InflaterInputStream iis = new InflaterInputStream(
                new ByteArrayInputStream(compressed), inflater)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = iis.read(buf)) != -1) baos.write(buf, 0, n);
        } finally {
            inflater.end();
        }
        return baos.toByteArray();
    }

    private static final class BlockHeader {
        int   magic;
        byte  version, flags;
        short shardId, totalShards;
        int   payloadLen, compressedLen;
        byte[] sha256;
        long  crc32c;
    }
}
