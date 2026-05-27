package com.vectras.vm.qemu;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class QemuExecConfigTest {
    @Test
    public void strictModeFallbackDisabledByDefault() {
        assertFalse(QemuExecConfig.ALLOW_QEMU_NAME_FALLBACK);
    }
}
