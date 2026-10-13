package com.realcak3d.scanimal.sensors

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.sqrt

/** Live readings shown in the Lab and used by the buddy. */
data class BodyReadings(
    val heading: Float = 0f,          // degrees, 0 = north
    val pitch: Float = 0f,
    val roll: Float = 0f,
    val tiltX: Float = 0f,            // gravity-ish, for eye tracking / games
    val tiltY: Float = 0f,
    val accel: Float = 0f,            // linear acceleration magnitude
    val linX: Float = 0f,             // linear acceleration (gravity removed), device axes
    val linY: Float = 0f,
    val lux: Float = -1f,
    val proximityNear: Boolean = false,
    val pressureHpa: Float = -1f,
    val altitudeM: Float = Float.NaN,
    val magneticUT: Float = 0f,
    val stepsSession: Int = 0,
    val batteryPct: Int = -1,
    val charging: Boolean = false,
    val soundDb: Float = 0f,
    val ambientTempC: Float = Float.NaN,
    val humidity: Float = Float.NaN,
    val torchOn: Boolean = false,
    val hasCompass: Boolean = false,
    val hasStepCounter: Boolean = false,
    val hasLight: Boolean = false,
    val hasProximity: Boolean = false,
    val hasBarometer: Boolean = false,
    val hasThermometer: Boolean = false,
    val hasHygrometer: Boolean = false,
    val hasTorch: Boolean = false,
)

/** Every on-board sensor we can make fun out of. Callbacks fire on the main thread. */
class BodySensors(private val ctx: Context) : SensorEventListener {
    private val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotation = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val light = sm.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY)
    private val pressure = sm.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private val magnetic = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    private val steps = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val thermo = sm.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
    private val hygro = sm.getDefaultSensor(Sensor.TYPE_RELATIVE_HUMIDITY)
    private val sigMotion = sm.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

    private val _r = MutableStateFlow(
        BodyReadings(hasCompass = rotation != null, hasStepCounter = steps != null, hasLight = light != null,
            hasProximity = proximity != null, hasBarometer = pressure != null,
            hasThermometer = thermo != null, hasHygrometer = hygro != null, hasTorch = hasTorchFlash(ctx))
    )
    val readings: StateFlow<BodyReadings> = _r.asStateFlow()

    var onShake: (() -> Unit)? = null
    var onSteps: ((Int) -> Unit)? = null
    var onDark: (() -> Unit)? = null
    var onPeekaboo: (() -> Unit)? = null
    var onCharging: (() -> Unit)? = null
    var onMagnet: ((Float) -> Unit)? = null
    var onAltitude: ((Double) -> Unit)? = null
    var onSignificantMotion: (() -> Unit)? = null

    private val sigMotionTrigger = object : android.hardware.TriggerEventListener() {
        override fun onTrigger(event: android.hardware.TriggerEvent?) {
            onSignificantMotion?.invoke()
            if (running) sigMotion?.let { sm.requestTriggerSensor(this, it) }
        }
    }

    private var running = false
    private var lastShake = 0L
    private var stepBase = -1f
    private var stepReported = 0
    private var darkSince = 0L
    private var nearAt = 0L
    private var lastAltReport = 0L
    private var gravity = FloatArray(3)
    private val rotMatrix = FloatArray(9)
    private val orient = FloatArray(3)
    private var wasCharging: Boolean? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            i ?: return
            val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
            _r.value = _r.value.copy(batteryPct = if (level >= 0) level * 100 / scale else -1, charging = charging)
            if (wasCharging == false && charging) onCharging?.invoke()
            wasCharging = charging
        }
    }

    fun start() {
        if (running) return
        running = true
        val rate = SensorManager.SENSOR_DELAY_GAME
        rotation?.let { sm.registerListener(this, it, rate) }
        accelerometer?.let { sm.registerListener(this, it, rate) }
        light?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        proximity?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        pressure?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        magnetic?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        thermo?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        hygro?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        sigMotion?.let { sm.requestTriggerSensor(sigMotionTrigger, it) }
        if (steps != null && (Build.VERSION.SDK_INT < 29 || ctx.granted(Manifest.permission.ACTIVITY_RECOGNITION))) {
            sm.registerListener(this, steps, SensorManager.SENSOR_DELAY_NORMAL)
        }
        ContextCompat.registerReceiver(ctx, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Background service: only the cheap sensors (steps + battery). */
    fun startStepsOnly() {
        if (running) return
        running = true
        if (steps != null && (Build.VERSION.SDK_INT < 29 || ctx.granted(Manifest.permission.ACTIVITY_RECOGNITION))) {
            sm.registerListener(this, steps, SensorManager.SENSOR_DELAY_NORMAL)
        }
        ContextCompat.registerReceiver(ctx, batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Call after the activity-recognition permission is granted. */
    fun restart() { stop(); start() }

    fun stop() {
        if (!running) return
        running = false
        sm.unregisterListener(this)
        runCatching { sigMotion?.let { sm.cancelTriggerSensor(sigMotionTrigger, it) } }
        runCatching { ctx.unregisterReceiver(batteryReceiver) }
        stepBase = -1f
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(e: SensorEvent) {
        val now = System.currentTimeMillis()
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotMatrix, e.values)
                SensorManager.getOrientation(rotMatrix, orient)
                val az = ((Math.toDegrees(orient[0].toDouble()) + 360) % 360).toFloat()
                // Light smoothing that respects the 359 -> 0 wrap.
                val prev = _r.value.heading
                var diff = az - prev
                if (diff > 180) diff -= 360
                if (diff < -180) diff += 360
                val smooth = (prev + diff * 0.25f + 360) % 360
                _r.value = _r.value.copy(
                    heading = smooth,
                    pitch = Math.toDegrees(orient[1].toDouble()).toFloat(),
                    roll = Math.toDegrees(orient[2].toDouble()).toFloat(),
                )
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val a = 0.85f
                for (i in 0..2) gravity[i] = a * gravity[i] + (1 - a) * e.values[i]
                val lx = e.values[0] - gravity[0]; val ly = e.values[1] - gravity[1]; val lz = e.values[2] - gravity[2]
                val mag = sqrt(lx * lx + ly * ly + lz * lz)
                _r.value = _r.value.copy(tiltX = gravity[0], tiltY = gravity[1], accel = mag, linX = lx, linY = ly)
                if (mag > 14f && now - lastShake > 900) { lastShake = now; onShake?.invoke() }
            }
            Sensor.TYPE_LIGHT -> {
                val lux = e.values[0]
                _r.value = _r.value.copy(lux = lux)
                if (lux < 4f) {
                    if (darkSince == 0L) darkSince = now
                    if (now - darkSince > 6_000) { darkSince = now + 10 * 60_000L; onDark?.invoke() }
                } else darkSince = 0L
            }
            Sensor.TYPE_PROXIMITY -> {
                val near = e.values[0] < (e.sensor.maximumRange.coerceAtMost(5f))
                val was = _r.value.proximityNear
                _r.value = _r.value.copy(proximityNear = near)
                if (near && !was) nearAt = now
                if (!near && was && now - nearAt in 400..5_000) onPeekaboo?.invoke()
            }
            Sensor.TYPE_PRESSURE -> {
                val hpa = e.values[0]
                val alt = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, hpa)
                _r.value = _r.value.copy(pressureHpa = hpa, altitudeM = alt)
                if (now - lastAltReport > 60_000) { lastAltReport = now; onAltitude?.invoke(alt.toDouble()) }
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val m = sqrt(e.values[0] * e.values[0] + e.values[1] * e.values[1] + e.values[2] * e.values[2])
                _r.value = _r.value.copy(magneticUT = m)
                if (m > 200f) onMagnet?.invoke(m)
            }
            Sensor.TYPE_AMBIENT_TEMPERATURE -> _r.value = _r.value.copy(ambientTempC = e.values[0])
            Sensor.TYPE_RELATIVE_HUMIDITY -> _r.value = _r.value.copy(humidity = e.values[0])
            Sensor.TYPE_STEP_COUNTER -> {
                val total = e.values[0]
                if (stepBase < 0) { stepBase = total; stepReported = 0 }
                val session = (total - stepBase).toInt()
                _r.value = _r.value.copy(stepsSession = session)
                if (session - stepReported >= 20) { onSteps?.invoke(session - stepReported); stepReported = session }
            }
        }
    }
}

/** Microphone loudness for the dance party. Only runs while the user has it switched on. */
class SoundMeter(private val ctx: Context) {
    private val _db = MutableStateFlow(0f)
    val db: StateFlow<Float> = _db.asStateFlow()
    private val _wave = MutableStateFlow(FloatArray(0))
    /** Latest ~1024 samples, -1..1, for the oscilloscope and spectrum views. */
    val wave: StateFlow<FloatArray> = _wave.asStateFlow()
    val sampleRate = 22050
    private var users = 0
    var onLoud: (() -> Unit)? = null
    @Volatile private var running = false
    private var thread: Thread? = null

    fun canRun() = ctx.granted(Manifest.permission.RECORD_AUDIO)

    @SuppressLint("MissingPermission")
    fun start() {
        if (running || !canRun()) return
        running = true
        thread = Thread {
            val rate = 22050
            val size = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT).coerceAtLeast(2048)
            val rec = runCatching { AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size) }.getOrNull()
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) { running = false; return@Thread }
            val buf = ShortArray(size / 2)
            val window = FloatArray(1024)
            var lastWave = 0L
            rec.startRecording()
            var loudSince = 0L
            while (running) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) continue
                var sum = 0.0
                for (i in 0 until n) sum += buf[i] * buf[i].toDouble()
                val rms = sqrt(sum / n)
                val db = (20 * log10(rms.coerceAtLeast(1.0))).toFloat() // ~0..90
                _db.value = db
                // Slide new samples into the analysis window.
                val take = minOf(n, window.size)
                System.arraycopy(window, take, window, 0, window.size - take)
                for (i in 0 until take) window[window.size - take + i] = buf[n - take + i] / 32768f
                val tNow = System.currentTimeMillis()
                if (tNow - lastWave > 45) { lastWave = tNow; _wave.value = window.copyOf() }
                val now = System.currentTimeMillis()
                if (db > 68) { if (loudSince == 0L) loudSince = now; if (now - loudSince > 700) { loudSince = 0L; onLoud?.invoke() } } else loudSince = 0L
            }
            rec.stop(); rec.release()
        }.apply { isDaemon = true; start() }
    }

    fun stop() { running = false; thread = null; _db.value = 0f }

    /** Reference-counted so the Lab and the Spectrum view can both use the mic. */
    fun acquire() { users++; start() }
    fun release() { users = (users - 1).coerceAtLeast(0); if (users == 0) stop() }
}

private fun hasTorchFlash(ctx: Context): Boolean =
    ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_FLASH)

/** The camera flash as a toy "firefly" light. */
class Torch(private val ctx: Context) {
    private val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
    private val id: String? = runCatching {
        cm?.cameraIdList?.firstOrNull { cm.getCameraCharacteristics(it).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()
    val available get() = id != null
    @Volatile var on = false; private set
    fun set(state: Boolean): Boolean {
        val c = id ?: return false
        return runCatching { cm?.setTorchMode(c, state); on = state; true }.getOrDefault(false)
    }
    fun toggle() = set(!on)
    fun off() { runCatching { if (on) set(false) } }
}

/** Vibration + beeps. */
class Haptics(ctx: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31)
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    else @Suppress("DEPRECATION") (ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator)
    private val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()
    var vibrationOn = true
    var soundOn = true

    fun buzz(ms: Long = 30, strength: Int = 120) {
        if (!vibrationOn) return
        val v = vibrator ?: return
        runCatching { v.vibrate(VibrationEffect.createOneShot(ms, strength.coerceIn(1, 255))) }
    }

    fun pattern(timings: LongArray) {
        if (!vibrationOn) return
        runCatching { vibrator?.vibrate(VibrationEffect.createWaveform(timings, -1)) }
    }

    fun beep(kind: Int = ToneGenerator.TONE_PROP_BEEP, ms: Int = 60) {
        if (!soundOn) return
        runCatching { tone?.startTone(kind, ms) }
    }

    fun ping(strength: Float) {
        beep(if (strength > 0.75f) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, 40)
        buzz((15 + strength * 45).toLong(), (40 + strength * 215).toInt())
    }

    /** Short melodic cues for game events (honours the sounds toggle). */
    fun sfx(kind: String) {
        if (!soundOn) return
        val tg = tone ?: return
        val seq: List<Pair<Int, Int>> = when (kind) {
            "eat" -> listOf(ToneGenerator.TONE_PROP_BEEP to 50)
            "level" -> listOf(ToneGenerator.TONE_DTMF_5 to 90, ToneGenerator.TONE_DTMF_9 to 90, ToneGenerator.TONE_DTMF_D to 140)
            "hatch" -> listOf(ToneGenerator.TONE_DTMF_1 to 80, ToneGenerator.TONE_DTMF_5 to 80, ToneGenerator.TONE_DTMF_9 to 120, ToneGenerator.TONE_SUP_RINGTONE to 180)
            "achieve" -> listOf(ToneGenerator.TONE_PROP_BEEP2 to 70, ToneGenerator.TONE_PROP_ACK to 120)
            "sticker" -> listOf(ToneGenerator.TONE_PROP_BEEP to 50, ToneGenerator.TONE_PROP_BEEP2 to 70)
            "notable" -> listOf(ToneGenerator.TONE_SUP_RADIO_ACK to 120)
            // A distinct "you're near a place you already pinned" chirp: low-high double blip.
            "revisit" -> listOf(ToneGenerator.TONE_DTMF_1 to 70, ToneGenerator.TONE_DTMF_9 to 110)
            "sad" -> listOf(ToneGenerator.TONE_SUP_ERROR to 180)
            "coin" -> listOf(ToneGenerator.TONE_PROP_ACK to 60)
            else -> listOf(ToneGenerator.TONE_PROP_BEEP to 40)
        }
        Thread {
            for ((t, ms) in seq) { runCatching { tg.startTone(t, ms) }; try { Thread.sleep((ms + 20).toLong()) } catch (_: InterruptedException) {} }
        }.apply { isDaemon = true; start() }
    }
}
