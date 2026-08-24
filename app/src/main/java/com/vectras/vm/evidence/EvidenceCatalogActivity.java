package com.vectras.vm.evidence;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Human-readable evidence surface backed by the same JSON artifact that can be
 * exported for audit, scientific reference, bug reports or chain-of-custody.
 */
public class EvidenceCatalogActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private TextView status;
    private TextView output;
    private ProgressBar progress;
    private Button refreshButton;
    private Button exportButton;
    private Button shareButton;

    private volatile JSONObject latestCatalog;
    private volatile EvidenceCatalogCollector.ExportResult latestExport;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle("Catálogo de Evidências");
        setContentView(buildUi());
        refreshCatalog();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(18), dp(20), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = new TextView(this);
        title.setText("CATÁLOGO DE EVIDÊNCIAS DA INSTALAÇÃO");
        title.setTextSize(21f);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        root.addView(title, fullWidthWrap());

        TextView subtitle = new TextView(this);
        subtitle.setText("Identidade da compilação + APK instalado + assinatura + assets + runtime + dispositivo + lacunas.\n\n"
                + "A tela registra observações verificáveis. Ela não transforma observação em certificação: "
                + "TOKEN_VAZIO permanece explícito até existir receipt correspondente.");
        subtitle.setTextSize(14f);
        subtitle.setPadding(0, dp(12), 0, dp(12));
        root.addView(subtitle, fullWidthWrap());

        status = new TextView(this);
        status.setTypeface(Typeface.MONOSPACE);
        status.setTextSize(13f);
        status.setText("Estado: aguardando coleta");
        root.addView(status, fullWidthWrap());

        progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(progress, progressParams);

        refreshButton = actionButton("ATUALIZAR EVIDÊNCIAS");
        refreshButton.setOnClickListener(v -> refreshCatalog());
        root.addView(refreshButton, fullWidthWrap());

        exportButton = actionButton("GERAR ARTEFATO JSON + SHA-256");
        exportButton.setOnClickListener(v -> exportCatalog(false));
        root.addView(exportButton, fullWidthWrap());

        shareButton = actionButton("COMPARTILHAR ÚLTIMO ARTEFATO");
        shareButton.setOnClickListener(v -> shareOrExport());
        root.addView(shareButton, fullWidthWrap());

        output = new TextView(this);
        output.setTypeface(Typeface.MONOSPACE);
        output.setTextSize(11.5f);
        output.setTextIsSelectable(true);
        output.setPadding(0, dp(16), 0, 0);
        output.setText("{}\n");
        root.addView(output, fullWidthWrap());
        return scroll;
    }

    private Button actionButton(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams fullWidthWrap() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, dp(5), 0, dp(5));
        return params;
    }

    private void refreshCatalog() {
        setBusy(true, "Coletando e calculando hashes locais…");
        executor.execute(() -> {
            try {
                JSONObject catalog = EvidenceCatalogCollector.collect(getApplicationContext());
                latestCatalog = catalog;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    try {
                        output.setText(catalog.toString(2));
                        int gaps = catalog.getJSONArray("token_vazio").length();
                        boolean postOk = catalog.getJSONObject("runtime_filesystem")
                                .getJSONObject("post_check").getBoolean("ok");
                        status.setText("Estado: OBSERVADO | post_check=" + (postOk ? "PASS" : "OPEN")
                                + " | TOKEN_VAZIO=" + gaps
                                + " | claim_allowed=false");
                    } catch (Exception renderError) {
                        status.setText("Estado: coleta OK, falha de renderização: "
                                + renderError.getClass().getSimpleName());
                    }
                    setBusy(false, null);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    status.setText("Falha de coleta: " + e.getClass().getSimpleName() + ": " + safeMessage(e));
                    setBusy(false, null);
                });
            }
        });
    }

    private void exportCatalog(boolean shareAfter) {
        JSONObject catalog = latestCatalog;
        if (catalog == null) {
            Toast.makeText(this, "Atualize o catálogo primeiro.", Toast.LENGTH_SHORT).show();
            refreshCatalog();
            return;
        }
        setBusy(true, "Publicando artefato append-only local…");
        executor.execute(() -> {
            try {
                EvidenceCatalogCollector.ExportResult result = EvidenceCatalogCollector.export(
                        getApplicationContext(), catalog);
                latestExport = result;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    status.setText("Artefato: " + result.jsonFile.getName()
                            + "\nSHA-256: " + result.sha256
                            + "\nCompanion: " + result.checksumFile.getName());
                    setBusy(false, null);
                    if (shareAfter) share(result);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    status.setText("Falha ao gerar artefato: " + e.getClass().getSimpleName()
                            + ": " + safeMessage(e));
                    setBusy(false, null);
                });
            }
        });
    }

    private void shareOrExport() {
        EvidenceCatalogCollector.ExportResult result = latestExport;
        if (result == null || !result.jsonFile.isFile() || !result.checksumFile.isFile()) {
            exportCatalog(true);
            return;
        }
        share(result);
    }

    private void share(EvidenceCatalogCollector.ExportResult result) {
        try {
            String authority = getPackageName() + ".provider";
            Uri jsonUri = FileProvider.getUriForFile(this, authority, result.jsonFile);
            Uri checksumUri = FileProvider.getUriForFile(this, authority, result.checksumFile);
            ArrayList<Uri> uris = new ArrayList<>();
            uris.add(jsonUri);
            uris.add(checksumUri);

            Intent send = new Intent(Intent.ACTION_SEND_MULTIPLE);
            send.setType("*/*");
            send.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            send.putExtra(Intent.EXTRA_SUBJECT, "Vectras Evidence Catalog " + result.sha256);
            send.putExtra(Intent.EXTRA_TEXT,
                    "Vectras installation evidence catalog. SHA-256: " + result.sha256);
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            ClipData clip = ClipData.newUri(getContentResolver(), "Vectras evidence catalog", jsonUri);
            clip.addItem(new ClipData.Item(checksumUri));
            send.setClipData(clip);
            startActivity(Intent.createChooser(send, "Compartilhar evidência"));
        } catch (Exception e) {
            Toast.makeText(this, "Falha ao compartilhar: " + safeMessage(e), Toast.LENGTH_LONG).show();
        }
    }

    private void setBusy(boolean busy, String message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        refreshButton.setEnabled(!busy);
        exportButton.setEnabled(!busy);
        shareButton.setEnabled(!busy);
        if (busy && message != null) status.setText(message);
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.trim().isEmpty()) return "sem detalhe";
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 500 ? message.substring(0, 500) + "…" : message;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
