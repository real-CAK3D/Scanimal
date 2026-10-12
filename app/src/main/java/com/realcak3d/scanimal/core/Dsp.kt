package com.realcak3d.scanimal.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

object Dsp {
    /** Magnitude spectrum (dB, ~0..90) of a power-of-two block using a Hann window and radix-2 FFT. */
    fun spectrum(samples: FloatArray): FloatArray {
        var n = 1
        while (n * 2 <= samples.size) n *= 2
        if (n < 8) return FloatArray(0)
        val re = DoubleArray(n) { i -> samples[i] * (0.5 - 0.5 * cos(2 * PI * i / (n - 1))) }
        val im = DoubleArray(n)
        // Bit reversal
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = re[i]; re[i] = re[j]; re[j] = t }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            for (i in 0 until n step len) {
                for (k in 0 until len / 2) {
                    val wr = cos(ang * k); val wi = sin(ang * k)
                    val ur = re[i + k]; val ui = im[i + k]
                    val vr = re[i + k + len / 2] * wr - im[i + k + len / 2] * wi
                    val vi = re[i + k + len / 2] * wi + im[i + k + len / 2] * wr
                    re[i + k] = ur + vr; im[i + k] = ui + vi
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi
                }
            }
            len = len shl 1
        }
        return FloatArray(n / 2) { k -> (20 * log10(sqrt(re[k] * re[k] + im[k] * im[k]) / n * 2 + 1e-9) + 100).toFloat().coerceAtLeast(0f) }
    }

    /** Wi-Fi channel number for a center frequency. */
    fun wifiChannel(mhz: Int): Int = when {
        mhz == 2484 -> 14
        mhz in 2412..2472 -> (mhz - 2407) / 5
        mhz in 5150..5895 -> (mhz - 5000) / 5
        mhz in 5925..7125 -> (mhz - 5950) / 5
        else -> 0
    }
}
