package com.realcak3d.scanimal.core

/** Pure helpers for reading the environment. No Android, so they're unit-tested. */
object Env {
    fun biomeFor(devicesInRange: Int): Biome = when {
        devicesInRange <= 1 -> Biome.WILDERNESS
        devicesInRange <= 5 -> Biome.OUTDOORS
        devicesInRange <= 14 -> Biome.SUBURB
        devicesInRange <= 30 -> Biome.CITY
        else -> Biome.HOTSPOT
    }

    /**
     * Weather from barometric pressure vs a slow baseline (hPa). Falling pressure means a storm is
     * coming; rising means it's clearing. This is how old analog barometers "forecast".
     */
    fun weatherFor(pressureHpa: Float, baselineHpa: Float): Weather {
        if (pressureHpa <= 0f || baselineHpa <= 0f) return Weather.UNKNOWN
        val d = pressureHpa - baselineHpa
        return when {
            d <= -3f -> Weather.STORMY
            d <= -1f -> Weather.CLOUDY
            d >= 2.5f -> Weather.CLEAR
            d >= 0.8f -> Weather.CLEARING
            else -> Weather.FAIR
        }
    }

    /** Motion from GPS speed (m/s), step cadence (steps/min) and whether the phone is pocketed. */
    fun motionFor(speedMps: Float, cadenceSpm: Float, pocket: Boolean): Motion = when {
        speedMps > 8.5f -> Motion.VEHICLE
        speedMps > 4.2f -> if (cadenceSpm > 60f) Motion.RUNNING else Motion.CYCLING
        speedMps > 2.6f -> if (cadenceSpm > 120f) Motion.RUNNING else Motion.CYCLING
        cadenceSpm > 130f -> Motion.RUNNING
        cadenceSpm > 25f || speedMps > 0.7f -> Motion.WALKING
        pocket -> Motion.POCKET
        else -> Motion.STILL
    }

    /** Barometric altitude (m) → a playful zone name. */
    fun altitudeZone(m: Double): String = when {
        m.isNaN() -> "—"
        m < -5 -> "below sea level"
        m < 150 -> "lowlands"
        m < 600 -> "hills"
        m < 1500 -> "highlands"
        m < 3000 -> "mountains"
        else -> "the clouds"
    }
}
