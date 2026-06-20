package com.vectras.vm.runtime;

import com.vectras.qemu.Config;

import org.json.JSONObject;

import java.io.File;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Locale;

/**
 * Low-cost runtime probes for separating "process alive" from "VM service ready".
 */
public final class QemuRuntimeProbe {
    public static final class Snapshot {
        public final long elapsedRealtimeMs;
        public final boolean processAlive;
        public final long pid;
        public final boolean qmpSocketExists;
        public final boolean vncSocketExists;
        public final boolean vncTcpOpen;
        public final String qmpSocketPath;
        public final String vncSocketPath;
        public final String note;

        Snapshot(long elapsedRealtimeMs,
                 boolean processAlive,
                 long pid,
                 boolean qmpSocketExists,
                 boolean vncSocketExists,
                 boolean vncTcpOpen,
                 String qmpSocketPath,
                 String vncSocketPath,
                 String note) {
            this.elapsedRealtimeMs = elapsedRealtimeMs;
            this.processAlive = processAlive;
            this.pid = pid;
            this.qmpSocketExists = qmpSocketExists;
            this.vncSocketExists = vncSocketExists;
            this.vncTcpOpen = vncTcpOpen;
            this.qmpSocketPath = safe(qmpSocketPath);
            this.vncSocketPath = safe(vncSocketPath);
            this.note = safe(note);
        }

        public boolean hasRuntimeSignal() {
            return processAlive || qmpSocketExists || vncSocketExists || vncTcpOpen;
        }

        public JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("elapsed_realtime_ms", elapsedRealtimeMs);
                json.put("process_alive", processAlive);
                json.put("pid", pid);
                json.put("qmp_socket_exists", qmpSocketExists);
                json.put("vnc_socket_exists", vncSocketExists);
                json.put("vnc_tcp_open", vncTcpOpen);
                json.put("qmp_socket_path", qmpSocketPath);
                json.put("vnc_socket_path", vncSocketPath);
                json.put("note", note);
            } catch (Exception ignored) {
            }
            return json;
        }
    }

    private QemuRuntimeProbe() {
        throw new AssertionError("QemuRuntimeProbe is a utility class and cannot be instantiated");
    }

    public static Snapshot capture(Process process, int tcpTimeoutMs) {
        long pid = safePid(process);
        boolean alive = process != null && process.isAlive();
        String qmp = Config.getLocalQMPSocketPath();
        String vnc = Config.getLocalVNCSocketPath();
        boolean qmpExists = exists(qmp);
        boolean vncExists = exists(vnc);
        boolean tcpVnc = isTcpPortOpen(Config.defaultVNCHost, Config.defaultVNCPort, Math.max(100, tcpTimeoutMs));
        String note = alive
                ? "process-alive"
                : (qmpExists || vncExists || tcpVnc ? "socket-signal-without-process-reference" : "no-runtime-signal");
        return new Snapshot(android.os.SystemClock.elapsedRealtime(), alive, pid, qmpExists, vncExists, tcpVnc, qmp, vnc, note);
    }

    public static Snapshot captureNoProcessReference() {
        return capture(null, 250);
    }

    private static boolean exists(String path) {
        return path != null && !path.trim().isEmpty() && new File(path).exists();
    }

    private static boolean isTcpPortOpen(String host, int port, int timeoutMs) {
        if (host == null || host.trim().isEmpty() || port <= 0) return false;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static long safePid(Process process) {
        if (process == null) return -1L;
        try {
            return process.pid();
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
