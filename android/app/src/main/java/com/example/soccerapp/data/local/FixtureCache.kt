package com.example.soccerapp.data.local

import com.example.soccerapp.model.Fixture

/**
 * Cache locale minimalista: le API gratuite hanno rate-limit (football-data
 * ~10 req/min; Odds API 500/mese). Riutilizza i dati della stessa sessione
 * invece di richiamare ripetutamente gli endpoint.
 */
object FixtureCache {

    @Volatile
    private var fixtures: List<Fixture>? = null

    fun get(): List<Fixture> = fixtures ?: emptyList()

    fun put(newFixtures: List<Fixture>) {
        fixtures = newFixtures
    }

    fun clear() {
        fixtures = null
    }
}