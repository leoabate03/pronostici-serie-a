# -*- coding: utf-8 -*-
"""
01_data_explore.py
==================
STEP 1 del progetto "Pronostici Calcio Serie A"

Obiettivo: verificare che le 3 fonti di dati gratuite siano accessibili e
contengano dati utili PRIMA di spendere tempo a costruire il modello.

Da eseguire in Google Colab (dalla cima in giu', blocchetto per blocchetto).

Fonti:
  1. Football-data.org  -> fixtures, risultati, classifica (API, gratis)
  2. The Odds API       -> quote bookmaker (500 richieste/mese gratis)
  3. Kaggle             -> dataset storico Serie A (gratis, per addestramento)

Requisiti: account gratuiti per ognuna delle 3 fonti. Inserisci le tue chiavi
nelle variabili qui sotto.
"""

# ---------------------------------------------------------------------------
# 0. SETUP / CHIAVI (MODIFICA QUESTE RIGHE)
# ---------------------------------------------------------------------------
FOOTBALL_DATA_TOKEN = "INSERISCI_IL_TUO_TOKEN"  # da football-data.org -> client/register
ODDS_API_KEY        = "INSERISCI_LA_TUA_CHIAVE"  # da the-odds-api.com (gratis, 500/mese)
KAGGLE_USERNAME     = "INSERISCI_TUO_USERNAME_KAGGLE"
KAGGLE_KEY          = "INSERISCI_TUA_KAGGLE_API_KEY"

# ---------------------------------------------------------------------------
# 1. INSTALLAZIONE DIPENDENZE E IMPORT
# ---------------------------------------------------------------------------
# In Colab: esegui questo blocco
!pip install -q kaggle pandas requests

import os
import json
import pandas as pd
import requests

os.environ["KAGGLE_USERNAME"] = KAGGLE_USERNAME
os.environ["KAGGLE_KEY"] = KAGGLE_KEY

# ---------------------------------------------------------------------------
# 2. FONTE 1: FOOTBALL-DATA.ORG  (fixtures/risultati Serie A)
#    La competizione Serie A ha codice "SA" un po' ovunque.
#    Verifica che l'endpoint risponda e dano quanti match ci sono.
# ---------------------------------------------------------------------------
HEADERS_FD = {"X-Auth-Token": FOOTBALL_DATA_TOKEN}

r = requests.get(
    "https://api.football-data.org/v4/competitions/SA/matches",
    headers=HEADERS_FD,
    timeout=30,
)
print("Status football-data.org:", r.status_code)
if r.ok:
    data = r.json()
    matches = data.get("matches", [])
    print(f"Match trovati: {len(matches)}")
    # Estrarre i campi utili
    rows = []
    for m in matches:
        rows.append({
            "date": m.get("utcDate"),
            "home": m["homeTeam"]["name"],
            "away": m["awayTeam"]["name"],
            "home_goals": m["score"]["fullTime"].get("home"),
            "away_goals": m["score"]["fullTime"].get("away"),
            "status": m.get("status"),
        })
    df_fd = pd.DataFrame(rows)
    print(df_fd.head(10).to_string())
    df_fd.to_csv("/content/fixtures_football_data.csv", index=False)
else:
    print("Errore, controlla il token o il rate limit.")
    df_fd = pd.DataFrame()

# ---------------------------------------------------------------------------
# 3. FONTE 2: THE ODDS API  (quote bookmaker)
#    Prima trova il "sport_key" del campionato italiano,
#    poi scarica le quote per h2h (1X2) e totals (over/under).
# ---------------------------------------------------------------------------
BASE_ODDS = "https://api.the-odds-api.com"

# Elenca gli sport disponibili
resp = requests.get(f"{BASE_ODDS}/v4/sports/?apiKey={ODDS_API_KEY}", timeout=30)
sports = resp.json()
soccer_games = [s for s in sports if s.get("group") == "Soccer" and "Italy" in s.get("title", "")]
print("Sport soccer italiani trovati:", [s["key"] for s in soccer_games][:5])

italy_key = None
for s in soccer_games:
    if "serie" in s["key"].lower():
        italy_key = s["key"]
        break
if italy_key is None and len(soccer_games) > 0:
    italy_key = soccer_games[0]["key"]
print("Usando sport_key:", italy_key)

if italy_key:
    url = f"{BASE_ODDS}/v4/sports/{italy_key}/odds/?apiKey={ODDS_API_KEY}&regions=eu&markets=h2h,totals"
    odds_resp = requests.get(url, timeout=30)
    print("Status Odds API:", odds_resp.status_code)
    print("\nQuota mensile residua (header):", odds_resp.headers.get("x-requests-remaining"))
    events = odds_resp.json()
    print(f"Eventi con quote: {len(events)}")
    if events:
        ev = events[0]
        print("\nEsempio evento:")
        print("  Home:", ev["home_team"], "| Away:", ev["away_team"])
        for bm in ev.get("bookmakers", [])[:3]:
            print("  Bookmaker:", bm["title"])
            for market in bm.get("markets", []):
                print("    Market:", market["key"], "->",
                      [(o["name"], o["price"]) for o in market.get("outcomes", [])][:6])
        df_odds = pd.DataFrame(
            [{"event": e["home_team"] + " vs " + e["away_team"],
              "commence_time": e["commence_time"]} for e in events]
        )
        df_odds.to_csv("/content/odds_fixtures.csv", index=False)

# ---------------------------------------------------------------------------
# 4. FONTE 3: KAGGLE  (dataset storico per addestramento)
#    Dataset consigliato: "Nayknow/datasoccer-database" contiene il noto
#    database europeo (25.000+ partite dal 2008). In alternativa cerca
#    su Kaggle "Serie A" e usa quello che trovi.
# ---------------------------------------------------------------------------
# !kaggle datasets download -d Nayknow/datasoccer-database -p /content/kaggle
# !unzip -o "/content/kaggle/datasoccer-database.zip" -d /content/kaggle

# Carica il file dei match (il nome cambia: di solito database.sqlite o matches.csv)
# df_kaggle = pd.read_sql("SELECT * FROM Match", sqlite3.connect("/content/kaggle/database.sqlite"))
print("\n=== Prossimi passi (fai a mano) ===")
print("1. Scarica il dataset Kaggle e caricalo qui sopra.")
print("2. Controlla che ci siano righe con League.name == 'Italy Serie A'.")
print("3. Se ok, passa al notebook step 2: entrenamento del modello.")