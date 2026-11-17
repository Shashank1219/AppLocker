package com.shashank.applocker

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shashank.applocker.ui.navigation.AppNavHost
import com.shashank.applocker.ui.theme.AppLockerTheme
import dagger.hilt.android.AndroidEntryPoint

/** Extends [FragmentActivity] because BiometricPrompt requires it. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppLockerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val start by viewModel.start.collectAsStateWithLifecycle()
                    // Not lifecycle-aware on purpose: a relock while stopped must be visible
                    // on the very first frame after returning.
                    val unlocked by viewModel.isUnlocked.collectAsState()
                    when (val s = start) {
                        StartState.Loading -> Unit
                        is StartState.Ready -> AppNavHost(
                            startDestination = s.route,
                            isUnlocked = unlocked,
                            onUnlocked = viewModel::onUnlocked,
                            onDataWiped = viewModel::onDataWiped,
                            modifier = Modifier.padding(innerPadding),
                        )
                        StartState.StorageError -> Column(
                            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(stringResource(R.string.start_storage_error))
                            Button(onClick = viewModel::load) { Text(stringResource(R.string.pin_retry)) }
                        }
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Rotation also stops the activity; only a real trip to the background relocks.
        if (!isChangingConfigurations) viewModel.onLeftForeground()
    }
}
