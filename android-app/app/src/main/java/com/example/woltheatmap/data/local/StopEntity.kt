package com.example.woltheatmap.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A pre-reduced, already-anonymized stop event waiting to be batch-uploaded.
 * Note what's deliberately absent: no raw lat/lng, no exact timestamp, no
 * user/device identifier. By the time a row exists here, it's already safe
 * to send to the server as-is.
 */
@Entity(tableName = "queued_stops")
data class StopEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val city: String,
    val geohash: String,
    val dayOfWeek: Int,      // 0-6
    val hourBucket: Int,     // 0-23
    val vehicleClass: String // "fast" | "slow"
)
