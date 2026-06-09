package com.vectras.vm.rafaelia.token;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class TokenVectorizationEngineTest {

    @Test
    public void testDirectionCount() {
        assertEquals(7, TokenVectorizationEngine.DIRECTION_COUNT);
    }

    @Test
    public void testVectorizeReturnsNonNull() {
        TokenVectorizationEngine engine = TokenVectorizationEngine.create();
        TokenVectorizationEngine.VectorizedToken tok = engine.vectorize("hello");
        assertNotNull(tok);
        assertEquals("hello", tok.text);
    }

    @Test
    public void testVectorLengthIs7() {
        TokenVectorizationEngine engine = TokenVectorizationEngine.create();
        TokenVectorizationEngine.VectorizedToken tok = engine.vectorize("world");
        assertEquals(7, tok.vector.length);
    }

    @Test
    public void testAllVectorComponentsInUnitRange() {
        TokenVectorizationEngine engine = TokenVectorizationEngine.create();
        TokenVectorizationEngine.VectorizedToken tok = engine.vectorize("rafaelia");
        for (int i = 0; i < 7; i++) {
            assertTrue("v[" + i + "]=" + tok.vector[i], tok.vector[i] >= 0.0 && tok.vector[i] <= 1.0);
        }
    }

    @Test
    public void testCoherenceInUnitRange() {
        TokenVectorizationEngine engine = TokenVectorizationEngine.create();
        TokenVectorizationEngine.VectorizedToken tok = engine.vectorize("test");
        assertTrue(tok.coherence >= 0.0 && tok.coherence <= 1.0);
    }

    @Test
    public void testClassificationNotNull() {
        TokenVectorizationEngine engine = TokenVectorizationEngine.create();
        TokenVectorizationEngine.VectorizedToken tok = engine.vectorize("test");
        assertNotNull(tok.classification);
    }

    @Test
    public void testChainAssignment() {
        TokenVectorizationEngine engine = TokenVectorizationEngine.create();
        TokenVectorizationEngine.VectorizedToken tok = engine.vectorize("fibonacci");
        assertNotNull(tok.chain);
    }

    @Test
    public void testVocabularySizeGrowsAfterVectorize() {
        TokenVectorizationEngine engine = TokenVectorizationEngine.create();
        assertEquals(0, engine.size());
        engine.vectorize("a");
        engine.vectorize("b");
        assertEquals(2, engine.size());
    }

    @Test
    public void testEvolveReturnsNonNull() {
        TokenVectorizationEngine engine = TokenVectorizationEngine.create();
        engine.vectorize("strong-token-abcdefg");
        List<TokenVectorizationEngine.VectorizedToken> survivors = engine.evolve();
        assertNotNull(survivors);
    }

    @Test
    public void testGenerationIncrementsOnEvolve() {
        TokenVectorizationEngine engine = TokenVectorizationEngine.create();
        assertEquals(0, engine.generation());
        engine.evolve();
        assertEquals(1, engine.generation());
    }
}
