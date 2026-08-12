package com.example.soccerapp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soccerapp.R
import com.example.soccerapp.model.BetSuggestion
import com.example.soccerapp.model.Fixture
import androidx.compose.ui.res.stringResource

/** Scorrevole della lista dei consigli con il maggior valore atteso. */
@Composable
fun ValueBetsScreen(viewModel: MainViewModel) {
    val suggestions = viewModel.state.value.suggestions
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Consigli per il valore (value bets)",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(16.dp),
        )
        Text(
            text = stringResource(R.string.disclaimer),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        suggestions.take(20).forEach { s ->
            SuggestionCard(s)
        }
        if (suggestions.isEmpty()) {
            Text(
                text = "Nessun value bet attualmente: le quote sembrano allineate " +
                    "alle probabilita' del modello. Ricontrolla dopo gli aggiornamenti.",
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun SuggestionCard(s: BetSuggestion) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(s.label, style = MaterialTheme.typography.titleMedium)
            Row {
                Text("Quota: ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${String.format("%.2f", s.odds)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("  |  Best: ${s.bestBookmaker}", style = MaterialTheme.typography.bodySmall)
            }
            Text(
                "EV: ${String.format("%.3f", s.expectedValue)}  |  Edge: " +
                    "${String.format("%.1f", s.edge * 100)}%",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}