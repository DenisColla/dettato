package it.migelino.dettato;

import android.Manifest;
import android.accessibilityservice.AccessibilityService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * Servizio di accessibilità: mostra il tasto quando la tastiera è aperta,
 * gestisce registrazione / elaborazione e incolla il testo nel campo attivo.
 */
public class DettatoService extends AccessibilityService {
    static DettatoService inst;

    static final int IDLE = 0;
    static final int REC = 1;
    static final int BUSY = 2;

    private static final int BLUE = 0xFF1E88E5;
    private static final int RED = 0xFFE53935;

    int state = IDLE;

    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable check = this::refresh;
    private WindowManager wm;
    private TextView btn;
    private GradientDrawable bg;
    private WindowManager.LayoutParams lp;
    private boolean shown;
    private boolean usingService;

    // ---------- Ciclo di vita ----------

    @Override
    protected void onServiceConnected() {
        inst = this;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildButton();
        refresh();
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        h.removeCallbacks(check);
        h.postDelayed(check, 120);
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        h.removeCallbacksAndMessages(null);
        Rec.release();
        if (RecorderService.running != null) RecorderService.running.end();
        if (shown) {
            try {
                wm.removeView(btn);
            } catch (Exception ignored) {
            }
            shown = false;
        }
        state = IDLE;
        if (inst == this) inst = null;
    }

    // ---------- Visibilità del tasto ----------

    /** Il tasto è visibile se la tastiera è aperta oppure se sta registrando / elaborando. */
    void refresh() {
        if (wm == null || btn == null) return;
        boolean want = state != IDLE || keyboardVisible();
        try {
            if (want && !shown) {
                clamp();
                wm.addView(btn, lp);
                shown = true;
            } else if (!want && shown) {
                wm.removeView(btn);
                shown = false;
            }
        } catch (Exception ignored) {
        }
    }

    private boolean keyboardVisible() {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            for (AccessibilityWindowInfo w : windows) {
                if (w.getType() == AccessibilityWindowInfo.TYPE_INPUT_METHOD) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void clamp() {
        DisplayMetrics m = getResources().getDisplayMetrics();
        lp.x = Math.max(0, Math.min(lp.x, m.widthPixels - lp.width));
        lp.y = Math.max(0, Math.min(lp.y, m.heightPixels - lp.height));
    }

    private void buildButton() {
        bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(BLUE);
        bg.setStroke(dp(2), Color.WHITE);

        btn = new TextView(this);
        btn.setText("\uD83C\uDF99"); // microfono
        btn.setTextSize(22);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(bg);

        int size = dp(56);
        lp = new WindowManager.LayoutParams(
                size, size,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        DisplayMetrics m = getResources().getDisplayMetrics();
        SharedPreferences p = Cfg.prefs(this);
        lp.x = p.getInt("btn_x", m.widthPixels - size - dp(12));
        lp.y = p.getInt("btn_y", (int) (m.heightPixels * 0.30f));

        btn.setOnTouchListener(new View.OnTouchListener() {
            int startX;
            int startY;
            float rawX;
            float rawY;
            boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = lp.x;
                        startY = lp.y;
                        rawX = e.getRawX();
                        rawY = e.getRawY();
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        int dx = (int) (e.getRawX() - rawX);
                        int dy = (int) (e.getRawY() - rawY);
                        if (!moved && Math.abs(dx) < dp(10) && Math.abs(dy) < dp(10)) return true;
                        moved = true;
                        lp.x = startX + dx;
                        lp.y = startY + dy;
                        try {
                            wm.updateViewLayout(btn, lp);
                        } catch (Exception ignored) {
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (moved) {
                            Cfg.prefs(DettatoService.this).edit()
                                    .putInt("btn_x", lp.x).putInt("btn_y", lp.y).apply();
                        } else {
                            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
                            onTap();
                        }
                        return true;
                    default:
                        return false;
                }
            }
        });
    }

    private void setLook(int color, float alpha) {
        bg.setColor(color);
        btn.setAlpha(alpha);
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    // ---------- Registrazione ----------

    private void onTap() {
        if (state == BUSY) {
            toast("Sto elaborando il testo, un attimo");
            return;
        }
        if (state == REC) {
            stopRecording();
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            toast("Apri l'app Dettato e consenti il microfono");
            return;
        }
        if (Cfg.load(this).key.isEmpty()) {
            toast("Apri l'app Dettato e inserisci la chiave API");
            return;
        }

        state = REC;
        setLook(RED, 1f);
        usingService = false;
        try {
            startForegroundService(new Intent(this, RecorderService.class));
            usingService = true;
        } catch (Exception e) {
            String err = Rec.start(this);
            if (err != null) recordFailed(err);
        }
    }

    /** Chiamato se il microfono non parte. */
    void recordFailed(String err) {
        Rec.release();
        state = IDLE;
        setLook(BLUE, 1f);
        toast("Dettato non riuscito: " + err);
        refresh();
    }

    private void stopRecording() {
        state = BUSY;
        setLook(BLUE, 0.55f); // torna blu, semitrasparente mentre elabora
        final File f = Rec.stop();
        if (usingService && RecorderService.running != null) RecorderService.running.end();

        if (f == null) {
            finish(null, "registrazione troppo breve o microfono non disponibile");
            return;
        }
        final Cfg c = Cfg.load(this);
        new Thread(() -> {
            String text = null;
            String err = null;
            try {
                text = Ai.process(f, c);
            } catch (Exception ex) {
                err = ex.getMessage();
            }
            final String t = text;
            final String er = err;
            h.post(() -> finish(t, er));
        }).start();
    }

    private void finish(String text, String err) {
        state = IDLE;
        setLook(BLUE, 1f);
        if (err != null) {
            toast("Dettato non riuscito: " + err);
        } else {
            insert(text);
            String w = Ai.lastWarning;
            if (w != null) toast(w);
        }
        refresh();
    }

    // ---------- Incolla ----------

    private void insert(String text) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("Dettato", text));

        boolean ok = false;
        AccessibilityNodeInfo node = findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (node != null) {
            ok = node.performAction(AccessibilityNodeInfo.ACTION_PASTE);
            if (!ok && node.isEditable()) {
                CharSequence cur = node.isShowingHintText() ? null : node.getText();
                String full = (cur == null || cur.length() == 0) ? text : cur + " " + text;
                Bundle args = new Bundle();
                args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, full);
                ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
            }
        }
        if (!ok) toast("Testo copiato negli appunti: tieni premuto nel campo e scegli Incolla");
    }
}
