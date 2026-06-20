package com.abhishek.workouttimer

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.WindowManager
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.abhishek.workouttimer.databinding.ActivityMainBinding
import com.abhishek.workouttimer.databinding.RowSettingBinding
import kotlin.math.ceil

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding

    // ---- Phase model ----
    private enum class Type { GET_READY, WORK, REST, DONE }
    private data class Phase(val type: Type, val durationSec: Int, val roundNo: Int, val totalRounds: Int)

    private var phases: List<Phase> = emptyList()
    private var index = 0
    private var phaseRemainingMs = 0L
    private var running = false
    private var lastWholeSec = -1

    // ---- Settings (with steppers) ----
    private lateinit var totalMin: Stepper   // total workout minutes
    private lateinit var workSec: Stepper
    private lateinit var restSec: Stepper
    private lateinit var beepWin: Stepper     // last-N-seconds beep countdown
    private lateinit var prepSec: Stepper      // get-ready lead-in

    private val handler = Handler(Looper.getMainLooper())
    private var lastTickMs = 0L
    private var tone: ToneGenerator? = null
    private val vibrator: Vibrator? by lazy {
        @Suppress("DEPRECATION")
        getSystemService(VIBRATOR_SERVICE) as? Vibrator
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        try { tone = ToneGenerator(AudioManager.STREAM_MUSIC, 100) } catch (_: Exception) {}

        totalMin = Stepper(b.rowTotal, "Total workout (min)", 20, 1, 180, 1) { onSettingsChanged() }
        workSec  = Stepper(b.rowWork,  "Work (sec)",          30, 5, 600, 5) { onSettingsChanged() }
        restSec  = Stepper(b.rowRest,  "Rest (sec)",          10, 0, 300, 5) { onSettingsChanged() }
        beepWin  = Stepper(b.rowBeep,  "Beep countdown (sec)", 5, 0, 10, 1) { }
        prepSec  = Stepper(b.rowPrep,  "Get ready (sec)",     10, 0, 30, 5) { onSettingsChanged() }

        b.startPauseBtn.setOnClickListener { toggleStartPause() }
        b.resetBtn.setOnClickListener { reset() }

        buildPhases()
        index = 0
        loadPhase(0, announce = false)
        renderIdle()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        tone?.release()
    }

    // ---- Build the phase schedule from settings ----
    private fun buildPhases() {
        val totalSec = totalMin.value * 60
        val work = workSec.value
        val rest = restSec.value
        val cycle = work + rest
        val rounds = if (cycle > 0) ceil(totalSec.toDouble() / cycle).toInt().coerceAtLeast(1) else 1

        val list = ArrayList<Phase>()
        if (prepSec.value > 0) list.add(Phase(Type.GET_READY, prepSec.value, 0, rounds))

        var spent = 0
        for (r in 1..rounds) {
            if (spent >= totalSec) break
            val w = minOf(work, totalSec - spent)
            if (w > 0) { list.add(Phase(Type.WORK, w, r, rounds)); spent += w }
            if (spent >= totalSec) break
            if (rest > 0) {
                val rr = minOf(rest, totalSec - spent)
                if (rr > 0) { list.add(Phase(Type.REST, rr, r, rounds)); spent += rr }
            }
        }
        list.add(Phase(Type.DONE, 0, rounds, rounds))
        phases = list
    }

    private fun onSettingsChanged() {
        if (running) return
        buildPhases()
        index = 0
        loadPhase(0, announce = false)
        renderIdle()
    }

    private fun loadPhase(i: Int, announce: Boolean) {
        index = i
        val p = phases[i]
        phaseRemainingMs = p.durationSec * 1000L
        lastWholeSec = p.durationSec
        render()
        if (announce) {
            when (p.type) {
                Type.WORK -> { goTone(); vibrate(longArrayOf(0, 120)) }
                Type.REST -> { restTone(); vibrate(longArrayOf(0, 80)) }
                Type.GET_READY -> { }
                Type.DONE -> { finishTone(); vibrate(longArrayOf(0, 200, 100, 200)) }
            }
        }
    }

    // ---- Controls ----
    private fun toggleStartPause() {
        if (phases.getOrNull(index)?.type == Type.DONE) { reset(); return }
        if (running) pause() else start()
    }

    private fun start() {
        running = true
        lastTickMs = System.currentTimeMillis()
        b.startPauseBtn.text = "PAUSE"
        setSettingsEnabled(false)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handler.post(ticker)
    }

    private fun pause() {
        running = false
        b.startPauseBtn.text = "RESUME"
        handler.removeCallbacks(ticker)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun reset() {
        running = false
        handler.removeCallbacks(ticker)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        buildPhases()
        loadPhase(0, announce = false)
        renderIdle()
    }

    private fun setSettingsEnabled(enabled: Boolean) {
        listOf(totalMin, workSec, restSec, beepWin, prepSec).forEach { it.setEnabled(enabled) }
        b.vibrateCheck.isEnabled = enabled
    }

    // ---- Tick loop (drift-free, beeps on whole-second boundaries) ----
    private val ticker = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            val delta = now - lastTickMs
            lastTickMs = now
            phaseRemainingMs -= delta

            val whole = ceil(phaseRemainingMs / 1000.0).toInt().coerceAtLeast(0)
            if (whole != lastWholeSec) {
                lastWholeSec = whole
                // countdown beep during the final beepWindow seconds of the phase
                val p = phases[index]
                if (p.type != Type.DONE && whole in 1..beepWin.value) countdownBeep()
                render()
            }

            if (phaseRemainingMs <= 0) {
                val next = index + 1
                if (next < phases.size) {
                    loadPhase(next, announce = true)
                    if (phases[next].type == Type.DONE) { finishWorkout(); return }
                } else { finishWorkout(); return }
            }
            handler.postDelayed(this, 50)
        }
    }

    private fun finishWorkout() {
        running = false
        handler.removeCallbacks(ticker)
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        index = phases.lastIndex
        b.startPauseBtn.text = "RESTART"
        setSettingsEnabled(true)
        render()
    }

    // ---- Rendering ----
    private fun renderIdle() {
        b.startPauseBtn.text = "START"
        setSettingsEnabled(true)
        render()
    }

    private fun render() {
        val p = phases[index]
        val secs = ceil((phaseRemainingMs.coerceAtLeast(0)) / 1000.0).toInt()
        b.timeText.text = fmt(if (p.type == Type.DONE) 0 else secs)

        val (label, colorRes) = when (p.type) {
            Type.GET_READY -> "GET READY" to R.color.getready
            Type.WORK -> "WORK" to R.color.work
            Type.REST -> "REST" to R.color.rest
            Type.DONE -> "DONE 🎉" to R.color.done
        }
        b.phaseLabel.text = label
        val color = ContextCompat.getColor(this, colorRes)
        b.phaseLabel.setTextColor(color)
        b.timeText.setTextColor(if (p.type == Type.GET_READY) ContextCompat.getColor(this, R.color.text) else color)

        b.roundText.text = if (p.type == Type.GET_READY) "Starting soon…"
            else "Round ${p.roundNo} / ${p.totalRounds}"

        b.totalText.text = "Total left  " + fmt(remainingWorkoutSec())
    }

    private fun remainingWorkoutSec(): Int {
        var ms = phaseRemainingMs.coerceAtLeast(0)
        for (j in (index + 1) until phases.size) ms += phases[j].durationSec * 1000L
        // exclude get-ready lead-in from "workout" total once running through it is fine to include;
        return ceil(ms / 1000.0).toInt()
    }

    private fun fmt(totalSec: Int): String {
        val s = totalSec.coerceAtLeast(0)
        return "%02d:%02d".format(s / 60, s % 60)
    }

    // ---- Sound / haptics ----
    private fun countdownBeep() { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) }
    private fun goTone() { tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 600) }
    private fun restTone() { tone?.startTone(ToneGenerator.TONE_PROP_ACK, 300) }
    private fun finishTone() { tone?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 900) }

    private fun vibrate(pattern: LongArray) {
        if (!b.vibrateCheck.isChecked) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION") v.vibrate(pattern, -1)
        }
    }

    // ---- Stepper helper bound to an included row_setting ----
    private inner class Stepper(
        row: RowSettingBinding,
        title: String,
        var value: Int,
        private val min: Int,
        private val max: Int,
        private val step: Int,
        private val onChange: () -> Unit
    ) {
        private val valueView: TextView = row.valueText
        private val minusBtn = row.minusBtn
        private val plusBtn = row.plusBtn
        init {
            row.label.text = title
            render()
            minusBtn.setOnClickListener { set(value - step) }
            plusBtn.setOnClickListener { set(value + step) }
        }
        private fun set(v: Int) {
            val nv = v.coerceIn(min, max)
            if (nv != value) { value = nv; render(); onChange() }
        }
        private fun render() { valueView.text = value.toString() }
        fun setEnabled(enabled: Boolean) {
            minusBtn.isEnabled = enabled
            plusBtn.isEnabled = enabled
            val a = if (enabled) 1f else 0.4f
            valueView.alpha = a; minusBtn.alpha = a; plusBtn.alpha = a
        }
    }
}
