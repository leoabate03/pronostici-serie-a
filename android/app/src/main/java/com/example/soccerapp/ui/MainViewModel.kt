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
import com.example.soccerapp.model.TeamStatsEngine
import com.example.soccerapp.model.TotalOdds
import com.example.soccerapp.model.ValueBetCalculator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class UiState(
    val fixtures: List<Fixture> = emptyList(),
    val predictions: Map<String, Prediction> = emptyMap(),
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
                val all = if (cached.isNotEmpty()) cached else {
                    fetchFixtures().also { FixtureCache.put(it) }
                }
                val engine = buildEngineFrom(all.filter { it.isFinished })
                val fixtures = all.filter { !it.isFinished }
                val predictions = fixtures.associate {
                    it.id to predictFor(it.homeTeam, it.awayTeam, engine)
                }
                val events = fetchOdds(fixtures)
                val suggestions = buildSuggestions(fixtures, events, engine)
                _state.value = _state.value.copy(
                    fixtures = fixtures,
                    predictions = predictions,
                    suggestions = suggestions,
                    oddsLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Errore: ${e.javaClass.simpleName}")
            }
        }
    }

    /** Addestra l'engine con i match gia' giocati, in ordine temporale. */
    private fun buildEngineFrom(finished: List<Fixture>): TeamStatsEngine {
        val engine = TeamStatsEngine()
        finished.sortedBy { it.utcDate }.forEach { f ->
            val hg = f.homeGoals ?: 0
            val ag = f.awayGoals ?: 0
            engine.consumeFinishedMatch(f.homeTeam, f.awayTeam, hg, ag)
        }
        return engine
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
        }
    }

    private suspend fun fetchOdds(fixtures: List<Fixture>): List<OddsEvent> {
        if (fixtures.isEmpty()) return emptyList()
        val api: OddsApi = NetworkModule.oddsApi
        return api.getOdds(ApiKeys.ODDS_API_KEY)
    }

    private fun buildSuggestions(
        fixtures: List<Fixture>,
        events: List<OddsEvent>,
        engine: TeamStatsEngine,
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

            val prediction = predictFor(fixture.homeTeam, fixture.awayTeam, engine)
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

    /**
     * Costruisce le feature (8) dalle statistiche accumulate nelle partite
     * finite e chiede al modello la probabilita' 1X2 / over.
     */
    private fun predictFor(
        home: String,
        away: String,
        engine: TeamStatsEngine,
    ): Prediction {
        val model = predictor
        // Fallback SENZA modello addestrato (baseline): frequenze storiche
        // della Serie A (~46% casa, 27% pareggio, 27% trasferta).
        if (model == null || !model.isModelLoaded) {
            return Prediction(0.46, 0.27, 0.27, 0.50)
        }
        return model.predict(engine.featuresFor(home, away))
    }
}