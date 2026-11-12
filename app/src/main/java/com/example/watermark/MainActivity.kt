package com.example.watermark

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.watermark.data.AppDatabase
import com.example.watermark.data.PhotoDao
import com.example.watermark.data.SettingsRepository
import com.example.watermark.ui.components.BottomDock
import com.example.watermark.ui.components.PermissionSheet
import com.example.watermark.ui.screens.camera.CameraScreen
import com.example.watermark.ui.screens.gallery.PhotoFullScreen
import com.example.watermark.ui.screens.gallery.PhotoGalleryScreen
import com.example.watermark.ui.screens.settings.SettingsScreen
import com.example.watermark.ui.screens.watermark.WatermarkPreviewScreen
import com.example.watermark.ui.theme.WmColor
import com.example.watermark.ui.theme.WatermarkTheme
import com.example.watermark.util.PermissionManager

class MainActivity : ComponentActivity() {
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var database: AppDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        settingsRepository = SettingsRepository(this)
        database = AppDatabase.getInstance(this)

        enableEdgeToEdge()
        // 深色底 → 状态栏图标必须是浅色
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            WatermarkTheme {
                MainApp(
                    settingsRepository = settingsRepository,
                    photoDao = database.photoDao()
                )
            }
        }
    }

    /** 音量键当快门：只有相机页注册了回调才消费，其它页面音量键照常工作。 */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val handler = com.example.watermark.ui.screens.camera.VolumeShutterBridge.handler
            if (handler != null) {
                handler.invoke()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
fun MainApp(
    settingsRepository: SettingsRepository,
    photoDao: PhotoDao
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val permissionManager = remember(context) { PermissionManager(context) }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val route = navBackStackEntry?.destination?.route

    var dockIndex by rememberSaveable { mutableStateOf(0) }
    var sheetVisible by rememberSaveable { mutableStateOf(!permissionManager.allGranted()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    var permanent by remember { mutableStateOf(false) }

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        askedOnce = true
        val stillDenied = result.filterValues { !it }.keys
        if (permissionManager.allGranted() && stillDenied.isEmpty()) {
            sheetVisible = false
            permanent = false
        } else {
            sheetVisible = true
            permanent = activity != null && PermissionManager.DISPLAY_GROUPS
                .filter { !permissionManager.groupGranted(it) }
                .any { permissionManager.permanentDenied(activity, it, askedBefore = true) }
        }
    }

    val showDock = route == AppScreen.CAMERA.route || route == AppScreen.GALLERY.route

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(WmColor.Ink)
    ) {
        NavigationHost(
            navController = navController,
            route = route,
            onScreenChange = { dockIndex = if (it == AppScreen.GALLERY) 1 else 0 },
            settingsRepository = settingsRepository,
            photoDao = photoDao
        )

        AnimatedVisibility(
            visible = showDock,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier.padding(
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 14.dp
                )
            ) {
                BottomDock(
                    selected = dockIndex,
                    onSelect = { index ->
                        val target = if (index == 0) AppScreen.CAMERA else AppScreen.GALLERY
                        if (route != target.route) {
                            navController.navigate(target.route) {
                                popUpTo(AppScreen.CAMERA.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                        dockIndex = index
                    }
                )
            }
        }

        // 首启权限引导：一张卡片讲完三项，永远不主动关闭 App
        AnimatedVisibility(
            visible = sheetVisible && !permissionManager.allGranted(),
            enter = fadeIn() + slideInVertically { it / 3 },
            exit = fadeOut() + slideOutVertically { it / 3 },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            PermissionSheet(
                groups = PermissionManager.DISPLAY_GROUPS,
                granted = { permissionManager.groupGranted(it) },
                permanent = permanent,
                onAllow = { launcher.launch(PermissionManager.REQUIRED.toTypedArray()) },
                onOpenSettings = { permissionManager.openAppSettings() },
                onLater = { sheetVisible = false }
            )
        }
    }
}

enum class AppScreen(val route: String, val label: String) {
    CAMERA("camera", "相机"),
    GALLERY("gallery", "相册")
}

/** 兼容旧调用点：是否已拿齐三项权限。 */
fun hasPermissions(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
        (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) &&
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

@Composable
fun NavigationHost(
    navController: NavHostController,
    route: String?,
    onScreenChange: (AppScreen) -> Unit,
    settingsRepository: SettingsRepository,
    photoDao: PhotoDao
) {
    NavHost(
        navController = navController,
        startDestination = AppScreen.CAMERA.route
    ) {
        composable(AppScreen.CAMERA.route) {
            onScreenChange(AppScreen.CAMERA)
            CameraScreen(
                navController = navController,
                settingsRepository = settingsRepository,
                photoDao = photoDao
            )
        }
        composable(AppScreen.GALLERY.route) {
            onScreenChange(AppScreen.GALLERY)
            PhotoGalleryScreen(navController = navController, photoDao = photoDao)
        }
        composable("settings") {
            SettingsScreen(
                navController = navController,
                settingsRepository = settingsRepository,
                photoDao = photoDao
            )
        }
        composable("verify") {
            com.example.watermark.ui.screens.verify.VerifyScreen(
                navController = navController,
                photoDao = photoDao
            )
        }
        composable(
            route = "watermark_preview/{uri}",
            arguments = listOf(navArgument("uri") { type = NavType.StringType })
        ) { backEntry ->
            val uri = backEntry.arguments?.getString("uri") ?: return@composable
            WatermarkPreviewScreen(
                navController = navController,
                uri = uri,
                photoDao = photoDao,
                settingsRepository = settingsRepository
            )
        }
        composable(
            route = "photo_fullscreen/{uri}?index={index}&total={total}",
            arguments = listOf(
                navArgument("uri") { type = NavType.StringType },
                navArgument("index") { type = NavType.IntType; defaultValue = 0 },
                navArgument("total") { type = NavType.IntType; defaultValue = 0 }
            )
        ) { backEntry ->
            val uri = backEntry.arguments?.getString("uri") ?: return@composable
            PhotoFullScreen(
                navController = navController,
                uri = uri,
                index = backEntry.arguments?.getInt("index") ?: 0,
                total = backEntry.arguments?.getInt("total") ?: 0
            )
        }
    }
}
