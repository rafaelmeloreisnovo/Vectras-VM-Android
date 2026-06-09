package com.vectras.vm.rafaelia.connector;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.List;

import static org.junit.Assert.*;

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
        BrainVaultStore.Entry e1 = store.recall("key");
        assertEquals(1, e1.hits);
        BrainVaultStore.Entry e2 = store.recall("key");
        assertEquals(2, e2.hits);
    }

    @Test
    public void testAutoLearnAfterThreshold() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        store.store("key", "val", "cat");
        for (int i = 0; i < BrainVaultStore.LEARN_THRESHOLD; i++) {
            store.recall("key");
        }
        BrainVaultStore.Entry e = store.recall("key");
        assertTrue("should be learned after " + BrainVaultStore.LEARN_THRESHOLD + " hits", e.learned);
    }

    @Test
    public void testQueryByCategory() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        store.store("a", "1", "catA");
        store.store("b", "2", "catA");
        store.store("c", "3", "catB");
        List<BrainVaultStore.Entry> results = store.queryByCategory("catA");
        assertEquals(2, results.size());
        for (BrainVaultStore.Entry e : results) assertEquals("catA", e.category);
    }

    @Test
    public void testQueryLearnedReturnsOnlyLearned() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        store.store("x", "vx", "cat");
        store.store("y", "vy", "cat");
        // Only recall "x" enough to learn it
        for (int i = 0; i <= BrainVaultStore.LEARN_THRESHOLD; i++) store.recall("x");
        List<BrainVaultStore.Entry> learned = store.queryLearned();
        assertEquals(1, learned.size());
        assertEquals("x", learned.get(0).key);
    }

    @Test
    public void testCrcIsComputedOnEntry() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        store.store("k", "v", "c");
        BrainVaultStore.Entry e = store.recall("k");
        assertNotNull(e);
        assertTrue(e.crc32c != 0);
    }

    @Test
    public void testStoreCountReflectsEntries() throws Exception {
        BrainVaultStore store = BrainVaultStore.open(tmp.newFolder("vault"));
        assertEquals(0, store.totalEntries());
        store.store("a", "1", "c");
        store.store("b", "2", "c");
        assertEquals(2, store.totalEntries());
    }
}
