package com.example.woltheatmap.util

import ch.hsr.geohash.GeoHash

object GeoUtils {
    private const val GEOHASH_PRECISION = 7

    fun encode(lat: Double, lng: Double): String =
        GeoHash.withCharacterPrecision(lat, lng, GEOHASH_PRECISION).toBase32()

    fun decodeToCenter(geohash: String): Pair<Double, Double> {
        val center = GeoHash.fromGeohashString(geohash).boundingBox.center
        return Pair(center.latitude, center.longitude)
    }
}
