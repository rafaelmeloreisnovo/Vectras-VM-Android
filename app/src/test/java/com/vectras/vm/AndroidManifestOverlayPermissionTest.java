package com.vectras.vm;

import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class AndroidManifestOverlayPermissionTest {

    @Test
    public void systemAlertWindowPermissionDocumentsSettingsGatedFlow() throws Exception {
        String manifest = new String(
                Files.readAllBytes(Paths.get("src/main/AndroidManifest.xml")),
                StandardCharsets.UTF_8
        );

        Assert.assertTrue(manifest.contains("android.permission.SYSTEM_ALERT_WINDOW"));
        Assert.assertTrue(manifest.contains("PermissionUtils.openOverlayPermissionSettings()"));
        Assert.assertTrue(manifest.contains("ACTION_MANAGE_OVERLAY_PERMISSION"));
        Assert.assertTrue(manifest.contains("PermissionUtils.canDrawOverlays()"));
    }
}
