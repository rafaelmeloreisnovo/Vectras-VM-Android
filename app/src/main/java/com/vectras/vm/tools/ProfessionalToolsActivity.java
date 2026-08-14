package com.vectras.vm.tools;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.vectras.vm.AppConfig;
import com.vectras.vm.R;
import com.vectras.vm.benchmark.BenchmarkManager;
import com.vectras.vm.benchmark.IndustrialStatistics;
import com.vectras.vm.benchmark.VectraBenchmark;
import com.vectras.vm.core.ExecutionPolicyCenter;
import com.vectras.vm.core.QualityStandardsCatalog;
import com.vectras.vm.utils.FileUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/**
 * ProfessionalToolsActivity - MEGA TOOLS Professional Engineering/Benchmark/Scientific System
 *
 * This activity provides a comprehensive benchmarking and evidence-reporting system
 * that integrates multiple technical methodologies and standards:
 *
 * - ISO/IEC 25010 (Software Quality Model)
 * - IEEE 829/1012 (Test Documentation and Verification)
 * - ACM Standards
 * - NIST Benchmarking Principles
 * - SPEC Methodology
 * - MLPerf Philosophy
 *
 * Features:
 * - Checklist-based category selection with estimated time tracking
 * - Multiple methodology standard indicators
 * - Per-metric benchmark result preservation
 * - Homogeneous repeated-series statistics only when such evidence exists
 * - Evidence-gated grade indicators
 * - Professional report generation
 *
 * Design Principles:
 * - Evidence first
 * - Fail closed when repeated-series evidence is absent
 * - No cross-metric statistical pooling
 * - No certification or scientific-grade promotion from one-shot heterogeneous metrics
 * - Professionally documented
 */
public class ProfessionalToolsActivity extends AppCompatActivity {
    private static final String TAG = "ProfessionalToolsActivity";

    // Time estimates in seconds for each category
    private static final int TIME_CPU_SINGLE = 15;
    private static final int TIME_CPU_MULTI = 20;
    private static final int TIME_MEMORY = 25;
    private static final int TIME_STORAGE = 30;
    private static final int TIME_INTEGRITY = 20;
    private static final int TIME_EMULATION = 15;

    // UI Elements - Category Checkboxes
    private CheckBox cbCpuSingle;
    private CheckBox cbCpuMulti;
    private CheckBox cbMemory;
    private CheckBox cbStorage;
    private CheckBox cbIntegrity;
    private CheckBox cbEmulation;

    // UI Elements - Methodology Chips
    private Chip chipISO;
    private Chip chipIEEE;
    private Chip chipACM;
    private Chip chipNIST;
    private Chip chipSPEC;
    private Chip chipMLPerf;

    // UI Elements - Status and Progress
    private Chip chipValidationStatus;
    private TextView tvEstimatedTime;
    private TextView tvCpuSingleTime;
    private TextView tvCpuMultiTime;
    private TextView tvMemoryTime;
    private TextView tvStorageTime;
    private TextView tvIntegrityTime;
    private TextView tvEmulationTime;
    private LinearLayout layoutProgress;
    private LinearProgressIndicator progressIndicator;
    private TextView tvProgressText;
    private TextView tvProgressDetail;

    // UI Elements - Results
    private LinearLayout layoutResults;
    private TextView tvExecutiveSummary;
    private Chip chipGradeIndustry;
    private Chip chipGradeAcademic;
    private Chip chipGradeScientific;
    private TextView tvGradeJustification;
    private TextView tvStatMean;
    private TextView tvStatMedian;
    private TextView tvStatStdDev;
    private TextView tvStatConfidence;
    private TextView tvStatReproducibility;

    // UI Elements - Buttons
    private MaterialButton btnRunAnalysis;
    private MaterialButton btnSelectAll;
    private MaterialButton btnDeselectAll;
    private LinearLayout btnViewFullReport;
    private LinearLayout btnExportReport;
    private LinearLayout btnShareReport;

    // Data
    private VectraBenchmark.BenchmarkResult[] lastResults;
    private BenchmarkManager.BenchmarkResult lastBenchmarkResult;
    private AnalysisReport lastReport;
    private final ExecutorService executor =
        ExecutionPolicyCenter.executor(ExecutionPolicyCenter.Channel.PROFESSIONAL);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_professional_tools);

        setupToolbar();
        initViews();
        bindStaticEstimatedTimes();
        applyStatisticsFallback();
        setupListeners();
        updateEstimatedTime();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar supportBar = getSupportActionBar();
        if (supportBar != null) {
            supportBar.setDisplayHomeAsUpEnabled(true);
            supportBar.setDisplayShowHomeEnabled(true);
        }
        toolbar.setTitle(getString(R.string.professional_tools));
    }

    private void initViews() {
        // Category Checkboxes
        cbCpuSingle = findViewById(R.id.cbCpuSingle);
        cbCpuMulti = findViewById(R.id.cbCpuMulti);
        cbMemory = findViewById(R.id.cbMemory);
        cbStorage = findViewById(R.id.cbStorage);
        cbIntegrity = findViewById(R.id.cbIntegrity);
        cbEmulation = findViewById(R.id.cbEmulation);

        // Methodology Chips
        chipISO = findViewById(R.id.chipISO);
        chipIEEE = findViewById(R.id.chipIEEE);
        chipACM = findViewById(R.id.chipACM);
        chipNIST = findViewById(R.id.chipNIST);
        chipSPEC = findViewById(R.id.chipSPEC);
        chipMLPerf = findViewById(R.id.chipMLPerf);

        // Status and Progress
        chipValidationStatus = findViewById(R.id.chipValidationStatus);
        tvEstimatedTime = findViewById(R.id.tvEstimatedTime);
        tvCpuSingleTime = findViewById(R.id.tvCpuSingleTime);
        tvCpuMultiTime = findViewById(R.id.tvCpuMultiTime);
        tvMemoryTime = findViewById(R.id.tvMemoryTime);
        tvStorageTime = findViewById(R.id.tvStorageTime);
        tvIntegrityTime = findViewById(R.id.tvIntegrityTime);
        tvEmulationTime = findViewById(R.id.tvEmulationTime);
        layoutProgress = findViewById(R.id.layoutProgress);
        progressIndicator = findViewById(R.id.progressIndicator);
        tvProgressText = findViewById(R.id.tvProgressText);
        tvProgressDetail = findViewById(R.id.tvProgressDetail);

        // Results
        layoutResults = findViewById(R.id.layoutResults);
        tvExecutiveSummary = findViewById(R.id.tvExecutiveSummary);
        chipGradeIndustry = findViewById(R.id.chipGradeIndustry);
        chipGradeAcademic = findViewById(R.id.chipGradeAcademic);
        chipGradeScientific = findViewById(R.id.chipGradeScientific);
        tvGradeJustification = findViewById(R.id.tvGradeJustification);
        tvStatMean = findViewById(R.id.tvStatMean);
        tvStatMedian = findViewById(R.id.tvStatMedian);
        tvStatStdDev = findViewById(R.id.tvStatStdDev);
        tvStatConfidence = findViewById(R.id.tvStatConfidence);
        tvStatReproducibility = findViewById(R.id.tvStatReproducibility);

        // Buttons
        btnRunAnalysis = findViewById(R.id.btnRunAnalysis);
        btnSelectAll = findViewById(R.id.btnSelectAll);
        btnDeselectAll = findViewById(R.id.btnDeselectAll);
        btnViewFullReport = findViewById(R.id.btnViewFullReport);
        btnExportReport = findViewById(R.id.btnExportReport);
        btnShareReport = findViewById(R.id.btnShareReport);
    }

    private void setupListeners() {
        // Category checkbox listeners - update estimated time
        View.OnClickListener checkboxListener = v -> updateEstimatedTime();
        cbCpuSingle.setOnClickListener(checkboxListener);
        cbCpuMulti.setOnClickListener(checkboxListener);
        cbMemory.setOnClickListener(checkboxListener);
        cbStorage.setOnClickListener(checkboxListener);
        cbIntegrity.setOnClickListener(checkboxListener);
        cbEmulation.setOnClickListener(checkboxListener);

        // Select/Deselect all
        btnSelectAll.setOnClickListener(v -> {
            cbCpuSingle.setChecked(true);
            cbCpuMulti.setChecked(true);
            cbMemory.setChecked(true);
            cbStorage.setChecked(true);
            cbIntegrity.setChecked(true);
            cbEmulation.setChecked(true);
            updateEstimatedTime();
        });

        btnDeselectAll.setOnClickListener(v -> {
            cbCpuSingle.setChecked(false);
            cbCpuMulti.setChecked(false);
            cbMemory.setChecked(false);
            cbStorage.setChecked(false);
            cbIntegrity.setChecked(false);
            cbEmulation.setChecked(false);
            updateEstimatedTime();
        });

        // Main action button
        btnRunAnalysis.setOnClickListener(v -> runAnalysis());

        // Result action buttons
        btnViewFullReport.setOnClickListener(v -> showFullReport());
        btnExportReport.setOnClickListener(v -> exportReport());
        btnShareReport.setOnClickListener(v -> shareReport());
    }

    private void bindStaticEstimatedTimes() {
        setCategoryEstimatedTime(tvCpuSingleTime, TIME_CPU_SINGLE);
        setCategoryEstimatedTime(tvCpuMultiTime, TIME_CPU_MULTI);
        setCategoryEstimatedTime(tvMemoryTime, TIME_MEMORY);
        setCategoryEstimatedTime(tvStorageTime, TIME_STORAGE);
        setCategoryEstimatedTime(tvIntegrityTime, TIME_INTEGRITY);
        setCategoryEstimatedTime(tvEmulationTime, TIME_EMULATION);
    }

    private void setCategoryEstimatedTime(TextView target, int seconds) {
        if (target == null) {
            return;
        }
        if (seconds > 0) {
            target.setText(getString(R.string.pro_tools_estimated_time_item_format, seconds));
        } else {
            target.setText(R.string.pro_tools_estimated_time_item_select_and_run);
        }
    }

    private void applyStatisticsFallback() {
        tvStatMean.setText(R.string.pro_tools_stat_not_run_yet);
        tvStatMedian.setText(R.string.pro_tools_stat_not_run_yet);
        tvStatStdDev.setText(R.string.pro_tools_stat_not_run_yet);
        tvStatConfidence.setText(R.string.pro_tools_confidence_not_run_yet);
        tvStatReproducibility.setText(R.string.pro_tools_stat_not_run_yet);
    }

    private void applyStatisticsCollectionError() {
        tvStatMean.setText(R.string.pro_tools_stat_collection_error);
        tvStatMedian.setText(R.string.pro_tools_stat_collection_error);
        tvStatStdDev.setText(R.string.pro_tools_stat_collection_error);
        tvStatConfidence.setText(R.string.pro_tools_confidence_collection_error);
        tvStatReproducibility.setText(R.string.pro_tools_stat_collection_error);
    }

    private void updateEstimatedTime() {
        int totalSeconds = 0;
        int selectedCategories = 0;

        if (cbCpuSingle.isChecked()) {
            totalSeconds += TIME_CPU_SINGLE;
            selectedCategories++;
        }
        if (cbCpuMulti.isChecked()) {
            totalSeconds += TIME_CPU_MULTI;
            selectedCategories++;
        }
        if (cbMemory.isChecked()) {
            totalSeconds += TIME_MEMORY;
            selectedCategories++;
        }
        if (cbStorage.isChecked()) {
            totalSeconds += TIME_STORAGE;
            selectedCategories++;
        }
        if (cbIntegrity.isChecked()) {
            totalSeconds += TIME_INTEGRITY;
            selectedCategories++;
        }
        if (cbEmulation.isChecked()) {
            totalSeconds += TIME_EMULATION;
            selectedCategories++;
        }

        if (totalSeconds == 0) {
            tvEstimatedTime.setText(getString(R.string.pro_tools_no_categories_selected));
            btnRunAnalysis.setEnabled(false);
        } else {
            int minutes = totalSeconds / 60;
            int seconds = totalSeconds % 60;
            String timeText;
            if (minutes > 0) {
                timeText = getString(R.string.pro_tools_estimated_time_format_min_sec, minutes, seconds);
            } else {
                timeText = getString(R.string.pro_tools_estimated_time_format_sec, seconds);
            }
            tvEstimatedTime.setText(timeText);
            btnRunAnalysis.setEnabled(true);
        }
    }

    private List<Integer> getSelectedCategories() {
        List<Integer> categories = new ArrayList<>();
        if (cbCpuSingle.isChecked()) categories.add(0);
        if (cbCpuMulti.isChecked()) categories.add(1);
        if (cbMemory.isChecked()) categories.add(2);
        if (cbStorage.isChecked()) categories.add(3);
        if (cbIntegrity.isChecked()) categories.add(4);
        if (cbEmulation.isChecked()) categories.add(5);
        return categories;
    }

    private List<String> getSelectedMethodologies() {
        List<String> methodologies = new ArrayList<>();
        if (chipISO.isChecked()) methodologies.add("ISO/IEC 25010");
        if (chipIEEE.isChecked()) methodologies.add("IEEE 829/1012");
        if (chipACM.isChecked()) methodologies.add("ACM");
        if (chipNIST.isChecked()) methodologies.add("NIST");
        if (chipSPEC.isChecked()) methodologies.add("SPEC");
        if (chipMLPerf.isChecked()) methodologies.add("MLPerf");
        return methodologies;
    }

    private void runAnalysis() {
        List<Integer> selectedCategories = getSelectedCategories();
        if (selectedCategories.isEmpty()) {
            Toast.makeText(this, R.string.pro_tools_select_at_least_one, Toast.LENGTH_SHORT).show();
            return;
        }

        // Update UI state
        layoutProgress.setVisibility(View.VISIBLE);
        layoutResults.setVisibility(View.GONE);
        btnRunAnalysis.setEnabled(false);
        chipValidationStatus.setText(R.string.pro_tools_status_running);
        progressIndicator.setProgress(0);

        // Run in background
        executor.execute(() -> {
            try {
                // Update progress - Starting
                mainHandler.post(() -> {
                    tvProgressText.setText(R.string.pro_tools_initializing);
                    tvProgressDetail.setText(R.string.pro_tools_warming_up);
                });

                BenchmarkManager benchmarkManager = new BenchmarkManager(this);
                BenchmarkManager.BenchmarkResult benchmarkResult = benchmarkManager.runBenchmark(
                    new BenchmarkManager.ProgressCallback() {
                        @Override
                        public void onProgress(int metricIndex, int totalMetrics, String currentMetric) {
                            mainHandler.post(() -> {
                                progressIndicator.setProgress(Math.max(5, Math.min(60, metricIndex)));
                                tvProgressText.setText(R.string.pro_tools_initializing);
                                tvProgressDetail.setText(currentMetric);
                            });
                        }

                        @Override
                        public void onWarning(String warning) {
                            mainHandler.post(() -> tvProgressDetail.setText(warning));
                        }

                        @Override
                        public void onComplete(BenchmarkManager.BenchmarkResult result) {
                            // handled by outer flow
                        }

                        @Override
                        public void onError(String error) {
                            // handled by outer flow
                        }
                    }, BenchmarkManager.ExecutionProfile.AUTO_ADAPTIVE,
                    ExecutionPolicyCenter.Channel.PROFESSIONAL);
                VectraBenchmark.BenchmarkResult[] results = benchmarkResult.metrics;
                lastResults = results;
                lastBenchmarkResult = benchmarkResult;

                // Update progress - Analyzing
                mainHandler.post(() -> {
                    progressIndicator.setProgress(60);
                    tvProgressText.setText(R.string.pro_tools_analyzing);
                    tvProgressDetail.setText(R.string.pro_tools_computing_statistics);
                });

                // Generate analysis report
                AnalysisReport report = generateAnalysisReport(
                    benchmarkResult, selectedCategories, getSelectedMethodologies());
                lastReport = report;

                // Update progress - Finalizing
                mainHandler.post(() -> {
                    progressIndicator.setProgress(90);
                    tvProgressText.setText(R.string.pro_tools_finalizing);
                    tvProgressDetail.setText(R.string.pro_tools_generating_report);
                });

                // Small delay for visual feedback
                Thread.sleep(500);

                // Update UI with results
                mainHandler.post(() -> {
                    progressIndicator.setProgress(100);
                    displayResults(report);
                    layoutProgress.setVisibility(View.GONE);
                    layoutResults.setVisibility(View.VISIBLE);
                    btnRunAnalysis.setEnabled(true);
                    chipValidationStatus.setText(R.string.pro_tools_status_complete);
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    layoutProgress.setVisibility(View.GONE);
                    layoutResults.setVisibility(View.VISIBLE);
                    applyStatisticsCollectionError();
                    btnRunAnalysis.setEnabled(true);
                    chipValidationStatus.setText(R.string.pro_tools_status_error);
                    Toast.makeText(this, getString(R.string.pro_tools_analysis_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private AnalysisReport generateAnalysisReport(BenchmarkManager.BenchmarkResult benchmarkResult,
                                                   List<Integer> selectedCategories,
                                                   List<String> methodologies) {
        VectraBenchmark.BenchmarkResult[] results = benchmarkResult.metrics;
        AnalysisReport report = new AnalysisReport();

        // Get device specifications
        VectraBenchmark.DeviceSpecification deviceSpec = VectraBenchmark.getDeviceSpecification();

        // Calculate metrics count without treating different metrics as repeated samples.
        int totalMetrics = 0;
        for (VectraBenchmark.BenchmarkResult r : results) {
            if (r != null) totalMetrics++;
        }

        report.totalScore = totalMetrics; // metric count only; not a statistical score
        report.categoryScores = new int[6]; // Not used in new format
        report.methodologies = methodologies;
        report.selectedCategories = selectedCategories;
        report.complianceStandards = QualityStandardsCatalog.getDefaultStandards();
        report.integrationSources = buildIntegrationSources();
        report.governance = benchmarkResult.governance;

        // Store device specifications
        report.deviceModel = deviceSpec.cpuModel;
        report.deviceManufacturer = Build.MANUFACTURER;
        report.cpuCores = deviceSpec.cpuCores;
        report.maxCpuFreqGHz = deviceSpec.maxCpuFreqHz / 1_000_000_000.0;
        report.totalRamGB = deviceSpec.totalRamBytes / (1024.0 * 1024.0 * 1024.0);
        report.cpuArchitecture = deviceSpec.cpuArchitecture;
        report.androidVersion = Build.VERSION.RELEASE;
        report.cpuAbi = Build.SUPPORTED_ABIS[0];

        // BenchmarkManager currently exposes one result per metric, not repeated samples
        // for the same metric/workload/input/unit identity. The only valid action is to
        // preserve the individual metrics and fail closed for aggregate reproducibility.
        markHomogeneousStatisticsNotMeasured(report);

        // Generate executive summary with device specs
        report.executiveSummary = generateExecutiveSummary(report, deviceSpec);

        // Generate grade justification
        report.gradeJustification = generateGradeJustification(report);

        report.timestamp = new Date();

        return report;
    }

    private void markHomogeneousStatisticsNotMeasured(AnalysisReport report) {
        report.homogeneousStatisticsAvailable = false;
        report.statisticsState = "NOT_MEASURED";
        report.statisticsBoundary =
                "Repeated samples for one metric/workload/input/unit were not captured by this run; "
                + "heterogeneous metric results are preserved individually and are not pooled.";
        report.mean = Double.NaN;
        report.median = Double.NaN;
        report.stdDev = Double.NaN;
        report.confidenceInterval95 = null;
        report.reproducibilityScore = Double.NaN;

        // Grade-like promotion is fail-closed until a receipt carries the required
        // homogeneous repeated-series evidence and provenance.
        report.isIndustryGrade = false;
        report.isAcademicGrade = false;
        report.isScientificGrade = false;
    }

    /**
     * Adapter for a future producer that supplies repeated samples of exactly one metric,
     * workload, input and unit. This method deliberately does not create a grade or a
     * synthetic "reproducibility percentage". It only summarizes the homogeneous series
     * using the methodology-bound IndustrialStatistics implementation.
     */
    @SuppressWarnings("unused")
    private void applyHomogeneousSeriesStatistics(AnalysisReport report, long[] homogeneousSamples) {
        IndustrialStatistics.SeriesSummary summary = IndustrialStatistics.summarize(homogeneousSamples);
        report.homogeneousStatisticsAvailable = summary.variabilityEstimable;
        report.statisticsState = summary.variabilityEstimable ? "OBSERVED_LIMITED" : "NOT_MEASURED";
        report.statisticsBoundary = summary.variabilityEstimable
                ? "Homogeneous repeated series summarized; no certification or grade is implied."
                : "One sample is an observation, not reproducibility evidence.";
        report.mean = summary.mean;
        report.median = summary.median;
        report.stdDev = summary.sampleStdDev;
        report.confidenceInterval95 = summary.confidenceIntervalEstimable
                ? new double[] {summary.ci95Low, summary.ci95High}
                : null;
        report.reproducibilityScore = Double.NaN;
        report.isIndustryGrade = false;
        report.isAcademicGrade = false;
        report.isScientificGrade = false;
    }

    private String generateExecutiveSummary(AnalysisReport report, VectraBenchmark.DeviceSpecification deviceSpec) {
        StringBuilder sb = new StringBuilder();

        // Device specifications header
        sb.append("DEVICE UNDER TEST (DUT)\n");
        sb.append("───────────────────────────────────\n");
        sb.append("CPU: ").append(deviceSpec.cpuModel).append("\n");
        sb.append("Cores: ").append(deviceSpec.cpuCores).append(" @ ").append(deviceSpec.getFormattedCpuFreq()).append("\n");
        sb.append("RAM: ").append(deviceSpec.getFormattedRam()).append("\n");
        sb.append("Architecture: ").append(deviceSpec.cpuArchitecture).append("\n\n");

        // Metrics summary
        sb.append("BENCHMARK SUMMARY\n");
        sb.append("───────────────────────────────────\n");
        sb.append("Total Metrics Measured: ").append(report.totalScore).append(" / 79\n");
        sb.append("Categories Selected: ").append(report.selectedCategories.size()).append(" / 6\n\n");

        // Methodologies applied
        sb.append("METHODOLOGIES APPLIED\n");
        sb.append("───────────────────────────────────\n");
        for (String method : report.methodologies) {
            sb.append("• ").append(method).append("\n");
        }
        sb.append("\n");

        appendGovernanceSummary(sb, report.governance);

        // Statistical summary: never manufacture a cross-metric series.
        sb.append("STATISTICAL SUMMARY\n");
        sb.append("───────────────────────────────────\n");
        sb.append("Repeated-series state: ").append(report.statisticsState).append("\n");
        if (report.homogeneousStatisticsAvailable) {
            sb.append("Mean Execution Time: ").append(formatStatValueOrMissing(report.mean)).append("\n");
            sb.append("Median Execution Time: ").append(formatStatValueOrMissing(report.median)).append("\n");
        } else {
            sb.append("Mean Execution Time: NOT_MEASURED\n");
            sb.append("Median Execution Time: NOT_MEASURED\n");
        }
        sb.append("Reproducibility: ").append(formatReproducibilityOrMissing(report.reproducibilityScore)).append("\n");
        sb.append("Boundary: ").append(report.statisticsBoundary);

        return sb.toString();
    }

    // Overload for backward compatibility
    private String generateExecutiveSummary(AnalysisReport report) {
        return generateExecutiveSummary(report, VectraBenchmark.getDeviceSpecification());
    }

    private String generateGradeJustification(AnalysisReport report) {
        if (!report.homogeneousStatisticsAvailable) {
            return "NOT_MEASURED: Industry/Academic/Scientific grade promotion is blocked because "
                    + "this execution did not capture a homogeneous repeated measurement series with receipt-bound provenance.";
        }

        // Even with a homogeneous series, this activity does not self-certify. External
        // acceptance predicates and receipt-bound provenance are required before promotion.
        return "OBSERVED_LIMITED: homogeneous series statistics may be reported, but no Industry/Academic/Scientific grade is asserted by this UI.";
    }

    private void displayResults(AnalysisReport report) {
        // Executive Summary
        tvExecutiveSummary.setText(report.executiveSummary);

        // Validation Grades: remain fail-closed unless a future explicit promotion gate is added.
        chipGradeIndustry.setChecked(report.isIndustryGrade);
        chipGradeAcademic.setChecked(report.isAcademicGrade);
        chipGradeScientific.setChecked(report.isScientificGrade);
        tvGradeJustification.setText(report.gradeJustification);

        // Statistical Analysis - only homogeneous repeated-series summaries are displayable.
        tvStatMean.setText(formatStatValueOrMissing(report.mean));
        tvStatMedian.setText(formatStatValueOrMissing(report.median));
        tvStatStdDev.setText(formatStatValueOrMissing(report.stdDev));

        if (report.confidenceInterval95 != null) {
            String ciLow = VectraBenchmark.formatTime((long) report.confidenceInterval95[0]);
            String ciHigh = VectraBenchmark.formatTime((long) report.confidenceInterval95[1]);
            tvStatConfidence.setText(getString(R.string.pro_tools_confidence_template, 95,
                    String.format(Locale.US, "[%s, %s]", ciLow, ciHigh)));
        } else {
            tvStatConfidence.setText(R.string.pro_tools_stat_no_data);
        }

        if (isFinitePositive(report.reproducibilityScore)) {
            tvStatReproducibility.setText(getString(R.string.pro_tools_reproducibility_template,
                    report.reproducibilityScore));
        } else {
            tvStatReproducibility.setText(R.string.pro_tools_stat_no_data);
        }
    }

    private boolean isFinitePositive(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0;
    }

    private String formatStatValueOrMissing(double value) {
        if (!isFinitePositive(value)) {
            return getString(R.string.pro_tools_stat_no_data);
        }
        return VectraBenchmark.formatTime((long) value);
    }

    private String formatReproducibilityOrMissing(double value) {
        if (!isFinitePositive(value)) {
            return "NOT_MEASURED";
        }
        return String.format(Locale.US, "%.1f%%", value);
    }

    private String formatNumber(double value) {
        // Use VectraBenchmark's formatTime for time-based values
        return VectraBenchmark.formatTime((long) value);
    }

    private void showFullReport() {
        if (lastReport == null) {
            Toast.makeText(this, R.string.pro_tools_no_results, Toast.LENGTH_SHORT).show();
            return;
        }

        String fullReport = generateFullReport(lastReport);

        TextView messageView = new TextView(this);
        messageView.setText(fullReport);
        messageView.setTextIsSelectable(true);
        messageView.setTypeface(android.graphics.Typeface.MONOSPACE);
        // Use 10sp for readability on various devices
        messageView.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 10);
        messageView.setPadding(16, 16, 16, 16);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        scrollView.addView(messageView);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.pro_tools_full_report_title)
                .setView(scrollView)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String generateFullReport(AnalysisReport report) {
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("╔════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║         VECTRAS PROFESSIONAL ANALYSIS REPORT                                   ║\n");
        sb.append("║         Engineering / Benchmark / Evidence-Gated System                        ║\n");
        sb.append("║         (Formal Engineering Metrics - SI Units)                                ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Metadata
        sb.append(String.format("║ Report Generated: %-60s║\n",
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(report.timestamp)));
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Section 0: Device Specifications
        sb.append("║ 0. DEVICE UNDER TEST (DUT) - TECHNICAL SPECIFICATIONS                         ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Manufacturer:         %-55s║\n", report.deviceManufacturer));
        sb.append(String.format("║  Model:                %-55s║\n", truncateStr(report.deviceModel, 55)));
        sb.append(String.format("║  CPU Cores:            %-55s║\n", report.cpuCores + " physical cores"));
        sb.append(String.format("║  Max CPU Frequency:    %-55s║\n", String.format(Locale.US, "%.2f GHz", report.maxCpuFreqGHz)));
        sb.append(String.format("║  Total RAM:            %-55s║\n", String.format(Locale.US, "%.1f GB", report.totalRamGB)));
        sb.append(String.format("║  Architecture:         %-55s║\n", report.cpuArchitecture != null ? report.cpuArchitecture : "N/A"));
        sb.append(String.format("║  Android Version:      %-55s║\n", report.androidVersion));
        sb.append(String.format("║  CPU ABI:              %-55s║\n", report.cpuAbi));
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Section G: Execution governance telemetry
        sb.append("║ G. EXECUTION GOVERNANCE / POLICY TELEMETRY                                    ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        if (report.governance != null) {
            sb.append(String.format("║  Policy Summary:       %-55s║\n", truncateStr(report.governance.toString(), 55)));
            sb.append("║  Evidence: executor governance available (summary mode).                      ║\n");
        } else {
            sb.append("║  Telemetry unavailable for this execution.                                    ║\n");
        }
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Section 1: Executive Summary
        sb.append("║ 1. EXECUTIVE TECHNICAL SUMMARY                                                ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(wrapText(report.executiveSummary, 78));
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Section 2: Methodology Standards
        sb.append("║ 2. METHODOLOGY STANDARDS APPLIED                                              ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        for (String method : report.methodologies) {
            sb.append(String.format("║  ✓ %-75s║\n", method));
        }
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Section 2A: Compliance Standards Catalog
        sb.append("║ 2A. COMPLIANCE STANDARDS CATALOG                                              ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        for (String standard : report.complianceStandards) {
            sb.append(String.format("║  • %-75s║\n", standard));
        }
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Section 2B: Rafaelia Integration Sources
        sb.append("║ 2B. INTEGRATION SOURCES (RAFAELIA)                                            ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        for (String source : report.integrationSources) {
            sb.append(String.format("║  • %-75s║\n", source));
        }
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Section 2C: Execution Governance / Applied Policy
        sb.append("║ 2C. EXECUTION GOVERNANCE / APPLIED POLICY                                     ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        appendGovernanceTable(sb, report.governance);
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Section 3: Statistical Analysis with explicit evidence state
        sb.append("║ 3. STATISTICAL ROBUSTNESS (HOMOGENEOUS SERIES ONLY)                           ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Evidence State:          %-52s║\n", truncateStr(report.statisticsState, 52)));
        sb.append(String.format("║  Mean Execution Time:     %-52s║\n", formatStatValueOrMissing(report.mean)));
        sb.append(String.format("║  Median Execution Time:   %-52s║\n", formatStatValueOrMissing(report.median)));
        sb.append(String.format("║  Standard Deviation:      %-52s║\n", formatStatValueOrMissing(report.stdDev)));
        if (report.confidenceInterval95 != null) {
            String ciLow = VectraBenchmark.formatTime((long) report.confidenceInterval95[0]);
            String ciHigh = VectraBenchmark.formatTime((long) report.confidenceInterval95[1]);
            sb.append(String.format("║  95%% Confidence Interval: [%s, %s]%-20s║\n", ciLow, ciHigh, ""));
        } else {
            sb.append(String.format("║  95%% Confidence Interval: %-52s║\n", "NOT_MEASURED"));
        }
        sb.append(String.format("║  Reproducibility:         %-52s║\n",
                formatReproducibilityOrMissing(report.reproducibilityScore)));
        sb.append("║  Boundary:                                                                    ║\n");
        sb.append(wrapText(report.statisticsBoundary, 78));
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Section 4: Validation Grade
        sb.append("║ 4. ALIGNMENT WITH ACADEMIC STANDARDS                                          ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║  Industry-grade:        %-54s║\n", report.isIndustryGrade ? "✓ PASSED" : "✗ NOT ASSERTED"));
        sb.append(String.format("║  Academic-grade:        %-54s║\n", report.isAcademicGrade ? "✓ PASSED" : "✗ NOT ASSERTED"));
        sb.append(String.format("║  Scientific-grade:      %-54s║\n", report.isScientificGrade ? "✓ PASSED" : "✗ NOT ASSERTED"));
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║  Justification:                                                               ║\n");
        sb.append(wrapText(report.gradeJustification, 78));
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Section 5: Metrics Summary
        sb.append("║ 5. METRICS TAXONOMY SUMMARY                                                   ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        String[] categoryNames = {"CPU Single-threaded", "CPU Multi-threaded", "Memory", "Storage", "Integrity", "Emulation"};
        int[] metricCounts = {20, 10, 15, 15, 10, 9}; // Standard metric counts per category
        for (int i = 0; i < categoryNames.length; i++) {
            boolean selected = report.selectedCategories.contains(i);
            String status = selected ? "✓ Measured" : "○ Not selected";
            sb.append(String.format("║  %-25s: %d metrics (%s)%-20s║\n",
                    categoryNames[i], metricCounts[i], status, ""));
        }
        sb.append(String.format("║  %-25s: %d metrics total%-30s║\n", "TOTAL MEASURED", report.totalScore, ""));
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");

        // Section 6: Formal Technical Verdict
        sb.append("║ 6. FORMAL TECHNICAL VERDICT                                                   ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════════╣\n");
        String verdict;
        if (report.isScientificGrade) {
            verdict = "Scientific-grade promotion was explicitly authorized by an external evidence gate.";
        } else if (report.isAcademicGrade) {
            verdict = "Academic-grade promotion was explicitly authorized by an external evidence gate.";
        } else if (report.isIndustryGrade) {
            verdict = "Industry-grade promotion was explicitly authorized by an external evidence gate.";
        } else {
            verdict = "No Industry/Academic/Scientific grade is asserted. Individual benchmark metrics are preserved, while repeated homogeneous-series evidence remains required for statistical reproducibility claims.";
        }
        sb.append(wrapText(verdict, 78));

        // Footer
        sb.append("╚════════════════════════════════════════════════════════════════════════════════╝\n");

        // Detailed Results (if available)
        if (lastResults != null) {
            sb.append("\n");
            sb.append(VectraBenchmark.formatReport(lastResults));
        }

        return sb.toString();
    }

    private void appendGovernanceSummary(StringBuilder sb, BenchmarkManager.ExecutionGovernance governance) {
        if (governance == null) {
            return;
        }
        sb.append("EXECUTION GOVERNANCE\n");
        sb.append("───────────────────────────────────\n");
        sb.append("Policy Profile: ").append(governance.profile).append("\n");
        sb.append("Effective SMP: ").append(governance.effectiveSmp).append("\n");
        sb.append("Thread limits (core/max): ").append(governance.coreThreads)
                .append('/').append(governance.maxThreads).append("\n");
        sb.append("Queue depth observed/capacity: ").append(governance.maxObservedQueueDepth)
                .append('/').append(governance.queueCapacity).append("\n");
        sb.append("Rejected tasks: ").append(governance.rejectedCount).append("\n");
        sb.append("CallerRuns activations: ").append(governance.callerRunsCount)
                .append(governance.callerRunsEnabled ? " (enabled)" : " (disabled)").append("\n");
        sb.append("Process limit (pid_max): ").append(governance.processLimit).append("\n");
        sb.append("Observed running processes: ").append(governance.runningProcessesObserved).append("\n\n");
    }

    private void appendGovernanceTable(StringBuilder sb, BenchmarkManager.ExecutionGovernance governance) {
        if (governance == null) {
            sb.append(String.format("║  %-77s║\n", "No governance telemetry captured"));
            return;
        }
        sb.append(String.format("║  Policy Profile: %-61s║\n", truncateStr(governance.profile, 61)));
        sb.append(String.format("║  Effective SMP: %-61s║\n", governance.effectiveSmp));
        sb.append(String.format("║  Thread limits (core/max): %-50s║\n", governance.coreThreads + "/" + governance.maxThreads));
        sb.append(String.format("║  Queue depth max/capacity: %-50s║\n", governance.maxObservedQueueDepth + "/" + governance.queueCapacity));
        sb.append(String.format("║  Rejected tasks: %-60s║\n", governance.rejectedCount));
        sb.append(String.format("║  CallerRuns activations: %-52s║\n", governance.callerRunsCount + (governance.callerRunsEnabled ? " (enabled)" : " (disabled)")));
        sb.append(String.format("║  Process limit (pid_max): %-52s║\n", governance.processLimit));
        sb.append(String.format("║  Observed running processes: %-48s║\n", governance.runningProcessesObserved));
    }

    private String truncateStr(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 3) + "...";
    }

    private String wrapText(String text, int width) {
        StringBuilder result = new StringBuilder();
        String[] lines = text.split("\n");
        for (String textLine : lines) {
            if (textLine.isEmpty()) {
                result.append(String.format("║ %-" + width + "s║\n", ""));
                continue;
            }
            String[] words = textLine.split(" ");
            StringBuilder line = new StringBuilder();

            for (String word : words) {
                if (line.length() + word.length() + 1 > width) {
                    result.append(String.format("║ %-" + width + "s║\n", line.toString().trim()));
                    line = new StringBuilder();
                }
                line.append(word).append(" ");
            }
            if (line.length() > 0) {
                result.append(String.format("║ %-" + width + "s║\n", line.toString().trim()));
            }
        }
        return result.toString();
    }

    private List<String> buildIntegrationSources() {
        List<String> sources = new ArrayList<>();
        sources.add("qemu_rafaelia: " + AppConfig.rafaeliaQemuRepo);
        sources.add("androidx_RmR: " + AppConfig.rafaeliaAndroidxRepo);
        return sources;
    }

    private void exportReport() {
        if (lastReport == null) {
            Toast.makeText(this, R.string.pro_tools_no_results, Toast.LENGTH_SHORT).show();
            return;
        }

        executor.execute(() -> {
            try {
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
                String fileName = "vectras_analysis_" + timestamp + ".txt";
                File exportDir = new File(AppConfig.maindirpath);
                if (!exportDir.exists()) {
                    exportDir.mkdirs();
                }
                File exportFile = new File(exportDir, fileName);

                String report = generateFullReport(lastReport);
                FileUtils.writeToFile(exportDir.getAbsolutePath(), fileName, report);

                mainHandler.post(() -> {
                    Toast.makeText(this,
                            getString(R.string.pro_tools_report_exported, exportFile.getAbsolutePath()),
                            Toast.LENGTH_LONG).show();
                });

            } catch (Exception e) {
                mainHandler.post(() -> {
                    Toast.makeText(this, getString(R.string.pro_tools_export_failed, e.getMessage()),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void shareReport() {
        if (lastReport == null) {
            Toast.makeText(this, R.string.pro_tools_no_results, Toast.LENGTH_SHORT).show();
            return;
        }

        String report = generateFullReport(lastReport);

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Vectras VM Professional Analysis Report");
        shareIntent.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.pro_tools_share_report)));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Internal class to hold analysis report data
     */
    private static class AnalysisReport {
        int totalScore;
        int[] categoryScores;
        List<String> methodologies;
        List<String> complianceStandards;
        List<String> integrationSources;
        List<Integer> selectedCategories;
        BenchmarkManager.ExecutionGovernance governance;

        // Statistical analysis: valid only for a homogeneous repeated series.
        boolean homogeneousStatisticsAvailable;
        String statisticsState;
        String statisticsBoundary;
        double mean;
        double median;
        double stdDev;
        double[] confidenceInterval95;
        double reproducibilityScore;

        // Validation grades
        boolean isIndustryGrade;
        boolean isAcademicGrade;
        boolean isScientificGrade;

        // Text content
        String executiveSummary;
        String gradeJustification;

        // Device specifications
        String deviceModel;
        String deviceManufacturer;
        String androidVersion;
        String cpuAbi;
        String cpuArchitecture;
        int cpuCores;
        double maxCpuFreqGHz;
        double totalRamGB;
        Date timestamp;
    }
}
