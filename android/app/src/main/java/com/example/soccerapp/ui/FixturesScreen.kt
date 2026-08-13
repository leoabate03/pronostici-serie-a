package com.example.soccerapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soccerapp.model.Fixture
import com.example.soccerapp.model.Prediction

private enum class FixtureFilter(val label: String) {
    ALL("Tutte"),
    SCHEDULED("In programma"),
    FINISHED("Terminate"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixturesScreen(
    viewModel: MainViewModel,
    onFixtureClick: (String) -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(FixtureFilter.ALL) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    val filtered = remember(state.fixtures, query, filter) {
        val q = query.trim().lowercase()
        state.fixtures.filter { f ->
            val matchesQuery = q.isEmpty() ||
                f.homeTeam.lowercase().contains(q) ||
                f.awayTeam.lowercase().contains(q)
            val matchesFilter = when (filter) {
                FixtureFilter.ALL -> true
                FixtureFilter.SCHEDULED -> !f.isFinished
                FixtureFilter.FINISHED -> f.isFinished
            }
            matchesQuery && matchesFilter
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { viewModel.refresh() },
            modifier = Modifier.padding(16.dp),
        ) {
            Text("Aggiorna dati")
        }

        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = if (state.modelActive) {
                    "Modello: attivo"
                } else {
                    "Modello: fallback (baseline Serie A)"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.modelActive) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        state.modelError?.let { err ->
            Text(
                text = "Dettaglio caricamento modello: $err",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Cerca squadra...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
        )

        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            FixtureFilter.values().forEach { f ->
                FilterChip(
                    selected = filter == f,
                    onClick = { filter = f },
                    label = { Text(f.label) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }

        state.error?.let { err ->
            Text(
                text = "Errore: $err",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (state.fixtures.isEmpty() && state.error == null) {
            Text(
                text = "Nessuna partita in programma. Potrebbe essere fuori stagione (Serie A: ago-mag). Riprova pi\u00f9 tardi.",
                modifier = Modifier.padding(16.dp),
            )
        } else if (filtered.isEmpty()) {
            Text(
                text = "Nessun risultato per la ricerca attuale.",
                modifier = Modifier.padding(16.dp),
            )
        }

        LazyColumn {
            items(filtered, key = { it.id }) { fixture ->
                FixtureCard(
                    fixture = fixture,
                    prediction = state.predictions[fixture.id],
                    onClick = { onFixtureClick(fixture.id) },
                )
            }
        }
    }
}

@Composable
private fun FixtureCard(
    fixture: Fixture,
    prediction: Prediction?,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${fixture.homeTeam} vs ${fixture.awayTeam}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = fixture.utcDate.replace("T", " ").take(16),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (fixture.isFinished) {
                Text(
                    text = "${fixture.homeGoals} - ${fixture.awayGoals}",
                    style = MaterialTheme.typography.titleMedium,
                )
            } else {
                prediction?.let { p ->
                    Column {
                        Text(
                            text = "Modello: 1 " + "%.0f%%".format(p.homeProb * 100) +
                                "  X " + "%.0f%%".format(p.drawProb * 100) +
                                "  2 " + "%.0f%%".format(p.awayProb * 100),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}
