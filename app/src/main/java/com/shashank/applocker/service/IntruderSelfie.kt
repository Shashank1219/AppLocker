package com.shashank.applocker.service

import androidx.lifecycle.LifecycleOwner
import com.shashank.applocker.di.DefaultDispatcher
import com.shashank.applocker.domain.intruder.IntruderCapturePolicy
import com.shashank.applocker.domain.repository.SettingsRepository
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Whether the user granted the permissions the selfie needs on this Android version. */
interface CameraPermissionChecker {
    fun isGranted(): Boolean
}

interface FrontCameraAvailability {
    fun hasFrontCamera(): Boolean
}

/** Takes one front-camera photo and saves it. Throws on any failure. */
interface IntruderCamera {
    suspend fun capture(owner: LifecycleOwner)
}

/** Where a photo is written. [open] returns null if no file could be created. */
interface IntruderPhotoSink {
    fun open(): PhotoTarget?
}

interface PhotoTarget {
    val outputStream: OutputStream

    /** The photo was written completely: make it visible in the gallery. */
    fun commit()

    /** Something failed: remove the partial photo. */
    fun abort()
}

/**
 * Takes an intruder photo after a wrong PIN on the lock screen.
 *
 * Runs off the main thread, never blocks, delays or changes the PIN result, and swallows every
 * failure (it logs nothing). One capture at a time: a wrong PIN during a capture is ignored.
 * The camera is opened only for the capture and released right after.
 * Must be called from the lock Activity, which is in the foreground: Android blocks camera
 * access from background contexts, so it is never called from the accessibility service.
 */
@Singleton
class IntruderSelfie @Inject constructor(
    private val settings: SettingsRepository,
    private val permission: CameraPermissionChecker,
    private val frontCamera: FrontCameraAvailability,
    private val camera: IntruderCamera,
    @DefaultDispatcher dispatcher: CoroutineDispatcher,
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val inProgress = AtomicBoolean(false)

    /** [failedAttempts] is the persisted count of consecutive wrong PINs, including this one. */
    fun onWrongPin(owner: LifecycleOwner, failedAttempts: Int) {
        if (!inProgress.compareAndSet(false, true)) return // one capture at a time
        scope.launch {
            try {
                val current = settings.settings.first()
                if (IntruderCapturePolicy.shouldCapture(
                        enabled = current.intruderSelfieEnabled,
                        permissionGranted = permission.isGranted(),
                        hasFrontCamera = frontCamera.hasFrontCamera(),
                        captureInProgress = false,
                        failedAttempts = failedAttempts,
                        threshold = current.intruderAfterAttempts,
                    )
                ) {
                    camera.capture(owner)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Silent by design: a failed photo must never affect unlocking.
            } finally {
                inProgress.set(false)
            }
        }
    }
}
