package com.relayo.app.navigation

sealed class RelayoDestination(val route:String, val label:String) {
    object MeshStatus:RelayoDestination("mesh_status", "Mesh")
    object Messages:RelayoDestination("messages", "Messages")

    companion object {
        val bottomNavItems = listOf(MeshStatus, Messages)
    }
}