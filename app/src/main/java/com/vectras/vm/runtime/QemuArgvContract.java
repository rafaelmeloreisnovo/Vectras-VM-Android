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
 * Immutable, inspectable representation of the QEMU launch command.
 *
 * <p>This does not replace the current shell string path yet; it provides the
 * missing proof layer: stable argv serialization + deterministic hash so each
 * VM launch can be audited without relying on lossy log text.</p>
 */
public final class QemuArgvContract {
    private final String qemuBinary;
    private final List<String> argv;
    private final String commandString;
    private final String argvSha256;
    private final boolean parsedFromShellString;

    private QemuArgvContract(String qemuBinary,
                             List<String> argv,
                             String commandString,
                             boolean parsedFromShellString) {
        this.qemuBinary = safe(qemuBinary);
        this.argv = Collections.unmodifiableList(new ArrayList<>(argv == null ? Collections.emptyList() : argv));
        this.commandString = safe(commandString);
        this.parsedFromShellString = parsedFromShellString;
        this.argvSha256 = sha256(stablePayload(this.qemuBinary, this.argv, this.commandString));
    }

    public static QemuArgvContract fromArgs(String qemuBinary, List<String> argv) {
        StringBuilder command = new StringBuilder();
        if (qemuBinary != null && !qemuBinary.trim().isEmpty()) {
            command.append(qemuBinary.trim());
        }
        if (argv != null) {
            for (String arg : argv) {
                if (arg == null || arg.trim().isEmpty()) continue;
                if (command.length() > 0) command.append(' ');
                command.append(arg.trim());
            }
        }
        return new QemuArgvContract(qemuBinary, argv, command.toString(), false);
    }

    /**
     * Compatibility parser for the current Vectras launch path where QEMU is
     * still written into a shell. It preserves quoted spans well enough for
     * diagnostics; the goal is audit visibility, not command execution.
     */
    public static QemuArgvContract fromShellCommand(String commandString) {
        List<String> tokens = splitShellLike(commandString);
        String binary = tokens.isEmpty() ? "" : tokens.get(0);
        List<String> args = tokens.size() <= 1 ? Collections.emptyList() : tokens.subList(1, tokens.size());
        return new QemuArgvContract(binary, args, commandString, true);
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
            json.put("arg_count", argv.size());
        } catch (Exception ignored) {
            // JSONObject.put should not fail for primitive/String payloads here.
        }
        return json;
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
