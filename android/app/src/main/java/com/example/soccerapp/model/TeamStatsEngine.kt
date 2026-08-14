package com.example.soccerapp.model

/**
 * Replica KOTLIN di `cumulative_team_feats` in notebooks/02_train_model.py.
 * Calcola le 6 feature "statistiche" usate dal modello a 8 feature:
 *   [home_att_avg, home_def_avg, away_att_avg, away_def_avg,
 *    home_form5, away_form5]
 * con la STESSA scala del training (is_home=1, gameweek_norm=0.5 fisso).
 *
 * Ogni squadra conserva:
 *   - media EWMA gol fatti (g) e subiti (s), default 1.3
 *   - ultimi 5 punti (3=win, 1=draw, 0=loss), default [3,3,3,3,3]
 *
 * Consumare le partite FINITE in ordine cronologico, poi chiamare
 * `featuresFor(home, away)` per costruire l'input del modello.
 */
class TeamStatsEngine {

    private data class TeamStats(
        var g: Double = 1.3,
        var s: Double = 1.3,
        val points: MutableList<Double> = mutableListOf(3.0, 3.0, 3.0, 3.0, 3.0),
    )

    private val teams = mutableMapOf<String, TeamStats>()

    fun consumeFinishedMatch(home: String, away: String, homeGoals: Int, awayGoals: Int) {
        consume(home, homeGoals, awayGoals)
        consume(away, awayGoals, homeGoals)
    }

    private fun consume(team: String, goalsFor: Int, goalsAgainst: Int) {
        val t = teams.getOrPut(team.lowercase().trim()) { TeamStats() }
        t.g = 0.8 * t.g + 0.2 * goalsFor
        t.s = 0.8 * t.s + 0.2 * goalsAgainst
        val pts = when {
            goalsFor > goalsAgainst -> 3.0
            goalsFor == goalsAgainst -> 1.0
            else -> 0.0
        }
        t.points.add(pts)
        if (t.points.size > 5) t.points.removeAt(0)
    }

    /**
     * Costruisce il vettore (8) atteso dal modello, in ordine FEATURE_COLS.
     * @param gameweek giornata di campionato (1..38); se null usa 0 (inizio).
     */
    fun featuresFor(home: String, away: String, gameweek: Int? = null): FloatArray {
        val h = teams[home.lowercase().trim()] ?: TeamStats()
        val a = teams[away.lowercase().trim()] ?: TeamStats()
        val gameweekNorm = (gameweek ?: 0).coerceIn(0, 38) / 38f
        return floatArrayOf(
            1.0f,                    // is_home
            gameweekNorm,            // gameweek_norm (0..1)
            h.g.toFloat(), h.s.toFloat(),
            a.g.toFloat(), a.s.toFloat(),
            (h.points.sum() / h.points.size).toFloat(),
            (a.points.sum() / a.points.size).toFloat(),
        )
    }
}