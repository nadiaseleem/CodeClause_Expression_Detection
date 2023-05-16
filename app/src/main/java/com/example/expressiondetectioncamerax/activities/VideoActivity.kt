package com.example.expressiondetectioncamerax.activities

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.expressiondetectioncamerax.R
import com.example.expressiondetectioncamerax.databinding.ActivityMainVideoBinding
import com.example.expressiondetectioncamerax.helper.graphicOverlay.RectOverlay
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.common.FirebaseVisionImageMetadata
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.hsalf.smileyrating.SmileyRating
import com.hsalf.smileyrating.helper.SmileyActiveIndicator
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors


@ExperimentalGetImage
class VideoActivity : AppCompatActivity() {
    lateinit var binding: ActivityMainVideoBinding
    var cameraFacing = CameraSelector.LENS_FACING_BACK
    private lateinit var detector: FirebaseVisionFaceDetector
    private var cameraPermissionGranted = false
    private var writePermissionGranted = false
    private lateinit var imageCapture: ImageCapture
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var smileyActiveIndicator: SmileyActiveIndicator
    private var old = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        smileyActiveIndicator = SmileyActiveIndicator()
        binding = ActivityMainVideoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        permissionsLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                cameraPermissionGranted =
                    permissions[Manifest.permission.CAMERA] ?: cameraPermissionGranted
                writePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE]
                    ?: writePermissionGranted

            }

        updateOrRequestPermissions()
        flipCamera()
        getImageFromGalleryClicked()
        binding.eyeStatus.setVisibility(View.INVISIBLE)
        setupSmilyRating()
    }



    private fun setupSmilyRating() {
        binding.smileRating.disallowSelection(true)
        binding.smileRating.setTitle(SmileyRating.Type.GREAT, "Awesome");
        binding.smileRating.setFaceColor(SmileyRating.Type.GREAT, Color.BLACK);
        binding.smileRating.setFaceBackgroundColor(
            SmileyRating.Type.GREAT,
            Color.rgb(139, 195, 74)
        );


        binding.smileRating.setTitle(SmileyRating.Type.GOOD, "Good");
        binding.smileRating.setFaceColor(SmileyRating.Type.GOOD, Color.BLACK);
        binding.smileRating.setFaceBackgroundColor(SmileyRating.Type.GOOD, Color.rgb(180, 196, 36));

        binding.smileRating.setTitle(SmileyRating.Type.BAD, "Bad");
        binding.smileRating.setFaceColor(SmileyRating.Type.BAD, Color.BLACK);
        binding.smileRating.setFaceBackgroundColor(SmileyRating.Type.BAD, Color.rgb(250, 160, 160));
    }

    private fun getImageFromGalleryClicked() {

        var resultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val data: Intent? = result.data
                    val uri = data?.data.toString()
                    val intent = Intent(this, ImageActivity::class.java)

                    intent.putExtra("Image", uri)
                    startActivity(intent)
                }
            }

        binding.detectImage.setOnClickListener {
            Intent(Intent.ACTION_GET_CONTENT).also {
                it.type = "image/*"
                resultLauncher.launch(it)

            }
        }

    }


    private fun flipCamera() {
        binding.flipCamera.setOnClickListener {
            cameraFacing = if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
                CameraSelector.LENS_FACING_FRONT
            } else {
                CameraSelector.LENS_FACING_BACK
            }
            startCamera(cameraFacing)


        }
    }

    private fun updateOrRequestPermissions() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this@VideoActivity,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        val hasWritePermission = ContextCompat.checkSelfPermission(
            this@VideoActivity,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED

        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        cameraPermissionGranted = hasCameraPermission
        writePermissionGranted = hasWritePermission || minSdk29

        val permissionsToRequest = mutableListOf<String>()

        if (!cameraPermissionGranted) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (!writePermissionGranted) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
        if (cameraPermissionGranted) {
            startCamera(cameraFacing)
        }

    }

    fun captureImageClicked() {
        binding.capture.setOnClickListener {

            if (writePermissionGranted) {
                takePicture()
            }
        }

    }

    private fun takePicture() {
        val imageCollection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, System.currentTimeMillis())
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.ORIENTATION, imageCapture.targetRotation)
            put(MediaStore.Images.Media.WIDTH, imageCapture.resolutionInfo?.resolution?.width)
            put(MediaStore.Images.Media.HEIGHT, imageCapture.resolutionInfo?.resolution?.height)


        }
        val outputFileOptions =
            ImageCapture.OutputFileOptions.Builder(contentResolver, imageCollection, contentValues)
                .build()


        imageCapture.takePicture(
            outputFileOptions,
            Executors.newCachedThreadPool(),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    runOnUiThread {
                        Toast.makeText(
                            this@VideoActivity,
                            getString(R.string.photo_saved_successfully),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(
                            this@VideoActivity,
                            getString(R.string.photo_save_failed),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
    }


    private fun setFlashIcon(camera: Camera) {
        if (camera.cameraInfo.hasFlashUnit()) {
            if (camera.cameraInfo.torchState.value == 0) {
                camera.cameraControl.enableTorch(true)
                binding.toggleFlash.setImageResource(R.drawable.baseline_flash_off_24)
            } else {
                camera.cameraControl.enableTorch(false)
                binding.toggleFlash.setImageResource(R.drawable.baseline_flash_on_24)
            }
        } else {
            runOnUiThread {
                Toast.makeText(
                    this@VideoActivity,
                    getString(R.string.flash_not_available),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    fun startCamera(cameraFacing: Int) {

        val listenableFuture = ProcessCameraProvider.getInstance(this)
        listenableFuture.addListener({
            try {
                val proessCameraProvider =
                    listenableFuture.get() as ProcessCameraProvider
                val preview =
                    Preview.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .build()


                val imageAnalysis = buildImageAnalysisUseCase()
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(windowManager.defaultDisplay.rotation).build()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(cameraFacing).build()
                proessCameraProvider.unbindAll()
                val camera =
                    proessCameraProvider.bindToLifecycle(
                        this,
                        cameraSelector,
                        preview,
                        imageAnalysis,
                        imageCapture
                    )
                captureImageClicked()
                binding.toggleFlash.setOnClickListener { setFlashIcon(camera) }
                preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun buildImageAnalysisUseCase(): ImageAnalysis {
        val cameraExecutorService = Executors.newSingleThreadExecutor()

        return ImageAnalysis.Builder()
            .setTargetResolution(Size(1280, 720))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build().apply {

                setAnalyzer(
                    cameraExecutorService,
                    ImageAnalysis.Analyzer { imageProxy ->
                        excuteFaceDetection(imageProxy)

                    })
            }
    }


    fun excuteFaceDetection(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val rotation =
            degreesToFirebaseRotation(imageProxy.imageInfo.rotationDegrees)
        if (mediaImage == null)
            return
        val image = FirebaseVisionImage.fromMediaImage(mediaImage, rotation)

        val highAccuracyOpts = FirebaseVisionFaceDetectorOptions.Builder()
            .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE)
            .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
            .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
            .enableTracking()
            .build()
        detector = FirebaseVision.getInstance().getVisionFaceDetector(highAccuracyOpts)
        detector.detectInImage(image)
            .addOnSuccessListener { faces ->
                if (faces.size == 0) {

                    binding.graphicOverlay.clear()
                    binding.graphicOverlay.setVisibility(View.INVISIBLE)
                    binding.eyeStatus.setVisibility(View.INVISIBLE)
                    if (old == 0)
                        binding.smileRating.setRating(-1, false)

                    old += 1


                }
                for (face in faces) {
                    drawGraphicOverlayForFace(face)
                    detectExpressionsInFace(face)
                    old = 0


                }

                imageProxy.close()

            }
            .addOnFailureListener { e ->
                Log.d("Test", e.message.toString())
            }
    }

    private fun drawGraphicOverlayForFace(face: FirebaseVisionFace) {
        binding.graphicOverlay.setVisibility(View.VISIBLE)

        val rect =
            RectOverlay(
                binding.graphicOverlay,
                face.boundingBox
            )
        binding.graphicOverlay.isVideo(true)
        binding.graphicOverlay.setGraphic(rect)

        binding.graphicOverlay.draw(Canvas())
    }

    private fun detectExpressionsInFace(face: FirebaseVisionFace) {

        when {

            face.leftEyeOpenProbability < 0.4 && face.rightEyeOpenProbability < 0.4 -> {
                binding.eyeStatus.setVisibility(View.VISIBLE)
                binding.eyeStatus.setText(R.string.both_eyes_message)
            }

            face.leftEyeOpenProbability < 0.4 -> {
                binding.eyeStatus.setVisibility(View.VISIBLE)
                binding.eyeStatus.setText(R.string.right_eye_message)
            }

            face.rightEyeOpenProbability < 0.4 -> {
                binding.eyeStatus.setVisibility(View.VISIBLE)
                binding.eyeStatus.setText(R.string.left_eye_message)
            }

            else -> binding.eyeStatus.setVisibility(View.GONE)

        }
        var smile: Int

        when (face.smilingProbability) {
            in 0.9..1.0 -> {
                smile = 5
            }

            in 0.7..0.8 -> {
                smile = 4

            }

            in 0.4..0.6 -> {
                smile = 3

            }

            in 0.2..0.3 -> {
                smile = 2

            }

            else -> smile = 1
        }


        binding.smileRating.setRating(smile, true)
        Log.d("@@@", "${face.smilingProbability}")


    }


    private fun degreesToFirebaseRotation(degrees: Int): Int = when (degrees) {
        0 -> FirebaseVisionImageMetadata.ROTATION_0
        90 -> FirebaseVisionImageMetadata.ROTATION_90
        180 -> FirebaseVisionImageMetadata.ROTATION_180
        270 -> FirebaseVisionImageMetadata.ROTATION_270
        else -> throw Exception("Rotation must be 0, 90, 180, or 270.")
    }


}