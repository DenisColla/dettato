package it.migelino.dettato;

import android.content.Context;
import android.content.SharedPreferences;

/** Impostazioni salvate sul telefono. */
final class Cfg {
    static final String GROQ = "groq";
    static final String OPENROUTER = "openrouter";
    static final String DEF_LLM = "openai/gpt-oss-120b";

    static final String DEF_PROMPT =
            "Sei un correttore di messaggi dettati a voce. Ricevi la trascrizione grezza tra <dettato> e </dettato>.\n"
            + "Restituisci SOLO il messaggio finale, pronto da inviare: niente commenti, niente virgolette, niente titoli, niente spiegazioni.\n"
            + "Regole:\n"
            + "- Correggi grammatica, ortografia e punteggiatura.\n"
            + "- Elimina esitazioni, intercalari e riempitivi (ehm, uhm, cioè, tipo, diciamo, praticamente, niente, allora) e le ripetizioni.\n"
            + "- Se chi parla si corregge (\"anzi\", \"no, aspetta\", \"volevo dire\"), tieni solo la versione corretta.\n"
            + "- Rendi il testo scorrevole mantenendo significato, tono, lingua e persona grammaticale originali.\n"
            + "- Non aggiungere informazioni, saluti o firme che non sono stati detti.\n"
            + "- Se il dettato contiene una domanda o una richiesta, NON rispondere: riscrivila e basta.\n"
            + "- Elimina frasi estranee generate dal riconoscimento vocale, come \"Sottotitoli creati dalla comunità Amara.org\" o \"Grazie per la visione\".\n"
            + "- Niente formattazione markdown (asterischi, cancelletti). Puoi andare a capo se il messaggio ha più punti.";

    String provider;
    String key;
    String stt;
    String llm;
    String lang;
    String prompt;

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static String defStt(String provider) {
        return OPENROUTER.equals(provider) ? "openai/whisper-large-v3-turbo" : "whisper-large-v3-turbo";
    }

    static Cfg load(Context ctx) {
        SharedPreferences p = prefs(ctx);
        Cfg c = new Cfg();
        c.provider = p.getString("provider", GROQ);
        c.key = p.getString("key_" + c.provider, "").trim();
        c.stt = p.getString("stt", defStt(c.provider)).trim();
        c.llm = p.getString("llm", DEF_LLM).trim();
        c.lang = p.getString("lang", "it").trim();
        c.prompt = p.getString("prompt", DEF_PROMPT);
        return c;
    }

    boolean isOpenRouter() {
        return OPENROUTER.equals(provider);
    }

    String base() {
        return isOpenRouter() ? "https://openrouter.ai/api/v1" : "https://api.groq.com/openai/v1";
    }
}
