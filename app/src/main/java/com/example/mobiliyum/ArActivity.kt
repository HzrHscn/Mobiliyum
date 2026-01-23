package com.example.mobiliyum

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.android.filament.Engine
import com.google.ar.core.Anchor
import com.google.ar.core.Config
import com.google.ar.core.Plane
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.loaders.ModelLoader
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberView
//import io.github.sceneview.math.Float3
import kotlin.math.PI

class ArActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modelPath = intent.getStringExtra("model_path") ?: return
        val modelScale = intent.getFloatExtra("model_scale", 1f)

        setContent {
            MaterialTheme {
                ARScreen(
                    modelPath = modelPath,
                    initialScale = modelScale,
                    onClose = { finish() }
                )
            }
        }
    }
}

@Composable
fun ARScreen(
    modelPath: String,
    initialScale: Float,
    onClose: () -> Unit
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val nodes = rememberNodes()
    val view = rememberView(engine)

    var anchorPlaced by remember { mutableStateOf(false) }
    var modelNode by remember { mutableStateOf<ModelNode?>(null) }

    var rotation by remember { mutableStateOf(0f) }
    var scale by remember { mutableStateOf(initialScale) }

    Box(modifier = Modifier.fillMaxSize()) {

        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            view = view,
            childNodes = nodes,
            modelLoader = modelLoader,
            sessionConfiguration = { _, config ->
                config.lightEstimationMode =
                    Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.instantPlacementMode =
                    Config.InstantPlacementMode.LOCAL_Y_UP
            },
            planeRenderer = true,
            onSessionUpdated = { _, frame ->

                if (anchorPlaced) return@ARScene

                val plane = frame.getUpdatedPlanes()
                    .firstOrNull { it.type == Plane.Type.HORIZONTAL_UPWARD_FACING }
                    ?: return@ARScene

                val anchor = plane
                    .createAnchorOrNull(plane.centerPose)
                    ?: return@ARScene

                val anchorNode = AnchorNode(engine, anchor)

                val createdModelNode = ModelNode(
                    modelInstance = modelLoader.createModelInstance(modelPath),
                    scaleToUnits = initialScale
                )

                anchorNode.addChildNode(createdModelNode)
                nodes += anchorNode

                modelNode = createdModelNode
                anchorPlaced = true
            }
        )

        // 🔔 Zemin bulunana kadar uyarı
        if (!anchorPlaced) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Telefonu düz bir zemine tutun",
                    color = Color.White
                )
            }
        }

        // ❌ Kapat butonu
        Button(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Text("Kapat")
        }

        // 🔄 Alt bar – DÖNDÜRME
        if (anchorPlaced && modelNode != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            ) {
                Text("Döndür", color = Color.White)
                Slider(
                    value = rotation,
                    onValueChange = {
                        rotation = it
                        modelNode?.rotation = Float3(
                            0f,
                            (it * PI / 180f).toFloat(),
                            0f
                        )
                    },
                    valueRange = -180f..180f
                )
            }
        }

        // 🔍 Sağ bar – BÜYÜT / KÜÇÜLT
        if (anchorPlaced && modelNode != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            ) {
                Text("Boyut", color = Color.White)
                Slider(
                    value = scale,
                    onValueChange = {
                        scale = it.coerceIn(0.5f, 1.5f)
                        modelNode?.scale = Float3(scale, scale, scale)
                    },
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier
                        .height(200.dp)
                        .rotate(-90f)
                )
            }
        }
    }
}
