package com.vectras.vm.evidence;

/**
 * Converts filesystem locations into the narrow logical paths allowed in a
 * shareable device-evidence receipt. The collector must not fall back to an
 * absolute path: those paths can carry adopted-storage volume IDs or guest
 * locations.
 */
public final class EvidencePathRedactor {
    public static final String TOKEN_VAZIO = "TOKEN_VAZIO";
    public static final String REDACTED_PATH = "<redacted-external-or-unknown-path>";

    private static final String[] SAFE_FILES_DIR_PREFIXES = new String[] {
            "usr/bin/",
            "bin/",
            "distro/bin/",
            "distro/usr/bin/",
            "distro/usr/local/bin/",
            "evidence/catalog/"
    };

    private EvidencePathRedactor() {
        throw new AssertionError("utility class");
    }

    public static String normalizeForReceipt(
            String canonicalPath,
            String canonicalFilesDir,
            String canonicalInstalledApk,
            String canonicalNativeLibraryDir
    ) {
        String path = normalize(canonicalPath);
        if (path.isEmpty()) {
            return TOKEN_VAZIO;
        }

        String filesDir = normalize(canonicalFilesDir);
        if (isSameOrChild(path, filesDir)) {
            String relative = relativePath(path, filesDir);
            return isSafeFilesDirRelative(relative)
                    ? logicalPath("<filesDir>", path, filesDir)
                    : REDACTED_PATH;
        }

        String installedApk = normalize(canonicalInstalledApk);
        if (!installedApk.isEmpty() && path.equals(installedApk)) {
            return "<installedApk>/" + leafName(path);
        }

        String nativeLibraryDir = normalize(canonicalNativeLibraryDir);
        if (isSameOrChild(path, nativeLibraryDir)) {
            return logicalPath("<nativeLibDir>", path, nativeLibraryDir);
        }

        return REDACTED_PATH;
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String out = value.trim().replace('\\', '/');
        while (out.length() > 1 && out.endsWith("/")) {
            out = out.substring(0, out.length() - 1);
        }
        return out;
    }

    private static boolean isSameOrChild(String path, String root) {
        return !root.isEmpty() && (path.equals(root) || path.startsWith(root + "/"));
    }

    private static String relativePath(String path, String root) {
        return path.equals(root) ? "" : path.substring(root.length() + 1);
    }

    private static boolean isSafeFilesDirRelative(String relative) {
        if (relative.isEmpty()) {
            return true;
        }
        for (String prefix : SAFE_FILES_DIR_PREFIXES) {
            if (relative.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String logicalPath(String token, String path, String root) {
        if (path.equals(root)) {
            return token;
        }
        return token + path.substring(root.length());
    }

    private static String leafName(String path) {
        int slash = path.lastIndexOf('/');
        return slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : "base.apk";
    }
}
