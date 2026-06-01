package com.vectras.vm.setupwizard;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@RunWith(RobolectricTestRunner.class)
public class SetupFeatureCoreBootstrapValidationTest {

    @Test
    public void validationFailsWhenBootstrapFilesMissing() {
        Context context = RuntimeEnvironment.getApplication();
        SetupFeatureCore.ProotBootstrapValidationResult result = SetupFeatureCore.validateProotBootstrapState(context);
        Assert.assertFalse(result.ok);
        Assert.assertTrue(result.errors.contains("missing-proot") || result.errors.contains("proot-not-executable"));
    }


    @Test
    public void isInstalledProotShouldNotRequireDistroFiles() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File filesDir = context.getFilesDir();
        File proot = new File(filesDir, "usr/bin/proot");
        File tmp = new File(filesDir, "usr/tmp");

        proot.getParentFile().mkdirs();
        tmp.mkdirs();
        proot.createNewFile();
        proot.setExecutable(true, true);

        assertTrue(SetupFeatureCore.isInstalledProot(context));
        Assert.assertFalse(SetupFeatureCore.validateProotBootstrapState(context).ok);
    }

    @Test
    public void validationSucceedsWhenRequiredFilesPresent() throws IOException {
        Context context = RuntimeEnvironment.getApplication();
        File filesDir = context.getFilesDir();
        File proot = new File(filesDir, "usr/bin/proot");
        File busybox = new File(filesDir, "distro/bin/busybox");
        File shell = new File(filesDir, "distro/bin/sh");
        File tmp = new File(filesDir, "usr/tmp");

        proot.getParentFile().mkdirs();
        busybox.getParentFile().mkdirs();
        shell.getParentFile().mkdirs();
        tmp.mkdirs();

        proot.createNewFile();
        busybox.createNewFile();
        shell.createNewFile();
        proot.setExecutable(true, true);
        busybox.setExecutable(true, true);
        shell.setExecutable(true, true);

        SetupFeatureCore.ProotBootstrapValidationResult result = SetupFeatureCore.validateProotBootstrapState(context);
        Assert.assertTrue(result.summary(), result.ok);
    }
    @Test
    public void bootstrapRollbackRestoreShouldPreservePreviousUsrOnFailure() throws Exception {
        File filesDir = Files.createTempDirectory("bootstrap-rollback-usr").toFile();
        File usrBin = new File(filesDir, "usr/bin");
        assertTrue(usrBin.mkdirs());
        File originalProot = new File(usrBin, "proot");
        assertTrue(originalProot.createNewFile());

        SetupFeatureCore.BootstrapRollback rollback = SetupFeatureCore.BootstrapRollback.prepare(
                filesDir.toPath(),
                filesDir.toPath(),
                "bootstrap",
                "token"
        );
        assertTrue(rollback.ready);
        assertFalse("live usr should be staged out before extraction", new File(filesDir, "usr").exists());
        assertTrue(new File(filesDir, "usr/bin").mkdirs());
        assertTrue(new File(filesDir, "usr/bin/broken-proot").createNewFile());

        rollback.restore();

        assertTrue("original proot restored", originalProot.exists());
        assertFalse("partial extraction removed", new File(filesDir, "usr/bin/broken-proot").exists());
    }

    @Test
    public void bootstrapRollbackCommitShouldDiscardBackupAndKeepNewUsr() throws Exception {
        File filesDir = Files.createTempDirectory("bootstrap-rollback-commit").toFile();
        File usrBin = new File(filesDir, "usr/bin");
        assertTrue(usrBin.mkdirs());
        assertTrue(new File(usrBin, "old-proot").createNewFile());

        SetupFeatureCore.BootstrapRollback rollback = SetupFeatureCore.BootstrapRollback.prepare(
                filesDir.toPath(),
                filesDir.toPath(),
                "bootstrap",
                "token"
        );
        assertTrue(rollback.ready);
        assertTrue(new File(filesDir, "usr/bin").mkdirs());
        File newProot = new File(filesDir, "usr/bin/proot");
        assertTrue(newProot.createNewFile());

        rollback.commit();

        assertTrue(newProot.exists());
        assertFalse(new File(filesDir, "usr/bin/old-proot").exists());
        assertEquals(0, filesDir.listFiles((dir, name) -> name.startsWith(".bootstrap-rollback-")).length);
    }

    @Test
    public void bootstrapRollbackRejectsFilesDirAsLiveTarget() throws Exception {
        File filesDir = Files.createTempDirectory("bootstrap-rollback-reject").toFile();
        SetupFeatureCore.BootstrapRollback rollback = SetupFeatureCore.BootstrapRollback.prepare(
                filesDir.toPath(),
                filesDir.toPath(),
                "alpine19",
                "token"
        );

        assertFalse(rollback.ready);
        assertTrue(rollback.detail.contains("rollback path rejected"));
    }

}
