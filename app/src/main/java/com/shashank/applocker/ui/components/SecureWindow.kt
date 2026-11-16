package com.shashank.applocker.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

private var secureOwners = 0

/**
 * Sets FLAG_SECURE (no screenshots, hidden in Recents) while this composable is in composition.
 * Reference-counted so moving between two secure screens never clears the flag in between.
 * Main thread only.
 */
@Composable
fun SecureWindow() {
    val context = LocalContext.current
    DisposableEffect(context) {
        val window = context.findActivity()?.window
        secureOwners++
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            secureOwners--
            if (secureOwners <= 0) {
                secureOwners = 0
                window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
