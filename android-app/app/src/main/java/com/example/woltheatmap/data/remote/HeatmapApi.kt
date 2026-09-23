package com.example.woltheatmap.data.remote

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

data class StopEventDto(
    val city: String,
    val geohash: String,
    val day_of_week: Int,
    val hour_bucket: Int,
    val vehicle_class: String
)

data class HeatmapCellDto(
    val geohash: String,
    val decayed_weight: Double
)

interface HeatmapApi {
    @POST("v1/stops/batch")
    suspend fun uploadStops(@Body events: List<StopEventDto>)

    @GET("v1/heatmap")
    suspend fun getHeatmap(
        @Query("city") city: String,
        @Query("vehicle_class") vehicleClass: String,
        @Query("day_of_week") dayOfWeek: Int,
        @Query("hour_bucket") hourBucket: Int
    ): List<HeatmapCellDto>
}
