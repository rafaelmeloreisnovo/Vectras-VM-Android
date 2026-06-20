package com.vectras.vm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.vectras.vm.main.core.MainStartVM;
import com.vectras.vm.runtime.ExpandedRuntimePreflight;
import com.vectras.vm.runtime.QemuArgvContract;
import com.vectras.vm.runtime.QemuRuntimeProbe;
import com.vectras.vm.runtime.RuntimeSessionReport;
import com.vectras.vterm.Terminal;

import java.io.File;

public class MainService extends Service {
    public static String CHANNEL_ID = "Vectras VM Service";
    private static final int NOTIFICATION_ID = 1;
    private static final String MACHINE_NAME = "Vectras VM";
    public static volatile String env = null;
    private String TAG = "MainService";
    public static volatile MainService service;
    private static volatile Context activityContext;
    private static final Object LOCK = new Object();

    @Override
    public void onCreate() {
        super.onCreate();
        synchronized (LOCK) {
            service = this;
        }
        createNotificationChannel();

        Intent stopSelf = new Intent(this, MainService.class);
        stopSelf.setAction("STOP");
        PendingIntent pStopSelf = PendingIntent.getService(
                this, 0, stopSelf, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Vectras VM")
                .setContentText(MACHINE_NAME + " running in background.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .addAction(R.drawable.round_logout_24, "Stop", pStopSelf)
                .build();

        startForeground(NOTIFICATION_ID, notification);

        String command;
        Context ctx;
        synchronized (LOCK) {
            command = env;
            ctx = getActivityContext();
        }

        if (command != null) {
            Context targetContext = ctx != null ? ctx : getApplicationContext();
            MainStartVM.ensureLastVmIdInitialized(MainStartVM.lastVMID);
            persistLaunchProof(targetContext, command, "foreground_service_on_create");
            Terminal vterm = new Terminal(targetContext);
            vterm.executeShellCommand2(command, true, targetContext);
        } else {
            Log.e(TAG, "env is null");
        }
    }

    public static void stopService() {
        Thread t = new Thread(() -> {
            MainService currentService;
            synchronized (LOCK) {
                currentService = service;
            }
            if (currentService != null) {
                currentService.stopForeground(true);
                currentService.stopSelf();
                cleanup(currentService.getApplicationContext());
                clearReferences();
            }

        });
        t.setName("HomeStartVM");
        t.start();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "STOP".equals(intent.getAction())) {
            stopForeground(true);
            stopSelf();
            cleanup(this);
            clearReferences();
            return START_NOT_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        cleanup(null);
        clearReferences();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID, CHANNEL_ID,
                    NotificationManager.IMPORTANCE_DEFAULT
            );

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    public static void startCommand(String _env, Context _context) {
        MainStartVM.ensureLastVmIdInitialized(MainStartVM.lastVMID);
        persistLaunchProof(_context, _env, "existing_service_start_command");
        Terminal vterm = new Terminal(_context);
        vterm.executeShellCommand2(_env, true, _context);
    }

    public static void setActivityContext(Context context) {
        synchronized (LOCK) {
            activityContext = context != null ? context.getApplicationContext() : null;
        }
    }

    public static Context getActivityContext() {
        synchronized (LOCK) {
            return activityContext;
        }
    }

    private static void persistLaunchProof(Context context, String command, String phase) {
        if (context == null) return;
        try {
            String vmId = MainStartVM.ensureLastVmIdInitialized(MainStartVM.lastVMID);
            ExpandedRuntimePreflight.Result preflight = ExpandedRuntimePreflight.run(context, null, null);
            QemuArgvContract contract = QemuArgvContract.fromShellCommand(command);
            File out = RuntimeSessionReport.begin(vmId, MainStartVM.lastVMName)
                    .phase("main_service", "dispatch", phase)
                    .putString("channel_id", CHANNEL_ID)
                    .putCommand(contract)
                    .putExpandedPreflight(preflight)
                    .putRuntimeProbe(QemuRuntimeProbe.captureNoProcessReference())
                    .putNativeSnapshot()
                    .persist(context);
            if (out != null) {
                Log.i("MainService", "Runtime proof persisted: " + out.getAbsolutePath());
            }
        } catch (Exception e) {
            Log.w("MainService", "Unable to persist runtime launch proof", e);
        }
    }

    private static void cleanup(Context fallbackContext) {
        synchronized (LOCK) {
            MainService currentService = service;
            Context ctx = fallbackContext;
            if (ctx == null && currentService != null) {
                ctx = currentService.getApplicationContext();
            }
            if (ctx != null) {
                VMManager.killallqemuprocesses(ctx);
            }
        }
    }

    private static void clearReferences() {
        synchronized (LOCK) {
            setActivityContext(null);
            env = null;
            service = null;
        }
    }
}
