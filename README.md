# Pronostici Calcio Serie A — AI + Value Bets

App Android gratuita che:
1. **Addestra un modello IA** (in Google Colab, GPU gratuita) per prevedere
   l'esito 1X2 e Over/Under 2.5 delle partite di Serie A.
2. **Confronta le quote dei bookmaker** (The Odds API, piano gratuito).
3. **Consiglia la "migliore quota"** con calcolo del valore atteso (EV).

> ⚠️ Military-level tutto gratuito. Il modello è statistico: non garantisce
> vincite. Scommettere comporta rischi (18+). Non affermare mai che i pronostici
> siano "certi".

---

## Struttura

```
notebooks/            # Pipeline di addestramento (da aprire in Google Colab)
  01_data_explore.py  # Verifica fonti dati (football-data, Odds API, Kaggle)
  02_train_model.py   # Feature engineering + Keras 2-testate + export .tflite
scripts/              # (vuoto, dipendenze Python, non necessari su Colab)
android/              # App Kotlin + Jetpack Compose
  app/src/main/java/.../data/network/  # Client Retrofit (football-data, Ods API)
  app/src/main/java/.../model/          # TFLite wrapper + calcolatore value bets
  app/src/main/java/.../ui/             # Schermate Compose
  app/src/main/assets/seriea_model.tflite  # <-- metti qui il modello addestrato
```

## Passo 1 — Account gratuiti (una volta)

| Servizio | URL | Cosa ti dà |
|---|---|---|
| Google (Colab) | colab.research.google.com | GPU gratuita per l'addestramento |
| Kaggle | kaggle.com | dataset storici Serie A |
| football-data.org | football-data.org/client/register | API fixtures/risultati, token `X-Auth-Token` |
| The Odds API | the-odds-api.com (piano Starter) | quote bookmaker, 500 richieste/mese |

## Passo 2 — Addestramento (Colab)

1. Apri `colab.research.google.com` → *Nuovo notebook* → incolla il contenuto di `notebooks/01_data_explore.py` (oppure importa il file `.py`).
2. Inserisci le tue chiavi in cima; esegui per verificare che le 3 fonti
   rispondano. Scarica un dataset storico Serie A da Kaggle (es. "datasoccer-database").
3. Contruisci `matches_raw.csv` (team, date, gol, e se possibile quote) e caricalo
   in Colab; esegui `notebooks/02_train_model.py`.
4. Alla fine scarica `seriea_model.tflite` e copialo in:
   `android/app/src/main/assets/seriea_model.tflite`
5. Annota i nomi estratti in output ("INPUT/OUTPUT TFLite") e tienili allineati
   con `TflitePredictor`.

## Passo 3 — App Android

1. Apri `android/` in **Android Studio** (versione gratuita).
2. In `app/src/main/java/com/example/soccerapp/di/ApiKeys.kt` inserisci il token
   football-data e la chiave The Odds API (o via `gradle.properties`).
3. Installa su emulatore o dispositivo (Android 8.0+/minSdk 26) e avvia.
4. App: scheda fixtures (partite) → tocca una partita → dettaglio con
   consigli; sezione "Value Bets" per le migliori quote con EV positivo.

> Il `.tflite` incluso è un segnaposto: finché non metti il modello addestrato,
> l'app usa le *frequenze storiche Serie A* (~46/27/27) come base — comunque
> funzionante per trovare gli edge. Il modello attuale è a **8 feature senza
> quote**; Over/Under 2.5 è disattivato finché un retrain con le quote non
> restituisce probabilità over affidabili.

## Calcolo del "migliore bookmaker"

Per ogni partita e per ogni esito 1X2 (Casa / Pareggio / Trasferta):
1. Da tutte le quote dei bookmaker presi, si toglie il margine e si normalizza.
2. Il modello dà la probabilità (predizioni statistiche → `TeamStatsEngine`,
   partite finite). Se `P_modello > P_implicita + 3%` → c'è "edge".
3. Quota più alta usata per l'esito = **migliore bookmaker**.
4. `EV = P_modello × (quota − 1) − (1 − P_modello)`. EV > 0 → consiglio.

Soglie in `ValueBetCalculator.kt` (`EDGE_THRESHOLD`, `MAX_MARGIN`).

## Limiti gratis (da sapere)

- **The Odds API**: 500 richieste/mese → ~1 chiamata/partita/giornata × vale.
  Usare il `FixtureCache` per non sprecare crediti.
- **football-data.org**: free tier limitato a competizioni top; rate-limit
  ~10 req/min.
- **Colab**: sessioni ~12h; la GPU gratuita può essere occupata nelle ore di punta.
- Il modello migra male con squadre neopromosse: il campione di addestramento
  richiede aggiornamenti stagionali.

## Disclaimer

Progetto didattico/fun personale. Non è un invito al gioco d'azzardo.