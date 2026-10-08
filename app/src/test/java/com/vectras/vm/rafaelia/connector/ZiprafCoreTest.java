package com.vectras.vm.rafaelia.connector;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.Assert.*;

public class ZiprafCoreTest {

    @Test
    public void testPackUnpackRoundtrip() throws Exception {
        byte[] original = "Hello RAFAELIA ZIPRAF Core test payload!".getBytes(StandardCharsets.UTF_8);
        byte[][] packed = ZiprafCore.pack(original);
        assertTrue(packed.length >= 1);
        byte[] result = ZiprafCore.unpack(packed);
        assertArrayEquals(original, result);
    }

    @Test
    public void testLargePayloadSharding() throws Exception {
        byte[] large = new byte[300 * 1024]; // 300KB > SHARD_MAX_BYTES(256KB)
        // High-entropy deterministic bytes: a repeating 256-byte pattern would
        // compress below the threshold and never exercise sharding.
        int state = 0x13579bdf;
        for (int i = 0; i < large.length; i++) {
            state = state * 1664525 + 1013904223;
            large[i] = (byte) (state >>> 24);
        }
        byte[][] shards = ZiprafCore.pack(large);
        assertTrue("expected sharding", shards.length > 1);
        byte[] result = ZiprafCore.unpack(shards);
        assertArrayEquals(large, result);
    }

    @Test
    public void testSingleBlockUnpack() throws Exception {
        byte[] data = "short payload".getBytes(StandardCharsets.UTF_8);
        byte[][] packed = ZiprafCore.pack(data);
        assertEquals(1, packed.length);
        byte[] result = ZiprafCore.unpackSingle(packed[0]);
        assertArrayEquals(data, result);
    }

    @Test
    public void testCorruptPayloadDetected() throws Exception {
        byte[] data = "integrity test".getBytes(StandardCharsets.UTF_8);
        byte[][] packed = ZiprafCore.pack(data);
        // Corrupt the payload bytes after header
        packed[0][ZiprafCore.HEADER_SIZE + 2] ^= 0xFF;
        try {
            ZiprafCore.unpack(packed);
            fail("Expected IOException for CRC mismatch");
        } catch (java.io.IOException e) {
            assertTrue(e.getMessage().contains("CRC32C") || e.getMessage().contains("SHA-256"));
        }
    }

    @Test
    public void testSha256Consistency() {
        byte[] data = "deterministic".getBytes(StandardCharsets.UTF_8);
        byte[] h1 = ZiprafCore.sha256(data);
        byte[] h2 = ZiprafCore.sha256(data);
        assertArrayEquals(h1, h2);
        assertEquals(32, h1.length);
    }

    @Test
    public void testSha256HexLength() {
        byte[] data = "test".getBytes(StandardCharsets.UTF_8);
        assertEquals(64, ZiprafCore.sha256Hex(data).length());
    }

    @Test
    public void testHeaderSize() {
        // MAGIC(4)+VERSION(1)+FLAGS(1)+SHARD_ID(2)+TOTAL(2)+RESV(2)+PAYLEN(4)+COMPLEN(4)+SHA256(32)+CRC(4)
        assertEquals(56, ZiprafCore.HEADER_SIZE);
    }

    private static void expectRejected(byte[] block) throws Exception {
        try {
            ZiprafCore.unpack(new byte[][]{block});
            fail("Malformed ZIPRAF header must fail closed");
        } catch (java.io.IOException expected) {
            assertTrue(expected.getMessage().contains("ZIPRAF"));
        }
    }

    @Test
    public void testMalformedHeaderRejectedBeforeBodyAllocation() throws Exception {
        byte[] valid = ZiprafCore.pack("bounded".getBytes(StandardCharsets.UTF_8))[0];
        byte[] wrongVersion = valid.clone();
        wrongVersion[4] = 2;
        expectRejected(wrongVersion);
        byte[] wrongShardCount = valid.clone();
        wrongShardCount[8] = 2; // TOTAL_SHARDS offset, little endian
        expectRejected(wrongShardCount);
        byte[] wrongEncodedLength = valid.clone();
        wrongEncodedLength[16] = (byte) 0xFF; // COMPRESSED_LEN offset
        expectRejected(wrongEncodedLength);
        byte[] wrongDecodedLength = valid.clone();
        wrongDecodedLength[12] = (byte) 0xFF; // PAYLOAD_LEN offset
        expectRejected(wrongDecodedLength);
    }

    @Test
    public void testEmptyPayload() throws Exception {
        byte[] empty = new byte[0];
        byte[][] packed = ZiprafCore.pack(empty);
        byte[] result = ZiprafCore.unpack(packed);
        assertArrayEquals(empty, result);
    }
}
