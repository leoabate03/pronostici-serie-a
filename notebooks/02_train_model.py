# -*- coding: utf-8 -*-
"""
02_train_model.py
=================
STEP 2 del progetto "Pronostici Calcio Serie A"

Addestra in Google Colab (GPU gratuita) un modello che prevede per ogni
partita di serie A:
    - Esito 1X2          (casa / pareggio / trasferta)
    - Over/Under 2.5 gol  (probabilita' di +2.5 gol)
Poi esporta il tutto in TensorFlow Lite (.tflite) per l'app Android.

PREREQUISITO: aver eseguito 01_data_explore.py e salvato /content/matches_raw.csv
(una riga per partita con colonne: season, date, home, away, home_goals,
away_goals e, se disponibili, le quote del bookmaker).
"""

# ---------------------------------------------------------------------------
# 1. IMPORTAZIONI E INSTALLAZIONE
# ---------------------------------------------------------------------------
!pip install -q tensorflow pandas numpy scikit-learn

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.model_selection import train_test_split
from sklearn.preprocessing import LabelEncoder

# Seed per riproducibilita'
tf.random.set_seed(42)
np.random.seed(42)

# ---------------------------------------------------------------------------
# 2. CARICAMENTO E ADATTAMENTO DATI
# ---------------------------------------------------------------------------
# Supporta DUE formati di CSV:
#  A) "Per partita": colonne home, away, home_goals, away_goals (e date/season).
#  B) "Fbref/Kaggle: per squadra": una riga per (partita, squadra) con colonne
#     team, opponent, venue (Home/Away), gf, ga, result. -> viene riadattato
#     automaticamente in formato A.
raw = pd.read_csv("/content/matches_raw.csv")
print("Righe del CSV:", len(raw))
print("Colonne:", list(raw.columns))

if {"home_goals", "away_goals"}.issubset(raw.columns):
    # ----- Formato A: gia' una riga per partita -----
    df = raw.copy()
else:
    # ----- Formato B (fbref/Kaggle): righe per squadra -> pivot per partita -----
    required = {"team", "opponent", "venue", "gf", "ga", "date"}
    missing = required - set(raw.columns)
    if missing:
        raise ValueError(
            f"Formato CSV non riconosciuto. Colonne mancanti: {missing}. "
            f"Aggiungi una colonna home_goals/away_goals oppure team/opponent/venue/gf/ga."
        )

    raw = raw.copy()
    raw["date"] = pd.to_datetime(raw["date"], errors="coerce")
    raw["venue_norm"] = raw["venue"].astype(str).str.strip().str.lower()
    raw["pair"] = raw[["team", "opponent"]].apply(
        lambda r: "|".join(sorted([
            str(r["team"]).strip().lower(),
            str(r["opponent"]).strip().lower(),
        ])),
        axis=1,
    )
    # Colonna "comp"/"round" facoltative ma utili per distinguere i match
    # disputati nello stesso giorno: gruppo per date + pair (e round se c'e').
    group_keys = ["date", "pair"] + [c for c in ["round", "comp"] if c in raw.columns]
    raw = raw.sort_values("date")

    frames = []
    for keys, g in raw.groupby(group_keys, dropna=False):
        date = keys[0]
        if date is None or pd.isna(date):
            continue
        home = g[g["venue_norm"] == "home"]
        away = g[g["venue_norm"] == "away"]
        if len(home) != 1 or len(away) != 1:
            continue  # match incompleto: salta
        h = home.iloc[0]
        a = away.iloc[0]
        frames.append({
            "season": h.get("season", pd.NA),
            "date": date,
            "home": h["team"],
            "away": a["team"],
            "home_goals": pd.to_numeric(h["gf"], errors="coerce"),
            "away_goals": pd.to_numeric(a["gf"], errors="coerce"),
        })
    df = pd.DataFrame(frames)
    print("ADS: riadattate", len(frames), "partite a partire da", len(raw), "righe squadra.")

# ----- 2a. Pulizia -----
if "date" not in df.columns or df["date"].isna().all():
    # dataset senza data usabile: ordina comunque per season stabilita
    df["date"] = pd.to_datetime(df.get("season", 2024), format="%Y")
df = df.dropna(subset=["home_goals", "away_goals"]).copy()
df["home_win"] = (df["home_goals"] > df["away_goals"]).astype(int)
df["draw"] = (df["home_goals"] == df["away_goals"]).astype(int)
df["away_win"] = (df["home_goals"] < df["away_goals"]).astype(int)
df["over25"] = (df["home_goals"] + df["away_goals"] >= 3).astype(int)
df["date"] = pd.to_datetime(df["date"])

print("Partite totali utilizzabili:", len(df))

# ----- 2b. Rating squadre (forza attacco/difesa, forma) -----
# Uniforma i nomi delle squadre tra le stagioni
df["home"] = df["home"].str.strip().str.lower()
df["away"] = df["away"].str.strip().str.lower()

def cumulative_team_feats(df):
    """Calcola, per ogni squadra e partita, la media gol fatti/subiti e i
    punti nelle ultime 5. Funziona per partite in ordine cronologico."""
    last = {}
    feats = []

    rows = df.sort_values("date").to_dict("records")
    for i, r in enumerate(rows):
        h, a = r["home"], r["away"]
        hf = last.get(h, {"g": 1.3, "s": 1.3, "pts": [3, 3, 3, 3, 3]})
        af = last.get(a, {"g": 1.3, "s": 1.3, "pts": [3, 3, 3, 3, 3]})
        feats.append({
            "home_att_avg": hf["g"], "home_def_avg": hf["s"],
            "away_att_avg": af["g"], "away_def_avg": af["s"],
            "home_form5": np.mean(hf["pts"]), "away_form5": np.mean(af["pts"]),
        })
        # aggiorna "last" con i risultati di questa partita
        hg, ag = r["home_goals"], r["away_goals"]
        def _update(tw, twg, tags, tagg):
            old = last.get(tw, {"g": 1.3, "s": 1.3, "pts": [3, 3, 3, 3, 3]})
            old["g"] = 0.8 * old["g"] + 0.2 * twg
            old["s"] = 0.8 * old["s"] + 0.2 * tagg
            pts = twg if twg > tagg else (1 if twg == tagg else 0)
            old["pts"] = (old["pts"] + [pts])[-5:]
            last[tw] = old
        _update(h, hg, a, ag)
        _update(a, ag, h, hg)
    return pd.DataFrame(feats)

feat_df = cumulative_team_feats(df)
X = pd.concat([df.reset_index(drop=True), feat_df], axis=1)

# ----- 2c. Variabili di gioco -----
X["is_home"] = 1.0
# Giornata di campionato (se disponibile) -> normalizzata
if "gameweek" in X.columns:
    X["gameweek_norm"] = X["gameweek"].fillna(0) / 38.0
else:
    X["gameweek_norm"] = 0.5
# Distanza dai match precedenti -> la omettiamo per semplicita'

# ----- 2d. Quote del bookmaker (predittore piu' forte) -----
# Se nel CSV hai le quote 1X2 (HOME_WIN_ODD, DRAW_ODD, AWAY_WIN_ODD)
# le usiamo come feature e ne calcoliamo la probabilita' implicita.
if {"HOME_WIN_ODD", "DRAW_ODD", "AWAY_WIN_ODD"}.issubset(X.columns):
    for col in ["HOME_WIN_ODD", "DRAW_ODD", "AWAY_WIN_ODD"]:
        X[col] = X[col].fillna(X[col].median())
    odds_sum = 1 / X["HOME_WIN_ODD"] + 1 / X["DRAW_ODD"] + 1 / X["AWAY_WIN_ODD"]
    X["impl_home"] = (1 / X["HOME_WIN_ODD"]) / odds_sum
    X["impl_draw"] = (1 / X["DRAW_ODD"]) / odds_sum
    X["impl_away"] = (1 / X["AWAY_WIN_ODD"]) / odds_sum
else:
    print("\nATTENZIONE: nessuna colonna di quote trovata. Le quote ovverride "
          "sono il segnale piu' forte -> riempi il CSV con HOME_WIN_ODD, "
          "DRAW_ODD, AWAY_WIN_ODD se possibili.")

# ----- 2e. Colonne finali -----
FEATURE_COLS = [
    "is_home", "gameweek_norm",
    "home_att_avg", "home_def_avg", "away_att_avg", "away_def_avg",
    "home_form5", "away_form5",
] + [c for c in ["impl_home", "impl_draw", "impl_away"] if c in X.columns]

print("\nFeature finali:", FEATURE_COLS)
X_feat = X[FEATURE_COLS].values.astype(np.float32)

# Target (due testate)
y_1x2 = X[["home_win", "draw", "away_win"]].values.astype(np.float32)
y_over = X["over25"].values.astype(np.float32)

# ---------------------------------------------------------------------------
# 3. TRAIN/VALIDATION SPLIT (per TEMPO, non random, per non spaesare il modello)
# ---------------------------------------------------------------------------
split_idx = int(len(X_feat) * 0.8)
X_tr, X_val = X_feat[:split_idx], X_feat[split_idx:]
y1_tr, y1_val = y_1x2[:split_idx], y_1x2[split_idx:]
y2_tr, y2_val = y_over[:split_idx], y_over[split_idx:]
print(f"Train: {len(X_tr)} esempi | Val: {len(X_val)} esempi")

# ---------------------------------------------------------------------------
# 4. DEFINIZIONE MODELLO (due testate: softmax 1X2 + sigmoide Over 2.5)
# ---------------------------------------------------------------------------
from tensorflow.keras import layers, Model

inputs = layers.Input(shape=(X_tr.shape[1],), name="features")

common = layers.Dense(64, activation="relu")(inputs)
common = layers.Dropout(0.2)(common)
common = layers.Dense(64, activation="relu")(common)
common = layers.Dropout(0.2)(common)

# Testata 1: esito 1X2
out_1x2 = layers.Dense(3, activation="softmax", name="outcome_1x2")(common)
# Testata 2: over/under 2.5
out_over = layers.Dense(1, activation="sigmoid", name="over_under_25")(common)

model = Model(inputs=inputs, outputs=[out_1x2, out_over])
model.compile(
    optimizer="adam",
    loss={"outcome_1x2": "categorical_crossentropy", "over_under_25": "binary_crossentropy"},
    metrics={"outcome_1x2": "accuracy", "over_under_25": "accuracy"},
)

model.summary()

# ---------------------------------------------------------------------------
# 5. ADDESTRAMENTO
# ---------------------------------------------------------------------------
history = model.fit(
    X_tr, {"outcome_1x2": y1_tr, "over_under_25": y2_tr},
    validation_data=(X_val, {"outcome_1x2": y1_val, "over_under_25": y2_val}),
    epochs=50, batch_size=64, verbose=1,
)

# ----- Valutazione (usa la LOG-LOSS come metrica chiave, non l'accuracy) -----
from sklearn.metrics import log_loss
p_val_1x2, p_val_over = model.predict(X_val)
print("\nLog-loss 1X2 su validation:", round(log_loss(y1_val, p_val_1x2), 4))
print("Log-loss Over/Under su validation:", round(log_loss(y2_val, p_val_over), 4))
print("Accuracy 1X2:", round((p_val_1x2.argmax(axis=1) == y1_val.argmax(axis=1)).mean(), 4))
print("Accuracy Over/Under:", round(((p_val_over > 0.5).astype(int).ravel() == y2_val).mean(), 4))

# ---------------------------------------------------------------------------
# 6. ESPORTAZIONE TENSORFLOW LITE
# ---------------------------------------------------------------------------
converter = tf.lite.TFLiteConverter.from_keras_model(model)
# Ottimizzazioni per ridurre dimensione apk
converter.optimizations = [tf.lite.Optimize.DEFAULT]
# Impedisce l'uso di op "nuove" (es. FULLY_CONNECTED v12) non supportate
# dal runtime TFLite 2.14/2.16 su device: solo TFLITE_BUILTINS.
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]
tflite_model = converter.convert()

out_path = "/content/seriea_model.tflite"
with open(out_path, "wb") as f:
    f.write(tflite_model)
print("\nModello esportato in:", out_path, "dimensione:", round(len(tflite_model) / 1e6, 2), "MB")

# ----- 6a. Verifica: ridai i NOMI degli input/output (servono in Android) -----
interpreter = tf.lite.Interpreter(model_content=tflite_model)
interpreter.allocate_tensors()
print("\n=== DETTAGLI INPUT/OUTPUT TFLite (copiali nel file Kotlin) ===")
for d in interpreter.get_input_details():
    print("INPUT  ", d["name"], "shape=", d["shape"], "dtype=", d["dtype"])
for d in interpreter.get_output_details():
    print("OUTPUT ", d["name"], "shape=", d["shape"], "dtype=", d["dtype"])

# ---------------------------------------------------------------------------
# 7. (FACOLTATIVO) SALVA LO STORICO ADDESTRAMENTO PER GRAFICI
# ---------------------------------------------------------------------------
pd.DataFrame(history.history).to_csv("/content/training_history.csv", index=False)
print("\nSalvato /content/training_history.csv. Da qui: scarica il .tflite e "
      "mettilo in  android/app/src/main/assets/seriea_model.tflite")