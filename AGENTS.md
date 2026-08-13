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
  model/          Models.kt, ValueBetCalculator.kt, TflitePredictor.kt, TeamStatsEngine.kt
  di/ApiKeys.kt   API keys live here — never commit real values
  ui/             Compose screens + MainViewModel
android/app/src/main/assets/seriea_model.tflite   trained model (8-feature, real)
```

## Key facts

- **No build verification possible on this machine** (no JDK/Gradle/Android SDK).
  Must be opened in Android Studio to compile. Python 3.9 is available locally
  to syntax-check notebook scripts: `python3 -m py_compile`.
- **The Odds API free tier = 500 requests/month.** One call costs
  `markets × regions`. The call in `OddsApi.kt` uses `h2h` × `eu` = 1 credit per
  refresh (totals was dropped while O/U is disabled). Never call
  season/historical endpoints from the app.
- **`totals` (over/under) odds are mainly for US sports** in The Odds API; for
  soccer they may be absent — and the app does not request them while the
  O/U model signal is ~coin-flip. Do not assume O/U odds exist.
- **football-data.org rate limits** (~10 req/min, free tier top competitions
  only). `FixtureCache` exists to avoid re-fetching; keep that pattern.
- **fixture keys don't match between APIs**: football-data uses numeric ids,
  Odds API uses hex ids — the ViewModel joins on normalized team names
  (`norm(home)+"|"+norm(away)`). Keep that join logic in sync when adding data
  sources.
- **The `.tflite` is a real trained model** (8-feature, float32 I/O, int8
  quantized weights, ~11KB). The app deliberately treats a failed `Interpreter`
  load as "no ML" and falls back to Serie A base rates (~46/27/27) — in that
  case the UI shows "Modello: fallback (baseline Serie A)". Do not add a
  crash/log `rethrow` there. Use the UI indicator (modelActive) to tell the two
  states apart instead of guessing.
- **TFLite runtime = LiteRT `com.google.ai.edge.litert:litert:2.1.6`** (Google
  Maven). It keeps the `org.tensorflow.lite.Interpreter` API (no import changes)
  but is newer than `tensorflow-lite 2.14/2.16` and supports `FULLY_CONNECTED`
  up to v13 — required because the model is exported with `FULLY_CONNECTED v12`.
  Do NOT downgrade back to `org.tensorflow:tensorflow-lite:2.14.0`; it cannot
  load this model. `tensorflow-lite-support` is unused in code — do not add it.
- **Notebook 02 pins `tensorflow==2.16.1`** (Keras 2). Colab's default Keras 3
  breaks `TFLiteConverter.from_keras_model` (`TypeError: 'NoneType' object is
  not callable`). Keep the pinned version so the `.tflite` exports cleanly.
- **The trained model is 8-feature, no odds** (currently live). `featureCount`
  in `TflitePredictor` (8) must match `FEATURE_COLS` in
  `notebooks/02_train_model.py`. Physical team-stat features come from
  `TeamStatsEngine`, which replicates the notebook's EWMA team ratings at the
  SAME scale (gol/partita ~1.3, forma in punti 0..3) — keep both in sync.
- **TFLite output order differs from keras output order.** `predict()` maps
  output tensors BY SHAPE (`[1,3]` = 1X2, `[1,1]` = over/under), never by
  positional index — the notebook prints `over_under_25` before `outcome_1x2`.
- **Over/Under is intentionally DISABLED** in `ValueBetCalculator.findValueBets`
  (current model has ~coin-flip O/U signal). Do not re-enable it for the
  8-feature model; only revisit after a retrain with odds columns.
- **football-data.org `/matches` returns finished + scheduled together.** The
  ViewModel splits them: finished feed the `TeamStatsEngine`, scheduled are
  shown in the UI. If this split is changed, keep `FixtureCache` caching BOTH.

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