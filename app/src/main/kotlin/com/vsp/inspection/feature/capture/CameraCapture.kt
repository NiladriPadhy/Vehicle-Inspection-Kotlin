package com.vsp.inspection.feature.capture

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.vsp.core.ui.components.PrimaryButton
import java.io.File
import java.util.concurrent.Executors

/**
 * CameraX capture surface. Requests the camera permission, shows a live preview with a framing
 * label, and returns the path of the captured JPEG via [onImageCaptured].
 */
@Composable
fun CameraCapture(
    instruction: String,
    onImageCaptured: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Camera permission is required to capture inspection photos.")
            PrimaryButton(
                text = "Grant camera permission",
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
            )
        }
        return
    }

    CameraPreview(instruction = instruction, onImageCaptured = onImageCaptured, modifier = modifier)
}

@Composable
private fun CameraPreview(
    instruction: String,
    onImageCaptured: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    LaunchedEffect(Unit) { controller.bindToLifecycle(lifecycleOwner) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Camera preview: $instruction" },
            factory = { ctx ->
                PreviewView(ctx).apply { this.controller = controller }
            },
        )

        if (instruction.isNotBlank()) {
            Surface(
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(),
            ) {
                Text(
                    text = instruction,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }

        PrimaryButton(
            text = "Capture",
            onClick = { capturePhoto(context, controller, executor, onImageCaptured) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(24.dp),
        )
    }
}

private fun capturePhoto(
    context: Context,
    controller: LifecycleCameraController,
    executor: java.util.concurrent.Executor,
    onImageCaptured: (String) -> Unit,
) {
    val outputFile = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
    val options = ImageCapture.OutputFileOptions.Builder(outputFile).build()
    controller.takePicture(
        options,
        executor,
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                ContextCompat.getMainExecutor(context).execute { onImageCaptured(outputFile.absolutePath) }
            }

            override fun onError(exception: ImageCaptureException) {
                // Swallow; the caller re-attempts capture. Quality gating happens downstream.
            }
        },
    )
}
