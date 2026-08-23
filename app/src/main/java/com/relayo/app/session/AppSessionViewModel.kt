package com.relayo.app.session

import android.content.Context
import androidx.lifecycle.ViewModel
import com.relayo.app.service.MeshForegroundService
import com.relayo.core.mesh.MeshSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val sessionManager:MeshSessionManager,
    @ApplicationContext private val context:Context
):ViewModel() {

    fun onPermissionsGranted() {
        sessionManager.startIfNeeded()
        try {
            MeshForegroundService.start(context)
        } catch(_:Exception) {
            // Fallback: ensure mesh still starts even if foreground service fails
            sessionManager.startIfNeeded()
        }
    }
}