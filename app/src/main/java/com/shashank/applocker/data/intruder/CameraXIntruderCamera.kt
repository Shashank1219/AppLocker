package com.shashank.applocker.data.intruder

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.shashank.applocker.service.IntruderCamera
import com.shashank.applocker.service.IntruderPhotoSink
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

/**
 * One silent front-camera photo with CameraX [ImageCapture] only: no preview, no flash, JPEG,
 * minimum latency. The camera is bound for the capture and unbound right after, in all cases.
 *
 * CameraX writes the JPEG with correct orientation metadata. The photo goes to a [PhotoTarget]
 * (MediaStore), never to private storage, and is removed again if anything fails.
 *
 * Whether ImageCapture works without a Preview use case varies by device; this needs checking on
 * real devices (see the README). If a device needs a preview, a tiny hidden one can be bound.
 */
@Singleton
class CameraXIntruderCamera @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sink: IntruderPhotoSink,
) : IntruderCamera {

    override suspend fun capture(owner: LifecycleOwner) {
        // CameraX binding must happen on the main thread. Everything heavy runs on CameraX's own
        // executors, so the UI is not blocked.
        withContext(Dispatchers.Main) {
            val target = sink.open() ?: error("no target")
            val executor = ContextCompat.getMainExecutor(context)
            var provider: ProcessCameraProvider? = null
            val imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build()
            try {
                provider = awaitProvider(executor)
                provider.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, imageCapture)
                suspendCancellableCoroutine { cont ->
                    imageCapture.takePicture(
                        ImageCapture.OutputFileOptions.Builder(target.outputStream).build(),
                        executor,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) = cont.resume(Unit)
                            override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
                        },
                    )
                }
                target.commit()
            } catch (e: Throwable) {
                target.abort()
                throw e
            } finally {
                // Release the camera right away.
                runCatching { provider?.unbind(imageCapture) }
                runCatching { target.outputStream.close() }
            }
        }
    }

    private suspend fun awaitProvider(executor: java.util.concurrent.Executor): ProcessCameraProvider =
        suspendCancellableCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener(
                {
                    try {
                        cont.resume(future.get())
                    } catch (e: Exception) {
                        cont.resumeWithException(e)
                    }
                },
                executor,
            )
        }
}
