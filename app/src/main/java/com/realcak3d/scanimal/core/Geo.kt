package com.realcak3d.scanimal.core

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Small geo helpers for on-device map pins. No Android dependencies, so it unit-tests on the JVM. */
object Geo {
    private const val EARTH_R = 6_371_000.0 // metres

    /** Great-circle distance in metres between two WGS84 lat/lon points (haversine). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_R * atan2(sqrt(a), sqrt(1 - a))
    }
}
