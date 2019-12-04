package com.example.cameraxdemo.activity

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Matrix
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.DisplayMetrics
import android.util.Log
import android.util.Rational
import android.util.Size
import android.view.*
import android.widget.Toast
import androidx.camera.core.*
import com.example.cameraxdemo.R
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), Executor {
    private val executor = Executors.newSingleThreadExecutor()
    private var mOrientation: Int = 0
    private var isFlashOn = false
    private var isFrontCamera = false
    var flashMode: FlashMode = FlashMode.OFF
    var lensFacing = CameraX.LensFacing.BACK

    enum class CameraOption { Picture, Video }

    var currentCameraMode = CameraOption.Picture

    var isVideoRecordingInProgress = false

    private lateinit var imageCapture: ImageCapture
    private lateinit var videoCapture: VideoCapture


    companion object {
        const val MEDIA_PATH = "media_path"
        const val MEDIA_TYPE = "media_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        setContentView(R.layout.activity_main)

//        CommonUtils.hideSystemUI(this)

        view_finder.post {
            bindCamera()
            setListener()
        }

        view_finder.addOnLayoutChangeListener { view, i, i2, i3, i4, i5, i6, i7, i8 ->
            updateTransform()
        }

//        view_camera.bindToLifecycle(this)
//
//        setListener()
//
//        setCameraProperties()
    }

//    private fun setCameraProperties() {
//        view_camera.flash = FlashMode.AUTO
//    }

    @SuppressLint("RestrictedApi")
    private fun bindCamera() {
        CameraX.unbindAll()

        val analyzerConfig = ImageAnalysisConfig.Builder().apply {
            setImageReaderMode(
                ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE
            )
        }.build()

        val analyzerUseCase = ImageAnalysis(analyzerConfig).apply {
            setAnalyzer(executor, LuminosityAnalyzer())
        }

        val metrics = DisplayMetrics().also { view_finder.display.getRealMetrics(it) }

        val aspectRatio = Rational(metrics.widthPixels, metrics.heightPixels)
        val rotation = view_finder.display.rotation
        val resolution = Size(metrics.widthPixels, metrics.heightPixels)

        val previewConfig = PreviewConfig.Builder().setLensFacing(lensFacing).build()

        val preview = Preview(previewConfig)

        preview.setOnPreviewOutputUpdateListener {
            val parent = view_finder.parent as ViewGroup
            parent.removeView(view_finder)
            view_finder.surfaceTexture = it.surfaceTexture
            parent.addView(view_finder, 0)
            updateTransform()
        }

        val imageCaptureConfig =
            ImageCaptureConfig.Builder().setLensFacing(lensFacing).setFlashMode(flashMode).build()

        val videoCaptureConfig =
            VideoCaptureConfig.Builder().setLensFacing(lensFacing).build()

        imageCapture = ImageCapture(imageCaptureConfig)
        videoCapture = VideoCapture(videoCaptureConfig)

        if (currentCameraMode == CameraOption.Picture) {
            CameraX.bindToLifecycle(
                this, preview, imageCapture
            )
        } else {
            CameraX.bindToLifecycle(
                this, preview, videoCapture
            )
        }

    }

    private fun updateTransform() {
        val matrix = Matrix()

        // Compute the center of the view finder
        val centerX = view_finder.width / 2f
        val centerY = view_finder.height / 2f

        // Correct preview output to account for display rotation
        val rotationDegrees = when (view_finder.display.rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> return
        }
        matrix.postRotate(-rotationDegrees.toFloat(), centerX, centerY)

        // Finally, apply transformations to our TextureView
        view_finder.setTransform(matrix)
    }

    private fun setListener() {
        ibCaptureImage.setOnClickListener {
            if (currentCameraMode == CameraOption.Picture) {
                bindCamera()
                clickPicture()
            }
//
            else if (currentCameraMode == CameraOption.Video) {

                if (isVideoRecordingInProgress) {
                    stopVideoRecording()
                } else {
                    bindCamera()
                    startVideoRecording()
                }

            }
        }
//
//        ibCaptureImage.setOnTouchListener { view, motionEvent ->
//            if (motionEvent.action == MotionEvent.ACTION_DOWN) {
////                bindCamera()
//                startVideoRecording()
//            } else if (motionEvent.action == MotionEvent.ACTION_UP) {
//                stopVideoRecording()
//            }
//            false
//        }

        ibFlash.setOnClickListener {
            isFlashOn = !isFlashOn

            if (isFlashOn) {
                ibFlash.setImageDrawable(getDrawable(R.drawable.icn_flash_on))
                flashMode = FlashMode.ON

                imageCapture.flashMode = flashMode
            } else {
                ibFlash.setImageDrawable(getDrawable(R.drawable.icn_flash_off))
                flashMode = FlashMode.OFF

                imageCapture.flashMode = flashMode
            }
        }

        ibFrontCamera.setOnClickListener {
            //            isFrontCamera = !isFrontCamera

            lensFacing = if (CameraX.LensFacing.BACK == lensFacing) {
                CameraX.LensFacing.FRONT
            } else {
                CameraX.LensFacing.BACK
            }

            try {
                // Only bind use cases if we can query a camera with this orientation
                CameraX.getCameraWithLensFacing(lensFacing)
                bindCamera()
            } catch (exc: Exception) {
                // Do nothing
            }
        }

        ivCameraOptionVideo.setOnClickListener {
            currentCameraMode = CameraOption.Video
            ivCameraOptionVideo.setBackgroundColor(resources.getColor(R.color.selectedMediaBackgroundColor))
            ivCameraOptionImage.setBackgroundColor(resources.getColor(R.color.unSelectedMediaBackgroundColor))
        }

        ivCameraOptionImage.setOnClickListener {
            currentCameraMode = CameraOption.Picture
            ivCameraOptionImage.setBackgroundColor(resources.getColor(R.color.selectedMediaBackgroundColor))
            ivCameraOptionVideo.setBackgroundColor(resources.getColor(R.color.unSelectedMediaBackgroundColor))
        }
    }

    private fun clickPicture() {
        val file = File(
            externalMediaDirs.first(),
            "${System.currentTimeMillis()}.jpg"
        )

        imageCapture.takePicture(file, executor,
            object : ImageCapture.OnImageSavedListener {
                override fun onError(
                    imageCaptureError: ImageCapture.ImageCaptureError,
                    message: String,
                    exc: Throwable?
                ) {
                    val msg = "Photo capture failed: $message"
                    Log.e("CameraXApp", msg, exc)
                    view_finder.post {
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onImageSaved(file: File) {
                    val msg = "Photo capture succeeded: ${file.absolutePath}"
                    Log.d("CameraXApp", msg)
                    view_finder.post {
                        Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@MainActivity, ImagePreviewActivity::class.java)
                        intent.putExtra(MEDIA_PATH, file.absolutePath)
                        intent.putExtra(MEDIA_TYPE, "image")
                        startActivity(intent)
                    }
                }
            })
    }

    private fun startVideoRecording() {
        isVideoRecordingInProgress = true
        val file = File(
            externalMediaDirs.first(),
            "${System.currentTimeMillis()}.mp4"
        )

        ibCaptureImage.setImageDrawable(getDrawable(R.drawable.ic_stop_video))

        videoCapture.startRecording(file, executor, object : VideoCapture.OnVideoSavedListener {
            override fun onVideoSaved(file: File) {
                val intent = Intent(this@MainActivity, ImagePreviewActivity::class.java)
                intent.putExtra(MEDIA_PATH, file.absolutePath)
                intent.putExtra(MEDIA_TYPE, "video")
                startActivity(intent)
            }

            override fun onError(
                videoCaptureError: VideoCapture.VideoCaptureError,
                message: String,
                cause: Throwable?
            ) {
                Log.d("Vidhi", message)
            }
        })
    }

    private fun stopVideoRecording() {
        isVideoRecordingInProgress = false
        ibCaptureImage.setImageDrawable(getDrawable(R.drawable.btn_capture))
        videoCapture.stopRecording()
    }

    override fun onResume() {
        val orientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                when (orientation) {
                    0, 180, 360 -> {
                        mOrientation = orientation
                        ibCaptureImage.rotation = 0f
                    }

                    90 -> {
                        mOrientation = orientation
                        ibCaptureImage.rotation = 270f
                    }

                    270 -> {
                        mOrientation = orientation
                        ibCaptureImage.rotation = 90f
                    }
                }
            }
        }

        orientationEventListener.enable()
        super.onResume()
    }


    override fun execute(p0: Runnable) {
        p0.run()
    }

    override fun onDestroy() {
        super.onDestroy()
        CameraX.unbindAll()
    }
}

private class LuminosityAnalyzer : ImageAnalysis.Analyzer {
    private var lastAnalyzedTimestamp = 0L

    /**
     * Helper extension function used to extract a byte array from an
     * image plane buffer
     */
    private fun ByteBuffer.toByteArray(): ByteArray {
        rewind()    // Rewind the buffer to zero
        val data = ByteArray(remaining())
        get(data)   // Copy the buffer into a byte array
        return data // Return the byte array
    }

    override fun analyze(image: ImageProxy, rotationDegrees: Int) {
        val currentTimestamp = System.currentTimeMillis()
        // Calculate the average luma no more often than every second
        if (currentTimestamp - lastAnalyzedTimestamp >=
            TimeUnit.SECONDS.toMillis(1)
        ) {
            // Since format in ImageAnalysis is YUV, image.planes[0]
            // contains the Y (luminance) plane
            val buffer = image.planes[0].buffer
            // Extract image data from callback object
            val data = buffer.toByteArray()
            // Convert the data into an array of pixel values
            val pixels = data.map { it.toInt() and 0xFF }
            // Compute average luminance for the image
            val luma = pixels.average()
            // Log the new luma value
            Log.d("CameraXApp", "Average luminosity: $luma")
            // Update timestamp of last analyzed frame
            lastAnalyzedTimestamp = currentTimestamp
        }
    }
}
