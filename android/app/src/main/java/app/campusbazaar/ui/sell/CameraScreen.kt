package app.campusbazaar.ui.sell

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import app.campusbazaar.ui.components.PrimaryButton
import app.campusbazaar.ui.components.SecondaryButton
import app.campusbazaar.ui.theme.CB
import app.campusbazaar.util.Permissions
import java.io.File

/**
 * In-app CameraX capture. Handles the camera permission properly: asks with context, explains
 * when denied, and sends the student to Settings if it was permanently denied.
 */
@Composable
fun CameraScreen(nav: NavHostController) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(Permissions.granted(context, Manifest.permission.CAMERA)) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        askedOnce = true
    }
    LaunchedEffect(Unit) { if (!granted && !askedOnce) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            CameraPreview(onCaptured = { uri ->
                nav.previousBackStackEntry?.savedStateHandle?.set(CAMERA_RESULT_KEY, uri.toString())
                nav.popBackStack()
            })
        } else {
            val activity = context as? Activity
            val permanentlyDenied = askedOnce && activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)
            Column(
                Modifier.fillMaxSize().background(CB.Paper).statusBarsPadding().navigationBarsPadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Outlined.CameraAlt, null, tint = CB.Ink, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Camera access needed", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (permanentlyDenied) "Camera access is turned off for CampusBazaar. Turn it on in Settings, or pick photos from your gallery instead."
                    else "CampusBazaar uses the camera only to photograph items you sell. Photos are uploaded when you add them to a listing.",
                    style = MaterialTheme.typography.bodyMedium, color = CB.InkSoft, textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                if (permanentlyDenied) {
                    PrimaryButton("Open Settings", {
                        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                    }, Modifier.fillMaxWidth())
                } else {
                    PrimaryButton("Allow camera", { launcher.launch(Manifest.permission.CAMERA) }, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Use gallery instead", { nav.popBackStack() }, Modifier.fillMaxWidth())
            }
        }
        IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.statusBarsPadding().padding(8.dp).align(Alignment.TopStart)) {
            Icon(Icons.Outlined.Close, "Close camera", tint = if (granted) Color.White else CB.Ink)
        }
    }
}

@Composable
private fun CameraPreview(onCaptured: (Uri) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember {
        LifecycleCameraController(context).apply {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            setEnabledUseCases(LifecycleCameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    var capturing by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx -> PreviewView(ctx).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "Fill the frame with the item. Natural light works best.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp).background(Color(0x99000000), CircleShape).padding(horizontal = 14.dp, vertical = 6.dp),
        )
        Box(
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 36.dp).size(78.dp)
                .border(4.dp, Color.White, CircleShape).padding(7.dp).background(if (capturing) CB.InkSoft else CB.Marigold, CircleShape)
                .clickable(enabled = !capturing) {
                    capturing = true
                    val dir = File(context.filesDir, "captures").apply { mkdirs() }
                    val file = File(dir, "item_${System.currentTimeMillis()}.jpg")
                    controller.takePicture(
                        ImageCapture.OutputFileOptions.Builder(file).build(),
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                capturing = false
                                onCaptured(Uri.fromFile(file))
                            }
                            override fun onError(exception: ImageCaptureException) {
                                capturing = false
                                Toast.makeText(context, "Couldn't take the photo. Try again.", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (capturing) CircularProgressIndicator(Modifier.size(26.dp), color = Color.White, strokeWidth = 2.dp)
        }
    }
}
