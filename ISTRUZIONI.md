# Dettato – istruzioni

App Android che mostra un tasto 🎙 quando la tastiera è aperta.
Tocchi il tasto: registra (niente timeout, non si ferma sulle pause).
Lo ritocchi: trascrive con Whisper, ripulisce il testo con l'LLM e lo incolla nel campo in cui stai scrivendo.
Chiama direttamente Groq o OpenRouter: non serve nessun server intermedio.

## A. Crea l'APK (una volta sola, dal PC, circa 10 minuti)

1. Crea un account gratuito su https://github.com
2. In alto a destra tocca **+** → **New repository** → nome `dettato` → scegli **Private** → **Create repository**.
3. Nella pagina del repository clicca il link **uploading an existing file**.
4. Estrai lo zip sul PC, apri la cartella `Dettato`, seleziona **tutto** il contenuto (compresa la cartella `.github`) e trascinalo nella pagina. In basso clicca **Commit changes**.
5. Apri la scheda **Actions**: parte "Crea APK" (3–5 minuti). Quando il pallino diventa verde, l'APK è pronto.
   Se diventa rosso, apri il passaggio fallito, copia le ultime righe rosse e mandale a Claude.

## B. Installa sul telefono

1. Dal telefono apri github.com (stesso account) → repository `dettato` → **Releases** (colonna a destra) → scarica **Dettato.apk**.
2. Aprilo. Se chiede di consentire l'installazione di app sconosciute, consentila al browser.
   Se Play Protect avvisa, scegli **Installa comunque** (l'app non viene dal Play Store).

## C. Configura (dentro l'app Dettato)

1. **1. Consenti microfono e notifiche** → Consenti.
2. **2. Attiva il servizio Dettato in Accessibilità** → trova Dettato (su Samsung: *App installate*) → attivalo.
   Se l'interruttore è grigio o compare "Impostazione con limitazioni": torna nell'app, tocca il pulsante **Info app**, menu **⋮** in alto a destra → **Consenti impostazioni con limitazioni**, poi riprova.
3. **3. Batteria senza restrizioni** → Consenti. (Xiaomi: attiva anche l'Avvio automatico.)
4. Scegli **Groq** o **OpenRouter**, incolla la chiave API, tocca **Salva impostazioni** e poi **Verifica chiave API**.
5. In alto lo stato deve mostrare tutte le spunte verdi.

## Uso

- Apri una chat: con la tastiera aperta compare il tasto blu 🎙.
- Tocca: diventa rosso e registra. Ritocca: torna blu (semitrasparente mentre elabora), poi il testo viene incollato.
- Per spostarlo, trascinalo: la posizione viene ricordata.
- Il testo resta anche negli appunti.

## Se qualcosa non va

| Messaggio | Cosa fare |
|---|---|
| "consenti il microfono" | Punto C1 |
| "inserisci la chiave API" | Punto C4 |
| HTTP 401 | Chiave sbagliata o del servizio sbagliato |
| HTTP 400 | Nome del modello sbagliato: premi Groq/OpenRouter per rimettere i modelli predefiniti |
| HTTP 402 | OpenRouter senza credito |
| "nessuna voce riconosciuta" o "registrazione vuota" | Il microfono è bloccato: segnalalo a Claude con marca e modello del telefono |
| "Testo copiato negli appunti" | Quel campo non accetta l'incolla automatico: tieni premuto e scegli Incolla |
| Il tasto non compare | Servizio in Accessibilità disattivato (Android a volte lo spegne dopo un aggiornamento): riattivalo |
