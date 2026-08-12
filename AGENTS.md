# AGENTS.md

Android app (Kotlin / Jetpack Compose) for Serie A soccer betting predictions,
with a TensorFlow Lite model trained in Google Colab and bookmaker odds from
The Odds API. Prediction = on-device, odds = remote free APIs.

## Layout

```
notebooks/   Python scripts for Google Colab (data exploration, training, .tflite export)
scripts/     (currently empty — reserved for local Python helpers)
android/     Gradle Android project, single module `:app`
android/app/src/main/java/com/example/soccerapp/
  data/network/   Retrofit clients (football-data.org, The Odds API), Moshi models
  data/local/     FixtureCache (in-memory, protects API rate limits)
  model/          Models.kt, ValueBetCalculator.kt, TflitePredictor.kt
  di/ApiKeys.kt   API keys live here — never commit real values
  ui/             Compose screens + MainViewModel
android/app/src/main/assets/seriea_model.tflite   trained model (placeholder until trained)
```

## Key facts

- **No build verification possible on this machine** (no JDK/Gradle/Android SDK).
  Must be opened in Android Studio to compile. Python 3.9 is available locally
  to syntax-check notebook scripts: `python3 -m py_compile`.
- **The Odds API free tier = 500 requests/month.** One call costs
  `markets × regions`. The call in `OddsApi.kt` uses `h2h,totals` × `eu` = 2
  credits per refresh. Never call season/historical endpoints from the app.
- **`totals` (over/under) odds are mainly for US sports** in The Odds API; for
  soccer they may be absent — `ValueBetCalculator` treats a missing O/U as
  "no suggestion", do not assume O/U odds exist.
- **football-data.org rate limits** (~10 req/min, free tier top competitions
  only). `FixtureCache` exists to avoid re-fetching; keep that pattern.
- **fixture keys don't match between APIs**: football-data uses numeric ids,
  Odds API uses hex ids — the ViewModel joins on normalized team names
  (`norm(home)+"|"+norm(away)`). Keep that join logic in sync when adding data
  sources.
- **The `.tflite` placeholder is a text file**, not a real model. The app
  deliberately treats a failed `Interpreter` load as "no ML" and falls back to
  bookmaker implied probabilities. Do not add a crash/log `rethrow` there.
- The `featureCount` in `TflitePredictor` (11, or 8 without odds) must match
  `FEATURE_COLS` in `notebooks/02_train_model.py`; the ViewModel writes the
  odds-implied features at fixed indices 8/9/10 (careful with the `n==8` path).

## Commands

- Notebooks: open in Google Colab (free GPU) — no local commands.
- Syntax-check a notebook's Python locally:
  `grep -v '^\s*!' notebooks/XX_*.py > /tmp/x.py && python3 -m py_compile /tmp/x.py && rm /tmp/x.py`
  (strip Colab `!` lines; `py_compile` needs a real file, not stdin)
- Android: build/run only via Android Studio; no validated CLI equivalent here.

## Gotchas for agents

- Training script uses `!pip` / `!unzip` magic — they're Colab-specific and
  will fail under plain `python3`; validate with the grep trick above.
- Never store API keys in `ApiKeys.kt` (it's a template with placeholders);
  prefer `gradle.properties` → BuildConfig, as the file comments suggest.
- Serena A competition code is `SA` on football-data.org; `soccer_italy_serie_a`
  is the assumed Odds API sport_key (notebook 01 discovers the real key).
- Keep the app's EV math in `ValueBetCalculator` (EV > 0 AND edge > 3% before
  recommending). Do not re-invent that logic in the UI layer.