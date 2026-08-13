package com.example.soccerapp.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Query

@JsonClass(generateAdapter = false)
data class OddsEvent(
    @Json(name = "id") val id: String,
    @Json(name = "home_team") val homeTeam: String,
    @Json(name = "away_team") val awayTeam: String,
    @Json(name = "commence_time") val commenceTime: String,
    @Json(name = "bookmakers") val bookmakers: List<OddsBookmaker>?,
)

@JsonClass(generateAdapter = false)
data class OddsBookmaker(
    @Json(name = "key") val key: String,
    @Json(name = "title") val title: String,
    @Json(name = "markets") val markets: List<OddsMarket>?,
)

@JsonClass(generateAdapter = false)
data class OddsMarket(
    @Json(name = "key") val key: String,     // "h2h" oppure "totals"
    @Json(name = "outcomes") val outcomes: List<OddsOutcome>,
)

@JsonClass(generateAdapter = false)
data class OddsOutcome(
    @Json(name = "name") val name: String,
    @Json(name = "price") val price: Double,
)

/**
 * Client per The Odds API. sport_key per la Serie A si trova con /v4/sports/
 * (notebook 01 lo individua in automatico). NB: il mercato "totals"
 * (over/under) e' disponibile principalmente per sport USA: se per il calcio
 * non viene restituito, l'app semplicemente non propone consigli over/under.
 */
interface OddsApi {

    @GET("v4/sports/soccer_italy_serie_a/odds")
    suspend fun getOdds(
        @Query("apiKey") apiKey: String,
        @Query("regions") regions: String = "eu",
        @Query("markets") markets: String = "h2h",
    ): List<OddsEvent>
}