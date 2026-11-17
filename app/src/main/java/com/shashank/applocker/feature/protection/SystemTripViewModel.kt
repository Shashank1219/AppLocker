package com.shashank.applocker.feature.protection

import androidx.lifecycle.ViewModel
import com.shashank.applocker.security.AppSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Lets a screen tell the session that the app itself is sending the user to a system settings screen. */
@HiltViewModel
class SystemTripViewModel @Inject constructor(private val session: AppSession) : ViewModel() {
    fun expectTrip() = session.expectSystemTrip()
}
