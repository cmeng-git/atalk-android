package com.pierfrancescosoffritti.androidyoutubeplayer.core.player

class PlayerConstants {

    enum class PlayerState {
        UNKNOWN, UNSTARTED, ENDED, PLAYING, PAUSED, BUFFERING, VIDEO_CUED
    }

    enum class PlaybackQuality {
        UNKNOWN, SMALL, MEDIUM, LARGE, HD720, HD1080, HIGH_RES, DEFAULT
    }

    enum class PlayerError {
        UNKNOWN, INVALID_PARAMETER_IN_REQUEST, HTML_5_PLAYER, VIDEO_NOT_FOUND,
        VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER, VIDEO_CONTENT_RESTRICTION_OR_UNAVAILABLE
    }
}