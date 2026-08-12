package com.example.soccerapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.soccerapp.data.local.FixtureCache
import com.example.soccerapp.data.network.FootballDataApi
import com.example.soccerapp.data.network.FdMatch
import com.example.soccerapp.data.network.NetworkModule
import com.example.soccerapp.data.network.OddsApi
import com.example.soccerapp.data.network.OddsEvent
import com.example.soccerapp.di.ApiKeys
import com.example.soccerapp.model.BetSuggestion
import com.example.soccerapp.model.Bookmaker
import com.example.soccerapp.model.Fixture
import com.example.soccerapp.model.H2hOdds
import com.example.soccerapp.model.Prediction
import com.example.soccerapp.model.TflitePredictor
import com.example.soccerapp.model.TotalOdds
import com.example.soccerapp.model.ValueBetCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(
    val fixtures: List<Fixture> = emptyList(),
    val oddsLoading: Boolean = false,
    val suggestions: List<BetSuggestion> = emptyList(),
    val error: String? = null,
)

class MainViewModel : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    // Il modello TFLite vive per tutta la durata del ViewModel.
    private var predictor: TflitePredictor? = null

    fun attachPredictor(predictor: TflitePredictor) {
        this.predictor = predictor
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(error = null)
            try {
                val cached = FixtureCache.get()
                val fixtures = if (cached.isNotEmpty()) cached else {
                    fetchFixtures().also { FixtureCache.put(it) }
                }
                val events = fetchOdds(fixtures)
                val suggestions = buildSuggestions(fixtures, events)
                _state.value = _state.value.copy(
                    fixtures = fixtures,
                    suggestions = suggestions,
                    oddsLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message)
            }
        }
    }

    private suspend fun fetchFixtures(): List<Fixture> {
        val api: FootballDataApi = NetworkModule.footballDataApi
        val resp = api.getMatches(ApiKeys.FOOTBALL_DATA_TOKEN)
        return resp.matches.map { m: FdMatch ->
            Fixture(
                id = m.id.toString(),
                homeTeam = m.homeTeam.name,
                awayTeam = m.awayTeam.name,
                homeGoals = m.score?.fullTime?.home,
                awayGoals = m.score?.fullTime?.away,
                utcDate = m.utcDate,
            )
        }.filter { !it.isFinished }
    }

    private suspend fun fetchOdds(fixtures: List<Fixture>): List<OddsEvent> {
        if (fixtures.isEmpty()) return emptyList()
        val api: OddsApi = NetworkModule.oddsApi
        return api.getOdds(ApiKeys.ODDS_API_KEY)
    }

    private fun buildSuggestions(
        fixtures: List<Fixture>,
        events: List<OddsEvent>,
    ): List<BetSuggestion> {
        // Teams' names are the only id common to both APIs. Normalise and map.
        fun norm(name: String): String =
            name.lowercase().replace(Regex("[^a-z0-9àèéìòù ]"), "").trim()
        val byTeams = fixtures.associateBy { (norm(it.homeTeam) + "|" + norm(it.awayTeam)) }

        val suggestions = mutableListOf<BetSuggestion>()
        val predPredictor = predictor ?: return suggestions

        events.forEach { event ->
            val home = event.homeTeam
            val away = event.awayTeam
            val key = norm(home) + "|" + norm(away)
            val fixture = byTeams[key] ?: return@forEach

            val bookmakers = parseBookmakers(event)

            val (best, titles) = ValueBetCalculator.bestOddsPerOutcome(bookmakers)
            if (best.isEmpty()) return@forEach

            val prediction = predictFor(best)
            suggestions += ValueBetCalculator.findValueBets(
                fixtureId = event.id,
                bestPerOutcome = best,
                bookmakerTitles = titles,
                prediction = prediction,
            )
        }
        return suggestions
    }

    private fun parseBookmakers(event: OddsEvent): List<Bookmaker> {
        return (event.bookmakers ?: emptyList()).map { bm ->
            var h2h: H2hOdds? = null
            var total: TotalOdds? = null
            bm.markets?.forEach { market ->
                when (market.key) {
                    "h2h" -> {
                        val home = market.outcomes.firstOrNull { it.name == event.homeTeam }
                        val draw = market.outcomes.firstOrNull { it.name == "Draw" }
                        val away = market.outcomes.firstOrNull { it.name == event.awayTeam }
                        if (home != null && draw != null && away != null) {
                            h2h = H2hOdds(home.price, draw.price, away.price)
                        }
                    }
                    "totals" -> {
                        val over = market.outcomes.firstOrNull { it.name.contains("Over 2.5") }
                        val under = market.outcomes.firstOrNull { it.name.contains("Under 2.5") }
                        if (over != null && under != null) {
                            total = TotalOdds(over.price, under.price)
                        }
                    }
                }
            }
            Bookmaker(title = bm.title, h2h = h2h, total = total)
        }
    }

    private fun predictFor(
        best: Map<ValueBetCalculator.Outcome, Double>,
    ): Prediction {
        // NOTA: qui andrei a costruire le feature (forma, attacco, difesa) da
        // dati locali. Per il primo MVP usiamo le quote impliche come base,
        // che e' gia' il predittore piu' forte. Poi il modello le raffinera'.
        // Probabilita' implicite medie dei bookmaker (base, margine normalizzato)
        val h = 1.0 / (best[ValueBetCalculator.Outcome.HOME] ?: 1.0)
        val d = 1.0 / (best[ValueBetCalculator.Outcome.DRAW] ?: 1.0)
        val a = 1.0 / (best[ValueBetCalculator.Outcome.AWAY] ?: 1.0)
        val sum = h + d + a
        if (sum <= 0.0) {
            return Prediction(0.34, 0.32, 0.34, 0.5)
        }
        val implHome = h / sum
        val implDraw = d / sum
        val implAway = a / sum

        val model = predictor
        // Fallback SENZA modello addestrato (fase baseline): usa le frequenze
        // storiche medie della Serie A (~46% casa, 27% pareggio, 27% trasferta).
        // Confrontate con le quote implicite del bookmaker producono gli edge.
        if (model == null || !model.isModelLoaded) {
            return Prediction(0.46, 0.27, 0.27, 0.50)
        }

        // FEATURE_COLS dal notebook (indici fissi):
        // [0]=is_home, [1]=gameweek_norm, [2]=home_att, [3]=home_def,
        // [4]=away_att, [5]=away_def, [6]=home_form5, [7]=away_form5,
        // [8]=impl_home, [9]=impl_draw, [10]=impl_away
        val n = model.featureCount

        if (n == 8) {
            // Modello addestrato SENZA quote (solo 8 feature di statistiche)
            return model.predict(floatArrayOf(1.0f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f))
        }

        val features = FloatArray(n) { 0.5f }
        features[0] = 1.0f   // is_home
        features[1] = 0.5f   // gameweek normalizzata (stima)
        if (n >= 11) {
            features[8] = implHome.toFloat()
            features[9] = implDraw.toFloat()
            features[10] = implAway.toFloat()
        }

        return model.predict(features)
    }
}