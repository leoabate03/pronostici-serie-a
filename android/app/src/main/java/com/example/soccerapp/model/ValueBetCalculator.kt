package com.example.soccerapp.model

/**
 * Calcolo dei consigli di scommessa con "valore" (value bets).
 *
 * Per ogni esito:
 *   - probabilita' implicita     = probabilita' media dei bookmaker (margine tolto)
 *   - probabilita' modello       = output del nostro modello
 *   - edge                       = modello - implicita
 *   - expected value             = modello * (quota - 1) - (1 - modello)
 *
 * Un esito e' finanziariamente interessante se EV > 0 e l'edge supera una
 * soglia minima (EDGE_THRESHOLD). Tra i bookmaker, per ogni esito, si prende
 * la QUOTA PIU' ALTA (best odds) e quindi "il miglior bookmaker".
 */
object ValueBetCalculator {

    /** Edge minimo (in probabilita') per consigliare un esito. 3% di default. */
    const val EDGE_THRESHOLD = 0.03

    /** Margine massimo del bookmaker accettato prima di scartarlo. */
    const val MAX_MARGIN = 0.10

    /**
     * @param fixtureId identificatore evento (The Odds API)
     * @param bestPerOutcome quota migliore per ciascun esito degli N bookmaker
     * @param bookmakerTitles nomi dei bookmaker delle quote migliori
     * @param prediction probabilita' del modello
     */
    fun findValueBets(
        fixtureId: String,
        bestPerOutcome: Map<Outcome, Double>,
        bookmakerTitles: Map<Outcome, String>,
        prediction: Prediction,
    ): List<BetSuggestion> {
        val suggestions = mutableListOf<BetSuggestion>()

        // 1X2
        val outcomes1x2 = listOf(
            Outcome.HOME to prediction.homeProb,
            Outcome.DRAW to prediction.drawProb,
            Outcome.AWAY to prediction.awayProb,
        )
        outcomes1x2.forEach { (outcome, modelProb) ->
            val implied = impliedProbability(outcome, bestPerOutcome)
            val oddsOut = bestPerOutcome[outcome] ?: return@forEach
            if (oddsOut <= 0.0) return@forEach
            val ev = modelProb * (oddsOut - 1.0) - (1.0 - modelProb)
            if (ev > 0 && modelProb - implied > EDGE_THRESHOLD) {
                suggestions.add(
                    BetSuggestion(
                        fixtureId = fixtureId,
                        label = outcome.label,
                        odds = oddsOut,
                        bestBookmaker = bookmakerTitles[outcome] ?: "—",
                        expectedValue = ev,
                        edge = modelProb - implied,
                    )
                )
            }
        }

        // Over/Under 2.5 DISATTIVATO: il modello attuale (8 feature, senza
        // quote) ha segnale ~moneta su questo mercato. Lo riattiveremo quando
        // il retrain con le quote restituira' una probabilita' over affidabile.
        // if (prediction.over25Prob ...) -> nessun suggerimento over/under.

        return suggestions.sortedByDescending { it.expectedValue }
    }

    /** Calcola la probabilita' implicita normalizzata per un dato esito. */
    private fun impliedProbability(outcome: Outcome, best: Map<Outcome, Double>): Double {
        val over = best[outcome] ?: return 0.0
        return when (outcome) {
            Outcome.HOME -> norm1x2(best)[0]
            Outcome.DRAW -> norm1x2(best)[1]
            Outcome.AWAY -> norm1x2(best)[2]
            Outcome.OVER_25 -> {
                val under = best[Outcome.UNDER_25] ?: return 1.0 / over
                (1.0 / over) / (1.0 / over + 1.0 / under)
            }
            Outcome.UNDER_25 -> 0.0
        }
    }

    private fun norm1x2(best: Map<Outcome, Double>): DoubleArray {
        val h = 1.0 / (best[Outcome.HOME] ?: 1.0)
        val d = 1.0 / (best[Outcome.DRAW] ?: 1.0)
        val a = 1.0 / (best[Outcome.AWAY] ?: 1.0)
        val s = h + d + a
        return doubleArrayOf(h / s, d / s, a / s)
    }

    enum class Outcome(val label: String) {
        HOME("Casa (1)"),
        DRAW("Pareggio (X)"),
        AWAY("Trasferta (2)"),
        OVER_25("Over 2.5"),
        UNDER_25("Under 2.5"),
    }

    /** Trova, tra i bookmaker, la quota piu' alta per ogni esito. */
    fun bestOddsPerOutcome(
        events: List<Bookmaker>,
    ): Pair<Map<Outcome, Double>, Map<Outcome, String>> {
        val best = mutableMapOf<Outcome, Double>()
        val titles = mutableMapOf<Outcome, String>()

        fun update(outcome: Outcome, odds: Double, title: String) {
            if (odds > (best[outcome] ?: 0.0)) {
                best[outcome] = odds
                titles[outcome] = title
            }
        }

        events.forEach { bm ->
            bm.h2h?.let { h ->
                update(Outcome.HOME, h.homeOdd, bm.title)
                update(Outcome.DRAW, h.drawOdd, bm.title)
                update(Outcome.AWAY, h.awayOdd, bm.title)
            }
            bm.total?.let { t ->
                update(Outcome.OVER_25, t.overOdd, bm.title)
                update(Outcome.UNDER_25, t.underOdd, bm.title)
            }
        }

        return best to titles
    }
}