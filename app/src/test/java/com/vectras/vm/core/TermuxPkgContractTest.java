package com.vectras.vm.core;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TermuxPkgContractTest {

    @Test
    public void bootstrapStageCarriesFreestandingTestToolchain() {
        List<String> packages = TermuxPkgContract.packages(
                TermuxPkgContract.Stage.BOOTSTRAP_TOOLCHAIN);

        assertTrue(packages.contains("proot"));
        assertTrue(packages.contains("proot-distro"));
        assertTrue(packages.contains("ninja"));
        assertTrue(packages.contains("clang"));
        assertTrue(packages.contains("lld"));
        assertTrue(packages.contains("cmake"));
        assertTrue(packages.contains("x11-repo"));
    }

    @Test
    public void qemuStageIsSeparatedFromRepositoryBootstrap() {
        List<String> bootstrap = TermuxPkgContract.packages(
                TermuxPkgContract.Stage.BOOTSTRAP_TOOLCHAIN);
        List<String> qemu = TermuxPkgContract.packages(
                TermuxPkgContract.Stage.VECTRAS_QEMU);

        assertTrue(bootstrap.contains("x11-repo"));
        assertFalse(bootstrap.contains("qemu-system-x86-64-headless"));
        assertTrue(qemu.contains("qemu-system-x86-64-headless"));
        assertTrue(qemu.contains("qemu-utils"));
    }

    @Test
    public void argvIsDirectAndDeterministic() {
        List<String> argv = TermuxPkgContract.pkgInstallArgv(
                TermuxPkgContract.Stage.BOOTSTRAP_TOOLCHAIN);

        assertEquals("pkg", argv.get(0));
        assertEquals("install", argv.get(1));
        assertEquals("-y", argv.get(2));
        assertEquals("bash", argv.get(3));
        assertFalse(TermuxPkgContract.hasDuplicatePackages());
    }
}
