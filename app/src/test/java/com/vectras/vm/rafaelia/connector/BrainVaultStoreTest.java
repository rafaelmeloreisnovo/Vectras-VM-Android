package com.vectras.vm.rafaelia.connector;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28)
public class BrainVaultStoreTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void testStoreAndRecall() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        store.store("key1", "value1", "cat1");
        BrainVaultStore.Entry entry = store.recall("key1");
        assertNotNull(entry);
        assertEquals("key1", entry.key);
        assertEquals("value1", entry.value);
        assertEquals("cat1", entry.category);
    }

    @Test
    public void testRecallNonexistentKey() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        assertNull(store.recall("nonexistent"));
    }

    @Test
    public void testHitCountIncrements() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        store.store("key", "val", "cat");
        BrainVaultStore.Entry first = store.recall("key");
        assertEquals(1, first.hits);
        BrainVaultStore.Entry second = store.recall("key");
        assertEquals(2, second.hits);
    }

    @Test
    public void testAutoLearnAfterThreshold() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        store.store("key", "val", "cat");
        for (int i = 0; i < BrainVaultStore.LEARN_THRESHOLD; i++) {
            store.recall("key");
        }
        BrainVaultStore.Entry entry = store.recall("key");
        assertTrue("should be learned after " + BrainVaultStore.LEARN_THRESHOLD + " hits", entry.learned);
    }

    @Test
    public void testQueryByCategory() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        store.store("a", "1", "catA");
        store.store("b", "2", "catA");
        store.store("c", "3", "catB");
        List<BrainVaultStore.Entry> results = store.queryByCategory("catA");
        assertEquals(2, results.size());
        for (BrainVaultStore.Entry entry : results) {
            assertEquals("catA", entry.category);
        }
    }

    @Test
    public void testQueryLearnedReturnsOnlyLearned() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        store.store("x", "vx", "cat");
        store.store("y", "vy", "cat");
        for (int i = 0; i <= BrainVaultStore.LEARN_THRESHOLD; i++) {
            store.recall("x");
        }
        List<BrainVaultStore.Entry> learned = store.queryLearned();
        assertEquals(1, learned.size());
        assertEquals("x", learned.get(0).key);
    }

    @Test
    public void testCrcIsComputedOnEntry() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        store.store("k", "v", "c");
        BrainVaultStore.Entry entry = store.recall("k");
        assertNotNull(entry);
        assertTrue(entry.crc32c != 0);
    }

    @Test
    public void testStoreCountReflectsEntries() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        assertEquals(0, store.totalEntries());
        store.store("a", "1", "c");
        store.store("b", "2", "c");
        assertEquals(2, store.totalEntries());
    }

    @Test
    public void testAppendOnlyLogReplaysCurrentProjection() throws Exception {
        File directory = tmp.newFolder("vault-replay");
        BrainVaultStore first = BrainVaultStore.open(directory);
        first.store("persisted-key", "persisted-value", "persisted-category");
        BrainVaultStore reopened = BrainVaultStore.open(directory);
        assertEquals(1, reopened.totalEntries());
        BrainVaultStore.Entry entry = reopened.recall("persisted-key");
        assertNotNull(entry);
        assertEquals("persisted-value", entry.value);
        assertEquals(1, entry.hits);
    }

    @Test
    public void testReplayRejectsTamperedRecordByCrc() throws Exception {
        File directory = tmp.newFolder("vault-tampered");
        BrainVaultStore store = BrainVaultStore.open(directory);
        store.store("integrity-key", "original-value", "integrity-category");
        File warmFile = new File(directory, "brainvault.jsonl");
        String original = new String(Files.readAllBytes(warmFile.toPath()), StandardCharsets.UTF_8);
        String tampered = original.replace("original-value", "tampered-value");
        assertFalse("test fixture must alter the persisted bytes", original.equals(tampered));
        Files.write(warmFile.toPath(), tampered.getBytes(StandardCharsets.UTF_8));
        BrainVaultStore reopened = BrainVaultStore.open(directory);
        assertEquals(0, reopened.totalEntries());
        assertNull(reopened.recall("integrity-key"));
    }

    @Test
    public void testFailedAppendDoesNotMutateMemoryProjection() throws Exception {
        File directory = tmp.newFolder("vault-io-failure");
        BrainVaultStore store = BrainVaultStore.open(directory);
        File warmPathAsDirectory = new File(directory, "brainvault.jsonl");
        assertTrue(warmPathAsDirectory.mkdir());
        try {
            store.store("must-not-appear", "value", "category");
            fail("store should fail when WARM path is a directory");
        } catch (IOException expected) {
            assertTrue(expected.getMessage() != null && !expected.getMessage().isBlank());
        }
        assertEquals(0, store.totalEntries());
        assertNull(store.recall("must-not-appear"));
    }
}
