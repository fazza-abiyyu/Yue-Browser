@file:OptIn(androidx.camera.core.ExperimentalGetImage::class)
package com.yue.browser.presentation.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (granted) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder()
                            .build()
                            .also { prev ->
                                prev.setSurfaceProvider(previewView.surfaceProvider)
                            }

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

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageAnalyzer
                            )
                        } catch (e: Exception) {
                            android.util.Log.e("ScannerScreen", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                },
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
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Text(
                    text = "Scan QR / Barcode",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        // Bottom hint
        Text(
            text = "Point camera at a QR code or barcode",
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
                        text = "Camera permission required",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "Grant camera access to scan QR codes and barcodes.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(24.dp))
                    androidx.compose.material3.Button(onClick = onRequestPermission) {
                        Text("Grant permission")
                    }
                }
            }
        }
    }
}
