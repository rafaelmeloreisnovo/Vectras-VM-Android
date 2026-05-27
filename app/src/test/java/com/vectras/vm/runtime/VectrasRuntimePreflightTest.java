package com.vectras.vm.runtime;

import org.junit.Test;
import static org.junit.Assert.assertNotNull;

public class VectrasRuntimePreflightTest {
    @Test
    public void statusEnumExposesExpectedValues() {
        assertNotNull(VectrasRuntimePreflight.Status.BLOCKER);
        assertNotNull(VectrasRuntimePreflight.Status.PASS);
    }
}
