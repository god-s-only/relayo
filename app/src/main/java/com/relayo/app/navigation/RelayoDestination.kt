package com.relayo.app.navigation

sealed class RelayoDestination(val route:String, val label:String) {
    object MeshStatus:RelayoDestination("mesh_status", "Mesh")
    object Messages:RelayoDestination("messages", "Messages")
    object VoiceNotes:RelayoDestination("voice_notes", "Voice")
    object NewsFeed:RelayoDestination("news_feed", "Feed")

    companion object {
        val bottomNavItems = listOf(MeshStatus, Messages, VoiceNotes, NewsFeed)
    }
}