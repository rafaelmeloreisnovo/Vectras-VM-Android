package com.vectras.vm.setupwizard;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.vectras.vm.core.ProcessLaunch;

import org.junit.Test;

public class SetupProcessIntegrationTest {

    @Test
    public void buildOwnerSafeTarExtractionCommand_disablesOwnerRestoreBeforeExtract() {
        assertArrayEquals(
                new String[] {"tar", "-o", "-x", "-f", "/data/user/0/app/files/source.tar",
                        "-C", "/data/user/0/app/files"},
                SetupProcessIntegration.buildOwnerSafeTarExtractionCommand(
                        "/data/user/0/app/files/source.tar", "/data/user/0/app/files")
        );
    }

    @Test
    public void validateTarLaunchResult_flagsTimeout() {
        String error = SetupProcessIntegration.validateTarLaunchResult(
                ProcessLaunch.LaunchStatus.TIMEOUT,
                -1,
                "timed out",
                "",
                "asset.tar",
                "tar xf"
        );
        assertTrue(error.contains("PROCESS_TIMEOUT"));
    }

    @Test
    public void validateTarLaunchResult_acceptsCleanSuccess() {
        String error = SetupProcessIntegration.validateTarLaunchResult(
                ProcessLaunch.LaunchStatus.SUCCESS,
                0,
                "ok",
                "",
                "asset.tar",
                "tar xf"
        );
        assertNull(error);
    }
}
