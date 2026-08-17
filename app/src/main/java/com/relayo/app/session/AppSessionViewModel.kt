package com.relayo.app.session

import androidx.lifecycle.ViewModel
import com.relayo.core.mesh.MeshSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppSessionViewModel @Inject constructor(
    private val sessionManager:MeshSessionManager
):ViewModel() {

    fun onPermissionsGranted() {
        sessionManager.startIfNeeded()
    }
}