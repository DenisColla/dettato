package it.migelino.dettato;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.SystemClock;

import java.io.File;

/** Registratore: MPEG4/AAC, mono, 16 kHz. Tutto sul thread principale. */
final class Rec {
    private static MediaRecorder mr;
    private static File file;
    private static long startedAt;

    static boolean isRecording() {
        return mr != null;
    }

    /** Avvia la registrazione. Restituisce null se tutto ok, altrimenti il messaggio d'errore. */
    static String start(Context c) {
        release();
        try {
            file = new File(c.getCacheDir(), "dettato.m4a");
            if (file.exists()) file.delete();
            mr = Build.VERSION.SDK_INT >= 31 ? new MediaRecorder(c) : new MediaRecorder();
            mr.setAudioSource(MediaRecorder.AudioSource.MIC);
            mr.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mr.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mr.setAudioChannels(1);
            mr.setAudioSamplingRate(16000);
            mr.setAudioEncodingBitRate(32000);
            mr.setOutputFile(file.getAbsolutePath());
            mr.prepare();
            mr.start();
            startedAt = SystemClock.elapsedRealtime();
            return null;
        } catch (Exception e) {
            release();
            return "microfono non disponibile (" + e.getMessage() + ")";
        }
    }

    /** Ferma la registrazione. Restituisce il file, oppure null se è troppo breve o fallita. */
    static File stop() {
        if (mr == null) return null;
        long dur = SystemClock.elapsedRealtime() - startedAt;
        boolean ok;
        try {
            mr.stop();
            ok = true;
        } catch (Exception e) {
            ok = false;
        }
        release();
        return (ok && dur >= 700) ? file : null;
    }

    static void release() {
        if (mr != null) {
            try {
                mr.release();
            } catch (Exception ignored) {
            }
            mr = null;
        }
    }
}
