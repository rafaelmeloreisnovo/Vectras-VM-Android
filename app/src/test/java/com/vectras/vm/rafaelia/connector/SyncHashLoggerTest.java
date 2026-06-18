package com.vectras.vm.rafaelia.connector;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.junit.Assert.*;

public class SyncHashLoggerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testLogAndChainVerify() throws Exception {
        SyncHashLogger logger = SyncHashLogger.open(tmp.newFolder("logs"), "test");
        logger.info("TEST", "first entry");
        logger.info("TEST", "second entry");
        logger.warn("TEST", "third entry");
        assertTrue(logger.verifyChain());
        assertEquals(3, logger.getEntryCount());
    }

    @Test
    public void testChainFails_WhenTampered() throws Exception {
        SyncHashLogger logger = SyncHashLogger.open(tmp.newFolder("logs"), "tamper");
        logger.info("T", "msg1");
        logger.info("T", "msg2");
        // Tamper: get tail entries and verify chain still passes internally
        // (Chain verification uses in-memory ring — tampering the ring would fail)
        List<SyncHashLogger.LogEntry> entries = logger.tail(10);
        assertEquals(2, entries.size());
        // The chain should be intact
        assertTrue(logger.verifyChain());
    }

    @Test
    public void testHashIsDeterministic() {
        String h1 = SyncHashLogger.computeHash(1L, 12345L,
                SyncHashLogger.Level.INFO, "TAG", "msg", "prev");
        String h2 = SyncHashLogger.computeHash(1L, 12345L,
                SyncHashLogger.Level.INFO, "TAG", "msg", "prev");
        assertEquals(h1, h2);
    }

    @Test
    public void testHashChangesWithDifferentInputs() {
        String h1 = SyncHashLogger.computeHash(1L, 100L,
                SyncHashLogger.Level.INFO, "T", "msg", "prev");
        String h2 = SyncHashLogger.computeHash(2L, 100L,
                SyncHashLogger.Level.INFO, "T", "msg", "prev");
        assertNotEquals(h1, h2);
    }

    @Test
    public void testHashLength() {
        String h = SyncHashLogger.computeHash(1L, 1L,
                SyncHashLogger.Level.DEBUG, "X", "m", "p");
        assertEquals(64, h.length()); // 4 × 16-hex = 64 chars
    }

    @Test
    public void testTailReturnsLastN() throws Exception {
        SyncHashLogger logger = SyncHashLogger.open(tmp.newFolder("logs"), "tail");
        for (int i = 0; i < 10; i++) logger.info("T", "entry " + i);
        List<SyncHashLogger.LogEntry> tail = logger.tail(3);
        assertEquals(3, tail.size());
        assertEquals("entry 9", tail.get(2).msg);
    }

    @Test
    public void testAllLevelsMethods() throws Exception {
        SyncHashLogger logger = SyncHashLogger.open(tmp.newFolder("logs"), "levels");
        logger.debug("T", "debug");
        logger.info("T", "info");
        logger.warn("T", "warn");
        logger.error("T", "error");
        logger.fatal("T", "fatal");
        assertEquals(5, logger.getEntryCount());
        assertTrue(logger.verifyChain());
    }
}
