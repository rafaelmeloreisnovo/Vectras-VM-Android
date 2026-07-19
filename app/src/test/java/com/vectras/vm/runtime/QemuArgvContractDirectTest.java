package com.vectras.vm.runtime;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class QemuArgvContractDirectTest {

    @Test
    public void shellCompatibilityInputBecomesDirectProcessArgv() {
        QemuArgvContract contract = QemuArgvContract.fromShellCommand(
                "cd /root && /usr/bin/qemu-system-aarch64 -name 'VM one' -drive file='/sdcard/a b.qcow2'"
        );

        Assert.assertTrue(contract.hasRecognizedQemuBinary());
        Assert.assertEquals(3, contract.getQemuTokenIndex());
        Assert.assertEquals("/usr/bin/qemu-system-aarch64", contract.toProcessArgv().get(0));
        Assert.assertEquals("VM one", contract.toProcessArgv().get(2));
        Assert.assertEquals("file=/sdcard/a b.qcow2", contract.toProcessArgv().get(4));
        Assert.assertFalse(contract.toProcessArgv().contains("cd"));
        Assert.assertFalse(contract.toProcessArgv().contains("&&"));
    }

    @Test
    public void shellOperatorsAfterQemuRemainLiteralArguments() {
        QemuArgvContract contract = QemuArgvContract.fromShellCommand(
                "qemu-system-x86_64 -name safe ; touch /sdcard/injected"
        );

        List<String> argv = contract.toProcessArgv();
        Assert.assertEquals("qemu-system-x86_64", argv.get(0));
        Assert.assertTrue(argv.contains(";"));
        Assert.assertTrue(argv.contains("touch"));
        Assert.assertTrue(argv.contains("/sdcard/injected"));
    }

    @Test(expected = IllegalStateException.class)
    public void nonQemuCommandCannotUseDirectDispatch() {
        QemuArgvContract.fromShellCommand("echo hello").toProcessArgv();
    }

    @Test
    public void explicitArgsPreserveArgumentBoundaries() {
        QemuArgvContract contract = QemuArgvContract.fromArgs(
                "/opt/qemu-system-i386",
                Arrays.asList("-name", "VM with spaces", "-nographic")
        );

        Assert.assertEquals(Arrays.asList(
                "/opt/qemu-system-i386", "-name", "VM with spaces", "-nographic"
        ), contract.toProcessArgv());
    }
}
