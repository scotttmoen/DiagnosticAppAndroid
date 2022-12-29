package com.example.diagnosticapp

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.example.diagnosticapp.databinding.LayoutBinding
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt
import kotlin.properties.Delegates

//TODO: Make popup of pos/neg, look at prismo,possible add statistics:mean std dev, sample size
class MainActivity : AppCompatActivity() {

    private lateinit var binding: LayoutBinding
    private var imageCapture: ImageCapture? = null
    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var analysisImage: ImageView
    private lateinit var relativeLayout: RelativeLayout
    private var posControlArrayRed = intArrayOf()
    private var posControlArrayGreen = intArrayOf()
    private var posControlArrayBlue = intArrayOf()
    private var negControlArrayRed = intArrayOf()
    private var negControlArrayGreen = intArrayOf()
    private var negControlArrayBlue = intArrayOf()
    private var posControlGreenAvg = 0.0
    private var negControlGreenAvg = 0.0
    private var posControlRedAvg = 0.0
    private var negControlRedAvg = 0.0
    private var posControlBlueAvg = 0.0
    private var negControlBlueAvg = 0.0
    private var screenWidth = 0
    private var sampleRed = 0
    private var sampleBlue = 0
    private var sampleGreen = 1
    private var controlOrSample = ""
    private var touchStatus = ""
    private val inactiveBack = androidx.appcompat.R.color.background_material_light
    private val activeBack = androidx.appcompat.R.color.material_deep_teal_200
    private val inactiveText = androidx.appcompat.R.color.secondary_text_disabled_material_light
    private val activeText = androidx.appcompat.R.color.primary_text_default_material_light
    private var btnColorThenTextArray = intArrayOf()
    private lateinit var imageView: ImageView
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var buttonType = Int
    private var posButtonSelectBool by Delegates.notNull<Boolean>()

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = LayoutBinding.inflate(layoutInflater)
        setContentView(binding.root)
        analysisImage = findViewById(R.id.analysis_image)
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()
        screenWidth = applicationContext.resources.displayMetrics.widthPixels
        relativeLayout = findViewById(R.id.analyzer_display_area)
        btnColorThenTextArray = intArrayOf(1,0,0,0,0,1,0,0,0,0)
        posButtonSelectBool = true

        if (allPermissionGranted()) {
            Toast.makeText(
                this,
                "Looks like we're ready.",
                Toast.LENGTH_SHORT
            ).show()
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                Constants.REQUIRED_PERMISSIONS,
                Constants.REQUEST_CODE_PERMISSIONS
            )
        }

        this.binding.btnCheese.setOnClickListener {
            btnColorThenTextArray = intArrayOf(1,0,0,0,0,0,0,0,0,0)
            prettifyButtons(btnColorThenTextArray)
            takeImage()
        }
        this.binding.declarePosControl.setOnClickListener {
            btnColorThenTextArray = intArrayOf(0,1,0,0,0,0,1,0,0,0)
            prettifyButtons(btnColorThenTextArray)
            this.binding.analysisImage.setOnTouchListener { _, m: MotionEvent ->
                handleButtonTouch(m, 1)
                true
            }
        }
        this.binding.declareNegControl.setOnClickListener {
            btnColorThenTextArray = intArrayOf(0,0,1,0,0,0,0,1,0,0)
            prettifyButtons(btnColorThenTextArray)
            Log.d("guitar", "start storing values for negative control")
            this.binding.analysisImage.setOnTouchListener { _, m: MotionEvent ->
                handleButtonTouch(m, 2)
                true
            }
        }
        this.binding.declareTest.setOnClickListener {
            btnColorThenTextArray = intArrayOf(0,0,0,1,0,0,0,0,1,0)
            prettifyButtons(btnColorThenTextArray)
            Log.d("guitar", "start storing values for samples")
            this.binding.analysisImage.setOnTouchListener { _, m: MotionEvent ->
                handleButtonTouch(m, 3)
                true
            }
        }
        this.binding.startAnalysis.setOnTouchListener{ _, m: MotionEvent ->
            btnColorThenTextArray = intArrayOf(0,0,0,1,0,0,0,0,0,1)
            prettifyButtons(btnColorThenTextArray)
            handleButtonTouch(m, 4)
            true
        }

        //Here is where you put the beginning button color state
        btnColorThenTextArray = intArrayOf(1,0,0,0,0,1,0,0,0,0)
        prettifyButtons(btnColorThenTextArray)
    }
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let { mFile ->
            File(mFile, resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }
    private fun handleButtonTouch(m: MotionEvent, btnType: Int) {
        val pointerCount = m.pointerCount
        for (i in 0 until pointerCount) {
            val x = m.getX(i)
            val y = m.getY(i)
            val action = m.actionMasked
            val actionIndex = m.actionIndex
            val actionString: String = when (action) {
                MotionEvent.ACTION_DOWN -> "DOWN"
                MotionEvent.ACTION_MOVE -> "MOVE"
                MotionEvent.ACTION_UP -> "UP"
                else -> ""
            }

            when (btnType) {
                1 -> {
                    controlOrSample = "Positive_Control"
                    if (actionString =="DOWN"){
                        this.imageView = findViewById(R.id.imageView)
                        imageView.isVisible = false
                        imageView.isVisible = true

                    }

                    if (actionString == "UP") {

                        Log.d("Button1", "Start storing values for positive control, Button1 UP")
                        posButtonSelectBool =! posButtonSelectBool
                        Log.d("Button1", "posbool is $posButtonSelectBool")

                        if (posButtonSelectBool == false){
                            posButtonSelectBool =! posButtonSelectBool
                            binding.declarePosControl.text = "Choose another?"
                        }

                        this.imageView.setOnTouchListener(CustomCircleTouchListener())
                        if  (posButtonSelectBool == false)
                        {
                            posButtonSelectBool == true
                        }
                        else
                        {
                            posButtonSelectBool == false
                        }
//                        val newRGB = getRGBAverage(imageView.x.toInt(),imageView.y.toInt(),1)
//                        posControlArrayRed += newRGB[0]
//                        posControlArrayGreen += newRGB[1]
//                        posControlArrayBlue += newRGB[2]
//                        posControlGreenAvg = 0.0
//                        posControlBlueAvg = 0.0
//                        posControlRedAvg = 0.0
//
//                        for (red in posControlArrayRed) {
//                            posControlRedAvg += red
//                        }
//                        for (green in posControlArrayGreen) {
//                            posControlGreenAvg += green
//                        }
//                        for (blue in posControlArrayBlue) {
//                            posControlBlueAvg += blue
//                        }
//
//                        posControlGreenAvg /= posControlArrayGreen.size
//                        posControlRedAvg /= posControlArrayRed.size
//                        posControlBlueAvg /= posControlArrayBlue.size
//
//                        val posContArrayGreenString = posControlArrayGreen.asList().toString()
//                        val posContArrayAvgGreenString = posControlGreenAvg.toString()
//                        touchStatus =
//                            "$actionIndex $controlOrSample X: ${imageView.x.roundToInt()} Y: ${imageView.y.roundToInt()} red: ${newRGB[0]} green: ${newRGB[1]} blue: ${newRGB[2]} greenArrayValues $posContArrayGreenString greenAveragefromArray $posContArrayAvgGreenString"
//                        Log.d("Button1End", touchStatus)

                    }
                }
                2->  {
                    controlOrSample = "Negative_Control"

                    if (actionString == "UP") {
                        val newRGB = getRGBAverage(imageView.x.toInt(),imageView.y.toInt(),2)

                        negControlArrayRed += newRGB[0]
                        negControlArrayGreen += newRGB[1]
                        negControlArrayBlue += newRGB[2]
                        negControlGreenAvg = 0.0
                        negControlBlueAvg = 0.0
                        negControlRedAvg = 0.0

                        for (red in negControlArrayRed) {
                            negControlRedAvg += red
                        }
                        for (green in negControlArrayGreen) {
                            negControlGreenAvg += green
                        }
                        for (blue in negControlArrayBlue) {
                            negControlBlueAvg += blue
                        }

                        negControlGreenAvg /= negControlArrayGreen.size
                        negControlRedAvg /= negControlArrayGreen.size
                        negControlBlueAvg /= negControlArrayGreen.size

                        val negContArrayGreenString = negControlArrayGreen.asList().toString()
                        val negContArrayGreenAvgString = negControlGreenAvg.toString()
                        touchStatus =
                            "$actionIndex $controlOrSample X: ${x.roundToInt()} Y: ${y.roundToInt()} red: $newRGB[0] **GREEN: $newRGB[1]** blue: $newRGB[2] neg control $negContArrayGreenString $negContArrayGreenAvgString"
                        Log.d("guitar", touchStatus)

                        this.binding.btnCheese.setBackgroundColor(ContextCompat.getColor(this, activeBack))
                        this.binding.declarePosControl.setBackgroundColor(ContextCompat.getColor(this, inactiveBack))
                        this.binding.declareTest.setTextColor(ContextCompat.getColor(this, inactiveText))
                        this.binding.declarePosControl.setTextColor(ContextCompat.getColor(this, inactiveText))
                    }
                }
                3 -> {
                    controlOrSample = "Sample"
                    if (actionString == "UP") {
                        val newRGB = getRGBAverage(imageView.x.toInt(),imageView.y.toInt(),3)
                        sampleRed = newRGB[0]
                        sampleGreen = newRGB[1]
                        sampleBlue = newRGB[2]
                        touchStatus =
                            "$actionIndex $controlOrSample X: ${x.roundToInt()} Y: ${y.roundToInt()} red: $sampleRed green: $sampleGreen blue: $sampleBlue **Green Sample: $sampleGreen**"
                        Log.d("guitar", touchStatus)
                    }
                }
                4 -> {
                    controlOrSample = "Run Analysis"
                    if (actionString == "DOWN") {

                        var greenDiff = (sampleGreen-negControlGreenAvg)/posControlGreenAvg*100
                        greenDiff = greenDiff.roundToInt().toDouble()
                        //blueDiff = ((posControlBlueAvg-negControlBlueAvg)/sampleBlue*100)
                        var blueDiff = (sampleBlue-negControlBlueAvg)/posControlBlueAvg*100
                        Log.d("guitar","${blueDiff::class.simpleName}")
                        blueDiff = blueDiff.roundToInt().toDouble()
                        //redDiff = ((posControlRedAvg-negControlRedAvg)/sampleRed*100)
                        var redDiff = (sampleRed-negControlRedAvg)/posControlRedAvg*100
                        redDiff = redDiff.roundToInt().toDouble()

                        touchStatus=
                            "$actionString  $controlOrSample X: ${x.roundToInt()} Y: ${y.roundToInt()} R: $redDiff% G:$greenDiff% B:$blueDiff% of pos-neg range"
                        Log.d("guitar", touchStatus)
                        Log.d("giraffe", (posControlBlueAvg-negControlBlueAvg).toString())
                        Log.d("giraffe2",  sampleBlue.toString())

                    }
                }
            }
        }
    }
    private fun takeImage() {
        val imageCapture = imageCapture ?: return
        imageCapture.takePicture(
            ContextCompat.getMainExecutor(this), // Defines where the callbacks are run
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {

                    var bitmap = imageProxyToBitmap(image)
                    bitmap = rotateBitmap(bitmap, 90.0F)

                    analysisImage.setImageBitmap(bitmap)
                    btnColorThenTextArray = intArrayOf(0,1,0,0,0,0,1,0,0,0)
                    prettifyButtons(btnColorThenTextArray)
                    image.close() // Make sure to close the image
                }

                override fun onError(exception: ImageCaptureException) {
                    // Handle exception
                }
            }
        )
    }
    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val planeProxy = image.planes[0]
        val buffer: ByteBuffer = planeProxy.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .build()
                .also { mPreview ->
                    mPreview.setSurfaceProvider(
                        binding.viewFinder.surfaceProvider
                    )
                }
            imageCapture = ImageCapture.Builder()
                .build()
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Log.d(Constants.TAG, "startCamera Fail", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.REQUEST_CODE_PERMISSIONS) {
            if (allPermissionGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions are not granted by the user in this apps permissions.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }
    private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(
            source, 0, 0, source.width, source.height, matrix, true
        )
    }
    private fun allPermissionGranted() =
        Constants.REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(
                baseContext, it
            ) == PackageManager.PERMISSION_GRANTED
        }
    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
    private fun getRGBAverage(x: Int, y:Int, buttonType: Int): IntArray {

        //TODO: somehow connect this to customlistener

        val forColors = getBitmapFromView(analysisImage)
        var r = 0
        var g = 0
        var b = 0
        val pixelToCheck = mutableListOf(
            listOf(x,y),
            listOf(x+1,y),
            listOf(x-1,y),
            listOf(x,y+1),
            listOf(x,y-1)
        )
        val adjX = imageView.x+15
        val adjY = imageView.y+15
        for (pixel in pixelToCheck.indices){
            if (adjX <= analysisImage.width && adjY>=0 && adjX>=0 && adjY <= analysisImage.height) {
                val gotColor = forColors.getPixel(pixelToCheck[pixel][0]+15, pixelToCheck[pixel][1]+15)
                r += Color.red(gotColor)
                g += Color.green(gotColor)
                b += Color.blue(gotColor)
            }
        }
        val averageR = r/5
        val averageG = g/5
        val averageB = b/5
        Log.d("getRGBAverage", " in getRGBaverage the average is, r$averageR g$averageG b$averageB button$buttonType")
        return intArrayOf(averageR, averageG, averageB)
    }
    private fun prettifyButtons(btn_color_array: IntArray){
        //first 5 values are button color, next 5 are text color; value 1 is active
        if(btn_color_array[0]==0){
            this.binding.btnCheese.setBackgroundColor(ContextCompat.getColor(this, inactiveBack))
        }else{
            this.binding.btnCheese.setBackgroundColor(ContextCompat.getColor(this, activeBack))
        }
        if(btn_color_array[1]==0){
            this.binding.declarePosControl.setBackgroundColor(ContextCompat.getColor(this, inactiveBack))
        }else{
            this.binding.declarePosControl.setBackgroundColor(ContextCompat.getColor(this, activeBack))
        }
        if(btn_color_array[2]==0){
            this.binding.declareNegControl.setBackgroundColor(ContextCompat.getColor(this, inactiveBack))
        }else{
            this.binding.declareNegControl.setBackgroundColor(ContextCompat.getColor(this, activeBack))
        }
        if(btn_color_array[3]==0){
            this.binding.declareTest.setBackgroundColor(ContextCompat.getColor(this, inactiveBack))
        }else{
            this.binding.declareTest.setBackgroundColor(ContextCompat.getColor(this, activeBack))
        }
        if(btn_color_array[4]==0){
            this.binding.startAnalysis.setBackgroundColor(ContextCompat.getColor(this, inactiveBack))
        }else{
            this.binding.startAnalysis.setBackgroundColor(ContextCompat.getColor(this, activeBack))
        }
        if(btn_color_array[5]==0){
            this.binding.btnCheese.setTextColor(ContextCompat.getColor(this, inactiveText))
        }else{
            this.binding.btnCheese.setTextColor(ContextCompat.getColor(this, activeText))
        }
        if(btn_color_array[6]==0){
            this.binding.declarePosControl.setTextColor(ContextCompat.getColor(this, inactiveText))
        }else{
            this.binding.declarePosControl.setTextColor(ContextCompat.getColor(this, activeText))
        }
        if(btn_color_array[7]==0){
            this.binding.declareNegControl.setTextColor(ContextCompat.getColor(this, inactiveText))
        }else{
            this.binding.declareNegControl.setTextColor(ContextCompat.getColor(this, activeText))
        }
        if(btn_color_array[8]==0){
            this.binding.declareTest.setTextColor(ContextCompat.getColor(this, inactiveText))
        }else{
            this.binding.declareTest.setTextColor(ContextCompat.getColor(this, activeText))
        }
        if(btn_color_array[9]==0){
            this.binding.startAnalysis.setTextColor(ContextCompat.getColor(this, inactiveText))
        }else{
            this.binding.startAnalysis.setTextColor(ContextCompat.getColor(this, activeText))
        }

    }
    private fun getBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(
            view.width, view.height, Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
    private inner class CustomCircleTouchListener (): View.OnTouchListener{
        //TODO: initiate new instance of imageview and record rgb of last move up before leaving button
        @RequiresApi(Build.VERSION_CODES.S)
        @SuppressLint("ClickableViewAccessibility")

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.action and MotionEvent.ACTION_MASK) {
                MotionEvent.ACTION_DOWN -> {
                    //Todo: the following will be used to name each image group that will pop up with the control/sample button press
                    initialX = imageView.x.toInt()
                    initialY = imageView.y.toInt()
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY

                }
                MotionEvent.ACTION_UP -> {Toast.makeText(
                    this@MainActivity,
                    " screen width in pixels $screenWidth",
                    Toast.LENGTH_SHORT

                ).show()

                    if  (posButtonSelectBool == false)
                    {
                        Log.d("Button1", "posbool is $posButtonSelectBool")
                    }


                    binding.declarePosControl.text = "Press to Select"

                    Log.d("Button1", "posbool is $posButtonSelectBool")

                    val newRGB = getRGBAverage(imageView.x.toInt(),imageView.y.toInt(),1)
                    posControlArrayRed += newRGB[0]
                    posControlArrayGreen += newRGB[1]
                    posControlArrayBlue += newRGB[2]
                    posControlGreenAvg = 0.0
                    posControlBlueAvg = 0.0
                    posControlRedAvg = 0.0

                    for (red in posControlArrayRed) {
                        posControlRedAvg += red
                    }
                    for (green in posControlArrayGreen) {
                        posControlGreenAvg += green
                    }
                    for (blue in posControlArrayBlue) {
                        posControlBlueAvg += blue
                    }

                    posControlGreenAvg /= posControlArrayGreen.size
                    posControlRedAvg /= posControlArrayRed.size
                    posControlBlueAvg /= posControlArrayBlue.size

                    val posContArrayGreenString = posControlArrayGreen.asList().toString()
                    val posContArrayAvgGreenString = posControlGreenAvg.toString()
                    touchStatus ="X: ${imageView.x.roundToInt()} Y: ${imageView.y.roundToInt()} red: ${newRGB[0]} green: ${newRGB[1]} blue: ${newRGB[2]} greenArrayValues $posContArrayGreenString greenAveragefromArray $posContArrayAvgGreenString"
                    Log.d("Button1End", touchStatus)


                }
                MotionEvent.ACTION_MOVE -> {

                    imageView.y = event.y
                    imageView.x = event.x
                    imageView.x  = initialX + event.rawX - initialTouchX
                    imageView.y  = initialY + event.rawY - initialTouchY
                }
            }

            relativeLayout.invalidate()
            return true
        }

    }

}

