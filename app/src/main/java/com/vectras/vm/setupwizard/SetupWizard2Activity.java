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

/**
 * Runtime bootstrap/repair gate.
 *
 * <p>The gate does not silently enter MainActivity while PRoot/rootfs/QEMU are
 * incomplete. When the embedded PRoot/Alpine seed is missing from app data, it
 * offers an explicit local repair action. That action performs only extraction
 * of APK-bundled assets on a worker thread; it does not download QEMU or promote
 * device-runtime claims.</p>
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
        body.setText("O app foi iniciado, mas o runtime ainda não está pronto.\n\n"
                + "Motivo técnico: " + postCheck.technicalReason() + "\n\n"
                + "PRoot e rootfs podem ser reparados localmente a partir dos assets verificados do APK. "
                + "QEMU permanece um gate separado e só é considerado pronto depois de existir e passar o preflight."
                + receiptNote);
        body.setTextSize(15f);
        body.setPadding(0, dp(18), 0, dp(18));
        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        boolean needsBaseRepair = postCheck.failedItems.contains("missing-proot")
                || postCheck.failedItems.contains("missing-distro-busybox");
        if (needsBaseRepair) {
            Button repair = new Button(this);
            repair.setText(repairRunning ? "REPARANDO RUNTIME BASE..." : "INSTALAR / REPARAR RUNTIME BASE");
            repair.setEnabled(!repairRunning);
            repair.setOnClickListener(v -> runBaseRepair(repair, body));
            root.addView(repair, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

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

        Button continueAnyway = new Button(this);
        boolean onlyQemuMissing = postCheck.failedItems.size() == 1
                && postCheck.failedItems.contains("missing-qemu-binary");
        continueAnyway.setText(onlyQemuMissing
                ? "ABRIR HOME PARA COMPLETAR QEMU"
                : "ABRIR HOME MESMO ASSIM");
        continueAnyway.setEnabled(!repairRunning);
        continueAnyway.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });
        root.addView(continueAnyway, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        setContentView(root);
    }

    private void runBaseRepair(Button repairButton, TextView body) {
        if (repairRunning) return;
        repairRunning = true;
        repairButton.setEnabled(false);
        repairButton.setText("REPARANDO RUNTIME BASE...");
        body.setText("Extraindo bootstrap PRoot + rootfs Alpine do próprio APK.\n\n"
                + "Esta tentativa gera receipt antes/depois com SHA-256, destino, modo, bit executável e post-check. "
                + "Nenhum download QEMU é feito nesta etapa.");

        final android.content.Context appContext = getApplicationContext();
        new Thread(() -> {
            BootstrapExtractionReceipt.Session receiptSession = null;
            BootstrapExtractionReceipt.ExportResult receiptResult = null;
            Exception receiptBeginFailure = null;
            Exception extractorFailure = null;
            boolean extracted = false;

            try {
                receiptSession = BootstrapExtractionReceipt.begin(appContext);
            } catch (Exception e) {
                // Evidence capture must not hide whether the extractor itself works.
                receiptBeginFailure = e;
            }

            try {
                extracted = SetupFeatureCore.startExtractSystemFiles(appContext);
            } catch (Exception e) {
                extractorFailure = e;
            }

            SetupFeatureCore.SetupPostCheckResult after = SetupFeatureCore.runSetupPostCheck(appContext);
            String lastError = SetupFeatureCore.lastErrorLog == null ? "" : SetupFeatureCore.lastErrorLog.trim();
            String receiptFailure = "";
            if (receiptSession != null) {
                try {
                    receiptResult = receiptSession.finish(extracted, lastError, extractorFailure);
                } catch (Exception e) {
                    receiptFailure = "receipt-finish-failed:" + e.getClass().getSimpleName() + ":" + compact(e.getMessage());
                }
            } else if (receiptBeginFailure != null) {
                receiptFailure = "receipt-begin-failed:" + receiptBeginFailure.getClass().getSimpleName()
                        + ":" + compact(receiptBeginFailure.getMessage());
            }

            final boolean extractedResult = extracted;
            final Exception extractorFailureResult = extractorFailure;
            final String lastErrorResult = lastError;
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
                    body.setText("Falha inesperada durante o reparo do runtime base.\n\n"
                            + "Gate: " + after.technicalReason() + "\n\n"
                            + "Exceção: " + extractorFailureResult.getClass().getName() + ": "
                            + compact(extractorFailureResult.getMessage()) + "\n\n"
                            + "Receipt: " + lastRepairReceiptSummary);
                    repairButton.setText("TENTAR REPARO NOVAMENTE");
                    repairButton.setEnabled(true);
                    return;
                }

                if (!extractedResult) {
                    body.setText("Falha ao reparar runtime base.\n\n"
                            + "Gate: " + after.technicalReason() + "\n\n"
                            + "Detalhe: " + compact(lastErrorResult) + "\n\n"
                            + "Receipt: " + lastRepairReceiptSummary);
                    repairButton.setText("TENTAR REPARO NOVAMENTE");
                    repairButton.setEnabled(true);
                    return;
                }
                renderCurrentState();
            });
        }, "vectras-runtime-base-repair").start();
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
