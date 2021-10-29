package com.example.skt_skindata

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "  Main Activity "
    }

    private var CAMERA_DIRECTION = "back"
    private var LAYOUT_OPEN = true // true: open, false: closed

    private lateinit var previewView: PreviewView
    private lateinit var imageViewPhoto: ImageView
    private lateinit var frameLayoutPreview: FrameLayout
    private lateinit var imageViewPreview: ImageView
    private lateinit var frameLayoutShutter: FrameLayout

    private lateinit var openLayout: ImageView
    private lateinit var camChange: ImageView
    private lateinit var selectLayout: LinearLayout

    private lateinit var idPrev: Button
    private lateinit var id: EditText
    private lateinit var idNext: Button

    private lateinit var lightTypeGroup: RadioGroup
    private lateinit var lightPositionGroup: RadioGroup
    private lateinit var lightStrengthGroup: RadioGroup

    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var cameraAnimationListener: Animation.AnimationListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findView()
        permissionCheck()
        setListener()

        setCameraAnimationListener()

        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    private fun findView() {
        previewView = findViewById(R.id.previewView)
        imageViewPhoto = findViewById(R.id.imageViewPhoto)
        imageViewPreview = findViewById(R.id.imageViewPreview)
        frameLayoutPreview = findViewById(R.id.frameLayoutPreview)
        frameLayoutShutter = findViewById(R.id.frameLayoutShutter)

        openLayout = findViewById(R.id.openLayout)
        camChange = findViewById(R.id.camChange)
        selectLayout = findViewById(R.id.selectLayout)

        idPrev = findViewById(R.id.id_prev)
        id = findViewById(R.id.id)
        idNext = findViewById(R.id.id_next)

        lightTypeGroup = findViewById(R.id.radio_type)
        lightPositionGroup = findViewById(R.id.radio_position)
        lightStrengthGroup = findViewById(R.id.radio_strength)
    }

    private fun permissionCheck() {
        val permissionList = listOf(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)

        if (!PermissionUtil.checkPermission(this, permissionList)) {
            PermissionUtil.requestPermission(this, permissionList)
        } else {
            openCamera(CAMERA_DIRECTION)
        }
    }

    private fun setListener() {
        imageViewPhoto.setOnClickListener {
            savePhoto()
        }

        camChange.setOnClickListener {
            if (CAMERA_DIRECTION == "front") {
                CAMERA_DIRECTION = "back"
                openCamera(CAMERA_DIRECTION)
            } else {
                CAMERA_DIRECTION = "front"
                openCamera(CAMERA_DIRECTION)
            }
        }

        openLayout.setOnClickListener {
            if (LAYOUT_OPEN) { // open -> close
                AnimationUtils.loadAnimation(this, R.anim.rotate_close).also { animation -> openLayout.startAnimation(animation) }
                AnimationUtils.loadAnimation(this, R.anim.translate_close).also { animation -> selectLayout.startAnimation(animation) }
                LAYOUT_OPEN = false
            } else { // close -> open
                AnimationUtils.loadAnimation(this, R.anim.rotate_open).also { animation -> openLayout.startAnimation(animation) }
                AnimationUtils.loadAnimation(this, R.anim.translate_open).also { animation -> selectLayout.startAnimation(animation) }
                LAYOUT_OPEN = true
            }
        }

        idPrev.setOnClickListener {
            if (id.text.toString() == "") id.setText("0")
            val a = Integer.parseInt(id.text.toString()) - 1
            id.setText(a.toString())
        }

        idNext.setOnClickListener {
            if (id.text.toString() == "") id.setText("1")
            val a = Integer.parseInt(id.text.toString()) + 1
            id.setText(a.toString())
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "Permission Granted")
            openCamera(CAMERA_DIRECTION)
        } else {
            Log.d(TAG, "Permission Denied")
        }
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists()) mediaDir else filesDir
    }

    private fun openCamera(cameraDirection: String) {
        Log.d(TAG, "openCamera")

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            imageCapture = ImageCapture.Builder().build()

            var cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            if (cameraDirection == "front") cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
                Log.d(TAG, "Binding Success")
            } catch (e: Exception) {
                Log.d(TAG, "Binding Failed $e")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun savePhoto() {
        imageCapture = imageCapture?: return

        // get file name from radio group
        val id = id.text.padStart(4, '0')
        val type = findViewById<RadioButton>(lightTypeGroup.checkedRadioButtonId).text
        val position = findViewById<RadioButton>(lightPositionGroup.checkedRadioButtonId).text
        val strength = findViewById<RadioButton>(lightStrengthGroup.checkedRadioButtonId).text

        val currentTimeStr = SimpleDateFormat("yymmdd_HHmmss", Locale.KOREA).format(System.currentTimeMillis())
        val fileName = String.format("%s_%s_%s_%s_%s.png", id, type, position, strength, currentTimeStr)
        val fullDirectory = File(outputDirectory.path + File.separator + id + File.separator + type + File.separator + position + File.separator + strength)

        if(!fullDirectory.exists()) fullDirectory.mkdirs()

        val photoFile = File(fullDirectory, fileName)
        val outputOption = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture?.takePicture(outputOption, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {

                val animation = AnimationUtils.loadAnimation(this@MainActivity, R.anim.camera_shutter)
                animation.setAnimationListener(cameraAnimationListener)

                val frameLayoutShutter = frameLayoutShutter
                frameLayoutShutter.animation = animation
                frameLayoutShutter.visibility = View.VISIBLE
                frameLayoutShutter.startAnimation(animation)

                Log.d(TAG, "imageCapture")
            }

            override fun onError(exception: ImageCaptureException) {
                exception.printStackTrace()
            }
        })
    }

    private fun setCameraAnimationListener() {
        cameraAnimationListener = object: Animation.AnimationListener {
            override fun onAnimationStart(p0: Animation?) {}

            override fun onAnimationEnd(p0: Animation?) {
                frameLayoutShutter.visibility = View.GONE
            }

            override fun onAnimationRepeat(p0: Animation?) {}
        }
    }
}