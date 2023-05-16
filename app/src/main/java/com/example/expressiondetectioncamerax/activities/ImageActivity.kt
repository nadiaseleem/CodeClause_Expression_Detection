package com.example.expressiondetectioncamerax.activities

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ExperimentalGetImage
import com.example.expressiondetectioncamerax.R
import com.example.expressiondetectioncamerax.databinding.ActivityImageBinding
import com.example.expressiondetectioncamerax.helper.graphicOverlay.RectOverlay
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.firebase.ml.vision.FirebaseVision
import com.google.firebase.ml.vision.common.FirebaseVisionImage
import com.google.firebase.ml.vision.face.FirebaseVisionFace
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetector
import com.google.firebase.ml.vision.face.FirebaseVisionFaceDetectorOptions
import com.hsalf.smileyrating.SmileyRating
import dmax.dialog.SpotsDialog



@ExperimentalGetImage
class ImageActivity : AppCompatActivity() {
    private lateinit var detector: FirebaseVisionFaceDetector
    private lateinit var binding: ActivityImageBinding
    private lateinit var alertDialog: AlertDialog
    private lateinit var uri:Uri
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityImageBinding.inflate(layoutInflater)
        setContentView(binding.root)

        makeAlertDialog()
        detectImageFromIntent()
        setTActionBar()
        retryDetectAnotherImageClicked()
        setupSmilyRating()


    }
    private fun setupSmilyRating() {

        binding.smileRating.disallowSelection(true)

        binding.smileRating.setTitle(SmileyRating.Type.GREAT, "Awesome");
        binding.smileRating.setFaceColor(SmileyRating.Type.GREAT, Color.BLACK);
        binding.smileRating.setFaceBackgroundColor(SmileyRating.Type.GREAT, Color.rgb(139, 195, 74));


        binding.smileRating.setTitle(SmileyRating.Type.GOOD, "Good");
        binding.smileRating.setFaceColor(SmileyRating.Type.GOOD, Color.BLACK);
        binding.smileRating.setFaceBackgroundColor(SmileyRating.Type.GOOD, Color.rgb(180, 196, 36));

        binding.smileRating.setTitle(SmileyRating.Type.BAD, "Bad");
        binding.smileRating.setFaceColor(SmileyRating.Type.BAD, Color.BLACK);
        binding.smileRating.setFaceBackgroundColor(SmileyRating.Type.BAD, Color.rgb(250, 160, 160));
    }

    private fun retryDetectAnotherImageClicked() {
        var resultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val uriString = result.data?.data.toString()
                    uri = Uri.parse(uriString)
                    binding.imageToDetect.setImageURI(uri)
                    alertDialog.show()
                    binding.graphicOverlay.clear()
                    binding.graphicOverlay.setVisibility(View.INVISIBLE)


                }
            }

        binding.fab.setOnClickListener {
            binding.smileRating.setRating(SmileyRating.Type.NONE,false);

            Intent(Intent.ACTION_GET_CONTENT).also {
                it.type = "image/*"
                resultLauncher.launch(it)

            }
        }


    }

    private fun makeAlertDialog() {

        alertDialog = SpotsDialog.Builder()
            .setContext(this)

            .setMessage(getString(R.string.processing_message))
            .setCancelable(true)
            .build().apply { show() }

    }

    private fun setTActionBar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        setTitle(getString(R.string.bak_to_camera))

    }

    private fun detectImageFromIntent() {
        val uriString = intent.extras?.getString("Image").toString()
        uri = Uri.parse(uriString)
        binding.imageToDetect.setImageURI(uri)
        val bitmap: Bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
        excuteFaceDetection(bitmap)
    }



    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            alertDialog.dismiss()
            finish()
        }
        return super.onOptionsItemSelected(item)
    }

    fun excuteFaceDetection(imageBitmap: Bitmap) {


        val firebaseImage = FirebaseVisionImage.fromBitmap(imageBitmap)
        val highcAccuracyOpts = FirebaseVisionFaceDetectorOptions.Builder()
            .setPerformanceMode(FirebaseVisionFaceDetectorOptions.ACCURATE) //                        .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
            .setClassificationMode(FirebaseVisionFaceDetectorOptions.ALL_CLASSIFICATIONS)
            .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
            .enableTracking()
            .build()
        detector = FirebaseVision.getInstance().getVisionFaceDetector(highcAccuracyOpts)
        detector.detectInImage(firebaseImage)
            .addOnSuccessListener(
                OnSuccessListener<List<FirebaseVisionFace>> { faces ->
                    alertDialog.dismiss()


                    if (faces.size == 0) {
                        binding.graphicOverlay.clear()
                        binding.graphicOverlay.setVisibility(View.INVISIBLE)
                        runOnUiThread{
                            Toast.makeText(this,getString(R.string.faces_not_detected),Toast.LENGTH_SHORT).show()
                        }

                    }
                    for (face in faces) {

                        runOnUiThread{

                            Toast.makeText(this,getString(R.string.faces_detected),Toast.LENGTH_SHORT).show()
                        }

                        drawGraphicOverlayForFace(face,imageBitmap)
                        detectExpressionsInFace(face)

                    }

                })
            .addOnFailureListener(
                OnFailureListener { e ->
                    runOnUiThread{
                        Toast.makeText(this, e.message.toString(),Toast.LENGTH_SHORT).show()
                    }

                    Log.d("Test", e.message.toString())
                    alertDialog.dismiss()

                })
    }

    private fun detectExpressionsInFace(face: FirebaseVisionFace) {
        var smile: Int

        when(face.smilingProbability){
            in 0.9 .. 1.0 -> {
                smile = 5
            }
            in 0.7 .. 0.8 ->{
                smile = 4

            }
            in 0.4 .. 0.6 ->{
                smile = 3

            }
            in 0.2 .. 0.3 ->{
                smile = 2

            }
            else->  smile = 1
        }


        binding.smileRating.setRating(smile,true)



    }

    private fun drawGraphicOverlayForFace(face: FirebaseVisionFace,imageBitmap: Bitmap) {
        binding.graphicOverlay.setVisibility(View.VISIBLE)

        val rect =
            RectOverlay(
                binding.graphicOverlay,
                face.boundingBox
            )
        binding.graphicOverlay.isVideo(false)
        binding.graphicOverlay.add(rect)


            binding.graphicOverlay.setCameraInfo(imageBitmap.width,imageBitmap.height)


        binding.graphicOverlay.draw(Canvas())
    }


}