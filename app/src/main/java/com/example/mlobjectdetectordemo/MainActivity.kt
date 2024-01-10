package com.example.mlobjectdetectordemo

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.mlobjectdetectordemo.ml.AutoModel1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import java.security.Policy

class MainActivity : AppCompatActivity() {

    var color= listOf<Int>(
        Color.BLUE,Color.GREEN,Color.CYAN,Color.GRAY,Color.BLACK,Color.DKGRAY,Color.MAGENTA,Color.YELLOW,Color.RED
    )
    lateinit var imgView:ImageView
    lateinit var cameraDevice: CameraDevice
    lateinit var textureView: TextureView
    lateinit var cameraManager: CameraManager
    lateinit var handler: Handler
    lateinit var bitmap: Bitmap
    lateinit var model:AutoModel1
    lateinit var imgprocessor:ImageProcessor
    lateinit var paint: Paint
    lateinit var  lables:List<String>
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        textureView=findViewById(R.id.texture_view)
        imgView=findViewById(R.id.img_view)
        get_permission()
        cameraManager= getSystemService(Context.CAMERA_SERVICE) as CameraManager


        var handlerThread=HandlerThread("videoThread")
         model = AutoModel1.newInstance(this)
        imgprocessor=ImageProcessor.Builder().add(ResizeOp(300,300,ResizeOp.ResizeMethod.BILINEAR)).build()
        paint= Paint()
        handlerThread.start()
        handler= Handler(handlerThread.looper)
        lables=FileUtil.loadLabels(this,"labels.txt.txt")

        textureView.surfaceTextureListener = object :TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                openCamera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap=textureView.bitmap!!

                var image = TensorImage.fromBitmap(bitmap)
                image= imgprocessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray
                var mutable=bitmap.copy(Bitmap.Config.ARGB_8888,true)
                val canvas=Canvas(mutable)

                val h =mutable.height
                val w=mutable.width
                paint.textSize=h/15f
                paint.strokeWidth=h/85f
                var x=0
                scores.forEachIndexed{index, fl ->
                    x=index
                    x*=4
                    if (fl>0.5){
                        paint.setColor(color.get(index))
                        paint.style=Paint.Style.STROKE
                        canvas.drawRect(RectF( locations.get(x+1)*w,locations.get(x)*h,locations.get(x+3)*w,locations.get(x+2)*h),paint)
                        paint.style=Paint.Style.FILL
                        canvas.drawText(lables.get(classes.get(index).toInt())+" "+fl.toString(),locations.get(x+1)*w,locations.get(x)*h,paint)
                    }
                }
                imgView.setImageBitmap(mutable)
            }

        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera() {

        cameraManager.openCamera(cameraManager.cameraIdList[0],object :CameraDevice.StateCallback(){
            override fun onOpened(p0: CameraDevice) {
                cameraDevice=p0
                var surfaceTexture=textureView.surfaceTexture

                var surface= Surface(surfaceTexture)
                var captureRequest=cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.setTag("location")
                captureRequest.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface),object :CameraCaptureSession.StateCallback(){
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(),null,handler)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {
                    }

                },handler)
            }

            override fun onDisconnected(p0: CameraDevice) {
            }

            override fun onError(p0: CameraDevice, p1: Int) {
            }

        },handler)
    }

    private fun get_permission() {
        if (ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA),101)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()

    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults[0]!=PackageManager.PERMISSION_GRANTED){
            get_permission()
        }
    }
}