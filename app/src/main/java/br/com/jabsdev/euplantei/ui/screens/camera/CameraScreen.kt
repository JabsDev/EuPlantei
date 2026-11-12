package br.com.jabsdev.euplantei.ui.screens.camera

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwitchCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.util.ImageUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onPhotoReady: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var permanentlyDenied by rememberSaveable { mutableStateOf(false) }
    var permissionAsked by rememberSaveable { mutableStateOf(false) }
    var lensFacing by rememberSaveable { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashOn by rememberSaveable { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var hasCamera by remember { mutableStateOf(true) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionAsked = true
        hasPermission = granted
        permanentlyDenied = !granted && activity?.let {
            !ActivityCompat.shouldShowRequestPermissionRationale(it, Manifest.permission.CAMERA)
        } == true
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) permanentlyDenied = false
    }

    LaunchedEffect(Unit) {
        if (!hasPermission && !permissionAsked) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri != null) {
            isCapturing = true
            scope.launch {
                runCatching { ImageUtils.prepareFromUri(context, uri) }
                    .onSuccess { file ->
                        isCapturing = false
                        onPhotoReady(file.absolutePath)
                    }
                    .onFailure {
                        isCapturing = false
                        errorText = context.getString(R.string.error_image_decode)
                    }
            }
        }
    }

    fun capturePhoto() {
        val capture = imageCapture ?: return
        if (isCapturing) return
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        isCapturing = true
        errorText = null
        val dir = File(context.cacheDir, "consultas").apply { mkdirs() }
        val raw = File(dir, "captura_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(raw).build()
        capture.takePicture(
            options,
            mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    scope.launch {
                        runCatching { ImageUtils.prepareFromFile(context, raw) }
                            .onSuccess { prepared ->
                                raw.delete()
                                isCapturing = false
                                onPhotoReady(prepared.absolutePath)
                            }
                            .onFailure {
                                raw.delete()
                                isCapturing = false
                                errorText = context.getString(R.string.camera_capture_failed)
                            }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    raw.delete()
                    isCapturing = false
                    errorText = context.getString(R.string.camera_capture_failed)
                }
            },
        )
    }

    DisposableEffect(hasPermission, lensFacing) {
        var cancelled = false
        if (hasPermission) {
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    if (cancelled) return@addListener
                    runCatching {
                        val cameraProvider = future.get()
                        val selector = CameraSelector.Builder()
                            .requireLensFacing(lensFacing)
                            .build()
                        if (!cameraProvider.hasCamera(selector)) {
                            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                lensFacing = CameraSelector.LENS_FACING_BACK
                            } else {
                                hasCamera = false
                            }
                            return@runCatching
                        }
                        hasCamera = true
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build()
                        capture.flashMode = if (flashOn) {
                            ImageCapture.FLASH_MODE_ON
                        } else {
                            ImageCapture.FLASH_MODE_OFF
                        }
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            selector,
                            preview,
                            capture,
                        )
                        provider = cameraProvider
                        imageCapture = capture
                    }.onFailure {
                        hasCamera = false
                        errorText = context.getString(R.string.error_camera)
                    }
                },
                mainExecutor,
            )
        }
        onDispose {
            cancelled = true
            provider?.unbindAll()
            imageCapture = null
        }
    }

    LaunchedEffect(flashOn) {
        imageCapture?.flashMode = if (flashOn) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (hasPermission && hasCamera) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.08f)),
            )
            GuideOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 30.dp),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CameraControlButton(
                icon = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cd_close),
                onClick = onBack,
            )
            Text(
                text = stringResource(R.string.camera_title),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(start = 12.dp),
            )
            Spacer(Modifier.weight(1f))
            CameraControlButton(
                icon = if (flashOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                contentDescription = stringResource(R.string.camera_flash),
                onClick = { flashOn = !flashOn },
            )
        }

        if (hasPermission && hasCamera) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.48f),
                ) {
                    Text(
                        text = stringResource(R.string.camera_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                    )
                }
                Spacer(Modifier.height(22.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    CameraControlButton(
                        icon = Icons.Rounded.PhotoLibrary,
                        contentDescription = stringResource(R.string.camera_gallery),
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        size = 54,
                    )
                    ShutterButton(
                        enabled = !isCapturing,
                        onClick = ::capturePhoto,
                    )
                    CameraControlButton(
                        icon = Icons.Rounded.SwitchCamera,
                        contentDescription = stringResource(R.string.camera_switch),
                        onClick = {
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        size = 54,
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = !hasPermission || !hasCamera,
            modifier = Modifier.align(Alignment.Center),
        ) {
            if (hasPermission && !hasCamera) {
                CameraMessageCard(
                    title = stringResource(R.string.camera_no_camera),
                    body = stringResource(R.string.camera_permission_denied_body),
                    primaryLabel = stringResource(R.string.camera_gallery),
                    onPrimary = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    secondaryLabel = stringResource(R.string.nav_back),
                    onSecondary = onBack,
                )
            } else {
                CameraMessageCard(
                    title = stringResource(
                        if (permanentlyDenied) {
                            R.string.camera_permission_denied_title
                        } else {
                            R.string.camera_permission_title
                        },
                    ),
                    body = stringResource(
                        if (permanentlyDenied) {
                            R.string.camera_permission_permanent
                        } else {
                            R.string.camera_permission_body
                        },
                    ),
                    primaryLabel = if (permanentlyDenied) {
                        stringResource(R.string.common_open_settings)
                    } else {
                        stringResource(R.string.camera_permission_grant)
                    },
                    onPrimary = {
                        if (permanentlyDenied) {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null),
                                ),
                            )
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    secondaryLabel = stringResource(R.string.camera_gallery),
                    onSecondary = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    tertiaryLabel = stringResource(R.string.nav_back),
                    onTertiary = onBack,
                )
            }
        }

        if (isCapturing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.46f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.camera_processing))
                    }
                }
            }
        }

        errorText?.let { message ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(20.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(message, modifier = Modifier.weight(1f))
                    TextButton(onClick = { errorText = null }) {
                        Text(stringResource(R.string.common_close))
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val frameWidth = size.width * 0.88f
        val frameHeight = size.height * 0.52f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2.2f
        val right = left + frameWidth
        val bottom = top + frameHeight
        val corner = size.minDimension * 0.08f
        val stroke = 5.dp.toPx()
        val color = Color.White.copy(alpha = 0.9f)

        fun line(start: Offset, end: Offset) = drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        // Cantos superiores
        line(Offset(left, top + corner), Offset(left, top))
        line(Offset(left, top), Offset(left + corner, top))
        line(Offset(right - corner, top), Offset(right, top))
        line(Offset(right, top), Offset(right, top + corner))
        // Cantos inferiores
        line(Offset(left, bottom - corner), Offset(left, bottom))
        line(Offset(left, bottom), Offset(left + corner, bottom))
        line(Offset(right - corner, bottom), Offset(right, bottom))
        line(Offset(right, bottom), Offset(right, bottom - corner))
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(82.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(68.dp)
                .clip(CircleShape)
                .background(Color.White),
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = stringResource(R.string.camera_capture),
                tint = Color(0xFF1B5E20),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun CameraControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Int = 48,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.42f),
        contentColor = Color.White,
        modifier = Modifier.size(size.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(23.dp))
        }
    }
}

@Composable
private fun CameraMessageCard(
    title: String,
    body: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    tertiaryLabel: String? = null,
    onTertiary: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(28.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(38.dp),
            )
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                Text(primaryLabel)
            }
            TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                Text(secondaryLabel)
            }
            if (tertiaryLabel != null && onTertiary != null) {
                TextButton(onClick = onTertiary, modifier = Modifier.fillMaxWidth()) {
                    Text(tertiaryLabel)
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is android.content.ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
