package com.vectras.vm.rafaelia.connector;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * JSON Export Connector — JSON/JSON600/JSON800 connector.
 *
 * <p>Three export profiles:
 * <pre>
 *   JSON    : Standard JSON export (arrays, objects, scalars)
 *   JSON600 : Matrix export format (up to 600 rows × N cols), with metadata envelope
 *   JSON800 : Raw binary-encoded batches (up to 800 events), JSONL format
 * </pre>
 *
 * <p>All exports include a canonical envelope:
 * <pre>
 *   {"profile":"JSON|JSON600|JSON800", "seq":N, "tsMs":T, "count":C, "data":[...]}
 * </pre>
 *
 * @author ∆RafaelVerboΩ / RAFAELIA-JSON
 */
public final class JsonExportConnector {

    public enum Profile { JSON, JSON600, JSON800 }

    static final int JSON600_MAX_ROWS    = 600;
    static final int JSON800_MAX_EVENTS  = 800;

    private final AtomicLong seqCounter = new AtomicLong(0);
    private final File       exportDir;

    private JsonExportConnector(File exportDir) {
        this.exportDir = exportDir;
        exportDir.mkdirs();
    }

    public static JsonExportConnector create(@NonNull File exportDir) {
        return new JsonExportConnector(exportDir);
    }

    // ─── Standard JSON export ─────────────────────────────────────────────────

    /**
     * Export a list of objects as standard JSON array with envelope.
     */
    @NonNull
    public String exportJson(@NonNull List<JSONObject> objects) throws JSONException {
        JSONObject envelope = buildEnvelope(Profile.JSON, objects.size());
        JSONArray arr = new JSONArray();
        for (JSONObject o : objects) arr.put(o);
        envelope.put("data", arr);
        return envelope.toString(2);
    }

    /**
     * Export a double[][] matrix in JSON600 format (metadata + rows).
     * @param matrix   rows × cols double matrix
     * @param colNames column header names
     */
    @NonNull
    public String exportJson600(@NonNull double[][] matrix,
                                 @Nullable String[] colNames) throws JSONException {
        int rows = Math.min(matrix.length, JSON600_MAX_ROWS);
        int cols = rows > 0 ? matrix[0].length : 0;

        JSONObject envelope = buildEnvelope(Profile.JSON600, rows);
        envelope.put("rows", rows);
        envelope.put("cols", cols);

        // Column headers
        if (colNames != null && colNames.length > 0) {
            JSONArray headers = new JSONArray();
            for (String h : colNames) headers.put(h);
            envelope.put("columns", headers);
        }

        JSONArray data = new JSONArray();
        for (int r = 0; r < rows; r++) {
            JSONArray row = new JSONArray();
            for (int c = 0; c < matrix[r].length; c++) row.put(matrix[r][c]);
            data.put(row);
        }
        envelope.put("data", data);
        return envelope.toString(2);
    }

    /**
     * Export raw events as JSON800 JSONL (one JSON object per line).
     * Writes directly to file at exportDir/json800_<seq>.jsonl
     */
    @NonNull
    public File exportJson800(@NonNull List<JSONObject> events) throws JSONException, IOException {
        int count = Math.min(events.size(), JSON800_MAX_EVENTS);
        long seq  = seqCounter.incrementAndGet();

        File outFile = new File(exportDir, "json800_" + seq + ".jsonl");
        try (BufferedWriter bw = new BufferedWriter(
                new FileWriter(outFile, StandardCharsets.UTF_8, false))) {
            // Write header line
            JSONObject header = buildEnvelope(Profile.JSON800, count);
            bw.write(header.toString());
            bw.newLine();
            // Write event lines
            for (int i = 0; i < count; i++) {
                bw.write(events.get(i).toString());
                bw.newLine();
            }
        }
        return outFile;
    }

    // ─── Matrix helpers ───────────────────────────────────────────────────────

    /**
     * Build a JSON600 matrix from float arrays (e.g., vectors from VectraBenchmark).
     */
    @NonNull
    public static double[][] vectorsToMatrix(@NonNull double[]... vectors) {
        if (vectors.length == 0) return new double[0][0];
        int cols = vectors[0].length;
        double[][] m = new double[vectors.length][cols];
        for (int i = 0; i < vectors.length; i++) {
            System.arraycopy(vectors[i], 0, m[i], 0, Math.min(cols, vectors[i].length));
        }
        return m;
    }

    // ─── Envelope builder ─────────────────────────────────────────────────────

    private JSONObject buildEnvelope(Profile profile, int count) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("profile", profile.name());
        o.put("seq",     seqCounter.get());
        o.put("tsMs",    System.currentTimeMillis());
        o.put("count",   count);
        return o;
    }
}
