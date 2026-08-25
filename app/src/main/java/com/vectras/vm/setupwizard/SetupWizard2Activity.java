package com.vectras.vm.setupwizard;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.vectras.vm.evidence.BootstrapExtractionReceipt;
import com.vectras.vm.evidence.EvidenceCatalogActivity;
import com.vectras.vm.main.MainActivity;

import java.io.File;

/**
 * Runtime bootstrap/repair gate.
 *
 * <p>The gate enters MainActivity only after PRoot, Alpine rootfs and QEMU are
 * present. Repair is APK-local: bootstrap/<abi>.tar, alpine19/<abi>.tar and
 * qemu19/<abi>.tar are extracted on a worker thread. No runtime download is
 * required by this flow.</p>
 */
public class SetupWizard2Activity extends AppCompatActivity {
    public static final String ACTION_DEBUG_PROOT_SELF_CHECK = "com.vectras.vm.action.DEBUG_PROOT_SELF_CHECK";
    public static final String EXTRA_DEBUG_PROOT_SELF_CHECK = "debug_proot_self_check";
    public static final int ACTION_SYSTEM_UPDATE = 1;

    private volatile boolean repairRunning;
    private volatile String lastRepairReceiptSummary = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        renderCurrentState();
    }

    private void renderCurrentState() {
        SetupFeatureCore.SetupPostCheckResult postCheck = SetupFeatureCore.runSetupPostCheck(this);
        if (postCheck.ok) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }
        showBlockedSetup(postCheck);
    }

    private void showBlockedSetup(SetupFeatureCore.SetupPostCheckResult postCheck) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Vectras runtime incompleto");
        title.setTextSize(22f);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView body = new TextView(this);
        String receiptNote = lastRepairReceiptSummary.isEmpty()
                ? ""
                : "\n\nReceipt da última tentativa: " + lastRepairReceiptSummary;
        body.setText("O runtime ainda não passou o gate.\n\n"
                + "Motivo técnico: " + postCheck.technicalReason() + "\n\n"
                + "O reparo instala PRoot + Alpine + QEMU usando somente os assets embutidos no APK. "
                + "A Home só é liberada quando o post-check encontra os três componentes."
                + receiptNote);
        body.setTextSize(15f);
        body.setPadding(0, dp(18), 0, dp(18));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button repair = new Button(this);
        repair.setText(repairRunning ? "REPARANDO RUNTIME COMPLETO..." : "INSTALAR / REPARAR RUNTIME COMPLETO");
        repair.setEnabled(!repairRunning);
        repair.setOnClickListener(v -> runFullRuntimeRepair(repair, body));
        root.addView(repair, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button retry = new Button(this);
        retry.setText("VERIFICAR NOVAMENTE");
        retry.setEnabled(!repairRunning);
        retry.setOnClickListener(v -> renderCurrentState());
        root.addView(retry, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        Button evidence = new Button(this);
        evidence.setText("CATÁLOGO DE EVIDÊNCIAS");
        evidence.setEnabled(!repairRunning);
        evidence.setOnClickListener(v -> startActivity(new Intent(this, EvidenceCatalogActivity.class)));
        root.addView(evidence, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
    }

    private void runFullRuntimeRepair(Button repairButton, TextView body) {
        if (repairRunning) return;
        repairRunning = true;
        repairButton.setEnabled(false);
        repairButton.setText("REPARANDO RUNTIME COMPLETO...");
        body.setText("Instalando bootstrap PRoot + Alpine + QEMU do próprio APK.\n\n"
                + "O estágio QEMU substitui a rootfs por uma imagem Alpine equivalente já contendo os binários "
                + "qemu-system-* e suas dependências. Nenhum download é feito no aparelho.");

        final android.content.Context appContext = getApplicationContext();
        new Thread(() -> {
            BootstrapExtractionReceipt.Session receiptSession = null;
            BootstrapExtractionReceipt.ExportResult receiptResult = null;
            Exception receiptBeginFailure = null;
            Exception extractorFailure = null;
            boolean baseReady = false;
            boolean qemuReady = false;

            try {
                receiptSession = BootstrapExtractionReceipt.begin(appContext);
            } catch (Exception e) {
                receiptBeginFailure = e;
            }

            try {
                // A historical setup path aborts when distro/bin exists even if the
                // base is incomplete. Preserve the pre-state in the receipt above,
                // then remove only the inconsistent distro so extraction can rebuild it.
                if (!SetupFeatureCore.isInstalledSystemFiles(appContext)) {
                    File partialDistro = new File(appContext.getFilesDir(), "distro");
                    if (partialDistro.exists()) {
                        SetupFeatureCore.deleteRecursively(partialDistro);
                    }
                }

                baseReady = SetupFeatureCore.startExtractSystemFiles(appContext);
                if (baseReady) {
                    if (!SetupFeatureCore.isInstalledQemu(appContext)) {
                        boolean qemuExtracted = SetupFeatureCore.extractSystemFiles(appContext, "qemu19", "distro");
                        if (qemuExtracted) {
                            String distroPath = new File(appContext.getFilesDir(), "distro").getAbsolutePath();
                            SetupFeatureCore.fixPermissions(distroPath);
                            SetupFeatureCore.setDNS(appContext);
                        }
                    }
                    qemuReady = SetupFeatureCore.isInstalledQemu(appContext);
                }
            } catch (Exception e) {
                extractorFailure = e;
            }

            SetupFeatureCore.SetupPostCheckResult after = SetupFeatureCore.runSetupPostCheck(appContext);
            boolean repaired = baseReady && qemuReady && after.ok;
            String rawLastError = SetupFeatureCore.lastErrorLog == null ? "" : SetupFeatureCore.lastErrorLog.trim();
            String evidenceError = repaired ? "" : rawLastError;
            if (!repaired && extractorFailure == null && evidenceError.isEmpty()) {
                evidenceError = "POST_CHECK:" + after.technicalReason()
                        + ";baseReady=" + baseReady
                        + ";qemuReady=" + qemuReady;
            }

            String receiptFailure = "";
            if (receiptSession != null) {
                try {
                    receiptResult = receiptSession.finish(repaired, evidenceError, extractorFailure);
                } catch (Exception e) {
                    receiptFailure = "receipt-finish-failed:" + e.getClass().getSimpleName() + ":" + compact(e.getMessage());
                }
            } else if (receiptBeginFailure != null) {
                receiptFailure = "receipt-begin-failed:" + receiptBeginFailure.getClass().getSimpleName()
                        + ":" + compact(receiptBeginFailure.getMessage());
            }

            final boolean repairedResult = repaired;
            final boolean baseReadyResult = baseReady;
            final boolean qemuReadyResult = qemuReady;
            final Exception extractorFailureResult = extractorFailure;
            final String evidenceErrorResult = evidenceError;
            final String receiptFailureResult = receiptFailure;
            final BootstrapExtractionReceipt.ExportResult receiptResultFinal = receiptResult;

            runOnUiThread(() -> {
                repairRunning = false;
                if (isFinishing() || isDestroyed()) return;

                if (receiptResultFinal != null) {
                    lastRepairReceiptSummary = receiptResultFinal.jsonFile.getName()
                            + " sha256=" + receiptResultFinal.sha256;
                } else if (!receiptFailureResult.isEmpty()) {
                    lastRepairReceiptSummary = "TOKEN_VAZIO receipt-write=" + receiptFailureResult;
                }

                if (extractorFailureResult != null) {
                    body.setText("Falha durante o reparo do runtime completo.\n\n"
                            + "Gate: " + after.technicalReason() + "\n\n"
                            + "Exceção: " + extractorFailureResult.getClass().getName() + ": "
                            + compact(extractorFailureResult.getMessage()) + "\n\n"
                            + "Receipt: " + lastRepairReceiptSummary);
                    repairButton.setText("TENTAR REPARO NOVAMENTE");
                    repairButton.setEnabled(true);
                    return;
                }

                if (!repairedResult) {
                    body.setText("Runtime ainda incompleto após o reparo.\n\n"
                            + "Gate: " + after.technicalReason() + "\n\n"
                            + "baseReady=" + baseReadyResult + " qemuReady=" + qemuReadyResult + "\n\n"
                            + "Detalhe: " + compact(evidenceErrorResult) + "\n\n"
                            + "Receipt: " + lastRepairReceiptSummary);
                    repairButton.setText("TENTAR REPARO NOVAMENTE");
                    repairButton.setEnabled(true);
                    return;
                }
                renderCurrentState();
            });
        }, "vectras-runtime-full-repair").start();
    }

    private static String compact(String value) {
        if (value == null) return "unknown";
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() > 1200) {
            return normalized.substring(0, 1200) + "…";
        }
        return normalized.isEmpty() ? "unknown" : normalized;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
