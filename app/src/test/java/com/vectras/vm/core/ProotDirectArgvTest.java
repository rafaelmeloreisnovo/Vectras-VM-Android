package com.vectras.vm.core;

import android.content.Context;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class ProotDirectArgvTest {

    @Test
    public void directGuestArgvReplacesLoginShellWithoutJoiningArguments() {
        Context context = Mockito.mock(Context.class);
        Mockito.when(context.getFilesDir()).thenReturn(new File("/data/user/0/com.vectras.vm/files"));

        ProotCommandBuilder builder = new ProotCommandBuilder(
                context,
                "/data/user/0/com.vectras.vm/files/distro",
                "/root"
        );

        List<String> command = builder.buildCommand(Arrays.asList(
                "/usr/bin/qemu-system-aarch64",
                "-name",
                "VM with spaces",
                ";",
                "touch"
        ));

        int executableIndex = command.indexOf("/usr/bin/qemu-system-aarch64");
        Assert.assertTrue(executableIndex > 0);
        Assert.assertEquals("-name", command.get(executableIndex + 1));
        Assert.assertEquals("VM with spaces", command.get(executableIndex + 2));
        Assert.assertEquals(";", command.get(executableIndex + 3));
        Assert.assertEquals("touch", command.get(executableIndex + 4));
        Assert.assertFalse(command.contains("--login"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void directGuestArgvRejectsBlankExecutable() {
        Context context = Mockito.mock(Context.class);
        Mockito.when(context.getFilesDir()).thenReturn(new File("/tmp/files"));
        new ProotCommandBuilder(context, "/rootfs", "/root")
                .buildCommand(Arrays.asList("  ", "-version"));
    }
}
