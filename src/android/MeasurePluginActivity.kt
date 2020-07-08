package com.gj.arcoredraw

import android.annotation.SuppressLint
import android.os.Build
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import com.google.ar.core.Anchor
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Quaternion
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.MaterialFactory
import com.google.ar.sceneform.rendering.ShapeFactory
import com.google.ar.sceneform.rendering.ViewRenderable
import com.google.ar.sceneform.rendering.Color
import com.google.ar.sceneform.ux.ArFragment

/**
 * Based on https://github.com/Terran-Marine/ARCoreMeasuredDistance
 */

class MeasurePluginActivity : AppCompatActivity() {
    var allowMultiple: Boolean = false
    var unit: String = "cm"
    var unitTxt: String = "cm"
    var length: Double = 0.0;

    private val measureArray = arrayListOf<String>()

    private val dataArray = arrayListOf<AnchorInfoBean>()
    private val lineNodeArray = arrayListOf<Node>()
    private val sphereNodeArray = arrayListOf<Node>()
    private val startNodeArray = arrayListOf<Node>()
    private val endNodeArray = arrayListOf<Node>()

    private  lateinit var startNode: AnchorNode

    fun getLayoutId(): Int {
        return getResources().getIdentifier("activity_measureplugin", "layout", getPackageName())
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val extras: Bundle = getIntent().getExtras()

        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
        setContentView(getLayoutId())

        allowMultiple = savedInstanceState?.getBoolean("allowMultiple") ?: false
        unit = savedInstanceState?.getString("unit") ?: extras.getString("unit")
        unitTxt = savedInstanceState?.getString("unitTxt") ?: extras.getString("unitTxt")

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
            MeasurePluginCallback.onFinish(measureArray.toTypedArray())
            finish()
        }
        initAr()
    }

    @SuppressLint("NewApi")
    private fun initAr() {
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
                MeasurePluginCallback.onUpdate(lengthTxt)

                drawLine(startAnchor, endAnchor)
            } else {
                startNode = AnchorNode(hitResult.createAnchor())
                startNode.setParent(getUI_ArSceneView().arSceneView.scene)
                MaterialFactory.makeOpaqueWithColor(this@MeasurePluginActivity, com.google.ar.sceneform.rendering.Color(0.33f, 0.87f, 0f))
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
    }

    private fun drawLine(firstAnchor: Anchor, secondAnchor: Anchor) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val firstAnchorNode = AnchorNode(firstAnchor)
            startNodeArray.add(firstAnchorNode)

            val secondAnchorNode = AnchorNode(secondAnchor)
            endNodeArray.add(secondAnchorNode)

            firstAnchorNode.setParent(getUI_ArSceneView().arSceneView.scene)
            secondAnchorNode.setParent(getUI_ArSceneView().arSceneView.scene)

            MaterialFactory.makeOpaqueWithColor(this@MeasurePluginActivity, com.google.ar.sceneform.rendering.Color(0.33f, 0.87f, 0f))
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

            MaterialFactory.makeOpaqueWithColor(this@MeasurePluginActivity, com.google.ar.sceneform.rendering.Color(0.33f, 0.87f, 0f))
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
                                .setView(this@MeasurePluginActivity, getRenderableTextId())
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