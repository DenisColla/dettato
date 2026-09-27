package it.migelino.dettato;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

/** Servizio in primo piano che tiene attivo il microfono mentre registri. */
public class RecorderService extends Service {
    static RecorderService running;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        goForeground(); // va chiamato subito, sempre
        DettatoService d = DettatoService.inst;
        if (d == null || d.state != DettatoService.REC) {
            end();
            return START_NOT_STICKY;
        }
        running = this;
        String err = Rec.start(this);
        if (err != null) {
            end();
            d.recordFailed(err);
        }
        return START_NOT_STICKY;
    }

    void end() {
        if (running == this) running = null;
        try {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } catch (Exception ignored) {
        }
        stopSelf();
    }

    @Override
    public void onDestroy() {
        if (running == this) running = null;
        super.onDestroy();
    }

    private void goForeground() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("rec", "Registrazione dettato", NotificationManager.IMPORTANCE_LOW));
        Notification n = new Notification.Builder(this, "rec")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Dettato")
                .setContentText("Registrazione in corso")
                .setOngoing(true)
                .build();
        try {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } catch (Exception e) {
            try {
                startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
            } catch (Exception ignored) {
            }
        }
    }
}
