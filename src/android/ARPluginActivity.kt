package com.gj.arcoredraw

import android.annotation.SuppressLint
import android.net.Uri
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLSurfaceView.Renderer
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.util.Log
import android.util.Pair
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.ar.core.*
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.ux.ArFragment
import java.io.IOException
import java.io.InputStream
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

import com.bumptech.glide.Glide
import com.bumptech.glide.RequestManager
import com.google.ar.core.exceptions.*
import java.util.HashMap


/**
 * Based on https://github.com/Terran-Marine/ARCoreMeasuredDistance
 */

// class ARPluginActivity : AppCompatActivity(), Renderer {
class ARPluginActivity : AppCompatActivity() {
    var allowMultiple: Boolean = false
    var startNew: Boolean = false
    var unit: String = "cm"
    var unitTxt: String = "cm"
    var length: Double = 0.0;
    private lateinit var session: Session
    private val backgroundRenderer = BackgroundRenderer()
    private val augmentedImageRenderer = AugmentedImageRenderer()
    private val trackingStateHelper = TrackingStateHelper(this)
    private val messageSnackbarHelper = SnackbarHelper()

    private val augmentedImageMap = HashMap<Int, Pair<AugmentedImage, Anchor>>()


    private lateinit var displayRotationHelper: DisplayRotationHelper


    private val measureArray = arrayListOf<String>()

    private val dataArray = arrayListOf<AnchorInfoBean>()
    private val lineNodeArray = arrayListOf<Node>()
    private val sphereNodeArray = arrayListOf<Node>()
    private val startNodeArray = arrayListOf<Node>()
    private val endNodeArray = arrayListOf<Node>()

    //private var fitToScanView: ImageView? = null
    private var glideRequestManager: RequestManager? = null


    private  lateinit var startNode: AnchorNode

    private lateinit var surfaceView: GLSurfaceView

    private var installRequested = false
    private var shouldConfigureSession = false


    private var TAG = "ARPlugin"

    companion object {
        val act = null    
    } 

    fun getLayoutId(): Int {
        return getResources().getIdentifier("activity_arplugin", "layout", getPackageName())
    }
    fun getRenderableTextId(): Int {
        return getResources().getIdentifier("renderable_text", "layout", getPackageName())
    }
    
    fun getUI_ArSceneView(): ArFragment {
        var id = getResources().getIdentifier("UI_ArSceneView", "id", getPackageName())

        return getSupportFragmentManager().findFragmentById(id) as ArFragment
    }
    fun getUI_Last(): ImageView {
        var id = getResources().getIdentifier("UI_Last", "id", getPackageName())

        return findViewById(id) as ImageView
    }
    fun getUI_Post(): ImageView {
        var id = getResources().getIdentifier("UI_Post", "id", getPackageName())

        return findViewById(id) as ImageView
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras: Bundle = getIntent().getExtras()
        displayRotationHelper = DisplayRotationHelper(/*context=*/this)

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(getLayoutId())
        //surfaceView = findViewById(R.id.surfaceview);
	    //     surfaceView = findViewById(getResources().getIdentifier("surfaceview", "id", getPackageName()));

        // surfaceView.setPreserveEGLContextOnPause(true);
        // surfaceView.setEGLContextClientVersion(2);
        // surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        // surfaceView.setRenderer(this);
        // surfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
        // surfaceView.setWillNotDraw(false);

        installRequested = false


        allowMultiple = savedInstanceState?.getBoolean("allowMultiple") ?: false
        unit = savedInstanceState?.getString("unit") ?: extras.getString("unit")
        unitTxt = savedInstanceState?.getString("unitTxt") ?: extras.getString("unitTxt")
        startNew = savedInstanceState?.getBoolean("startNew") ?: extras.getBoolean("startNew")

        //act = this

        //fitToScanView = findViewById<ImageView>(R.id.image_view_fit_to_scan)
        //glideRequestManager = Glide.with(this)
        //glideRequestManager
        //        .load(Uri.parse("file:///android_asset/fit_to_scan.png"))
        //        .into(fitToScanView)
        initView()
    }

    private fun clearAll() {
        if (dataArray.size > 0) {
            while (dataArray.size > 1) {
                dataArray.removeAt(dataArray.size - 1)
                val index = startNodeArray.size - 1
                startNodeArray[index].removeChild(lineNodeArray.removeAt(index))
                endNodeArray[index].removeChild(sphereNodeArray.removeAt(index + 1))
                getUI_ArSceneView().arSceneView.scene.removeChild(startNodeArray.removeAt(index))
                getUI_ArSceneView().arSceneView.scene.removeChild(endNodeArray.removeAt(index))
            }

            measureArray.clear()

            dataArray.clear()
            lineNodeArray.clear()
            sphereNodeArray.clear()
            startNodeArray.clear()
            endNodeArray.clear()
            getUI_ArSceneView().arSceneView.scene.removeChild(startNode)
        }
    }

    private fun initView() {
        getUI_Last().setOnClickListener {
            clearAll()
        }

        getUI_Post().setOnClickListener {
            ARPluginCallback.onFinish(measureArray.toTypedArray())
            finish()
        }
        initAr()
    }

    @SuppressLint("NewApi")
    private fun initAr() {
        Log.i(TAG, "initAr")
        
        getUI_ArSceneView().setOnTapArPlaneListener { hitResult, plane, motionEvent ->

            if (!allowMultiple && dataArray.size > 1) {
                clearAll()
            }

            val anchorInfoBean = AnchorInfoBean("", hitResult.createAnchor(), 0.0)
            dataArray.add(anchorInfoBean)

            if (dataArray.size > 1) {
                val endAnchor = dataArray[dataArray.size - 1].anchor
                val startAnchor = dataArray[dataArray.size - 2].anchor


                val startPose = endAnchor.pose
                val endPose = startAnchor.pose
                val dx = startPose.tx() - endPose.tx()
                val dy = startPose.ty() - endPose.ty()
                val dz = startPose.tz() - endPose.tz()

                anchorInfoBean.length = Math.sqrt((dx * dx + dy * dy + dz * dz).toDouble())

                if (unit == "cm") {
                    length = anchorInfoBean.length * 100 // Meter to CM
                } else {
                    length = anchorInfoBean.length * 39.37007874 // Meter to IN
                }

                val lengthTxt = "${String.format("%.1f", length)}${unitTxt}"
                measureArray.add(lengthTxt)
                ARPluginCallback.onUpdate(lengthTxt)

                drawLine(startAnchor, endAnchor)
            } else {
                startNode = AnchorNode(hitResult.createAnchor())
                startNode.setParent(getUI_ArSceneView().arSceneView.scene)
                MaterialFactory.makeOpaqueWithColor(this@ARPluginActivity, com.google.ar.sceneform.rendering.Color(0.33f, 0.87f, 0f))
                        .thenAccept { material ->
                            val sphere = ShapeFactory.makeSphere(0.02f, Vector3.zero(), material)
                            sphereNodeArray.add(    Node().apply {
                                setParent(startNode)
                                localPosition = Vector3.zero()
                                renderable = sphere
                            })
                        }
            }
        }
        Log.i(TAG, "initAr check start new: " + startNew)
        if(startNew) {
            Log.i(TAG, "initAr: startNew: " + startNew)
            initArIr()
        } else {
            Log.i(TAG, " not init arIr")
        }
        Log.d(TAG, "initAr completed")
    }

    private fun initArIr() {
        Log.d(TAG, "read database");
        val inputStream: InputStream = this.assets.open("sample_database.imgdb")
        Log.d(TAG, "get Session")
        session = Session(this);
        Log.d(TAG, "configure session")
        configureSession();
        Log.d(TAG, "deserialize database");
        val imageDatabase: AugmentedImageDatabase = AugmentedImageDatabase.deserialize(session, inputStream)
        Log.d(TAG, "deserialized!")
    }

    // override fun onResume() {
    //     super.onResume()
    //     Log.d(TAG, "on Resume");
    //     if (session == null) {
    //         var exception: Exception? = null
    //         var message: String? = null
    //         try {
    //             when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
    //                 ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
    //                     installRequested = true
    //                     return
    //                 }
    //                 ArCoreApk.InstallStatus.INSTALLED -> {
    //                 }
    //             }

    //             // ARCore requires camera permissions to operate. If we did not yet obtain runtime
    //             // permission on Android M and above, now is a good time to ask the user for it.
    //             if (!CameraPermissionHelper.hasCameraPermission(this)) {
    //                 CameraPermissionHelper.requestCameraPermission(this)
    //                 return
    //             }

    //             session = Session(/* context = */this)
    //         } catch (e: UnavailableArcoreNotInstalledException) {
    //             message = "Please install ARCore"
    //             exception = e
    //         } catch (e: UnavailableUserDeclinedInstallationException) {
    //             message = "Please install ARCore"
    //             exception = e
    //         } catch (e: UnavailableApkTooOldException) {
    //             message = "Please update ARCore"
    //             exception = e
    //         } catch (e: UnavailableSdkTooOldException) {
    //             message = "Please update this app"
    //             exception = e
    //         } catch (e: Exception) {
    //             message = "This device does not support AR"
    //             exception = e
    //         }

    //         if (message != null) {
    //             messageSnackbarHelper.showError(this, message)
    //             Log.e(TAG, "Exception creating session", exception)
    //             return
    //         }

    //         shouldConfigureSession = true
    //     }

    //     if (shouldConfigureSession) {
    //         configureSession()
    //         shouldConfigureSession = false
    //     }

    //     // Note that order matters - see the note in onPause(), the reverse applies here.
    //     try {
    //         session.resume()
    //     } catch (e: CameraNotAvailableException) {
    //         messageSnackbarHelper.showError(this, "Camera not available. Try restarting the app.")
    //         //session = null
    //         return
    //     }

    //     surfaceView.onResume()
    //     displayRotationHelper.onResume()

    //     //fitToScanView.setVisibility(View.VISIBLE)
    // }

    // public override fun onPause() {
    //     super.onPause()
    //     if (session != null) {
    //         // Note that the order matters - GLSurfaceView is paused first so that it does not try
    //         // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
    //         // still call session.update() and get a SessionPausedException.
    //         displayRotationHelper.onPause()
    //         surfaceView.onPause()
    //         session.pause()
    //     }
    // }

    // override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
    //     super.onRequestPermissionsResult(requestCode, permissions, results)
    //     if (!CameraPermissionHelper.hasCameraPermission(this)) {
    //         Toast.makeText(
    //                 this, "Camera permissions are needed to run this application", Toast.LENGTH_LONG)
    //                 .show()
    //         if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
    //             // Permission denied with checking "Do not ask again".
    //             CameraPermissionHelper.launchPermissionSettings(this)
    //         }
    //         finish()
    //     }
    // }


    // override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
    //     displayRotationHelper?.onSurfaceChanged(width, height)
    //     GLES20.glViewport(0, 0, width, height)
    // }

    // override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
    //     GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

    //     // Prepare the rendering objects. This involves reading shaders, so may throw an IOException.
    //     try {
    //         // Create the texture and pass it to ARCore session to be filled during update().
    //         backgroundRenderer.createOnGlThread(/*context=*/this)
    //         augmentedImageRenderer.createOnGlThread(/*context=*/this)
    //     } catch (e: IOException) {
    //         Log.e(TAG, "Failed to read an asset file", e)
    //     }

    // }


    // override fun onDrawFrame(gl: GL10) {
    //     // Clear screen to notify driver it should not load any pixels from previous frame.
    //     GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

    //     if (session == null) {
    //         Log.e(TAG, "onDrawFrame session is null!!!")
    //         return
    //     }
    //     // Notify ARCore session that the view size changed so that the perspective matrix and
    //     // the video background can be properly adjusted.
    //     displayRotationHelper?.updateSessionIfNeeded(session)

    //     try {
    //         session?.setCameraTextureName(backgroundRenderer.getTextureId())

    //         // Obtain the current frame from ARSession. When the configuration is set to
    //         // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
    //         // camera framerate.
    //         val frame = session?.update()
    //         val camera = frame?.getCamera()

    //         // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
    //         trackingStateHelper.updateKeepScreenOnFlag(camera?.getTrackingState())

    //         // If frame is ready, render camera preview image to the GL surface.
    //         if (frame != null) {
    //             backgroundRenderer.draw(frame)
    //         } else {
    //             Log.e(TAG, "onDrawFrame frame is null!!!")
    //         }

    //         // Get projection matrix.
    //         val projmtx = FloatArray(16)
    //         camera?.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f)

    //         // Get camera matrix and draw.
    //         val viewmtx = FloatArray(16)
    //         camera?.getViewMatrix(viewmtx, 0)

    //         // Compute lighting from average intensity of the image.
    //         val colorCorrectionRgba = FloatArray(4)
    //         frame?.getLightEstimate()?.getColorCorrection(colorCorrectionRgba, 0)

    //         // Visualize augmented images.
    //         if (frame != null) {
    //             drawAugmentedImages(frame, projmtx, viewmtx, colorCorrectionRgba)
    //         } else {
    //             Log.e(TAG, "onDrawFrame frame is null 2!!!")
    //         }
    //     } catch (t: Throwable) {
    //         // Avoid crashing the application due to unhandled exceptions.
    //         Log.e(TAG, "Exception on the OpenGL thread", t)
    //     }
    // }

    // private fun drawAugmentedImages(
    //         frame: Frame, projmtx: FloatArray, viewmtx: FloatArray, colorCorrectionRgba: FloatArray) {
    //     val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

    //     // Iterate to update augmentedImageMap, remove elements we cannot draw.
    //     for (augmentedImage in updatedAugmentedImages) {
    //          Log.d(TAG, "drawAugmentedImages")
    //         when (augmentedImage.trackingState) {
    //             TrackingState.PAUSED -> {
    //                 // When an image is in PAUSED state, but the camera is not PAUSED, it has been detected,
    //                 // but not yet tracked.
    //                 val text = String.format("Detected Image %d", augmentedImage.index)
    //                 Log.d(TAG, "drawAugmentedImages " + text)
    //                 messageSnackbarHelper.showMessage(this, text)
    //             }

    //             TrackingState.TRACKING -> {
    //                 // Have to switch to UI Thread to update View.
    //                 //this.runOnUiThread { fitToScanView.setVisibility(View.GONE) }
    //                 val text = String.format("Detected Image %d", augmentedImage.index)
    //                 Log.d(TAG, "drawAugmentedImages " + text)
    //                 // Create a new anchor for newly found images.
    //                 if (!augmentedImageMap.containsKey(augmentedImage.index)) {
    //                     val centerPoseAnchor = augmentedImage.createAnchor(augmentedImage.centerPose)
    //                     augmentedImageMap.put(
    //                             augmentedImage.index, Pair.create(augmentedImage, centerPoseAnchor))
    //                 }
    //             }

    //             TrackingState.STOPPED -> augmentedImageMap.remove(augmentedImage.index)

    //             else -> {
    //             }
    //         }
    //     }

    //     // Draw all images in augmentedImageMap
    //     for (pair in augmentedImageMap.values) {
    //         val augmentedImage = pair.first
    //         val centerAnchor = augmentedImageMap.get(augmentedImage.getIndex())?.second
    //         when (augmentedImage.getTrackingState()) {
    //             TrackingState.TRACKING -> augmentedImageRenderer.draw(
    //                     viewmtx, projmtx, augmentedImage, centerAnchor!!, colorCorrectionRgba)
    //             else -> {
    //             }
    //         }
    //     }
    // }


    private fun configureSession(): Config {
        val config: Config = Config(session)
        session?.configure(config)
        return config
    }

    private fun drawLine(firstAnchor: Anchor, secondAnchor: Anchor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val firstAnchorNode = AnchorNode(firstAnchor)
            startNodeArray.add(firstAnchorNode)

            val secondAnchorNode = AnchorNode(secondAnchor)
            endNodeArray.add(secondAnchorNode)

            firstAnchorNode.setParent(getUI_ArSceneView().arSceneView.scene)
            secondAnchorNode.setParent(getUI_ArSceneView().arSceneView.scene)

            MaterialFactory.makeOpaqueWithColor(this@ARPluginActivity, com.google.ar.sceneform.rendering.Color(0.33f, 0.87f, 0f))
                    .thenAccept { material ->
                        val sphere = ShapeFactory.makeSphere(0.02f, Vector3(0.0f, 0.0f, 0.0f), material)
                        sphereNodeArray.add(Node().apply {
                            setParent(secondAnchorNode)
                            localPosition = Vector3.zero()
                            renderable = sphere
                        })
                    }

            val firstWorldPosition = firstAnchorNode.worldPosition
            val secondWorldPosition = secondAnchorNode.worldPosition

            val difference = Vector3.subtract(firstWorldPosition, secondWorldPosition)
            val directionFromTopToBottom = difference.normalized()
            val rotationFromAToB = Quaternion.lookRotation(directionFromTopToBottom, Vector3.up())

            MaterialFactory.makeOpaqueWithColor(this@ARPluginActivity, com.google.ar.sceneform.rendering.Color(0.33f, 0.87f, 0f))
                    .thenAccept { material ->
                        val lineMode = ShapeFactory.makeCube(Vector3(0.01f, 0.01f, difference.length()), Vector3.zero(), material)
                        val lineNode = Node().apply {
                            setParent(firstAnchorNode)
                                renderable = lineMode
                                worldPosition = Vector3.add(firstWorldPosition, secondWorldPosition).scaled(0.5f)
                                worldRotation = rotationFromAToB
                        }
                        lineNodeArray.add(Node().apply {
                            setParent(firstAnchorNode)
                            renderable = lineMode
                            worldPosition = Vector3.add(firstWorldPosition, secondWorldPosition).scaled(0.5f)
                            worldRotation = rotationFromAToB
                        })

                        ViewRenderable.builder()
                                .setView(this@ARPluginActivity, getRenderableTextId())
                                .build()
                                .thenAccept { it ->
                                    (it.view as TextView).text = "${String.format("%.1f", length)}${unitTxt}"
                                    it.isShadowCaster = false
                                    FaceToCameraNode().apply {
                                        setParent(lineNode)
                                        localRotation = Quaternion.axisAngle(Vector3(0f, 1f, 0f), 90f)
                                        localPosition = Vector3(0f, 0.02f, 0f)
                                        renderable = it
                                    }
                                }
                    }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
