package com.vectras.vm.qemu;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class QemuExecConfigTest {
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void strictModeFallbackDisabledByDefault() {
        assertFalse(QemuExecConfig.ALLOW_QEMU_NAME_FALLBACK);
    }

    // normalizeHostAbi: artifact key mapping

    @Test
    public void normalizeHostAbi_arm64v8a_yields_aarch64() {
        assertEquals("aarch64", QemuExecConfig.normalizeHostAbi("arm64-v8a"));
    }

    @Test
    public void normalizeHostAbi_aarch64_yields_aarch64() {
        assertEquals("aarch64", QemuExecConfig.normalizeHostAbi("aarch64"));
    }

    @Test
    public void normalizeHostAbi_armeabi_v7a_yields_arm() {
        assertEquals("arm", QemuExecConfig.normalizeHostAbi("armeabi-v7a"));
    }

    @Test
    public void normalizeHostAbi_x86_64_yields_x86_64() {
        assertEquals("x86_64", QemuExecConfig.normalizeHostAbi("x86_64"));
    }

    @Test
    public void normalizeHostAbi_x86_yields_i386() {
        assertEquals("i386", QemuExecConfig.normalizeHostAbi("x86"));
    }

    @Test
    public void normalizeHostAbi_unknown_yields_empty() {
        assertEquals("", QemuExecConfig.normalizeHostAbi("mips"));
    }

    // detectRootfsArch: loader-based arch detection

    @Test
    public void detectRootfsArch_aarch64_musl() throws Exception {
        File rootfs = tmp.newFolder("rootfs-aarch64");
        makeFile(rootfs, "lib/ld-musl-aarch64.so.1");
        assertEquals("aarch64", QemuExecConfig.detectRootfsArch(rootfs));
    }

    @Test
    public void detectRootfsArch_aarch64_glibc() throws Exception {
        File rootfs = tmp.newFolder("rootfs-aarch64-glibc");
        makeFile(rootfs, "lib/ld-linux-aarch64.so.1");
        assertEquals("aarch64", QemuExecConfig.detectRootfsArch(rootfs));
    }

    @Test
    public void detectRootfsArch_arm_musl_armhf() throws Exception {
        File rootfs = tmp.newFolder("rootfs-arm");
        makeFile(rootfs, "lib/ld-musl-armhf.so.1");
        assertEquals("arm", QemuExecConfig.detectRootfsArch(rootfs));
    }

    @Test
    public void detectRootfsArch_arm_glibc() throws Exception {
        File rootfs = tmp.newFolder("rootfs-arm-glibc");
        makeFile(rootfs, "lib/ld-linux-armhf.so.3");
        assertEquals("arm", QemuExecConfig.detectRootfsArch(rootfs));
    }

    @Test
    public void detectRootfsArch_x86_64_glibc() throws Exception {
        File rootfs = tmp.newFolder("rootfs-x86_64");
        makeFile(rootfs, "lib64/ld-linux-x86-64.so.2");
        assertEquals("x86_64", QemuExecConfig.detectRootfsArch(rootfs));
    }

    @Test
    public void detectRootfsArch_i386() throws Exception {
        File rootfs = tmp.newFolder("rootfs-i386");
        makeFile(rootfs, "lib/ld-linux.so.2");
        assertEquals("i386", QemuExecConfig.detectRootfsArch(rootfs));
    }

    @Test
    public void detectRootfsArch_empty_rootfs_returns_empty() throws Exception {
        File rootfs = tmp.newFolder("rootfs-empty");
        assertEquals("", QemuExecConfig.detectRootfsArch(rootfs));
    }

    @Test
    public void detectRootfsArch_null_returns_empty() {
        assertEquals("", QemuExecConfig.detectRootfsArch(null));
    }

    private static void makeFile(File rootfs, String relativePath) throws Exception {
        File f = new File(rootfs, relativePath);
        f.getParentFile().mkdirs();
        f.createNewFile();
    }
}
