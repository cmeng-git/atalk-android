package com.pierfrancescosoffritti.androidyoutubeplayer.core.player

import android.os.Handler
import android.os.Looper
import androidx.annotation.RestrictTo

import android.text.TextUtils
import android.webkit.JavascriptInterface

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener

/**
 * Bridge used for Javascript-Java communication.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class YouTubePlayerBridge(private val youTubePlayerOwner: YouTubePlayerBridgeCallbacks) {

    companion object {
        // these constants correspond to the values in the Javascript player
        private const val STATE_UNSTARTED = "UNSTARTED"
        private const val STATE_ENDED = "ENDED"
        private const val STATE_PLAYING = "PLAYING"
        private const val STATE_PAUSED = "PAUSED"
        private const val STATE_BUFFERING = "BUFFERING"
        private const val STATE_CUED = "CUED"

        private const val QUALITY_SMALL = "small"
        private const val QUALITY_MEDIUM = "medium"
        private const val QUALITY_LARGE = "large"
        private const val QUALITY_HD720 = "hd720"
        private const val QUALITY_HD1080 = "hd1080"
        private const val QUALITY_HIGH_RES = "highres"
        private const val QUALITY_DEFAULT = "default"

        private const val ERROR_INVALID_PARAMETER_IN_REQUEST = "2"
        private const val ERROR_HTML_5_PLAYER = "5"
        private const val ERROR_VIDEO_NOT_FOUND = "100"
        private const val ERROR_VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER1 = "101"
        private const val ERROR_VIDEO_NOT_PLAYABLE_CONTENT_RESTRICTION = "150"
    }

    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper())

    interface YouTubePlayerBridgeCallbacks {
        fun getInstance(): YouTubePlayer
        fun getListeners(): Collection<YouTubePlayerListener>
        fun onYouTubeIFrameAPIReady()
    }

    @JavascriptInterface
    fun sendYouTubeIFrameAPIReady() =
        mainThreadHandler.post { youTubePlayerOwner.onYouTubeIFrameAPIReady() }

    @JavascriptInterface
    fun sendReady() {
        mainThreadHandler.post {
            for (listener in youTubePlayerOwner.getListeners())
                listener.onReady(youTubePlayerOwner.getInstance())
        }
    }

    @JavascriptInterface
    fun sendStateChange(state: String) {
        val playerState = parsePlayerState(state)

        mainThreadHandler.post {
            for (listener in youTubePlayerOwner.getListeners())
                listener.onStateChange(youTubePlayerOwner.getInstance(), playerState)
        }
    }

    @JavascriptInterface
    fun sendPlaybackQualityChange(quality: String) {
        val playbackQuality = parsePlaybackQuality(quality)

        mainThreadHandler.post {
            for (listener in youTubePlayerOwner.getListeners())
                listener.onPlaybackQualityChange(youTubePlayerOwner.getInstance(), playbackQuality)
        }
    }

    @JavascriptInterface
    fun sendPlaybackRateChange(rate: String) {
        mainThreadHandler.post {
            for (listener in youTubePlayerOwner.getListeners())
                listener.onPlaybackRateChange(youTubePlayerOwner.getInstance(), rate)
        }
    }

    @JavascriptInterface
    fun sendError(error: String) {
        val playerError = parsePlayerError(error)

        mainThreadHandler.post {
            for (listener in youTubePlayerOwner.getListeners())
                listener.onError(youTubePlayerOwner.getInstance(), playerError)
        }
    }

    @JavascriptInterface
    fun sendApiChange() {
        mainThreadHandler.post {
            for (listener in youTubePlayerOwner.getListeners())
                listener.onApiChange(youTubePlayerOwner.getInstance())
        }
    }

    @JavascriptInterface
    fun sendVideoCurrentTime(seconds: String) {
        val currentTimeSeconds: Float
        try {
            currentTimeSeconds = seconds.toFloat()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return
        }

        mainThreadHandler.post {
            for (listener in youTubePlayerOwner.getListeners())
                listener.onCurrentSecond(youTubePlayerOwner.getInstance(), currentTimeSeconds)
        }
    }

    @JavascriptInterface
    fun sendVideoDuration(seconds: String) {
        val videoDuration: Float
        try {
            val finalSeconds = if (TextUtils.isEmpty(seconds)) "0" else seconds
            videoDuration = finalSeconds.toFloat()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return
        }

        mainThreadHandler.post {
            for (listener in youTubePlayerOwner.getListeners())
                listener.onVideoDuration(youTubePlayerOwner.getInstance(), videoDuration)
        }
    }

    @JavascriptInterface
    fun sendVideoLoadedFraction(fraction: String) {
        val loadedFraction: Float
        try {
            loadedFraction = fraction.toFloat()
        } catch (e: NumberFormatException) {
            e.printStackTrace()
            return
        }

        mainThreadHandler.post {
            for (listener in youTubePlayerOwner.getListeners())
                listener.onVideoLoadedFraction(youTubePlayerOwner.getInstance(), loadedFraction)
        }
    }

    @JavascriptInterface
    fun sendVideoId(videoId: String) {
        mainThreadHandler.post {
            for (listener in youTubePlayerOwner.getListeners())
                listener.onVideoId(youTubePlayerOwner.getInstance(), videoId)
        }
    }

    @JavascriptInterface
    fun sendVideoUrl(videoUrl: String) {
        mainThreadHandler.post {
            for (listener in youTubePlayerOwner.getListeners())
                listener.onVideoUrl(youTubePlayerOwner.getInstance(), videoUrl)
        }
    }

    private fun parsePlayerState(state: String): PlayerConstants.PlayerState {
        return when {
            state.equals(STATE_UNSTARTED, ignoreCase = true) -> PlayerConstants.PlayerState.UNSTARTED
            state.equals(STATE_ENDED, ignoreCase = true) -> PlayerConstants.PlayerState.ENDED
            state.equals(STATE_PLAYING, ignoreCase = true) -> PlayerConstants.PlayerState.PLAYING
            state.equals(STATE_PAUSED, ignoreCase = true) -> PlayerConstants.PlayerState.PAUSED
            state.equals(STATE_BUFFERING, ignoreCase = true) -> PlayerConstants.PlayerState.BUFFERING
            state.equals(STATE_CUED, ignoreCase = true) -> PlayerConstants.PlayerState.VIDEO_CUED
            else -> PlayerConstants.PlayerState.UNKNOWN
        }
    }

    private fun parsePlaybackQuality(quality: String): PlayerConstants.PlaybackQuality {
        return when {
            quality.equals(QUALITY_SMALL, ignoreCase = true) -> PlayerConstants.PlaybackQuality.SMALL
            quality.equals(QUALITY_MEDIUM, ignoreCase = true) -> PlayerConstants.PlaybackQuality.MEDIUM
            quality.equals(QUALITY_LARGE, ignoreCase = true) -> PlayerConstants.PlaybackQuality.LARGE
            quality.equals(QUALITY_HD720, ignoreCase = true) -> PlayerConstants.PlaybackQuality.HD720
            quality.equals(QUALITY_HD1080, ignoreCase = true) -> PlayerConstants.PlaybackQuality.HD1080
            quality.equals(QUALITY_HIGH_RES, ignoreCase = true) -> PlayerConstants.PlaybackQuality.HIGH_RES
            quality.equals(QUALITY_DEFAULT, ignoreCase = true) -> PlayerConstants.PlaybackQuality.DEFAULT
            else -> PlayerConstants.PlaybackQuality.UNKNOWN
        }
    }

    private fun parsePlayerError(error: String): PlayerConstants.PlayerError {
        return when {
            error.equals(ERROR_INVALID_PARAMETER_IN_REQUEST, ignoreCase = true) -> PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST
            error.equals(ERROR_HTML_5_PLAYER, ignoreCase = true) -> PlayerConstants.PlayerError.HTML_5_PLAYER
            error.equals(ERROR_VIDEO_NOT_FOUND, ignoreCase = true) -> PlayerConstants.PlayerError.VIDEO_NOT_FOUND
            error.equals(ERROR_VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER1, ignoreCase = true) -> PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER
            error.equals(ERROR_VIDEO_NOT_PLAYABLE_CONTENT_RESTRICTION, ignoreCase = true) -> PlayerConstants.PlayerError.VIDEO_CONTENT_RESTRICTION_OR_UNAVAILABLE
            else -> PlayerConstants.PlayerError.UNKNOWN
        }
    }
}
