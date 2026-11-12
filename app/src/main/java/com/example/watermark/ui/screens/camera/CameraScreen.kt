package com.example.watermark.ui.screens.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.example.watermark.PhotoCache
import com.example.watermark.data.PhotoDao
import com.example.watermark.data.SettingsRepository
import com.example.watermark.data.WatermarkPosition
import com.example.watermark.data.WatermarkSettings
import com.example.watermark.domain.LocationProvider
import com.example.watermark.domain.WatermarkEngine
import com.example.watermark.ui.components.BreathingDot
import com.example.watermark.ui.components.GlassIconButton
import com.example.watermark.ui.components.WmIcon
import com.example.watermark.ui.components.WmIconView
import com.example.watermark.ui.components.StampWatermarkView
import com.example.watermark.ui.components.StampBrandMark
import com.example.watermark.ui.components.StampMapThumb
import com.example.watermark.ui.components.WatermarkView
import com.example.watermark.ui.components.stampHasPlate
import com.example.watermark.ui.components.stampIsPill
import com.example.watermark.ui.components.stampSizeScale
import com.example.watermark.ui.theme.WmColor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors

/** CameraX 会话句柄。 */
private class CameraSession {
    var provider: ProcessCameraProvider? = null
    var imageCapture: ImageCapture? = null
    var preview: Preview? = null
    var camera: androidx.camera.core.Camera? = null
}

/**
 * 音量键快门桥：MainActivity 收到音量键时调用这里注册的处理函数，
 * 只有相机页注册了回调才消费按键，其它页面音量键照常调音量。
 */
object VolumeShutterBridge {
    var handler: (() -> Unit)? = null
}

@Composable
fun CameraScreen(
    navController: NavHostController,
    settingsRepository: SettingsRepository,
    photoDao: PhotoDao
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(
        SettingsRepository.defaultSettings,
        lifecycleOwner.lifecycle
    )
    val photos by photoDao.getAll().collectAsStateWithLifecycle(emptyList())

    val session = remember { CameraSession() }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    val textureViewRef = remember { mutableStateOf<TextureView?>(null) }
    var currentLocation by remember { mutableStateOf<String?>(null) }
    var currentFix by remember { mutableStateOf<LocationProvider.Fix?>(null) }
    var lensFacing by rememberSaveable { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    var loadingCapture by remember { mutableStateOf(false) }
    var retryTick by remember { mutableStateOf(0) }
    // 拍摄工具状态都以设置仓为准，改一项就落盘，下次进来还在这套参数上
    val gridOn = settings.showGrid
    val levelOn = settings.showLevel
    val ratio = settings.cameraRatio
    val timerSec = settings.cameraTimerSec
    val burst = settings.cameraBurst
    val flashMode = settings.cameraFlash
    val scope = rememberCoroutineScope()
    fun updateSettings(block: (WatermarkSettings) -> WatermarkSettings) {
        scope.launch { settingsRepository.saveSettings(block(settings)) }
    }
    // 变焦：pinch 手势驱动，记在本地状态里
    var zoomRatio by remember { mutableStateOf(1f) }
    var zoomRange by remember { mutableStateOf(1f..6f) }
    var showZoomLabel by remember { mutableStateOf(false) }
    // 定时拍照倒计时
    var countdownLeft by remember { mutableStateOf(0) }
    // 水平仪 roll 角（度），0 表示放平
    var rollDeg by remember { mutableStateOf(0f) }

    // 定位一次即可，失败就给个明确文案
    LaunchedEffect(Unit) {
        currentLocation = try {
            val fix = LocationProvider(context).getBestFix()
            currentFix = fix
            fix?.address
        } catch (e: Exception) {
            Log.e("CameraScreen", "Location fetch failed", e)
            null
        }
    }

    LaunchedEffect(lensFacing, retryTick) {
        // 等 TextureView 真正挂上再绑定，避免首帧 ref 为空导致反复重启、cameraReady 一直 false
        val textureView = snapshotFlow { textureViewRef.value }.filterNotNull().first()
        cameraReady = false
        cameraError = null
        try {
            val cameraProvider = withContext(Dispatchers.IO) {
                ProcessCameraProvider.getInstance(context).get()
            }
            val surface = Surface(textureView.surfaceTexture)
            Log.d("CameraScreen", "binding preview for surface=$surface")
            val preview = Preview.Builder().build().also { p ->
                p.setSurfaceProvider { request ->
                    request.provideSurface(surface, ContextCompat.getMainExecutor(context)) { }
                }
            }
            val imgCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            cameraProvider.unbindAll()
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            val boundCamera = cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imgCapture)

            session.provider = cameraProvider
            session.imageCapture = imgCapture
            session.preview = preview
            session.camera = boundCamera
            // 变焦范围以当前镜头为准；闪光灯按设置即时生效
            boundCamera.cameraInfo.zoomState.value?.let { zs ->
                zoomRange = zs.minZoomRatio..zs.maxZoomRatio
            }
            imgCapture.flashMode = flashModeToX(flashMode)
            zoomRatio = 1f
            cameraReady = true
            Log.d("CameraScreen", "camera ready")
        } catch (e: CancellationException) {
            Log.d("CameraScreen", "camera init cancelled")
        } catch (e: Exception) {
            cameraError = "相机初始化失败：${e.message}"
            Log.e("CameraScreen", "Camera init failed", e)
        }
    }

    val latestSession by rememberUpdatedState(session)
    DisposableEffect(lifecycleOwner.lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                // 退到后台/拍完跳预览页时解绑，回前台/返回相机页必须重新绑一次，
                // 否则会话停留 DETACHED，预览是冻结帧、快门点了直接失败。
                Lifecycle.Event.ON_STOP -> latestSession.provider?.unbindAll()
                Lifecycle.Event.ON_START -> retryTick++
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            loadingCapture = true
            val saved = PhotoCache.saveFromUri(it, context)
            loadingCapture = false
            saveAndNavigate(saved?.let { listOf(it) } ?: emptyList(), navController)
        }
    }

    // 时间每秒刷新，水印跟着走
    var timestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            timestamp = System.currentTimeMillis()
        }
    }
    // 附加字段：方位角/气压计常驻订阅，天气按坐标拉取（失败静默降级）
    var weather by remember { mutableStateOf<com.example.watermark.domain.ExtraFields.Weather?>(null) }
    LaunchedEffect(Unit) {
        com.example.watermark.domain.Bearing.register(context)
        com.example.watermark.domain.Barometer.register(context)
    }
    LaunchedEffect(currentFix) {
        val fix = currentFix ?: return@LaunchedEffect
        weather = withContext(Dispatchers.IO) {
            com.example.watermark.domain.WeatherProvider.fetch(fix.latitude, fix.longitude)
        }
    }
    val extraText = com.example.watermark.domain.ExtraFields.compose(
        context = context,
        settings = settings,
        location = currentFix?.let { f ->
            android.location.Location("fix").apply {
                latitude = f.latitude
                longitude = f.longitude
                f.altitude?.let { setAltitude(it) }
            }
        },
        weather = weather,
        timestampMs = timestamp
    )
    val wmText = remember(settings, currentLocation, timestamp, extraText) {
        WatermarkEngine().buildWatermarkText(settings, currentLocation, timestamp, extraText = extraText)
    }
    val stampParts = remember(settings, currentLocation, currentFix, timestamp, extraText) {
        WatermarkEngine().buildStampParts(
            settings = settings,
            currentLocation = currentLocation,
            latitude = currentFix?.latitude,
            longitude = currentFix?.longitude,
            timestampMs = timestamp,
            extraText = extraText
        )
    }

    var tipVisible by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(4200)
        tipVisible = false
    }

    // 闪光灯、变焦随设置/手势即时下发
    LaunchedEffect(flashMode) {
        session.imageCapture?.flashMode = flashModeToX(flashMode)
    }
    LaunchedEffect(zoomRatio) {
        session.camera?.cameraControl?.setZoomRatio(zoomRatio)
        if (zoomRatio > 1.02f) {
            showZoomLabel = true
            delay(1300)
            showZoomLabel = false
        }
    }

    // 水平仪：用旋转矢量算 roll，0° 表示机身放平
    LaunchedEffect(levelOn) {
        if (!levelOn) {
            rollDeg = 0f
            return@LaunchedEffect
        }
        val sm = context.getSystemService(android.content.Context.SENSOR_SERVICE)
            as? android.hardware.SensorManager ?: return@LaunchedEffect
        val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
            ?: sm.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
            ?: return@LaunchedEffect
        val listener = object : android.hardware.SensorEventListener {
            private val rv = FloatArray(9)
            private val ori = FloatArray(3)
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                if (event.values.size >= 3) {
                    android.hardware.SensorManager.getRotationMatrixFromVector(rv, event.values)
                    android.hardware.SensorManager.getOrientation(rv, ori)
                    rollDeg = Math.toDegrees(ori[2].toDouble()).toFloat()
                }
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, android.hardware.SensorManager.SENSOR_DELAY_UI)
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            sm.unregisterListener(listener)
        }
    }

    // 拍照主流程：定时倒计时 → 连拍 N 张 → 进预览页
    fun startCaptureSequence() {
        if (loadingCapture || !cameraReady) {
            Log.d("CameraScreen", "capture skipped loading=$loadingCapture ready=$cameraReady")
            return
        }
        val target = burst.coerceAtLeast(1)
        Log.d("CameraScreen", "capture start target=$target timer=$timerSec ratio=$ratio")
        loadingCapture = true
        val shots = mutableListOf<Uri>()
        var done = 0

        fun finish() {
            loadingCapture = false
            Log.d("CameraScreen", "capture finish got=${shots.size}/$target")
            saveAndNavigate(shots, navController)
        }

        fun step() {
            if (done >= target) {
                finish()
                return
            }
            capturePhoto(
                imageCaptureRef = session.imageCapture,
                executor = executor,
                context = context,
                ratio = ratio,
                onCaptured = { bitmap ->
                    val u = PhotoCache.saveForBatch(bitmap, context)
                    u?.let { shots.add(it) }
                    done++
                    Log.d("CameraScreen", "burst captured $done/$target, uri=$u")
                    if (done >= target || u == null) {
                        finish()
                    } else {
                        // 真机连续 takePicture 容易 busy，留一点间隔
                        scope.launch {
                            delay(200)
                            step()
                        }
                    }
                },
                onError = { msg ->
                    Log.w("CameraScreen", "burst error at ${done + 1}/$target: $msg")
                    if (shots.isNotEmpty()) {
                        finish()
                    } else {
                        loadingCapture = false
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        step()
    }

    var timerJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val onShutter: () -> Unit = onShutter@{
        Log.d(
            "CameraScreen",
            "shutter pressed loading=$loadingCapture ready=$cameraReady timer=$timerSec burst=$burst countdown=$countdownLeft"
        )
        if (loadingCapture) return@onShutter
        if (countdownLeft > 0) {
            // 倒计时中再按一次 = 取消
            timerJob?.cancel()
            timerJob = null
            countdownLeft = 0
            return@onShutter
        }
        if (timerSec > 0) {
            timerJob = scope.launch {
                countdownLeft = timerSec
                while (countdownLeft > 0) {
                    delay(1000)
                    countdownLeft--
                }
                timerJob = null
                startCaptureSequence()
            }
        } else {
            startCaptureSequence()
        }
    }
    val latestOnShutter by rememberUpdatedState(onShutter)

    // 音量键快门：只在相机页、且开关打开时消费
    DisposableEffect(Unit) {
        VolumeShutterBridge.handler = { latestOnShutter() }
        onDispose { VolumeShutterBridge.handler = null }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(WmColor.Ink)) {
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        // 安全框按出片比例走：4:3 竖幅 / 16:9 竖幅 / 1:1 / 全屏
        val frameWidth = maxWidth * 0.754f
        val frameHeight = when (ratio) {
            "1:1" -> frameWidth
            "16:9" -> frameWidth * 16f / 9f
            "FULL" -> maxHeight * 0.82f
            else -> frameWidth * 4f / 3f
        }.coerceAtMost(maxHeight * 0.92f)
        val centerY = maxHeight * 0.44f
        val halfW = frameWidth / 2
        val cyOffset = centerY - maxHeight / 2

        // 1. 取景层
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            textureViewRef.value = this@apply
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            textureViewRef.value = null
                            session.provider?.unbindAll()
                            return true
                        }
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, gestureZoom, _ ->
                        if (gestureZoom != 1f) {
                            zoomRatio = (zoomRatio * gestureZoom)
                                .coerceIn(zoomRange.start, zoomRange.endInclusive)
                        }
                    }
                }
        )

        // 2. 上下压暗，让浮层有落点
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color(0xCC05070A),
                        0.32f to Color(0x0005070A),
                        0.72f to Color(0x0005070A),
                        1f to Color(0xE605070A)
                    )
                )
        )

        // 3. 三分网格（整屏）+ 3:4 安全框四角
        Canvas(Modifier.fillMaxSize()) {
            if (gridOn) {
                for (i in 1..2) {
                    val x = size.width * i / 3f
                    val y = size.height * i / 3f
                    drawLine(Color(0x29FFFFFF), Offset(x, 0f), Offset(x, size.height), 1.dp.toPx())
                    drawLine(Color(0x29FFFFFF), Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
                }
            }
            val cx = size.width / 2f
            val cy = size.height / 2f + cyOffset.toPx()
            val halfWpx = halfW.toPx()
            val halfHpx = (frameHeight / 2).toPx()
            val left = cx - halfWpx
            val right = cx + halfWpx
            val top = cy - halfHpx
            val bottom = cy + halfHpx
            val corner = 20.dp.toPx()
            val stroke = 1.5.dp.toPx()
            val c = Color(0x8CFFFFFF)

            // 四角括号
            listOf(
                Triple(Offset(left, top), 1f, 1f),
                Triple(Offset(right, top), -1f, 1f),
                Triple(Offset(left, bottom), 1f, -1f),
                Triple(Offset(right, bottom), -1f, -1f)
            ).forEach { (p, sx, sy) ->
                drawLine(c, p, Offset(p.x + corner * sx, p.y), stroke, StrokeCap.Round)
                drawLine(c, p, Offset(p.x, p.y + corner * sy), stroke, StrokeCap.Round)
            }

            // 水平仪单独一层，画在水印之上
        }


        // 4. 顶部：位置胶囊 + 网格 + 设置
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(start = 16.dp, end = 16.dp)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 14.dp),
            verticalAlignment = Alignment.Top
        ) {
            LocationChip(
                location = currentLocation,
                manual = if (settings.lockTimeLocation) "" else settings.locationOverride
            )
            Box(Modifier.weight(1f))
            GlassIconButton(
                icon = WmIcon.Grid,
                onClick = { updateSettings { it.copy(showGrid = !gridOn) } },
                tint = if (gridOn) WmColor.Amber else WmColor.TextPrimary,
                size = 38.dp
            )
            Spacer(Modifier.width(8.dp))
            GlassIconButton(icon = WmIcon.Gear, onClick = { navController.navigate("settings") }, size = 38.dp)
        }

        // 5. 水印（真实渲染，跟着设置走；底部让位给拍摄控件）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 150.dp, bottom = navBottom + 318.dp),
            contentAlignment = when (settings.watermarkPosition) {
                WatermarkPosition.TOP_LEFT -> Alignment.TopStart
                WatermarkPosition.TOP_RIGHT -> Alignment.TopEnd
                WatermarkPosition.BOTTOM_LEFT -> Alignment.BottomStart
                WatermarkPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
                WatermarkPosition.CENTER -> Alignment.Center
            }
        ) {
            Column(
                horizontalAlignment = if (settings.watermarkPosition == WatermarkPosition.TOP_RIGHT ||
                    settings.watermarkPosition == WatermarkPosition.BOTTOM_RIGHT
                ) Alignment.End else Alignment.Start
            ) {
                AnimatedVisibility(visible = tipVisible, enter = fadeIn(), exit = fadeOut()) {
                    Row(
                        modifier = Modifier
                            .padding(bottom = 7.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x29FFB43A))
                            .border(1.dp, Color(0x6BFFB43A), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        WmIconView(WmIcon.Plus, tint = Color(0xFFFFD79A), size = 13.dp)
                        Text(
                            " 拖动水印可微调位置",
                            color = Color(0xFFFFD79A),
                            fontSize = 11.sp
                        )
                    }
                }
                if (com.example.watermark.domain.IndustryLayout.effectiveLayout(settings) == com.example.watermark.data.WatermarkLayout.STAMP) {
                    StampWatermarkView(
                        time = stampParts.time,
                        date = stampParts.date,
                        week = stampParts.week,
                        location = stampParts.location,
                        coordinate = stampParts.coordinate,
                        title = stampParts.title,
                        extra = stampParts.extra,
                        details = stampParts.details.map { it.asText() },
                        color = Color(settings.watermarkColor),
                        bgColor = Color(settings.watermarkBgColor),
                        scale = 0.95f * stampSizeScale(settings.watermarkSize),
                        plate = stampHasPlate(settings.watermarkStyle),
                        pill = stampIsPill(settings.watermarkStyle),
                        stroke = settings.watermarkStyle == com.example.watermark.data.WatermarkStyle.STROKE,
                        modifier = Modifier.widthIn(max = 330.dp)
                    )
                } else {
                    WatermarkView(
                        settings = settings,
                        text = wmText,
                        details = stampParts.details.map { it.asText() },
                        fontSize = (21f * stampSizeScale(settings.watermarkSize)).sp,
                        maxLines = 3,
                        modifier = Modifier.widthIn(max = 240.dp)
                    )
                }
            }
        }

        // 右上角：坐标地图（两种排版都带，不挡控制）
        StampMapThumb(
            latitude = currentFix?.latitude,
            longitude = currentFix?.longitude,
            scale = 0.92f,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 22.dp, top = 156.dp)
        )

        // 水平仪（放在水印之后，避免被底板压住）
        if (levelOn) {
            Canvas(Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                // 往上放到画面 30% 处，避免压住居中的水印
                val cy = size.height * 0.30f
                val levelColor = if (kotlin.math.abs(rollDeg) < 1f) WmColor.Amber else Color(0x8CFFFFFF)
                val rad = Math.toRadians(rollDeg.toDouble())
                val halfLen = 70.dp.toPx()
                val dx = (halfLen * kotlin.math.cos(rad)).toFloat()
                val dy = (halfLen * kotlin.math.sin(rad)).toFloat()
                drawLine(
                    levelColor,
                    Offset(cx - dx, cy - dy),
                    Offset(cx + dx, cy + dy),
                    if (kotlin.math.abs(rollDeg) < 1f) 2.dp.toPx() else 1.2.dp.toPx(),
                    StrokeCap.Round
                )
                drawLine(levelColor, Offset(cx - 7.dp.toPx(), cy), Offset(cx + 7.dp.toPx(), cy), 1.dp.toPx())
                drawLine(levelColor, Offset(cx, cy - 7.dp.toPx()), Offset(cx, cy + 7.dp.toPx()), 1.dp.toPx())
            }
        }

        // 右下角品牌角标（两种排版都带）
        StampBrandMark(
            code = stampParts.code,
            scale = 0.95f,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = navBottom + 320.dp)
        )

        // 5.5 拍摄工具条：比例 / 定时 / 连拍 / 闪光 / 水平仪
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = navBottom + 262.dp)
                .padding(horizontal = 16.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.width(12.dp))
            ShootTool(
                label = ratioLabel(ratio),
                active = ratio != "4:3",
                onClick = {
                    val next = when (ratio) {
                        "4:3" -> "16:9"
                        "16:9" -> "1:1"
                        "1:1" -> "FULL"
                        else -> "4:3"
                    }
                    updateSettings { it.copy(cameraRatio = next) }
                }
            )
            ShootTool(
                label = if (timerSec == 0) "定时" else "${timerSec}s",
                active = timerSec > 0,
                onClick = {
                    val next = when (timerSec) {
                        0 -> 3
                        3 -> 5
                        5 -> 10
                        else -> 0
                    }
                    updateSettings { it.copy(cameraTimerSec = next) }
                }
            )
            ShootTool(
                label = if (burst <= 1) "连拍" else "连拍 $burst",
                active = burst > 1,
                onClick = {
                    val next = when (burst) {
                        1 -> 3
                        3 -> 5
                        else -> 1
                    }
                    updateSettings { it.copy(cameraBurst = next) }
                }
            )
            ShootTool(
                label = when (flashMode) {
                    1 -> "闪光 开"
                    2 -> "闪光 自动"
                    else -> "闪光"
                },
                active = flashMode != 0,
                onClick = {
                    updateSettings { it.copy(cameraFlash = (flashMode + 1) % 3) }
                }
            )
            ShootTool(
                label = "水平仪",
                active = levelOn,
                onClick = { updateSettings { it.copy(showLevel = !levelOn) } }
            )
            Spacer(Modifier.width(12.dp))
        }

        // 变焦读数
        if (showZoomLabel) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = navBottom + 300.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x9E101318))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(14.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = String.format("%.1fx", zoomRatio),
                    color = WmColor.AmberText,
                    fontSize = 11.sp
                )
            }
        }

        // 定时倒计时
        if (countdownLeft > 0) {
            Box(Modifier.fillMaxSize().background(Color(0x59000000)), contentAlignment = Alignment.Center) {
                Text(
                    text = countdownLeft.toString(),
                    color = WmColor.AmberText,
                    fontSize = 72.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // 6. 底部：最近照片 / 快门 / 翻转
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = navBottom + 112.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val thumb = rememberLatestThumbnail(photos.firstOrNull()?.uri)
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF23282F))
                    .border(1.5.dp, Color(0x80FFFFFF), RoundedCornerShape(12.dp))
                    .clickable { navController.navigate("gallery") },
                contentAlignment = Alignment.Center
            ) {
                if (thumb != null) {
                    androidx.compose.foundation.Image(
                        bitmap = thumb.asImageBitmap(),
                        contentDescription = "最近照片",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    WmIconView(WmIcon.Gallery, tint = WmColor.TextSecondary, size = 22.dp)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(15.dp)
                        .background(Color(0x8C000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (photos.isEmpty()) "相册" else "相册 ${photos.size}",
                        color = Color(0xFFDDE2E9),
                        fontSize = 9.sp
                    )
                }
            }

            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                ShutterButton(
                    enabled = cameraReady && !loadingCapture,
                    onClick = { latestOnShutter() }
                )
            }

            Column(
                modifier = Modifier.width(54.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                GlassIconButton(
                    icon = WmIcon.Flip,
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK)
                            CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
                    },
                    size = 46.dp
                )
                Text(
                    text = "翻转",
                    color = WmColor.TextSecondary,
                    fontSize = 10.sp,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.padding(top = 7.dp)
                )
            }
        }

        // 7. 状态层
        when {
            cameraError != null -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = cameraError!!,
                        color = WmColor.TextSecondary,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "点这里重试",
                        color = WmColor.AmberText,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .background(Color(0x1FFFB43A))
                            .clickable {
                                cameraError = null
                                retryTick++
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
            !cameraReady -> {
                Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WmColor.Amber, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
                }
            }
        }

        if (loadingCapture) {
            Box(Modifier.fillMaxSize().background(Color(0x66000000)), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = WmColor.Amber, strokeWidth = 2.5.dp, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** 比例标签按竖屏方向显示：存储仍是横向命名（16:9），屏上写 9:16 更符合竖拍直觉。 */
private fun ratioLabel(ratio: String): String = when (ratio) {
    "16:9" -> "9:16"
    "FULL" -> "全屏"
    else -> ratio
}

/** 拍摄工具条上的小胶囊。 */
@Composable
private fun ShootTool(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(if (active) Color(0x33FFB43A) else Color(0x66000000))
            .border(
                1.dp,
                if (active) Color(0x8CFFB43A) else Color(0x1FFFFFFF),
                RoundedCornerShape(15.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (active) Color(0xFFFFCE86) else Color(0xFFDDE2E9),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun LocationChip(location: String?, manual: String) {
    val text = when {
        manual.isNotBlank() -> manual
        location.isNullOrBlank() -> "定位失败 · 用手动位置"
        else -> location
    }
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Color(0x9E101318))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WmIconView(
            type = WmIcon.Pin,
            tint = WmColor.AmberText,
            size = 14.dp
        )
        Text(
            text = text,
            color = Color(0xFFE9EDF2),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 7.dp)
                .widthIn(max = 190.dp)
        )
        BreathingDot(
            color = if (manual.isNotBlank() || !location.isNullOrBlank()) WmColor.Amber else WmColor.TextTertiary,
            size = 6.dp,
            modifier = Modifier.padding(start = 5.dp)
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val inner by animateDpAsState(if (pressed) 52.dp else 62.dp, tween(90), label = "shutterInner")
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, tween(90), label = "shutterScale")
    Box(
        modifier = modifier.size(94.dp),
        contentAlignment = Alignment.Center
    ) {
        // 外圈细线
        Box(
            modifier = Modifier
                .size(94.dp)
                .clip(CircleShape)
                .border(1.dp, Color(0x29FFFFFF), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(80.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    if (enabled) Brush.sweepGradient(
                        listOf(WmColor.AmberSoft, WmColor.Amber, WmColor.AmberDeep, WmColor.AmberSoft)
                    ) else Brush.sweepGradient(listOf(Color(0xFF3A3F47), Color(0xFF262A31)))
                )
                .clickable(interactionSource = interaction, indication = null, enabled = enabled) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(inner)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFEDF0F4)))
                    )
            )
        }
    }
}

/** 最近一张照片的缩略图，异步解码，不阻塞主线程。 */
@Composable
private fun rememberLatestThumbnail(uriString: String?): Bitmap? {
    val context = LocalContext.current
    var bitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(uriString) {
        val uri = uriString ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(uri))?.use { stream ->
                    val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeStream(stream, null, opts)
                }
            }.getOrNull()
        }
    }
    return bitmap
}

/** 解码拍照文件：长边压到 2400，既够水印导出，又不至于 OOM。 */
private fun decodeSampled(file: File, maxDim: Int = 2400): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
    // 按 EXIF 方向摆正，否则竖拍的照片进预览页会横躺
    val rotation = try {
        when (android.media.ExifInterface(file.absolutePath).getAttributeInt(
            android.media.ExifInterface.TAG_ORIENTATION,
            android.media.ExifInterface.ORIENTATION_NORMAL
        )) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (e: Exception) {
        0f
    }
    if (rotation == 0f) return bitmap
    val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

fun capturePhoto(
    imageCaptureRef: ImageCapture?,
    executor: Executor,
    context: android.content.Context,
    ratio: String = "4:3",
    onCaptured: (Bitmap) -> Unit,
    onError: (String) -> Unit
) {
    if (imageCaptureRef == null) {
        onError("相机不可用")
        return
    }
    val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}_${System.nanoTime()}.jpg")
    val outputOptions = ImageCapture.OutputFileOptions.Builder(file).build()

    imageCaptureRef.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                // 解码 + EXIF 旋转都放到后台线程，主线程只接收结果（否则大图解码会 ANR
                executor.execute {
                    try {
                        val decoded = decodeSampled(file)
                        file.delete()
                        val bitmap = decoded?.let { cropToRatio(it, ratio) }
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            if (bitmap != null) onCaptured(bitmap) else onError("拍照失败：无法解码")
                        }
                    } catch (e: Exception) {
                        file.delete()
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            onError("拍照失败: ${e.message}")
                        }
                    }
                }
            }

            override fun onError(exception: ImageCaptureException) {
                onError("拍照失败: ${exception.message}")
                file.delete()
            }
        }
    )
}

/** 出片比例裁切：以画面中心为基准等比裁，FULL 表示不裁。 */
private fun cropToRatio(bitmap: Bitmap, ratio: String): Bitmap {
    val target = when (ratio) {
        "4:3" -> 3f / 4f
        "16:9" -> 9f / 16f
        "1:1" -> 1f
        else -> return bitmap
    }
    val w = bitmap.width
    val h = bitmap.height
    val current = h.toFloat() / w.toFloat()
    if (kotlin.math.abs(current - target) < 0.01f) return bitmap
    val (cw, ch) = if (current > target) {
        // 太高 → 裁高度
        w to (w * target).toInt()
    } else {
        (h / target).toInt() to h
    }
    val x = (w - cw) / 2
    val y = (h - ch) / 2
    val cropped = Bitmap.createBitmap(bitmap, x, y, cw, ch)
    if (cropped !== bitmap) bitmap.recycle()
    return cropped
}

private fun flashModeToX(mode: Int): Int = when (mode) {
    1 -> ImageCapture.FLASH_MODE_ON
    2 -> ImageCapture.FLASH_MODE_AUTO
    else -> ImageCapture.FLASH_MODE_OFF
}

/** 连拍批次：预览页保存时按这个列表逐张导出（进程内传递，避免导航参数塞多图）。 */
object BurstBatch {
    var pending: List<Uri> = emptyList()
}

fun saveAndNavigate(uris: List<Uri>, navController: NavHostController) {
    val first = uris.firstOrNull() ?: return
    BurstBatch.pending = uris
    navController.navigate("watermark_preview/${Uri.encode(first.toString())}")
}
