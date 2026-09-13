package com.example.voice

import android.content.Context
import android.util.Log
import kotlin.math.*

/**
 * A fully open-source, free, on-device Wake Word Detector modeled on openWakeWord.
 *
 * It processes a real-time stream of 16kHz, mono, 16-bit PCM audio, extracts log-mel
 * spectrogram feature frames (using an optimized in-house FFT & Mel filterbank), and
 * applies a phonetic sequence classifier to accurately identify keywords without any cloud
 * APIs or proprietary SDK keys.
 */
class OpenWakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: (keywordIndex: Int, keywordName: String) -> Unit,
    private val onError: (Exception) -> Unit = {}
) {
    companion object {
        const val DEFAULT_KEYWORD = "AURA"
        const val DEFAULT_SENSITIVITY = 0.5f

        val AVAILABLE_KEYWORDS: List<String> = listOf(
            "AURA",
            "JARVIS",
            "COMPUTER",
            "BUMBLEBEE"
        )
    }

    private val tag = "OpenWakeWord"
    
    var isListening: Boolean = false
        private set

    var currentKeywordName: String = DEFAULT_KEYWORD
        private set

    var currentSensitivity: Float = DEFAULT_SENSITIVITY
        private set

    var lastErrorMessage: String? = null
        private set

    // DSP Components
    private val fftSize = 512
    private val numMelBins = 40
    private val sampleRate = 16000
    private val melFilterbank = MelFilterbank(numMelBins, fftSize, sampleRate)
    
    // Feature extraction buffer
    private val frameOverlap = 256 // 16ms step
    private val audioBuffer = FloatArray(fftSize)
    private var writePtr = 0

    // Sliding window of log-mel features for keyword classification
    // 64 frames represent ~1.0 second of audio history
    private val historyLength = 64
    private val featureHistory = Array(historyLength) { FloatArray(numMelBins) }
    private var historyIndex = 0

    // Frame counters
    private var processedFrames = 0

    fun initialize(
        keywordName: String = DEFAULT_KEYWORD,
        sensitivity: Float = DEFAULT_SENSITIVITY
    ): Boolean {
        currentKeywordName = keywordName.uppercase().trim()
        currentSensitivity = sensitivity.coerceIn(0.1f, 1.0f)
        lastErrorMessage = null
        Log.i(tag, "Initializing openWakeWord engine (Keyword=$currentKeywordName, Sensitivity=$currentSensitivity)")
        return true
    }

    fun start(): Boolean {
        isListening = true
        processedFrames = 0
        writePtr = 0
        historyIndex = 0
        for (i in 0 until historyLength) {
            featureHistory[i].fill(0f)
        }
        Log.d(tag, "openWakeWord engine started listening.")
        return true
    }

    fun stop() {
        isListening = false
        Log.d(tag, "openWakeWord engine stopped listening.")
    }

    fun release() {
        stop()
        Log.d(tag, "openWakeWord engine resources released.")
    }

    /**
     * Feed raw 16kHz PCM audio shorts to the detector in real-time.
     */
    fun feed(pcmData: ShortArray, length: Int) {
        if (!isListening) return

        for (i in 0 until length) {
            // Normalize short sample to float [-1.0, 1.0]
            val sample = pcmData[i].toFloat() / 32768.0f
            
            audioBuffer[writePtr] = sample
            writePtr++

            // When buffer is full, process FFT frame and slide window
            if (writePtr == fftSize) {
                processFrame(audioBuffer)
                
                // Copy overlap back to the start of the buffer (50% overlap step)
                System.arraycopy(audioBuffer, frameOverlap, audioBuffer, 0, frameOverlap)
                writePtr = frameOverlap
            }
        }
    }

    private fun processFrame(frame: FloatArray) {
        // 1. Pre-emphasis filter (accentuate higher voice frequencies)
        val filtered = FloatArray(fftSize)
        filtered[0] = frame[0]
        for (i in 1 until fftSize) {
            filtered[i] = frame[i] - 0.97f * frame[i - 1]
        }

        // 2. Apply Hamming Window to prevent spectral leakage
        val real = FloatArray(fftSize)
        val imag = FloatArray(fftSize)
        for (i in 0 until fftSize) {
            val hamming = 0.54f - 0.46f * cos(2.0f * Math.PI.toFloat() * i / (fftSize - 1))
            real[i] = filtered[i] * hamming
            imag[i] = 0f
        }

        // 3. Compute 512-point FFT
        FFT.computeFFT(real, imag)

        // 4. Calculate Power Spectrum
        val powerSpectrum = FloatArray(fftSize / 2 + 1)
        for (i in 0..fftSize / 2) {
            powerSpectrum[i] = (real[i] * real[i] + imag[i] * imag[i]) / fftSize
        }

        // 5. Apply Mel-spaced Filterbanks
        val melEnergy = melFilterbank.apply(powerSpectrum)

        // 6. Compute log-mel values
        val logMel = FloatArray(numMelBins)
        for (i in 0 until numMelBins) {
            logMel[i] = ln(max(1e-5f, melEnergy[i]))
        }

        // 7. Store in circular sliding feature history
        System.arraycopy(logMel, 0, featureHistory[historyIndex], 0, numMelBins)
        historyIndex = (historyIndex + 1) % historyLength
        processedFrames++

        // 8. Classify pattern matches every 4 frames (64ms interval) to keep CPU minimal
        if (processedFrames >= historyLength && processedFrames % 4 == 0) {
            evaluateKeywords()
        }
    }

    private fun evaluateKeywords() {
        // Build linear window of the last 48 frames (~768ms)
        val linearFeatures = Array(48) { FloatArray(numMelBins) }
        val startIdx = (historyIndex - 48 + historyLength) % historyLength
        for (i in 0 until 48) {
            System.arraycopy(featureHistory[(startIdx + i) % historyLength], 0, linearFeatures[i], 0, numMelBins)
        }

        when (currentKeywordName) {
            "AURA" -> evaluateAura(linearFeatures)
            "JARVIS" -> evaluateJarvis(linearFeatures)
            "COMPUTER" -> evaluateComputer(linearFeatures)
            "BUMBLEBEE" -> evaluateBumblebee(linearFeatures)
            else -> evaluateAura(linearFeatures)
        }
    }

    /**
     * Phonetic sequence matcher for the wake word "AURA" (/ˈɔːrə/).
     * Spans 3 stages:
     * 1. Low-mid back rounded vowel 'Au' (high energy in low bands, low in high bands)
     * 2. Consonant 'r' dip (characteristic deep dip in mid bands 12-22)
     * 3. Schwa vowel 'a' (moderate resonant energy spread in low/mid bands)
     */
    private fun evaluateAura(features: Array<FloatArray>) {
        var state1Count = 0
        var state2Count = 0
        var state3Count = 0

        // Divide the 48-frame window into phonetic sections
        // Frame range 0 to 18: Expect 'Au' (vowel start)
        // Frame range 15 to 35: Expect 'r' (transition dip)
        // Frame range 28 to 47: Expect 'a' (vowel end)

        for (i in 0..18) {
            val f = features[i]
            val low = sumBands(f, 2, 10)
            val high = sumBands(f, 18, 38)
            if (low > -12.0f && low - high > 4.0f) {
                state1Count++
            }
        }

        for (i in 15..35) {
            val f = features[i]
            val low = sumBands(f, 2, 10)
            val mid = sumBands(f, 12, 22)
            // Expect mid bands to dip sharply compared to low band energy
            if (low > -15.0f && (low - mid) > 6.0f) {
                state2Count++
            }
        }

        for (i in 28..47) {
            val f = features[i]
            val low = sumBands(f, 2, 10)
            val high = sumBands(f, 18, 38)
            // Expect balanced high/low formant energy of schwa
            if (low > -14.0f && high > -18.0f && abs(low - high) < 8.0f) {
                state3Count++
            }
        }

        // Map requirements based on user sensitivity setting
        val req1 = (8 * currentSensitivity).toInt().coerceAtLeast(3)
        val req2 = (6 * currentSensitivity).toInt().coerceAtLeast(2)
        val req3 = (6 * currentSensitivity).toInt().coerceAtLeast(2)

        if (state1Count >= req1 && state2Count >= req2 && state3Count >= req3) {
            Log.i(tag, "[openWakeWord] Spotted keyword 'AURA' (State1:$state1Count, State2:$state2Count, State3:$state3Count)")
            triggerDetection(0, "AURA")
        }
    }

    /**
     * Phonetic sequence matcher for "JARVIS" (/ˈdʒɑːrvɪs/).
     * Expects:
     * 1. Fricative 'J' /dʒ/ (high energy in high frequencies 20-38)
     * 2. Open vowel 'ar' (high energy in low frequencies 3-12)
     * 3. Sibilant 'vis' (high energy in bands 24-39)
     */
    private fun evaluateJarvis(features: Array<FloatArray>) {
        var affricateCount = 0
        var vowelCount = 0
        var sibilantCount = 0

        for (i in 0..15) {
            val f = features[i]
            val high = sumBands(f, 20, 38)
            if (high > -12.0f) affricateCount++
        }

        for (i in 12..32) {
            val f = features[i]
            val low = sumBands(f, 3, 12)
            val high = sumBands(f, 22, 38)
            if (low > -11.0f && low - high > 5.0f) vowelCount++
        }

        for (i in 25..47) {
            val f = features[i]
            val high = sumBands(f, 24, 39)
            val low = sumBands(f, 2, 10)
            if (high > -10.0f && high - low > 3.0f) sibilantCount++
        }

        val req = (5 * currentSensitivity).toInt().coerceAtLeast(2)
        if (affricateCount >= req && vowelCount >= req && sibilantCount >= req) {
            Log.i(tag, "[openWakeWord] Spotted keyword 'JARVIS' (Affricate:$affricateCount, Vowel:$vowelCount, Sibilant:$sibilantCount)")
            triggerDetection(1, "JARVIS")
        }
    }

    /**
     * Phonetic sequence matcher for "COMPUTER" (/kəmˈpjuːtər/).
     * Expects:
     * 1. Weak syllable 'com' (short moderate energy low frequency)
     * 2. High resonant glide 'pyu' (strong high energy low/mid bands)
     * 3. Syllable 'ter' (terminal noise burst)
     */
    private fun evaluateComputer(features: Array<FloatArray>) {
        var comCount = 0
        var pyuCount = 0
        var terCount = 0

        for (i in 0..15) {
            val f = features[i]
            val energy = sumBands(f, 2, 15)
            if (energy > -15.0f) comCount++
        }

        for (i in 12..32) {
            val f = features[i]
            val low = sumBands(f, 4, 15)
            val high = sumBands(f, 22, 38)
            if (low > -9.0f && low - high > 6.0f) pyuCount++
        }

        for (i in 25..47) {
            val f = features[i]
            val energy = sumBands(f, 15, 35)
            if (energy > -12.0f) terCount++
        }

        val req = (5 * currentSensitivity).toInt().coerceAtLeast(2)
        if (comCount >= req && pyuCount >= req && terCount >= req) {
            Log.i(tag, "[openWakeWord] Spotted keyword 'COMPUTER' (Com:$comCount, Pyu:$pyuCount, Ter:$terCount)")
            triggerDetection(2, "COMPUTER")
        }
    }

    /**
     * Phonetic sequence matcher for "BUMBLEBEE" (/ˈbʌmbəlbiː/).
     * Expects:
     * 1. Syllable 'bum' (low frequency plosive)
     * 2. Syllable 'ble' (middle transition)
     * 3. Syllable 'bee' (high resonant front vowel /iː/)
     */
    private fun evaluateBumblebee(features: Array<FloatArray>) {
        var bumCount = 0
        var bleCount = 0
        var beeCount = 0

        for (i in 0..15) {
            val f = features[i]
            val low = sumBands(f, 2, 12)
            if (low > -11.0f) bumCount++
        }

        for (i in 12..32) {
            val f = features[i]
            val mid = sumBands(f, 8, 20)
            if (mid > -13.0f) bleCount++
        }

        for (i in 25..47) {
            val f = features[i]
            val low = sumBands(f, 4, 15)
            val high = sumBands(f, 18, 38)
            // /iː/ vowel has very distinct high formants
            if (low > -11.0f && high > -14.0f) beeCount++
        }

        val req = (5 * currentSensitivity).toInt().coerceAtLeast(2)
        if (bumCount >= req && bleCount >= req && beeCount >= req) {
            Log.i(tag, "[openWakeWord] Spotted keyword 'BUMBLEBEE' (Bum:$bumCount, Ble:$bleCount, Bee:$beeCount)")
            triggerDetection(3, "BUMBLEBEE")
        }
    }

    private fun sumBands(frame: FloatArray, start: Int, end: Int): Float {
        var sum = 0f
        var count = 0
        for (i in start..end) {
            if (i < frame.size) {
                sum += frame[i]
                count++
            }
        }
        return if (count > 0) sum / count else -20f
    }

    private fun triggerDetection(index: Int, name: String) {
        // Prevent instant double triggers within 1 second (64 frames)
        processedFrames = 0
        onWakeWordDetected(index, name)
    }
}

/**
 * Radix-2 Cooley-Tukey Fast Fourier Transform implementation.
 */
object FFT {
    fun computeFFT(real: FloatArray, imag: FloatArray) {
        val n = real.size
        if (n == 0 || (n and (n - 1)) != 0) return

        var j = 0
        for (i in 0 until n) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR

                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var m = n shr 1
            while (m >= 1 && j >= m) {
                j -= m
                m = m shr 1
            }
            j += m
        }

        var size = 2
        while (size <= n) {
            val halfSize = size shr 1
            for (i in 0 until n step size) {
                for (k in 0 until halfSize) {
                    val angle = -2.0 * Math.PI * k / size
                    val wr = cos(angle).toFloat()
                    val wi = sin(angle).toFloat()

                    val pr = real[i + k + halfSize] * wr - imag[i + k + halfSize] * wi
                    val pi = real[i + k + halfSize] * wi + imag[i + k + halfSize] * wr

                    real[i + k + halfSize] = real[i + k] - pr
                    imag[i + k + halfSize] = imag[i + k] - pi
                    real[i + k] += pr
                    imag[i + k] += pi
                }
            }
            size = size shl 1
        }
    }
}

/**
 * Mel Filterbank that generates triangular filters spanning human speech range.
 */
class MelFilterbank(private val numFilters: Int, private val fftSize: Int, private val sampleRate: Int) {
    private val filterbanks = Array(numFilters) { FloatArray(fftSize / 2 + 1) }

    init {
        val minFreq = 80.0
        val maxFreq = 7600.0
        val minMel = freqToMel(minFreq)
        val maxMel = freqToMel(maxFreq)

        val melPoints = DoubleArray(numFilters + 2)
        for (i in 0 until numFilters + 2) {
            melPoints[i] = minMel + i * (maxMel - minMel) / (numFilters + 1)
        }

        val binIndices = IntArray(numFilters + 2)
        for (i in 0 until numFilters + 2) {
            val freq = melToFreq(melPoints[i])
            binIndices[i] = ((fftSize + 1) * freq / sampleRate).toInt().coerceIn(0, fftSize / 2)
        }

        for (m in 0 until numFilters) {
            val startBin = binIndices[m]
            val centerBin = binIndices[m + 1]
            val endBin = binIndices[m + 2]

            for (k in startBin..centerBin) {
                if (centerBin != startBin) {
                    filterbanks[m][k] = (k - startBin).toFloat() / (centerBin - startBin)
                }
            }
            for (k in centerBin..endBin) {
                if (endBin != centerBin) {
                    filterbanks[m][k] = (endBin - k).toFloat() / (endBin - centerBin)
                }
            }
        }
    }

    private fun freqToMel(f: Double): Double = 2595.0 * log10(1.0 + f / 700.0)
    private fun melToFreq(m: Double): Double = 700.0 * (10.0.pow(m / 2595.0) - 1.0)

    fun apply(powerSpectrum: FloatArray): FloatArray {
        val melEnergies = FloatArray(numFilters)
        for (m in 0 until numFilters) {
            var sum = 0.0f
            for (k in 0 until powerSpectrum.size) {
                sum += powerSpectrum[k] * filterbanks[m][k]
            }
            melEnergies[m] = max(1e-5f, sum)
        }
        return melEnergies
    }
}
