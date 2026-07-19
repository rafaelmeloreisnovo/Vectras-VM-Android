package com.vectras.vm.runtime;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Immutable, inspectable representation of a QEMU launch command.
 *
 * <p>The contract supports two distinct uses:</p>
 * <ul>
 *     <li>stable audit serialization and deterministic hashing;</li>
 *     <li>direct argv dispatch without evaluating the command through a shell.</li>
 * </ul>
 *
 * <p>{@link #fromShellCommand(String)} exists only as a compatibility boundary for
 * legacy callers that still build a command string. Once parsed, callers must use
 * {@link #toProcessArgv()} for execution. Shell operators then remain literal argv
 * values and cannot be evaluated by {@code /bin/sh}.</p>
 */
public final class QemuArgvContract {
    private final String qemuBinary;
    private final List<String> argv;
    private final String commandString;
    private final String argvSha256;
    private final boolean parsedFromShellString;
    private final int qemuTokenIndex;

    private QemuArgvContract(String qemuBinary,
                             List<String> argv,
                             String commandString,
                             boolean parsedFromShellString,
                             int qemuTokenIndex) {
        this.qemuBinary = safe(qemuBinary);
        this.argv = Collections.unmodifiableList(new ArrayList<>(argv == null ? Collections.emptyList() : argv));
        this.commandString = safe(commandString);
        this.parsedFromShellString = parsedFromShellString;
        this.qemuTokenIndex = qemuTokenIndex;
        this.argvSha256 = sha256(stablePayload(this.qemuBinary, this.argv, this.commandString));
    }

    public static QemuArgvContract fromArgs(String qemuBinary, List<String> argv) {
        StringBuilder command = new StringBuilder();
        if (qemuBinary != null && !qemuBinary.trim().isEmpty()) {
            command.append(qemuBinary.trim());
        }
        if (argv != null) {
            for (String arg : argv) {
                if (arg == null || arg.isEmpty()) continue;
                if (command.length() > 0) command.append(' ');
                command.append(arg);
            }
        }
        int qemuIndex = isQemuBinaryToken(qemuBinary) ? 0 : -1;
        return new QemuArgvContract(qemuBinary, argv, command.toString(), false, qemuIndex);
    }

    /**
     * Compatibility parser for the legacy Vectras launch path where QEMU may be
     * embedded in a shell-like command string. Quoted spans and backslash escapes
     * are decoded into tokens, but no token is executed by a shell.
     */
    public static QemuArgvContract fromShellCommand(String commandString) {
        List<String> tokens = splitShellLike(commandString);
        int qemuIndex = findQemuTokenIndex(tokens);
        if (qemuIndex < 0) {
            String binary = tokens.isEmpty() ? "" : tokens.get(0);
            List<String> args = tokens.size() <= 1 ? Collections.emptyList() : tokens.subList(1, tokens.size());
            return new QemuArgvContract(binary, args, commandString, true, -1);
        }
        String binary = tokens.get(qemuIndex);
        List<String> args = qemuIndex + 1 >= tokens.size() ? Collections.emptyList() : tokens.subList(qemuIndex + 1, tokens.size());
        return new QemuArgvContract(binary, args, commandString, true, qemuIndex);
    }

    public String getQemuBinary() {
        return qemuBinary;
    }

    public List<String> getArgv() {
        return argv;
    }

    public String getCommandString() {
        return commandString;
    }

    public String getArgvSha256() {
        return argvSha256;
    }

    public boolean isParsedFromShellString() {
        return parsedFromShellString;
    }

    public int getQemuTokenIndex() {
        return qemuTokenIndex;
    }

    /** Returns true only for a basename beginning with qemu-system-. */
    public boolean hasRecognizedQemuBinary() {
        return qemuTokenIndex >= 0 && isQemuBinaryToken(qemuBinary);
    }

    /**
     * Builds the exact process argv used for direct guest execution.
     *
     * @throws IllegalStateException if the parsed command does not contain a
     *                               recognized qemu-system-* executable.
     */
    public List<String> toProcessArgv() {
        if (!hasRecognizedQemuBinary()) {
            throw new IllegalStateException("Direct argv dispatch requires qemu-system-* binary");
        }
        ArrayList<String> processArgv = new ArrayList<>(argv.size() + 1);
        processArgv.add(qemuBinary);
        processArgv.addAll(argv);
        return Collections.unmodifiableList(processArgv);
    }

    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        JSONArray args = new JSONArray();
        for (String arg : argv) args.put(arg);
        try {
            json.put("qemu_binary", qemuBinary);
            json.put("argv", args);
            json.put("command_string", commandString);
            json.put("argv_sha256", argvSha256);
            json.put("parsed_from_shell_string", parsedFromShellString);
            json.put("qemu_token_index", qemuTokenIndex);
            json.put("arg_count", argv.size());
            json.put("direct_argv_allowed", hasRecognizedQemuBinary());
        } catch (Exception ignored) {
            // JSONObject.put should not fail for primitive/String payloads here.
        }
        return json;
    }

    private static int findQemuTokenIndex(List<String> tokens) {
        if (tokens == null) return -1;
        for (int i = 0; i < tokens.size(); i++) {
            if (isQemuBinaryToken(tokens.get(i))) return i;
        }
        return -1;
    }

    private static boolean isQemuBinaryToken(String token) {
        if (token == null) return false;
        String normalized = token.trim();
        if (normalized.isEmpty()) return false;
        int slash = Math.max(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'));
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return base.startsWith("qemu-system-");
    }

    private static List<String> splitShellLike(String value) {
        ArrayList<String> out = new ArrayList<>();
        if (value == null || value.trim().isEmpty()) return out;
        StringBuilder current = new StringBuilder();
        boolean single = false;
        boolean dbl = false;
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            if (c == '\\' && !single) {
                escaped = true;
                continue;
            }
            if (c == '\'' && !dbl) {
                single = !single;
                continue;
            }
            if (c == '"' && !single) {
                dbl = !dbl;
                continue;
            }
            if (Character.isWhitespace(c) && !single && !dbl) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                continue;
            }
            current.append(c);
        }
        if (escaped) current.append('\\');
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    private static String stablePayload(String binary, List<String> argv, String command) {
        StringBuilder sb = new StringBuilder();
        sb.append("binary=").append(safe(binary)).append('\n');
        sb.append("command=").append(safe(command)).append('\n');
        if (argv != null) {
            for (int i = 0; i < argv.size(); i++) {
                sb.append(String.format(Locale.US, "arg[%04d]=", i)).append(safe(argv.get(i))).append('\n');
            }
        }
        return sb.toString();
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] raw = digest.digest(safe(value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format(Locale.US, "%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) {
            return "sha256-error:" + e.getClass().getSimpleName();
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
