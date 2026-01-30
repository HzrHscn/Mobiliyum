package com.example.mobiliyum

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.ar.core.Config
import com.google.ar.core.Plane
import io.github.sceneview.ar.ARScene
import io.github.sceneview.ar.arcore.createAnchorOrNull
import io.github.sceneview.ar.arcore.getUpdatedPlanes
import io.github.sceneview.ar.node.AnchorNode
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp

class ArActivity : ComponentActivity() {

    private val TAG = "ArActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val modelPath = intent.getStringExtra("model_path") ?: run {
            Log.e(TAG, "❌ Model path yok!")
            finish()
            return
        }
        val modelScale = intent.getFloatExtra("model_scale", 0.5f) // ✅ Varsayılan küçültüldü
        val productName = intent.getStringExtra("product_name") ?: "Ürün"

        Log.d(TAG, "🚀 AR başlatıldı - Model: $modelPath, Scale: $modelScale")

        setContent {
            MaterialTheme {
                ARScreen(
                    modelPath = modelPath,
                    initialScale = modelScale,
                    productName = productName,
                    onClose = {
                        Log.d(TAG, "🔴 Kapatılıyor...")
                        try {
                            // Sadece activity'yi kapat, uygulamayı değil
                            finish()
                        } catch (e: Exception) {
                            Log.e(TAG, "Kapatma hatası: ${e.message}")
                            finish()
                        }
                    }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "💀 Activity destroyed")
    }
}

@Composable
fun ARScreen(
    modelPath: String,
    initialScale: Float,
    productName: String,
    onClose: () -> Unit
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val nodes = rememberNodes()

    var anchorPlaced by remember { mutableStateOf(false) }
    var modelNode by remember { mutableStateOf<ModelNode?>(null) }
    var anchorNode by remember { mutableStateOf<AnchorNode?>(null) }

    // Rotation state
    var rotationY by remember { mutableStateOf(0f) }

    // Model lock durumu
    var isLocked by remember { mutableStateOf(false) }

    // Plane detection durumu
    var planeDetected by remember { mutableStateOf(false) }

    // Bildirim
    var notification by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // İlk pozisyon kaydet
    var initialPosition by remember { mutableStateOf<Position?>(null) }
    var initialRotation by remember { mutableStateOf(0f) }

    fun showNotification(msg: String) {
        notification = msg
        scope.launch {
            delay(2500)
            notification = null
        }
    }

    // ✅ Model yerleştirme fonksiyonu
    fun placeModel(plane: Plane) {
        if (anchorPlaced) return

        try {
            // Anchor oluştur - ORTADA
            val anchor = plane.createAnchorOrNull(plane.centerPose) ?: return

            val newAnchorNode = AnchorNode(engine, anchor)

            // Model oluştur
            val model = ModelNode(
                modelInstance = modelLoader.createModelInstance(modelPath),
                scaleToUnits = initialScale
            ).apply {
                // Işıklandırma aktif
                isShadowCaster = true
                isShadowReceiver = true

                // ✅ Pivot noktasını merkeze al
                centerOrigin(Position(0f, 0f, 0f))
            }

            newAnchorNode.addChildNode(model)
            nodes += newAnchorNode

            modelNode = model
            anchorNode = newAnchorNode
            anchorPlaced = true

            // İlk pozisyonu kaydet
            initialPosition = model.worldPosition
            initialRotation = 0f

            showNotification("✅ Model yerleştirildi")
            Log.d("ARScreen", "✅ Model yerleştirildi - Scale: $initialScale")
        } catch (e: Exception) {
            Log.e("ARScreen", "❌ Hata: ${e.message}", e)
        }
    }

    // Back button
    BackHandler {
        onClose()
    }

    Box(modifier = Modifier.fillMaxSize()) {

        ARScene(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            modelLoader = modelLoader,
            childNodes = nodes,
            sessionConfiguration = { session, config ->
                // ✅ Gelişmiş ışıklandırma
                config.lightEstimationMode = Config.LightEstimationMode.ENVIRONMENTAL_HDR
                config.planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                config.focusMode = Config.FocusMode.AUTO

                // Instant placement kapat - daha stabil
                config.instantPlacementMode = Config.InstantPlacementMode.DISABLED

                // ✅ Update mode - sürekli ışık güncellemesi
                config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
            },
            planeRenderer = true,
            onSessionUpdated = { _, frame ->
                // ✅ Plane detection kontrolü
                val planes = frame.getUpdatedPlanes()
                    .filter {
                        it.type == Plane.Type.HORIZONTAL_UPWARD_FACING &&
                                it.trackingState == com.google.ar.core.TrackingState.TRACKING
                    }

                if (planes.isNotEmpty() && !planeDetected) {
                    planeDetected = true
                }

                // Model zaten yerleştirildiyse işlem yapma
                if (anchorPlaced) return@ARScene

                // Otomatik yerleştirme (ilk uygun plane bulunduğunda)
                val plane = planes.firstOrNull() ?: return@ARScene
                placeModel(plane)
            }
        )

        // ⏳ Yükleniyor
        if (!anchorPlaced) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 4.dp,
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = if (planeDetected)
                            "📍 Model yerleştiriliyor..."
                        else
                            "📱 Düz zemin aranıyor...",
                        color = Color.White,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }

        // ⚠️ Uyarı (üstte)
        if (anchorPlaced) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFA000))
                    .padding(vertical = 14.dp, horizontal = 16.dp)
                    .align(Alignment.TopCenter)
            ) {
                Text(
                    text = "⚠️ YASAL UYARI\n" +
                            "Bu AR görüntüsü sadece görselleştirme amaçlıdır ve ürünün gerçek " +
                            "boyutlarını tam olarak yansıtmayabilir. Satın alma öncesi ölçüleri " +
                            "mutlaka manuel olarak kontrol ediniz. Şirketimiz boyut uyumsuzluğundan " +
                            "sorumlu tutulamaz.",
                    color = Color.White,
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
            }
        }

        // 📢 Bildirim
        notification?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .background(Color(0xFF2196F3), shape = MaterialTheme.shapes.medium)
                    .padding(16.dp)
                    .align(Alignment.Center)
            ) {
                Text(
                    text = msg,
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            }
        }

        // ❌ Kapat
        Button(
            onClick = {
                Log.d("ARScreen", "🔴 Kapat tıklandı")
                // AR'ı temizle
                try {
                    modelNode?.let { model ->
                        anchorNode?.removeChildNode(model)
                    }
                    anchorNode?.destroy()
                    nodes.clear()
                } catch (e: Exception) {
                    Log.e("ARScreen", "Temizleme hatası: ${e.message}")
                }
                // Activity'yi kapat
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 16.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F))
        ) {
            Text("✕", color = Color.White, fontSize = 18.sp)
        }

        // 🔄 Sıfırla (tam sıfırlama)
        if (anchorPlaced) {
            Button(
                onClick = {
                    // Model ve anchor'ı tamamen kaldır
                    modelNode?.let { model ->
                        anchorNode?.removeChildNode(model)
                    }
                    anchorNode?.let { anchor ->
                        nodes -= anchor
                        anchor.destroy()
                    }

                    modelNode = null
                    anchorNode = null
                    anchorPlaced = false
                    rotationY = 0f
                    isLocked = false

                    showNotification("🔄 Model sıfırlandı. Yeniden yerleştirin.")
                    Log.d("ARScreen", "🔄 TAM SIFIRLAMA yapıldı")
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 60.dp, start = 16.dp),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2196F3))
            ) {
                Text("🔄", color = Color.White, fontSize = 18.sp)
            }
        }

        // 🔒 Sabitle/Serbest
        if (anchorPlaced && modelNode != null) {
            Button(
                onClick = {
                    isLocked = !isLocked

                    // ✅ Model hareket kontrolü
                    modelNode?.let { node ->
                        node.isEditable = !isLocked
                        Log.d("ARScreen", "Model isEditable: ${!isLocked}")
                    }

                    if (isLocked) {
                        showNotification("🔒 Sabitlendi - Sadece döndürebilirsiniz")
                    } else {
                        showNotification("🔓 Serbest - Taşıyabilirsiniz")
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 200.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isLocked) Color(0xFFD32F2F) else Color(0xFF4CAF50)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        if (isLocked) "🔒" else "📌",
                        fontSize = 18.sp
                    )
                    Text(
                        if (isLocked) "Kilitli" else "Sabitle",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }
            }
        }

        // 🔄 Döndürme Kontrolü
        if (anchorPlaced && modelNode != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Döndür",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                    Text(
                        "${rotationY.toInt()}°",
                        color = Color(0xFFFFEB3B),
                        fontSize = 20.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Slider(
                    value = rotationY,
                    onValueChange = { newAngle ->
                        rotationY = newAngle

                        modelNode?.let { node ->
                            // ✅ Y ekseni rotasyonu (Rotation objesi)
                            node.rotation = Rotation(
                                x = 0f,
                                y = newAngle,  // DERECE cinsinden
                                z = 0f
                            )
                        }

                        Log.d("ARScreen", "🔄 Rotation: $newAngle°")
                    },
                    valueRange = 0f..360f,
                    steps = 71,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color(0xFFFFEB3B),
                        inactiveTrackColor = Color.Gray
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Hızlı döndürme - Modern Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Üst sıra: -90°, -45°
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                rotationY = (rotationY - 90f).let { if (it < 0) it + 360f else it }
                                modelNode?.rotation = Rotation(0f, rotationY, 0f)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF424242)),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                "↶ 90°",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                rotationY = (rotationY - 45f).let { if (it < 0) it + 360f else it }
                                modelNode?.rotation = Rotation(0f, rotationY, 0f)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF616161)),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                "↶ 45°",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }

                    // Alt sıra: +45°, +90°
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                rotationY = (rotationY + 45f) % 360f
                                modelNode?.rotation = Rotation(0f, rotationY, 0f)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF616161)),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                "45° ↷",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = {
                                rotationY = (rotationY + 90f) % 360f
                                modelNode?.rotation = Rotation(0f, rotationY, 0f)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF424242)),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text(
                                "90° ↷",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}