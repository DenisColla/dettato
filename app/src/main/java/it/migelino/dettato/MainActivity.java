package it.migelino.dettato;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;

/** Schermata di configurazione: permessi, chiave API, modelli e prompt. */
public class MainActivity extends Activity {
    private SharedPreferences p;
    private TextView status;
    private RadioButton rbGroq;
    private RadioButton rbOpenRouter;
    private EditText key;
    private EditText stt;
    private EditText llm;
    private EditText lang;
    private EditText prompt;
    private String provider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        p = Cfg.prefs(this);

        ScrollView sv = new ScrollView(this);
        sv.setFitsSystemWindows(true);
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        ll.setPadding(pad, pad, pad, dp(48));
        sv.addView(ll);

        status = new TextView(this);
        status.setTextSize(15);
        status.setPadding(0, 0, 0, dp(12));
        ll.addView(status);

        ll.addView(title("Permessi (una volta sola)"));
        ll.addView(button("1. Consenti microfono e notifiche", v -> askPermissions()));
        ll.addView(button("2. Attiva il servizio Dettato in Accessibilità", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        ll.addView(button("Se il servizio è bloccato: Info app → ⋮ → Consenti impostazioni con limitazioni", v ->
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())))));
        ll.addView(button("3. Batteria senza restrizioni", v -> askBattery()));

        ll.addView(title("Servizio AI"));
        RadioGroup rg = new RadioGroup(this);
        rbGroq = new RadioButton(this);
        rbGroq.setText("Groq (più veloce)");
        rbGroq.setId(View.generateViewId());
        rbOpenRouter = new RadioButton(this);
        rbOpenRouter.setText("OpenRouter");
        rbOpenRouter.setId(View.generateViewId());
        rg.addView(rbGroq);
        rg.addView(rbOpenRouter);
        ll.addView(rg);

        ll.addView(label("Chiave API del servizio scelto"));
        key = field(false);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        ll.addView(key);

        ll.addView(label("Modello di trascrizione"));
        stt = field(false);
        ll.addView(stt);

        ll.addView(label("Modello di pulizia del testo"));
        llm = field(false);
        ll.addView(llm);

        ll.addView(label("Lingua del dettato (it = italiano; vuoto = automatica; solo Groq)"));
        lang = field(false);
        ll.addView(lang);

        ll.addView(label("Istruzioni di pulizia"));
        prompt = field(true);
        ll.addView(prompt);
        ll.addView(button("Ripristina istruzioni predefinite", v -> prompt.setText(Cfg.DEF_PROMPT)));

        ll.addView(title("Salva"));
        ll.addView(button("Salva impostazioni", v -> {
            save();
            Toast.makeText(this, "Impostazioni salvate", Toast.LENGTH_SHORT).show();
            refreshStatus();
        }));
        ll.addView(button("Verifica chiave API", v -> verifyKey()));

        setContentView(sv);

        // Carica i valori salvati
        Cfg c = Cfg.load(this);
        provider = c.provider;
        (c.isOpenRouter() ? rbOpenRouter : rbGroq).setChecked(true);
        key.setText(c.key);
        stt.setText(c.stt);
        llm.setText(c.llm);
        lang.setText(c.lang);
        prompt.setText(c.prompt);

        rg.setOnCheckedChangeListener((group, checkedId) -> {
            String next = checkedId == rbOpenRouter.getId() ? Cfg.OPENROUTER : Cfg.GROQ;
            if (next.equals(provider)) return;
            p.edit().putString("key_" + provider, key.getText().toString().trim()).apply();
            provider = next;
            key.setText(p.getString("key_" + provider, ""));
            stt.setText(Cfg.defStt(provider));
            llm.setText(Cfg.DEF_LLM);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private void save() {
        String s = stt.getText().toString().trim();
        String l = llm.getText().toString().trim();
        String pr = prompt.getText().toString().trim();
        p.edit()
                .putString("provider", provider)
                .putString("key_" + provider, key.getText().toString().trim())
                .putString("stt", s.isEmpty() ? Cfg.defStt(provider) : s)
                .putString("llm", l.isEmpty() ? Cfg.DEF_LLM : l)
                .putString("lang", lang.getText().toString().trim())
                .putString("prompt", pr.isEmpty() ? Cfg.DEF_PROMPT : pr)
                .apply();
    }

    private void refreshStatus() {
        boolean mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean acc = DettatoService.inst != null;
        PowerManager pm = getSystemService(PowerManager.class);
        boolean bat = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        boolean k = !Cfg.load(this).key.isEmpty();
        String txt = "STATO\n"
                + (mic ? "✅" : "❌") + " Microfono\n"
                + (acc ? "✅" : "❌") + " Servizio in Accessibilità\n"
                + (bat ? "✅" : "⚠️") + " Batteria senza restrizioni\n"
                + (k ? "✅" : "❌") + " Chiave API salvata\n"
                + ((mic && acc && k) ? "\nTutto pronto: apri una chat, tocca il tasto 🎙 e parla."
                : "\nCompleta i punti con ❌.");
        status.setText(txt);
    }

    private void askPermissions() {
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatus();
    }

    private void askBattery() {
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
        }
    }

    private void verifyKey() {
        save();
        final Cfg c = Cfg.load(this);
        if (c.key.isEmpty()) {
            Toast.makeText(this, "Inserisci prima la chiave", Toast.LENGTH_SHORT).show();
            return;
        }
        new Thread(() -> {
            String msg;
            try {
                String url = c.isOpenRouter() ? c.base() + "/key" : c.base() + "/models";
                HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
                con.setConnectTimeout(15000);
                con.setReadTimeout(15000);
                con.setRequestProperty("Authorization", "Bearer " + c.key);
                int code = con.getResponseCode();
                con.disconnect();
                msg = code == 200 ? "Chiave valida ✅" : "Chiave NON valida (HTTP " + code + ")";
            } catch (Exception e) {
                msg = "Errore di rete: " + e.getMessage();
            }
            final String m = msg;
            runOnUiThread(() -> Toast.makeText(this, m, Toast.LENGTH_LONG).show());
        }).start();
    }

    // ---------- Piccoli helper grafici ----------

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(18);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(20), 0, dp(6));
        return t;
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setPadding(0, dp(10), 0, 0);
        return t;
    }

    private Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        return b;
    }

    private EditText field(boolean multiline) {
        EditText e = new EditText(this);
        if (multiline) {
            e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            e.setMinLines(6);
            e.setTextSize(13);
        } else {
            e.setSingleLine(true);
        }
        return e;
    }
}
