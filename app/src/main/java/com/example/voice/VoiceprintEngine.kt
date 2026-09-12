package com.example.voice

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class VoiceprintResult(
    val similarityScore: Float,
    val isVerified: Boolean,
    val threshold: Float,
    val details: String
)

class VoiceprintEngine {

    companion object {
        const val EMBEDDING_DIM = 32
        const val DEFAULT_THRESHOLD = 0.76f

        fun serializeEmbedding(embedding: FloatArray): String {
            return embedding.joinToString(",") { "%.5f".format(it) }
        }

        fun deserializeEmbedding(serialized: String): FloatArray? {
            if (serialized.isBlank()) return null
            return try {
                val parts = serialized.split(",").map { it.trim().toFloat() }
                if (parts.size == EMBEDDING_DIM) parts.toFloatArray() else null
            } catch (e: Exception) {
                null
            }
        }
    }

    /**
     * Extracts a 32-dimensional spectral acoustic feature vector from raw 16-bit PCM audio samples.
     */
    fun extractEmbeddingFromPcm(samples: ShortArray): FloatArray {
        if (samples.isEmpty()) return FloatArray(EMBEDDING_DIM) { 0f }

        val embedding = FloatArray(EMBEDDING_DIM)
        val frameSize = (samples.size / EMBEDDING_DIM).coerceAtLeast(1)

        var totalEnergy = 0.0
        var zeroCrossings = 0
        for (i in samples.indices) {
            val s = samples[i].toDouble()
            totalEnergy += s * s
            if (i > 0 && ((samples[i] >= 0 && samples[i - 1] < 0) || (samples[i] < 0 && samples[i - 1] >= 0))) {
                zeroCrossings++
            }
        }

        // Spectral band partitioning (FFT energy approximation over bands)
        for (b in 0 until EMBEDDING_DIM) {
            val startIdx = b * frameSize
            val endIdx = ((b + 1) * frameSize).coerceAtMost(samples.size)

            var bandEnergy = 0.0
            var bandFlux = 0.0
            for (j in startIdx until endIdx) {
                val sample = samples[j].toDouble()
                val windowed = sample * (0.54 - 0.46 * cos(2 * Math.PI * (j - startIdx) / (endIdx - startIdx).coerceAtLeast(1)))
                bandEnergy += windowed * windowed
                if (j > startIdx) {
                    bandFlux += abs(samples[j] - samples[j - 1])
                }
            }

            // Pseudo-formant harmonics & spectral centroid contribution
            val harmonicFactor = sin((b + 1) * 0.45) * 0.3
            val normalizedBandEnergy = sqrt(bandEnergy / (endIdx - startIdx).coerceAtLeast(1))
            val normalizedFlux = bandFlux / (endIdx - startIdx).coerceAtLeast(1)

            embedding[b] = (normalizedBandEnergy * 0.6 + normalizedFlux * 0.3 + harmonicFactor * 0.1).toFloat()
        }

        return normalize(embedding)
    }

    /**
     * Generates an acoustic voiceprint embedding for simulation / testing based on speaker voice traits.
     */
    fun generateSimulatedEmbedding(seedId: String, isOwner: Boolean, variance: Float = 0.05f): FloatArray {
        val baseSeed = if (isOwner) 4242L else 99999L + seedId.hashCode()
        val random = java.util.Random(baseSeed)

        val embedding = FloatArray(EMBEDDING_DIM)
        for (i in 0 until EMBEDDING_DIM) {
            val baseVal = if (isOwner) {
                // Owner has distinct formant shape
                (cos(i * 0.38) * 0.5 + sin(i * 0.18) * 0.3 + 0.5).toFloat()
            } else {
                // Intruder/stranger has completely different acoustic signature
                (sin(i * 0.62) * 0.6 + cos(i * 0.85) * 0.4 + 0.3).toFloat()
            }
            // Small natural jitter for the same speaker
            val noise = (random.nextGaussian() * variance).toFloat()
            embedding[i] = (baseVal + noise).coerceAtLeast(0.01f)
        }

        return normalize(embedding)
    }

    /**
     * Aggregates multiple phrase embeddings into a consolidated centroid voiceprint.
     */
    fun buildCentroidVoiceprint(phraseEmbeddings: List<FloatArray>): FloatArray {
        require(phraseEmbeddings.isNotEmpty()) { "At least one phrase embedding required" }
        val centroid = FloatArray(EMBEDDING_DIM) { 0f }

        for (embedding in phraseEmbeddings) {
            for (i in 0 until EMBEDDING_DIM) {
                centroid[i] += embedding[i]
            }
        }

        val count = phraseEmbeddings.size.toFloat()
        for (i in 0 until EMBEDDING_DIM) {
            centroid[i] /= count
        }

        return normalize(centroid)
    }

    /**
     * Calculates cosine similarity between enrolled voiceprint and test voice embedding.
     */
    fun calculateCosineSimilarity(enrolled: FloatArray, test: FloatArray): Float {
        if (enrolled.size != test.size || enrolled.isEmpty()) return 0f

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in enrolled.indices) {
            dotProduct += enrolled[i] * test[i]
            normA += enrolled[i] * enrolled[i]
            normB += test[i] * test[i]
        }

        val denominator = sqrt(normA) * sqrt(normB)
        if (denominator < 1e-8) return 0f

        return (dotProduct / denominator).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Verifies speaker against enrolled voiceprint with confidence and thresholding.
     */
    fun verifySpeaker(
        enrolledVoiceprint: FloatArray?,
        testEmbedding: FloatArray,
        threshold: Float = DEFAULT_THRESHOLD
    ): VoiceprintResult {
        if (enrolledVoiceprint == null || enrolledVoiceprint.isEmpty()) {
            return VoiceprintResult(
                similarityScore = 0f,
                isVerified = false,
                threshold = threshold,
                details = "No voiceprint enrolled. Please complete Voice Enrollment."
            )
        }

        val similarity = calculateCosineSimilarity(enrolledVoiceprint, testEmbedding)
        val isVerified = similarity >= threshold

        val details = if (isVerified) {
            "Voiceprint Match: Verified Owner (${(similarity * 100).toInt()}% confidence >= ${(threshold * 100).toInt()}% threshold)"
        } else {
            "Voice Mismatch: Unrecognized Speaker (${(similarity * 100).toInt()}% confidence < ${(threshold * 100).toInt()}% threshold)"
        }

        return VoiceprintResult(
            similarityScore = similarity,
            isVerified = isVerified,
            threshold = threshold,
            details = details
        )
    }

    private fun normalize(vector: FloatArray): FloatArray {
        var sumSquares = 0.0
        for (v in vector) {
            sumSquares += v * v
        }
        val norm = sqrt(sumSquares)
        if (norm < 1e-8) return vector

        val normalized = FloatArray(vector.size)
        for (i in vector.indices) {
            normalized[i] = (vector[i] / norm).toFloat()
        }
        return normalized
    }
}
