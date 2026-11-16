package com.shashank.applocker.feature.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shashank.applocker.R
import com.shashank.applocker.ui.navigation.PlaceholderScreen

@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(title = stringResource(R.string.placeholder_onboarding), modifier = modifier)
}
