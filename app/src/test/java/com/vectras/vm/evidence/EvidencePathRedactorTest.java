package com.vectras.vm.evidence;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class EvidencePathRedactorTest {
    private static final String FILES_DIR = "/data/user/0/com.rafacodephi.app/files";
    private static final String INSTALLED_APK = "/data/app/~~opaque/com.rafacodephi.app-base/base.apk";
    private static final String NATIVE_LIBRARY_DIR = "/data/app/~~opaque/com.rafacodephi.app-base/lib/arm64";

    @Test
    public void keepsOnlyLogicalAppRoots() {
        assertEquals("<filesDir>/evidence/catalog/receipt.json",
                EvidencePathRedactor.normalizeForReceipt(
                        FILES_DIR + "/evidence/catalog/receipt.json",
                        FILES_DIR, INSTALLED_APK, NATIVE_LIBRARY_DIR));
        assertEquals("<installedApk>/base.apk",
                EvidencePathRedactor.normalizeForReceipt(
                        INSTALLED_APK, FILES_DIR, INSTALLED_APK, NATIVE_LIBRARY_DIR));
        assertEquals("<nativeLibDir>/libvectra_core_accel.so",
                EvidencePathRedactor.normalizeForReceipt(
                        NATIVE_LIBRARY_DIR + "/libvectra_core_accel.so",
                        FILES_DIR, INSTALLED_APK, NATIVE_LIBRARY_DIR));
    }

    @Test
    public void redactsAdoptedStorageAndUnknownPaths() {
        String rawPath = "/mnt/expand/7F3A-19BC/user/0/com.rafacodephi.app/files/usr/bin/qemu-system-x86_64";
        String receiptPath = EvidencePathRedactor.normalizeForReceipt(
                rawPath, FILES_DIR, INSTALLED_APK, NATIVE_LIBRARY_DIR);

        assertEquals(EvidencePathRedactor.REDACTED_PATH, receiptPath);
        assertFalse(receiptPath.contains("7F3A-19BC"));
        assertFalse(receiptPath.contains("qemu-system-x86_64"));
    }

    @Test
    public void rejectsSiblingOfFilesDirectory() {
        assertEquals(EvidencePathRedactor.REDACTED_PATH,
                EvidencePathRedactor.normalizeForReceipt(
                        FILES_DIR + "-backup/guest.img",
                        FILES_DIR, INSTALLED_APK, NATIVE_LIBRARY_DIR));
    }

    @Test
    public void emitsTokenForMissingPath() {
        assertEquals(EvidencePathRedactor.TOKEN_VAZIO,
                EvidencePathRedactor.normalizeForReceipt(
                        null, FILES_DIR, INSTALLED_APK, NATIVE_LIBRARY_DIR));
    }
}
