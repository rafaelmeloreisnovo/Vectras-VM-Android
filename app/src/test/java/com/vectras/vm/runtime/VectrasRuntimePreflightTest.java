package com.vectras.vm.runtime;

import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class VectrasRuntimePreflightTest {
    @Test
    public void failsWhenShellMissing() throws Exception {
        File base = Files.createTempDirectory("vectras-preflight").toFile();
        File proot = new File(base, "proot");
        proot.createNewFile();
        proot.setExecutable(true);
        File rootfs = new File(base, "distro");
        rootfs.mkdirs();
        VectrasRuntimePreflight.Result result = VectrasRuntimePreflight.evaluate(
                proot.getAbsolutePath(), rootfs.getAbsolutePath(), new File(rootfs, "bin/sh").getAbsolutePath(),
                new File(base, "usr/tmp").getAbsolutePath(), new File(rootfs, "root").getAbsolutePath(), true, Collections.emptyList());
        assertFalse(result.ok);
        assertFalse(result.hasShell);
    }

    @Test
    public void failsWhenProotMissing() throws Exception {
        File base = Files.createTempDirectory("vectras-preflight").toFile();
        File rootfs = new File(base, "distro");
        File shell = new File(rootfs, "bin/sh");
        shell.getParentFile().mkdirs();
        rootfs.mkdirs();
        shell.createNewFile();
        shell.setExecutable(true);
        VectrasRuntimePreflight.Result result = VectrasRuntimePreflight.evaluate(
                new File(base, "missing-proot").getAbsolutePath(), rootfs.getAbsolutePath(), shell.getAbsolutePath(),
                new File(base, "usr/tmp").getAbsolutePath(), new File(rootfs, "root").getAbsolutePath(), true, Collections.emptyList());
        assertFalse(result.ok);
        assertFalse(result.hasProot);
    }

    @Test
    public void passesWhenAllPathsPresent() throws Exception {
        File base = Files.createTempDirectory("vectras-preflight").toFile();
        File proot = new File(base, "proot");
        File rootfs = new File(base, "distro");
        File shell = new File(rootfs, "bin/sh");
        File tmp = new File(base, "usr/tmp");
        File home = new File(rootfs, "root");
        proot.createNewFile(); proot.setExecutable(true);
        shell.getParentFile().mkdirs(); shell.createNewFile(); shell.setExecutable(true);
        tmp.mkdirs(); home.mkdirs();
        VectrasRuntimePreflight.Result result = VectrasRuntimePreflight.evaluate(
                proot.getAbsolutePath(), rootfs.getAbsolutePath(), shell.getAbsolutePath(), tmp.getAbsolutePath(), home.getAbsolutePath(), true, Collections.emptyList());
        assertTrue(result.ok);
    }
}
