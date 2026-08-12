package com.example.soccerapp.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.soccerapp.model.Fixture

@Composable
fun FixturesScreen(
    viewModel: MainViewModel,
    onFixtureClick: (String) -> Unit,
) {
    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            onClick = { viewModel.refresh() },
            modifier = Modifier.padding(16.dp),
        ) {
            Text("Aggiorna dati")
        }
        viewModel.state.value.error?.let { err ->
            Text(
                text = "Errore: $err",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        LazyColumn {
            items(viewModel.state.value.fixtures, key = { it.id }) { fixture ->
                FixtureCard(fixture) { onFixtureClick(fixture.id) }
            }
        }
    }
}

@Composable
private fun FixtureCard(fixture: Fixture, onClick: () -> Unit) {
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
            }
        }
    }
}