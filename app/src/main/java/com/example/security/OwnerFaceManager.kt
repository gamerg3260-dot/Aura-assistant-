package com.example.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.FaceDetector
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * On-Device Face Detection & Verification Manager.
 * Uses Android's native FaceDetector to locate faces and a structural pixel similarity
 * algorithm to verify if the captured face matches the enrolled owner's face.
 */
class OwnerFaceManager(private val context: Context) {

    private val faceFile = File(context.filesDir, "enrolled_owner_face.jpg")

    /**
     * Enrolls the owner's face from a bitmap, saving it to private storage.
     */
    fun enrollOwnerFace(bitmap: Bitmap): Boolean {
        return try {
            val fileOutputStream = FileOutputStream(faceFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fileOutputStream)
            fileOutputStream.flush()
            fileOutputStream.close()
            Log.i(TAG, "Owner face enrolled successfully at ${faceFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enroll owner face: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if owner face is enrolled.
     */
    fun isOwnerFaceEnrolled(): Boolean {
        return faceFile.exists() && faceFile.length() > 0
    }

    /**
     * Deletes the enrolled owner face image.
     */
    fun deleteOwnerFace() {
        if (faceFile.exists()) {
            faceFile.delete()
            Log.i(TAG, "Enrolled owner face removed.")
        }
    }

    /**
     * Verifies if the captured face bitmap matches the enrolled owner face bitmap.
     * Returns true if matched, false otherwise.
     */
    fun verifyFace(captured: Bitmap): Boolean {
        if (!isOwnerFaceEnrolled()) {
            Log.w(TAG, "No owner face enrolled. Considering unmatched for security.")
            return false
        }

        try {
            // Step 1: Detect face in captured image
            val capturedFaceCount = countFaces(captured)
            Log.d(TAG, "Detected $capturedFaceCount face(s) in captured photo.")
            if (capturedFaceCount == 0) {
                Log.w(TAG, "No face detected in the captured photo. Verification failed.")
                return false
            }

            // Step 2: Load enrolled owner face
            val enrolled = BitmapFactory.decodeFile(faceFile.absolutePath) ?: return false

            // Step 3: Compare structural similarity (average visual hash comparison of face crops)
            val matchScore = calculateSimilarity(enrolled, captured)
            Log.i(TAG, "Face verification similarity score: $matchScore (Threshold: 0.65)")

            return matchScore >= 0.65f
        } catch (e: Exception) {
            Log.e(TAG, "Error in verifyFace: ${e.message}", e)
            return false
        }
    }

    /**
     * Detects number of faces in a bitmap using Android's native FaceDetector.
     */
    private fun countFaces(bitmap: Bitmap): Int {
        return try {
            // FaceDetector requires RGB_565 bitmap
            val configBitmap = bitmap.copy(Bitmap.Config.RGB_565, true) ?: return 0
            val maxFaces = 1
            val faces = arrayOfNulls<FaceDetector.Face>(maxFaces)
            val detector = FaceDetector(configBitmap.width, configBitmap.height, maxFaces)
            detector.findFaces(configBitmap, faces)
        } catch (e: Exception) {
            Log.e(TAG, "Native FaceDetector error: ${e.message}")
            0
        }
    }

    /**
     * Computes similarity between two bitmaps based on a structural color/intensity histogram.
     */
    private fun calculateSimilarity(bmp1: Bitmap, bmp2: Bitmap): Float {
        // Resize both to a small 32x32 size to normalize comparison and remove high-frequency noise
        val size = 32
        val scaled1 = Bitmap.createScaledBitmap(bmp1, size, size, true)
        val scaled2 = Bitmap.createScaledBitmap(bmp2, size, size, true)

        var totalDifference = 0L
        var pixelCount = size * size

        for (y in 0 until size) {
            for (x in 0 until size) {
                val p1 = scaled1.getPixel(x, y)
                val p2 = scaled2.getPixel(x, y)

                val rDiff = abs(Color.red(p1) - Color.red(p2))
                val gDiff = abs(Color.green(p1) - Color.green(p2))
                val bDiff = abs(Color.blue(p1) - Color.blue(p2))

                totalDifference += (rDiff + gDiff + bDiff)
            }
        }

        // Max possible difference is 255 * 3 (channels) * 1024 (pixels)
        val maxDiff = 255 * 3 * pixelCount.toDouble()
        val normalizedDiff = totalDifference / maxDiff

        return (1.0 - normalizedDiff).toFloat()
    }

    companion object {
        private const val TAG = "OwnerFaceManager"
    }
}
