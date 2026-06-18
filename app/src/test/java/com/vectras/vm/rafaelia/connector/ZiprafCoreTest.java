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
        for (int i = 0; i < large.length; i++) large[i] = (byte)(i ^ 0xAB);
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
        assertEquals(52, ZiprafCore.HEADER_SIZE);
    }

    @Test
    public void testEmptyPayload() throws Exception {
        byte[] empty = new byte[0];
        byte[][] packed = ZiprafCore.pack(empty);
        byte[] result = ZiprafCore.unpack(packed);
        assertArrayEquals(empty, result);
    }
}
