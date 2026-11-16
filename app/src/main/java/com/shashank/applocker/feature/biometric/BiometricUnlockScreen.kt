package com.shashank.applocker.feature.biometric

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shashank.applocker.R
import com.shashank.applocker.ui.navigation.PlaceholderScreen

@Composable
fun BiometricUnlockScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.placeholder_biometric_unlock), modifier = modifier)
}
