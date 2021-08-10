package com.pierfrancescosoffritti.androidyoutubeplayer.core.player

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener

/**
 * Use this interface to control the playback of YouTube videos and to listen to their events.
 */
interface YouTubePlayer {
    /**
     * Loads and automatically plays the video.
     * @param videoId id of the video
     * @param startSeconds the time from which the video should start playing
     */
    fun loadVideo(videoId: String, startSeconds: Float)

    /**
     * Loads the video's thumbnail and prepares the player to play the video. Does not automatically play the video.
     * @param videoId id of the video
     * @param startSeconds the time from which the video should start playing
     */
    fun cueVideo(videoId: String, startSeconds: Float)

    /**
     * Loads the video playlist.
     * @param playlist youtube playlist id
     * @param startIndex the first video start playing
     */
    fun loadPlaylist(playlist: String, startIndex: Int)

    fun loadPlaylist_videoIds(videoIds: String)

    fun play()
    fun pause()

    fun nextVideo()
    fun previousVideo()

    fun mute()
    fun unMute()

    /**
     * @param volumePercent Integer between 0 and 100
     */
    fun setVolume(volumePercent: Int)

    /**
     *
     * @param time The absolute time in seconds to seek to
     */
    fun seekTo(time: Float)

    /**
     *
     * @param time a signed advance time in seconds to seek to in backward or forward
     */
    fun advanceTo(time: Float)

    fun setPlaybackRate(rate: Float)

    fun getVideoUrl()

    fun addListener(listener: YouTubePlayerListener): Boolean
    fun removeListener(listener: YouTubePlayerListener): Boolean
}
