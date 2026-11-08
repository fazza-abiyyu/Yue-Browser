@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)
package com.yue.browser.presentation.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.view.ScaleGestureDetector
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import com.yue.browser.R
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

class ScannerActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        setContent {
            ScannerScreen(
                initialGranted = granted,
                onBack = { finish() },
                onCodeDetected = { result -> handleScanResult(result) }
            )
        }
    }

    private fun decodeSampledBitmap(uri: Uri, maxDim: Int = 2048): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: return null
        var sample = 1
        while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val hasPermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        setContent {
            ScannerScreen(
                initialGranted = hasPermission,
                onBack = { finish() },
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onCodeDetected = { result -> handleScanResult(result) }
            )
        }
    }

    private fun handleScanResult(result: String) {
        val url = result.trim()
        val isUrl = url.startsWith("http://") || url.startsWith("https://")
        val intent = Intent().apply {
            putExtra("scan_result", url)
            putExtra("scan_is_url", isUrl)
            if (isUrl) data = Uri.parse(url)
        }
        setResult(RESULT_OK, intent)
        finish()
    }
}

/** Decode gambar terpilih ke bitmap ter-undersample (maks ~2048px) agar hemat memori. */
private fun decodeSampledBitmap(context: android.content.Context, uri: Uri, maxDim: Int = 2048): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        ?: return null
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

/** Ikon garis: senter (garis 2dp, gaya drawer). isOn = tanpa garis silang + tint kuning. */
@Composable
private fun FlashLineIcon(tint: Color, isOn: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height
        val bolt = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.29f, h * 0.08f)
            lineTo(w * 0.29f, h * 0.54f)
            lineTo(w * 0.42f, h * 0.54f)
            lineTo(w * 0.42f, h * 0.92f)
            lineTo(w * 0.71f, h * 0.42f)
            lineTo(w * 0.54f, h * 0.42f)
            lineTo(w * 0.71f, h * 0.08f)
            close()
        }
        // OFF = outline putih, ON = solid kuning — tanpa garis silang agar bersih
        drawPath(
            bolt,
            color = tint,
            style = if (isOn) androidx.compose.ui.graphics.drawscope.Fill
            else androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
    }
}

/** Ikon garis: gambar/foto (garis 2dp, gaya drawer). */
@Composable
private fun GalleryLineIcon(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(24.dp)) {
        val stroke = 2.dp.toPx()
        val w = size.width
        val h = size.height
        drawRoundRect(
            color = tint,
            topLeft = Offset(stroke / 2, stroke / 2),
            size = androidx.compose.ui.geometry.Size(w - stroke, h - stroke),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx()),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke)
        )
        drawLine(tint, Offset(w * 0.20f, h * 0.66f), Offset(w * 0.42f, h * 0.42f), strokeWidth = stroke)
        drawLine(tint, Offset(w * 0.42f, h * 0.42f), Offset(w * 0.58f, h * 0.60f), strokeWidth = stroke)
        drawLine(tint, Offset(w * 0.58f, h * 0.60f), Offset(w * 0.72f, h * 0.46f), strokeWidth = stroke)
        drawLine(tint, Offset(w * 0.72f, h * 0.46f), Offset(w * 0.84f, h * 0.60f), strokeWidth = stroke)
        drawCircle(tint, radius = stroke * 0.9f, center = Offset(w * 0.30f, h * 0.26f))
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScannerScreen(
    initialGranted: Boolean,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit = {},
    onCodeDetected: ((String) -> Unit)? = null
) {
    var granted by remember { mutableStateOf(initialGranted) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // Ref kamera untuk pinch-to-zoom — AtomicReference agar update zoom tidak memicu rebind (feedback user 2026-11-07)
    val cameraRef = remember { java.util.concurrent.atomic.AtomicReference<androidx.camera.core.Camera>(null) }

    var torchOn by remember { mutableStateOf(false) }
    var showImagePicker by remember { mutableStateOf(false) }

    val scannerTitle = stringResource(R.string.scanner_title)
    val scannerHint = stringResource(R.string.scanner_hint)
    val msgReadFailed = stringResource(R.string.scanner_image_read_failed)
    val msgNoCode = stringResource(R.string.scanner_no_code_found)
    val msgReadError = stringResource(R.string.scanner_image_read_error)

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.PERFORMANCE
            scaleType = PreviewView.ScaleType.FILL_CENTER

            // Pinch-to-zoom: skala relatif terhadap zoom aktif, dibatasi rentang kamera
            val scaleDetector = ScaleGestureDetector(context,
                object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val camera = cameraRef.get() ?: return true
                        val zoomState = camera.cameraInfo.zoomState.value ?: return true
                        val newZoom = (zoomState.zoomRatio * detector.scaleFactor)
                            .coerceIn(1f, zoomState.maxZoomRatio)
                        camera.cameraControl.setZoomRatio(newZoom)
                        return true
                    }
                })
            setOnTouchListener { _, event ->
                scaleDetector.onTouchEvent(event)
                true
            }
        }
    }

    // Pindah binding kamera ke sini (sekali) — sebelumnya di AndroidView.update sehingga
    // setiap recompose me-rebind kamera & mereset torch (feedback user 2026-11-07)
    LaunchedEffect(granted) {
        if (!granted) return@LaunchedEffect
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { prev -> prev.setSurfaceProvider(previewView.surfaceProvider) }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyzer ->
                    analyzer.setAnalyzer(
                        Executors.newSingleThreadExecutor()
                    ) { imageProxy ->
                        val mediaImage = imageProxy.image
                        if (mediaImage != null) {
                            val inputImage = InputImage.fromMediaImage(
                                mediaImage,
                                imageProxy.imageInfo.rotationDegrees
                            )
                            val scanner = BarcodeScanning.getClient()
                            scanner.process(inputImage)
                                .addOnSuccessListener { barcodes ->
                                    for (barcode in barcodes) {
                                        val rawValue = barcode.rawValue
                                        if (!rawValue.isNullOrEmpty()) {
                                            onCodeDetected?.invoke(rawValue)
                                            imageProxy.close()
                                            return@addOnSuccessListener
                                        }
                                    }
                                }
                                .addOnCompleteListener {
                                    imageProxy.close()
                                }
                        } else {
                            imageProxy.close()
                        }
                    }
                }

            try {
                cameraProvider.unbindAll()
                val camera = cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalyzer
                )
                cameraRef.set(camera)
            } catch (e: Exception) {
                android.util.Log.e("ScannerScreen", "Camera binding failed", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Scan QR/barcode dari file gambar terpilih
    fun scanFromFile(uri: Uri) {
        val bitmap = decodeSampledBitmap(context, uri)
        if (bitmap == null) {
            Toast.makeText(context, msgReadFailed, Toast.LENGTH_SHORT).show()
            return
        }
        BarcodeScanning.getClient()
            .process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { barcodes ->
                val found = barcodes.firstNotNullOfOrNull { it.rawValue }
                if (!found.isNullOrEmpty()) {
                    onCodeDetected?.invoke(found)
                } else {
                    Toast.makeText(context, msgNoCode, Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, msgReadError, Toast.LENGTH_SHORT).show()
            }
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { scanFromFile(it) } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (granted) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Dim overlay + transparent scan window using Canvas
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // Scan window dimensions (centered)
            val scanSize = canvasWidth * 0.65f
            val scanLeft = (canvasWidth - scanSize) / 2
            val scanTop = (canvasHeight - scanSize) / 2 - 40.dp.toPx()
            val scanRight = scanLeft + scanSize
            val scanBottom = scanTop + scanSize

            // Draw dim overlay everywhere EXCEPT inside the scan window
            // Top area
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, 0f),
                size = Size(canvasWidth, scanTop)
            )
            // Bottom area
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, scanBottom),
                size = Size(canvasWidth, canvasHeight - scanBottom)
            )
            // Left area
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(0f, scanTop),
                size = Size(scanLeft, scanSize)
            )
            // Right area
            drawRect(
                color = Color.Black.copy(alpha = 0.55f),
                topLeft = Offset(scanRight, scanTop),
                size = Size(canvasWidth - scanRight, scanSize)
            )

            // Draw scan window border (corner brackets style)
            val strokeWidth = 3.dp.toPx()
            val cornerLength = 30.dp.toPx()
            val cornerColor = Color.White.copy(alpha = 0.9f)

            // Top-left corner
            drawLine(cornerColor,
                start = Offset(scanLeft, scanTop + cornerLength),
                end = Offset(scanLeft, scanTop),
                strokeWidth = strokeWidth
            )
            drawLine(cornerColor,
                start = Offset(scanLeft, scanTop),
                end = Offset(scanLeft + cornerLength, scanTop),
                strokeWidth = strokeWidth
            )
            // Top-right corner
            drawLine(cornerColor,
                start = Offset(scanRight, scanTop + cornerLength),
                end = Offset(scanRight, scanTop),
                strokeWidth = strokeWidth
            )
            drawLine(cornerColor,
                start = Offset(scanRight - cornerLength, scanTop),
                end = Offset(scanRight, scanTop),
                strokeWidth = strokeWidth
            )
            // Bottom-left corner
            drawLine(cornerColor,
                start = Offset(scanLeft, scanBottom - cornerLength),
                end = Offset(scanLeft, scanBottom),
                strokeWidth = strokeWidth
            )
            drawLine(cornerColor,
                start = Offset(scanLeft, scanBottom),
                end = Offset(scanLeft + cornerLength, scanBottom),
                strokeWidth = strokeWidth
            )
            // Bottom-right corner
            drawLine(cornerColor,
                start = Offset(scanRight, scanBottom - cornerLength),
                end = Offset(scanRight, scanBottom),
                strokeWidth = strokeWidth
            )
            drawLine(cornerColor,
                start = Offset(scanRight - cornerLength, scanBottom),
                end = Offset(scanRight, scanBottom),
                strokeWidth = strokeWidth
            )
        }

        // Top bar (transparent, floating)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White
                    )
                }
                Text(
                    text = scannerTitle,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
                Spacer(Modifier.weight(1f))
                // Scan dari file gambar (galeri)
                IconButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                    GalleryLineIcon(tint = Color.White)
                }
                // Senter
                IconButton(onClick = {
                    torchOn = !torchOn
                    cameraRef.get()?.cameraControl?.enableTorch(torchOn)
                }) {
                    FlashLineIcon(
                        tint = if (torchOn) Color(0xFFFFD54F) else Color.White,
                        isOn = torchOn
                    )
                }
            }
        }

        // Bottom hint
        Text(
            text = scannerHint,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp),
            textAlign = TextAlign.Center
        )

        // Permission denied overlay
        if (!granted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.scanner_permission_required),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.scanner_permission_desc),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    androidx.compose.material3.Button(onClick = onRequestPermission) {
                        Text(stringResource(R.string.scanner_grant_permission))
                    }
                }
            }
        }
    }
}
