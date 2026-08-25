package com.relayo.app.navigation

sealed class RelayoDestination(val route:String, val label:String) {
    object MeshStatus:RelayoDestination("mesh_status", "Mesh")
    object Messages:RelayoDestination("messages", "Messages")
    object VoiceNotes:RelayoDestination("voice_notes", "Voice")
    object NewsFeed:RelayoDestination("news_feed", "Feed")
    object Alerts:RelayoDestination("alerts", "Alerts")
    object QrBoards:RelayoDestination("qr_boards", "Boards")
    object Bridge:RelayoDestination("bridge", "Bridge")
    object More:RelayoDestination("more", "More")

    companion object {
        val bottomNavItems = listOf(MeshStatus, Messages, VoiceNotes, NewsFeed, More)
        val moreItems = listOf(Alerts, QrBoards, Bridge)
        const val CHAT_DETAIL_ROUTE = "messages/{peerId}"
        fun chatDetailRoute(peerId:String) = "messages/$peerId"
        const val VOICE_DETAIL_ROUTE = "voice_notes/{peerId}"
        fun voiceDetailRoute(peerId:String) = "voice_notes/$peerId"
    }
}