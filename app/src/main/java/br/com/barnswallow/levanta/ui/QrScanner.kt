package br.com.barnswallow.levanta.ui

import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/** Câmera traseira lendo QR codes e códigos de barras; chama onCode a cada leitura. */
@Composable
fun QrScanner(modifier: Modifier = Modifier, onError: () -> Unit = {}, onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val currentOnError by rememberUpdatedState(onError)
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }

    DisposableEffect(lifecycleOwner) {
        val providerFuture = ProcessCameraProvider.getInstance(context)
        val executor = Executors.newSingleThreadExecutor()
        val scanner = BarcodeScanning.getClient()
        var disposed = false

        providerFuture.addListener({
            if (disposed) return@addListener
            val provider = runCatching { providerFuture.get() }.getOrElse {
                currentOnError()
                return@addListener
            }
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            analysis.setAnalyzer(executor) { proxy -> analyze(scanner, proxy) { currentOnCode(it) } }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }.onFailure { currentOnError() }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            disposed = true
            // Só desfaz se a câmera já iniciou; get() num future pendente travaria a tela.
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            executor.shutdown()
            scanner.close()
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}

@OptIn(ExperimentalGetImage::class)
private fun analyze(scanner: BarcodeScanner, proxy: ImageProxy, onCode: (String) -> Unit) {
    val media = proxy.image
    if (media == null) {
        proxy.close()
        return
    }
    val input = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    // Os listeners do ML Kit rodam na thread principal.
    scanner.process(input)
        .addOnSuccessListener { codes ->
            codes.firstNotNullOfOrNull { it.rawValue }?.let(onCode)
        }
        .addOnCompleteListener { proxy.close() }
}
