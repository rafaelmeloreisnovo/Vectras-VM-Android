package com.vectras.vm.rafaelia;

import org.junit.Test;

import static org.junit.Assert.*;

public class VectraCoreKernelRuntimeTest {

    @Test
    public void testInitialRIsZero() {
        VectraCoreKernelRuntime rt = VectraCoreKernelRuntime.create();
        assertEquals(0.0, rt.r(), 1e-9);
    }

    @Test
    public void testUpdateIncreasesR() {
        VectraCoreKernelRuntime rt = VectraCoreKernelRuntime.create();
        double r1 = rt.update(0.8, 0.5);
        assertTrue("R should increase after update with positive energy", r1 > 0.0);
    }

    @Test
    public void testRIsClampedToUnitRange() {
        VectraCoreKernelRuntime rt = VectraCoreKernelRuntime.create();
        for (int i = 0; i < 100; i++) rt.update(1.0, 1.0);
        assertTrue("R must stay ≤ 1.0", rt.r() <= 1.0);
    }

    @Test
    public void testDefaultPhiEthics() {
        VectraCoreKernelRuntime rt = VectraCoreKernelRuntime.create();
        assertEquals(VectraCoreKernelRuntime.PHI_ETHICS_DEFAULT, rt.phiEthics(), 1e-9);
    }

    @Test
    public void testSetPhiEthicsClamped() {
        VectraCoreKernelRuntime rt = VectraCoreKernelRuntime.create();
        rt.setPhiEthics(2.0);
        assertEquals(1.0, rt.phiEthics(), 1e-9);
        rt.setPhiEthics(-1.0);
        assertEquals(0.0, rt.phiEthics(), 1e-9);
    }

    @Test
    public void testPipelineCyclesThroughPhases() {
        VectraCoreKernelRuntime rt = VectraCoreKernelRuntime.create();
        rt.update(0.5, 0.5);
        assertEquals(VectraCoreKernelRuntime.PipelinePhase.DELTA, rt.phase());
        rt.update(0.5, 0.5);
        assertEquals(VectraCoreKernelRuntime.PipelinePhase.SIGMA, rt.phase());
        rt.update(0.5, 0.5);
        assertEquals(VectraCoreKernelRuntime.PipelinePhase.OMEGA, rt.phase());
        rt.update(0.5, 0.5);
        assertEquals(VectraCoreKernelRuntime.PipelinePhase.PSI, rt.phase());
    }

    @Test
    public void testAuditLedgerGrows() {
        VectraCoreKernelRuntime rt = VectraCoreKernelRuntime.create();
        rt.audit("TEST", "OK");
        rt.audit("TEST", "WARN");
        assertEquals(2, rt.auditLedger().size());
    }

    @Test
    public void testSplitBuffMeanInUnitRange() {
        VectraCoreKernelRuntime rt = VectraCoreKernelRuntime.create();
        for (int i = 0; i < 10; i++) rt.update(0.5, 0.5);
        double mean = rt.splitBuffMean();
        assertTrue(mean >= 0.0 && mean <= 1.0);
    }

    @Test
    public void testTickCountIncrements() {
        VectraCoreKernelRuntime rt = VectraCoreKernelRuntime.create();
        assertEquals(0, rt.tickCount());
        rt.update(0.5, 0.5);
        assertEquals(1, rt.tickCount());
    }

    @Test
    public void testArchIsKnown() {
        VectraCoreKernelRuntime rt = VectraCoreKernelRuntime.create();
        assertNotNull(rt.arch());
    }
}
