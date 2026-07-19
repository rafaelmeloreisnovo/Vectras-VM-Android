package com.vectras.vm.qemu;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

public class QemuExecConfigRuntimeTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void androidAbisMapToArtifactRuntimeArchitectures() {
        Assert.assertEquals("aarch64", QemuExecConfig.normalizeHostAbi("arm64-v8a"));
        Assert.assertEquals("arm", QemuExecConfig.normalizeHostAbi("armeabi-v7a"));
        Assert.assertEquals("x86_64", QemuExecConfig.normalizeHostAbi("x86_64"));
        Assert.assertEquals("i386", QemuExecConfig.normalizeHostAbi("x86"));
    }

    @Test
    public void ambiguousAbiIsRejected() {
        Assert.assertEquals("", QemuExecConfig.normalizeHostAbi("mips64"));
        Assert.assertEquals("", QemuExecConfig.normalizeHostAbi(null));
    }

    @Test
    public void alpineMarkerSelectsMusl() throws Exception {
        File rootfs = temporaryFolder.newFolder("alpine-rootfs");
        File etc = new File(rootfs, "etc");
        Assert.assertTrue(etc.mkdirs());
        Assert.assertTrue(new File(etc, "alpine-release").createNewFile());

        Assert.assertEquals("musl", QemuExecConfig.detectRootfsLibc(rootfs));
    }

    @Test
    public void glibcLoaderSelectsGlibc() throws Exception {
        File rootfs = temporaryFolder.newFolder("glibc-rootfs");
        File lib = new File(rootfs, "lib");
        Assert.assertTrue(lib.mkdirs());
        Assert.assertTrue(new File(lib, "ld-linux-aarch64.so.1").createNewFile());

        Assert.assertEquals("glibc", QemuExecConfig.detectRootfsLibc(rootfs));
    }

    @Test
    public void unknownRootfsIsRejected() throws Exception {
        File rootfs = temporaryFolder.newFolder("unknown-rootfs");
        Assert.assertEquals("", QemuExecConfig.detectRootfsLibc(rootfs));
        Assert.assertEquals("", QemuExecConfig.detectRootfsLibc(null));
    }
}
