package com.example.soccerapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soccerapp.model.Fixture
import com.example.soccerapp.model.BetSuggestion

@Composable
fun MatchDetailScreen(
    viewModel: MainViewModel,
    fixtureId: String?,
) {
    val state by viewModel.state.collectAsState()
    val fixture = state.fixtures.firstOrNull { it.id == fixtureId }
    val suggestion = state.suggestions.firstOrNull { it.fixtureId == fixtureId }

    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        if (fixture == null) {
            Text("Partita non trovata. Torna alla lista.")
            return
        }
        Text(
            text = "${fixture.homeTeam} vs ${fixture.awayTeam}",
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = fixture.utcDate.replace("T", " ").take(16),
            style = MaterialTheme.typography.bodySmall,
        )

        if (suggestion == null) {
            Text(
                "Nessun valore trovato per questa partita (o niente quote).",
                modifier = Modifier.padding(top = 16.dp),
            )
        } else {
            Text(
                "Consiglio: ${suggestion.label}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text("Quota migliore: ${String.format("%.2f", suggestion.odds)} (${suggestion.bestBookmaker})")
            Text("EV: ${String.format("%.3f", suggestion.expectedValue)}")
        }
    }
}