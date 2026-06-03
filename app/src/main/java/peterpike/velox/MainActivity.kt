package peterpike.velox

import android.Manifest
import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraEffect.IMAGE_CAPTURE
import androidx.camera.core.CameraEffect.PREVIEW
import androidx.camera.core.CameraEffect.VIDEO_CAPTURE
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.media3.effect.Media3Effect
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import peterpike.velox.databinding.ActivityMainBinding
import java.lang.Thread.sleep
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@UnstableApi
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
  //  private lateinit var cameraExecutor: ExecutorService
    private lateinit var media3Effect : Media3Effect
    private lateinit var useCaseGroupBuilder : UseCaseGroup.Builder
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var tts: TextToSpeech? = null
    var max_speed : Int =0
    var av_speed : Float =0F
    var sum_dist :Float = 0F
    var start_time: Long=0
    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (allPermissionsGranted()) {            startCamera()         }
        else { requestPermissions() }

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(viewBinding.root)

        viewBinding.videoCaptureButton.setOnClickListener { captureVideo() }
        viewBinding.resetButton.setOnClickListener {   max_speed =0 ; av_speed=0F ; sum_dist=0F; start_time=0}
        viewBinding.countdownButton.setOnClickListener {
            captureVideo()
            lifecycleScope.launch{ countdown() }
        }
        viewBinding.quitButton.setOnClickListener {   finish() }

      //  cameraExecutor = Executors.newSingleThreadExecutor()

        tts = TextToSpeech(this, this)

        val d_formatter = SimpleDateFormat("mm:ss", Locale.getDefault())
        val m_formatter: DecimalFormat = NumberFormat.getInstance(Locale.GERMANY) as DecimalFormat
        m_formatter.applyPattern("###.###")
        var old_lat=0.0
        var old_lon=0.0
        var neu_dist = FloatArray(1)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { }
        locationRequest = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 1000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
            maxWaitTime = 100
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val speed=Math.round(location.speed*3.6).toInt()
                    val lat=location.latitude
                    val lon=location.longitude
                    if(old_lat==0.0){ old_lat=lat; old_lon=lon}
                    val time=location.time
                    if(start_time<1){start_time=time}
                    Location.distanceBetween(old_lat, old_lon, lat, lon, neu_dist)
                    sum_dist=sum_dist+neu_dist[0]
                    old_lat=lat
                    old_lon=lon
                    if(speed > max_speed){max_speed=speed}
                    av_speed=sum_dist/((time-start_time)/1000F)*3.6F
                    updateOvlt(speed.toString()
                            +"\n"+max_speed
                            +"\n"+Math.round(av_speed*10.0)/10.0
                            +"\n"+m_formatter.format(Math.round(sum_dist))
                            +"\n"+d_formatter.format(time-start_time)
                    )
                }
            }
        }
        fusedLocationClient.requestLocationUpdates(locationRequest,locationCallback,Looper.getMainLooper())
    }

     override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts!!.setLanguage(Locale.US)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS","The Language not supported!")
            }
            else {            }
        }
    }

    override fun onStart() {        super.onStart()    }
    override fun onStop() {        super.onStop()    }

    override fun onDestroy() {
        super.onDestroy()
     //   cameraExecutor.shutdown()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        if (tts != null) { tts!!.stop(); tts!!.shutdown() }
    }
     suspend fun countdown(){
        tts!!.speak("start in 15 seconds when i say go", TextToSpeech.QUEUE_FLUSH, null,"")
        delay(7000)
        tts!!.speak("beep", TextToSpeech.QUEUE_FLUSH, null,"")
        delay(1500)
        tts!!.speak("beep", TextToSpeech.QUEUE_FLUSH, null,"")
        delay(1500)
        tts!!.speak("beep", TextToSpeech.QUEUE_FLUSH, null,"")
        delay(1500)
        tts!!.speak("beep", TextToSpeech.QUEUE_FLUSH, null,"")
        delay(1500)
        tts!!.speak("go go go", TextToSpeech.QUEUE_FLUSH, null,"")
         max_speed =0 ; av_speed=0F ; sum_dist=0F; start_time=0
    }
    private val activityResultLauncher =//
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions())
        { permissions ->
            // Handle Permission granted/rejected
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && it.value == false)
                    permissionGranted = false
            }
            if (!permissionGranted) {Toast.makeText(baseContext,"Permission request denied",Toast.LENGTH_SHORT).show() }
            else {                startCamera()            }
        }


    private fun captureVideo() {
        val videoCapture = this.videoCapture ?: return
        viewBinding.videoCaptureButton.isEnabled = false
        val curRecording = recording
        if (curRecording != null) {
            // Stop the current recording session.
            curRecording.stop()
            recording = null
            return
        }
        // create and start a new recording session
        val name = SimpleDateFormat(FILENAME_FORMAT, Locale.GERMANY).format(System.currentTimeMillis())
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/velox")
            }
        }

        val mediaStoreOutputOptions = MediaStoreOutputOptions
            .Builder(contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()
        recording = videoCapture.output
            .prepareRecording(this, mediaStoreOutputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(this@MainActivity,
                        Manifest.permission.RECORD_AUDIO) ==
                    PermissionChecker.PERMISSION_GRANTED)
                {
                    withAudioEnabled()
                }
            }
            .start(ContextCompat.getMainExecutor(this)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        viewBinding.videoCaptureButton.apply {
                            text = "Stop!"
                            isEnabled = true
                        }
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                       //     val msg = "Video capture succeeded: ${recordEvent.outputResults.outputUri}"
                            val msg = "Video captured to Mainstorage/Videos/velox/"+name+".mp4"
                            Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                            Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                            Toast.makeText(baseContext, msg, Toast.LENGTH_LONG).show()
                            Log.d(TAG, msg)
                        } else {
                            recording?.close()
                            recording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                        }
                        viewBinding.videoCaptureButton.apply {
                            //   text = getString(R.string.start_capture)
                            text = "Record"
                            isEnabled = true
                        }
                    }
                }
            }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            // Preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
                }

            val recorder = Recorder.Builder()
                //    .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                //    .setQualitySelector(QualitySelector.from(Quality.HD))
                .setQualitySelector(QualitySelector.from(Quality.LOWEST))
                .build()
            videoCapture = VideoCapture.Builder(recorder)
                .setVideoStabilizationEnabled(true)
                .build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            useCaseGroupBuilder = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(videoCapture!!)

            media3Effect = Media3Effect(
                application,
                PREVIEW or VIDEO_CAPTURE or IMAGE_CAPTURE,
                ContextCompat.getMainExecutor(application),
            ){}
            updateOvlt("no GPS yet")
            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()
                // Bind use cases to camera
                cameraProvider.bindToLifecycle(this, cameraSelector, useCaseGroupBuilder.build())
            }
            catch(exc: Exception) {            Log.e(TAG, "Use case binding failed", exc)        }

        }, ContextCompat.getMainExecutor(this))
    }


    private fun requestPermissions() { activityResultLauncher.launch(REQUIRED_PERMISSIONS) }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "Velox"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private val REQUIRED_PERMISSIONS = mutableListOf (
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        .apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) { add(Manifest.permission.WRITE_EXTERNAL_STORAGE) }
        }.toTypedArray()
    }

    private fun updateOvlt(text :String){
        val overlay = createOverlayEffectFromBundle(text)
        val effects = ImmutableList.Builder<Effect>()
        overlay?.let{effects.add(it)}
        media3Effect.setEffects(effects.build())
        useCaseGroupBuilder.addEffect(media3Effect)
    }

    private fun createOverlayEffectFromBundle(text :String): OverlayEffect? {
        val overlayText= SpannableString(text)
        val overlayBuilder = ImmutableList.Builder<TextureOverlay>()
        val overlaySettings = StaticOverlaySettings.Builder()
            .setScale(0.57f, 0.7f)
            .setRotationDegrees(0f)
            .setAlphaScale( 1f )
            .setBackgroundFrameAnchor(-0.9f,0.4f)
            .setOverlayFrameAnchor(-0.9f,0.4f)
            .build()
        overlayText.setSpan(
            ForegroundColorSpan(
                ContextCompat.getColor(this, R.color.black)
            ),0,overlayText.length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        overlayText.setSpan(AbsoluteSizeSpan(200, false),0,overlayText.length,Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val speedOverlay = TextOverlay.createStaticTextOverlay(overlayText, overlaySettings)
        overlayBuilder.add(speedOverlay)
        val overlays = overlayBuilder.build()
        return if (overlays.isEmpty()) null
        else OverlayEffect(overlays)
    }
}

