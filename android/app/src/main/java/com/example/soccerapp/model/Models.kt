package com.example.soccerapp.model

/** Una singola partita in programma o terminata. */
data class Fixture(
    val id: String,
    val homeTeam: String,
    val awayTeam: String,
    val homeGoals: Int?,
    val awayGoals: Int?,
    val utcDate: String,
) {
    val isFinished: Boolean get() = homeGoals != null && awayGoals != null
}

/** Quote per una partita, raggruppate per bookmaker. */
data class Bookmaker(
    val title: String,
    val h2h: H2hOdds?,
    val total: TotalOdds?,
)

/** Probabilita' implicite 1X2 medie dei bookmaker (per margine, normalizzate). */
data class H2hOdds(
    val homeOdd: Double,
    val drawOdd: Double,
    val awayOdd: Double,
) {
    val impliedHome: Double get() = normalize(1.0 / homeOdd)
    val impliedDraw: Double get() = normalize(1.0 / drawOdd)
    val impliedAway: Double get() = normalize(1.0 / awayOdd)

    private fun normalize(v: Double): Double {
        val sum = 1.0 / homeOdd + 1.0 / drawOdd + 1.0 / awayOdd
        return v / sum
    }
}

/** Quote Over/Under 2.5. */
data class TotalOdds(
    val overOdd: Double,
    val underOdd: Double,
) {
    val impliedOver: Double
        get() {
            val sum = 1.0 / overOdd + 1.0 / underOdd
            return (1.0 / overOdd) / sum
        }
}

/** Output del modello: probabilita' predette. */
data class Prediction(
    val homeProb: Double,
    val drawProb: Double,
    val awayProb: Double,
    val over25Prob: Double,
)

/** Un consiglio di scommessa piu' una determinata partita. */
data class BetSuggestion(
    val fixtureId: String,
    val label: String,          // es. "Casa 1X2" oppure "Over 2.5"
    val odds: Double,           // miglior quota trovata tra i bookmaker
    val bestBookmaker: String,  // nome del bookmaker con la quota migliore
    val expectedValue: Double,  // EV atteso (positivo = valore)
    val edge: Double,           // modello - implicita (in punti di probabilita')
)