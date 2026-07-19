package com.vectras.vm.runtime;

import android.content.Context;
import android.util.Log;

import com.vectras.vm.VMManager;
import com.vectras.vm.core.ProcessBudgetRegistry;
import com.vectras.vm.core.ProotCommandBuilder;
import com.vectras.vm.main.core.MainStartVM;
import com.vectras.vterm.Terminal;

import java.io.File;

/**
 * Launches qemu-system-* through PRoot with an argument vector, never through
 * shell command evaluation.
 *
 * <p>This is the canonical dispatcher used by MainService. Generic terminal
 * commands keep their historical shell path, while QEMU receives a dedicated
 * process argv assembled from {@link QemuArgvContract}.</p>
 */
public final class QemuDirectLauncher {
    private static final String TAG = "QemuDirectLauncher";
    private static final String USER = "root";
    private static final String DISPLAY = ":0";

    private QemuDirectLauncher() {
    }

    /**
     * Starts the launch asynchronously.
     *
     * @return false only when the supplied contract is not a qemu-system-* launch
     *         and the caller should use its non-QEMU compatibility path.
     */
    public static boolean launch(Context context, QemuArgvContract contract) {
        if (context == null || contract == null || !contract.hasRecognizedQemuBinary()) {
            return false;
        }

        Context appContext = context.getApplicationContext();
        String vmId = MainStartVM.ensureLastVmIdInitialized(MainStartVM.lastVMID);
        if (!VMManager.canRegisterAnotherVmProcess()) {
            Log.e(TAG, "Direct QEMU launch blocked: process capacity reached");
            return true;
        }
        if (!VMManager.tryMarkVmStarting(vmId)) {
            Log.w(TAG, "Direct QEMU launch already in progress for vmId=" + vmId);
            return true;
        }

        Thread launcher = new Thread(() -> runLaunch(appContext, vmId, contract));
        launcher.setName("qemu-direct-argv-" + vmId);
        launcher.start();
        return true;
    }

    private static void runLaunch(Context context, String vmId, QemuArgvContract contract) {
        Process process = null;
        ProcessBudgetRegistry.SlotToken slot = null;
        try {
            String filesDir = context.getFilesDir().getAbsolutePath();
            ProotCommandBuilder proot = new ProotCommandBuilder(
                    context,
                    filesDir + "/distro",
                    "/root"
            )
                    .setUser(USER)
                    .setDisplay(DISPLAY)
                    .setPulseServer("127.0.0.1")
                    .setXdgRuntimeDir("/tmp")
                    .setSdlVideoDriver("x11");

            ProcessBuilder builder = new ProcessBuilder();
            proot.applyEnvironment(builder.environment());
            builder.command(proot.buildCommand(contract.toProcessArgv()));

            slot = ProcessBudgetRegistry.tryAcquireSlot(
                    "qemu-direct",
                    "main-service-argv",
                    "QemuDirectLauncher#runLaunch",
                    vmId
            );
            if (slot == null) {
                Log.e(TAG, "Direct QEMU launch blocked by process budget for vmId=" + vmId);
                return;
            }

            Log.i(TAG, "Dispatching direct QEMU argv: hash=" + contract.getArgvSha256()
                    + " args=" + contract.getArgv().size());
            process = builder.start();
            ProcessBudgetRegistry.bindProcess(slot, process);
            synchronized (Terminal.class) {
                Terminal.qemuProcess = process;
            }
            Terminal.resetStreamingStopToken();
            VMManager.registerVmProcess(context, vmId, process);

            // Empty stdin command is intentional: QEMU is already part of ProcessBuilder argv.
            Terminal.streamLog("", process, false, null);
        } catch (Exception failure) {
            Log.e(TAG, "Direct QEMU argv launch failed for vmId=" + vmId, failure);
            com.vectras.vm.logger.VectrasStatus.logError(
                    "<font color='red'>QEMU direct argv failed: "
                            + failure.getClass().getSimpleName() + "</font>"
            );
        } finally {
            try {
                VMManager.unregisterVmProcess(vmId, process);
            } catch (Exception unregisterFailure) {
                Log.w(TAG, "Unable to unregister direct QEMU process", unregisterFailure);
            }
            try {
                com.vectras.vm.utils.FileUtils.closeFdsForVm(vmId);
            } catch (Exception closeFailure) {
                Log.w(TAG, "Unable to close VM file descriptors", closeFailure);
            }
            synchronized (Terminal.class) {
                if (Terminal.qemuProcess == process) {
                    Terminal.qemuProcess = null;
                }
            }
            VMManager.clearVmStarting(vmId);
            ProcessBudgetRegistry.releaseSlot(slot, "qemu_direct_finally");
        }
    }
}
