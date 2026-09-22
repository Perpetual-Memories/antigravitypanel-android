package com.nzd.antigravitypanel.ui.qrlogin

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.nzd.antigravitypanel.data.qrlogin.QrDecoder
import com.nzd.antigravitypanel.data.qrlogin.setQrAnalysisSize
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val SCAN_TITLE = "扫码登录"
// PC 端那张码的正式名字就叫「Cookie 二维码」，照着叫，用户对得上号
private const val SCAN_HINT = "对准电脑版「反重力数据面板」的 Cookie 二维码"

/** 未授权时那两个按钮的固定宽度，见下面「两个按钮同宽」那条注释。 */
private val PERMISSION_BUTTON_WIDTH = 180.dp

/**
 * 全屏扫码页。版式照 lsfTB 的 `QRCodeScanner`：黑底全屏预览、顶部一条控制栏、
 * 中间四角框、底部一行提示；我们这边按钮少一个闪光灯——扫的是屏幕上的码，不需要补光，
 * Miuix 图标集里也没有手电筒。
 *
 * **没有闪光灯，多了一个「相册」**：PC 端的二维码常常是截了图传到手机上的，
 * 用相册选那张截图比举着手机对着显示器扫更快，也更不容易对不准。
 */
@Composable
actual fun QrScannerScreen(
    onScanned: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val decoder = remember { QrDecoder() }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    // 扫到一次之后要立刻停：不停的话，相机对着同一个码会一秒回调几十次。
    // 用 AtomicBoolean 而不是 Compose 的 state：这个标记是在分析线程上读的，
    // state 的读写要过 snapshot 系统，跨线程读它可能拿到旧值。
    val active = remember { AtomicBoolean(true) }
    // 最新回调。相机只绑定一次（在 AndroidView 的 factory 里），
    // 而外面传进来的 lambda 每次重组都可能换，不套一层就会一直调最初那一个。
    val currentOnScanned by rememberUpdatedState(onScanned)

    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted -> granted = isGranted }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let { decoder.decode(it) }
            }
        }.getOrNull()
        if (text.isNullOrBlank()) {
            Toast.makeText(context, "这张图里没找到二维码", Toast.LENGTH_SHORT).show()
        } else {
            active.set(false)
            currentOnScanned(text)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission(context)) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    DisposableEffect(Unit) {
        onDispose {
            active.set(false)
            // 不 unbind 的话相机一直开着，回到概览还能听见取景的声音（而且是实打实的耗电）
            runCatching { provider?.unbindAll() }
            analyzerExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                    scope.launch {
                        // 挂起等，而不是 `future.get()`：那是阻塞的，而这段跑在主线程上
                        val cameraProvider = runCatching { awaitCameraProvider(ctx) }.getOrNull()
                            ?: return@launch
                        provider = cameraProvider
                        val preview = androidx.camera.core.Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .setQrAnalysisSize()
                            .build()
                            .apply {
                                setAnalyzer(analyzerExecutor) { proxy ->
                                    if (active.get()) {
                                        val text = decoder.decode(proxy)
                                        if (!text.isNullOrBlank()) {
                                            active.set(false)
                                            // 回到主线程再回调：外面拿到结果要动 Compose 的状态
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                currentOnScanned(text)
                                            }
                                        }
                                    }
                                    proxy.close()
                                }
                            }
                        runCatching {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    }
                    previewView
                },
                modifier = Modifier.fillMaxSize(),
            )

            ScannerTopBar(
                onDismiss = onDismiss,
                onPickImage = { pickImage.launch("image/*") },
            )

            ScanFrame()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = SCAN_HINT,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                )
            }
        } else {
            // 没权限也别白屏：给一句原因和一个再试一次的入口。
            // 用户可能是在系统弹窗里误点了拒绝，这时候除了回设置别无他法，
            // 但再给一次机会成本很低。
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "扫码需要相机权限",
                    color = Color.White,
                    fontSize = 15.sp,
                )
                Text(
                    text = "扫的是 PC 端屏幕上的二维码，不会拍照也不会录像",
                    modifier = Modifier.padding(top = 8.dp),
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                )
                Spacer(modifier = Modifier.height(20.dp))
                // 两个按钮**同宽**：「授予权限」四个字、「返回」两个字，
                // 按内容撑开的话一长一短，看着像主次两个按钮，而它们其实是一对等位的选择
                Button(
                    modifier = Modifier.width(PERMISSION_BUTTON_WIDTH),
                    onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    colors = ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.primary,
                        contentColor = Color.White,
                    ),
                ) {
                    Text("授予权限")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    modifier = Modifier.width(PERMISSION_BUTTON_WIDTH),
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        color = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("返回")
                }
            }
        }
    }
}

/**
 * 顶部一条：返回 / 标题 / 相册。
 *
 * 全部走白色 —— 底下是相机预览，画面明暗不定，用主题色的话遇上亮画面就糊了。
 */
@Composable
private fun ScannerTopBar(
    onDismiss: () -> Unit,
    onPickImage: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = MiuixIcons.Back,
                    contentDescription = "返回",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                text = SCAN_TITLE,
                modifier = Modifier.weight(1f),
                color = Color.White,
                fontSize = 17.sp,
            )
            IconButton(onClick = onPickImage) {
                Icon(
                    imageVector = MiuixIcons.Photos,
                    contentDescription = "从相册选二维码",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

/**
 * 中间那个四角框。参照 lsfTB：外圈四角装饰 + 内缩一圈的实线方框。
 *
 * 它**只是视觉引导**，不是识别区域——实际解码吃的是整幅画面（见 `QrDecoder`），
 * 用户对不准框也应该能扫上。
 */
@Composable
private fun ScanFrame() {
    val accent = MiuixTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.aspectRatio(1f)) {
            ScanCorners(color = accent)
        }
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .padding(16.dp)
                .border(
                    width = 2.dp,
                    color = accent.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                )
                .clip(RoundedCornerShape(12.dp)),
        )
    }
}

/** 四个直角装饰。每个角由横竖两条拼成，所以一条边要放两个 Box。 */
@Composable
private fun ScanCorners(color: Color) {
    val length = 24.dp
    val thickness = 4.dp
    val radius = 4.dp
    Box(modifier = Modifier.fillMaxSize()) {
        Corner(Alignment.TopStart, length, thickness, radius, color, horizontalFirst = true)
        Corner(Alignment.TopStart, length, thickness, radius, color, horizontalFirst = false)
        Corner(Alignment.TopEnd, length, thickness, radius, color, horizontalFirst = true)
        Corner(Alignment.TopEnd, length, thickness, radius, color, horizontalFirst = false)
        Corner(Alignment.BottomStart, length, thickness, radius, color, horizontalFirst = true)
        Corner(Alignment.BottomStart, length, thickness, radius, color, horizontalFirst = false)
        Corner(Alignment.BottomEnd, length, thickness, radius, color, horizontalFirst = true)
        Corner(Alignment.BottomEnd, length, thickness, radius, color, horizontalFirst = false)
    }
}

// BoxScope 不是可有可无的：`Modifier.align()` 是 BoxScope 上的成员扩展，
// 少了这个 receiver 编译期就找不到它
@Composable
private fun BoxScope.Corner(
    alignment: Alignment,
    length: androidx.compose.ui.unit.Dp,
    thickness: androidx.compose.ui.unit.Dp,
    radius: androidx.compose.ui.unit.Dp,
    color: Color,
    horizontalFirst: Boolean,
) {
    val shape = when (alignment) {
        Alignment.TopStart -> RoundedCornerShape(topStart = radius)
        Alignment.TopEnd -> RoundedCornerShape(topEnd = radius)
        Alignment.BottomStart -> RoundedCornerShape(bottomStart = radius)
        else -> RoundedCornerShape(bottomEnd = radius)
    }
    Box(
        modifier = Modifier
            .align(alignment)
            .then(
                if (horizontalFirst) {
                    Modifier.size(length, thickness)
                } else {
                    Modifier.size(thickness, length)
                },
            )
            .clip(shape)
            .background(color),
    )
}

private fun hasCameraPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED

/**
 * 拿 [ProcessCameraProvider]。它给的是 `ListenableFuture`，直接 `.get()` 会阻塞调用线程，
 * 而这段是在主线程的 `scope.launch` 里跑的 —— 阻塞一下就是掉帧甚至 ANR。
 * 所以挂起等回调，主线程该干嘛干嘛。
 */
private suspend fun awaitCameraProvider(context: Context): ProcessCameraProvider =
    suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener(
            { continuation.resumeWith(runCatching { future.get() }) },
            ContextCompat.getMainExecutor(context),
        )
    }
