package com.example.soccerapp.data.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

object NetworkModule {

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    val footballDataApi: FootballDataApi
        get() = Retrofit.Builder()
            .baseUrl("https://api.football-data.org/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(FootballDataApi::class.java)

    val oddsApi: OddsApi
        get() = Retrofit.Builder()
            .baseUrl("https://api.the-odds-api.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create())
            .build()
            .create(OddsApi::class.java)
}