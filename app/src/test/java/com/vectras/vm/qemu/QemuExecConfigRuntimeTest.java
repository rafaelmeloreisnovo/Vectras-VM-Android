package com.vectras.vm.qemu;

import org.junit.Assert;
import org.junit.Test;

public class QemuExecConfigRuntimeTest {

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
}
