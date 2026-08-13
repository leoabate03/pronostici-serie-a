package com.example.soccerapp.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Header

@JsonClass(generateAdapter = false)
data class FdMatchResponse(
    @Json(name = "matches") val matches: List<FdMatch>,
)

@JsonClass(generateAdapter = false)
data class FdMatch(
    @Json(name = "id") val id: Long,
    @Json(name = "utcDate") val utcDate: String,
    @Json(name = "homeTeam") val homeTeam: FdTeam,
    @Json(name = "awayTeam") val awayTeam: FdTeam,
    @Json(name = "score") val score: FdScore?,
)

@JsonClass(generateAdapter = false)
data class FdTeam(
    @Json(name = "name") val name: String,
)

@JsonClass(generateAdapter = false)
data class FdScore(
    @Json(name = "fullTime") val fullTime: FdFullTime?,
)

@JsonClass(generateAdapter = false)
data class FdFullTime(
    @Json(name = "home") val home: Int?,
    @Json(name = "away") val away: Int?,
)

/** Client per football-data.org (fixtures + risultati Serie A -> competizione 'SA'). */
interface FootballDataApi {

    @GET("v4/competitions/SA/matches?status=FINISHED,SCHEDULED")
    suspend fun getMatches(@Header("X-Auth-Token") token: String): FdMatchResponse
}