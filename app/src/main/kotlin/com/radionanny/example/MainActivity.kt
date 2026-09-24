package com.radionanny.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var cameraManager: CameraManager
    private lateinit var cameraId: String
    private lateinit var cameraDevice: CameraDevice
    private lateinit var captureSession: CameraCaptureSession
    private lateinit var textureView: TextureView
    private var isCameraOn = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textureView = findViewById(R.id.textureView)
        btnStart.setOnClickListener { startRadio() }
        btnStop.setOnClickListener { stopRadio() }
        btnCamera.setOnClickListener { toggleCamera() }
        btnSend.setOnClickListener { sendViaTelegram() }

        cameraManager = getSystemService(CAMERA_SERVICE) as CameraManager
        
        // Check permissions
        checkPermissions()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                    1001
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Разрешения на камеру отказано", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startRadio() {
        // Start radio streaming (existing functionality)
        sendTelegramMessage("📻 Radio nanny started")
        // TODO: Start foreground service
    }

    private fun stopRadio() {
        sendTelegramMessage("⏹️ Radio nanny stopped")
        // Stop radio streaming
    }

    private fun toggleCamera() {
        if (isCameraOn) {
            stopCamera()
        } else {
            startCamera()
        }
    }

    private fun startCamera() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            Toast.makeText(this, "Camera requires API 21+", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cameras = cameraManager.getCameraIdList()
            if (cameras.length == 0) {
                Toast.makeText(this, "No cameras found", Toast.LENGTH_SHORT).show()
                return
            }

            cameraId = cameras[0]
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.INFO_AVAILABLE_STREAM_CONFIGURATION_MAP) as StreamConfigurationMap

            // Start camera preview on texture view
            textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                    startCameraPreview(surfaceTexture, width, height)
                }

                override fun onSurfaceTextureSizeChanged(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
                    // Update camera preview size
                }

                override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture) {
                    stopCamera()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Camera error: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("CameraTag", e.message, e)
        }
    }

    private fun startCameraPreview(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        try {
            cameraManager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    startPreview(width, height)
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Toast.makeText(MainActivity.this, "Camera error: ${error}", Toast.LENGTH_SHORT).show()
                    cameraManager.closeCamera(cameraDevice)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    cameraDevice.close()
                }
            }, null)
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun startPreview(width: Int, height: Int) {
        try {
            val surface = Surface(textureView.surfaceTexture)
            cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    .addTarget(surface)
                    .apply {
                        cameraDevice.createCaptureSession(arrayOf(surface)) {
                            it -> captureSession = it
                            textureView.post { /* Preview started */ }
                        }
                    }
            isCameraOn = true
            btnCamera.text = "Остановить камеру"
            sendTelegramMessage("📸 Камера включена")
        } catch (e: CameraAccessException) {
            e.printStackTrace()
        }
    }

    private fun stopCamera() {
        try {
            if (captureSession != null) captureSession.stopRepeating()
            if (cameraDevice != null) cameraDevice.close()
            if (textureView.surfaceTexture != null) textureView.surfaceTexture = null
            isCameraOn = false
            btnCamera.text = "Включить камеру"
            sendTelegramMessage("📷 Камера выключена")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sendViaTelegram() {
        val message = "Radio Nanny status: ${if (isCameraOn) "Camera ON" else "Radio OFF"}"

        val client = OkHttpClient()
        val botToken = "8460199942:AAFlF6U-l56EktYXUF3_EIDpeQoDnVk0h70"
        val chatId = "6433663595"
        val url = "https://api.telegram.org/bot$botToken/sendMessage"

        val json = JSONObject().apply {
            put("chat_id", chatId)
            put("text", message)
        }

        val request = Request.Builder().url(url).post(json.toString()).build()
        client.newCall(request).execute()

        sendTelegramMessage("📤 Статус отправлен в Telegram")
    }

    private fun sendTelegramMessage(message: String) {
        lifecycleScope.launch {
            val client = OkHttpClient()
            val botToken = "8460199942:AAFlF6U-l56EktYXUF3_EIDpeQoDnVk0h70"
            val chatId = "6433663595"
            val url = "https://api.telegram.org/bot$botToken/sendMessage"

            val json = JSONObject().apply {
                put("chat_id", chatId)
                put("text", message)
            }

            val request = Request.Builder().url(url).post(json.toString()).build()
            client.newCall(request).execute()
        }
    }
}