package com.example.soccerapp.di

import com.example.soccerapp.BuildConfig

/**
 * CHIAVI API — letture da BuildConfig, che a build-time prende i valori da
 * android/gradle.properties (file NON versionato, vedi .gitignore).
 *
 * Per configurare, modifica android/gradle.properties (o copia
 * gradle.properties.example):
 *   FD_TOKEN=...
 *   ODDS_KEY=...
 *
 * Il fallback a "INSERISCI_..." e' del build.gradle se il file manca.
 * Oggettivamente NON mettere mai i valori reali direttamente qui.
 */
object ApiKeys {
    const val FOOTBALL_DATA_TOKEN = BuildConfig.FOOTBALL_DATA_TOKEN
    const val ODDS_API_KEY = BuildConfig.ODDS_API_KEY
}