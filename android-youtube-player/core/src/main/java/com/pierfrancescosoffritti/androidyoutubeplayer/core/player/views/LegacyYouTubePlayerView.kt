package com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views

import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.ConnectivityManager.NetworkCallback
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.pierfrancescosoffritti.androidyoutubeplayer.R
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerCallback
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerFullScreenListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.FullScreenHelper
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.PlaybackResumer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.ui.DefaultPlayerUiController
import com.pierfrancescosoffritti.androidyoutubeplayer.core.ui.PlayerUiController

internal class LegacyYouTubePlayerView(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        SixteenByNineFrameLayout(context, attrs, defStyleAttr), LifecycleEventObserver {

    constructor(context: Context) : this(context, null, 0)
    constructor(context: Context, attrs: AttributeSet? = null) : this(context, attrs, 0)

    internal val youTubePlayer: WebViewYouTubePlayer = WebViewYouTubePlayer(context)
    private val defaultPlayerUiController: DefaultPlayerUiController

    private val playbackResumer = PlaybackResumer()
    private val fullScreenHelper = FullScreenHelper(this)

    private var networkCallback = NetworkCallback();
    internal var isYouTubePlayerReady = false
    private var initialize = { }
    private val youTubePlayerCallbacks = HashSet<YouTubePlayerCallback>()
    private val mainThreadHandler: Handler = Handler(Looper.getMainLooper())

    internal var canPlay = true
        private set

    var isUsingCustomUi = false
        private set

    init {
        addView(youTubePlayer, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        defaultPlayerUiController = DefaultPlayerUiController(this, youTubePlayer)

        fullScreenHelper.addFullScreenListener(defaultPlayerUiController)

        youTubePlayer.addListener(defaultPlayerUiController)
        youTubePlayer.addListener(playbackResumer)

        // stop playing if the user loads a video but then leaves the app before the video starts playing.
        youTubePlayer.addListener(object : AbstractYouTubePlayerListener() {
            override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                if (state == PlayerConstants.PlayerState.PLAYING && !isEligibleForPlayback())
                    youTubePlayer.pause()
            }
        })

        youTubePlayer.addListener(object : AbstractYouTubePlayerListener() {
            override fun onReady(youTubePlayer: YouTubePlayer) {
                isYouTubePlayerReady = true

                youTubePlayerCallbacks.forEach { it.onYouTubePlayer(youTubePlayer) }
                youTubePlayerCallbacks.clear()

                youTubePlayer.removeListener(this)
            }
        })
    }

    /**
     * Initialize the player. You must call this method before using the player.
     * @param youTubePlayerListener listener for player events
     * @param handleNetworkEvents if set to true a broadcast receiver will be registered and network events will be handled automatically.
     * If set to false, you should handle network events with your own broadcast receiver.
     * @param playerOptions customizable options for the embedded video player, can be null.
     */
    fun initialize(youTubePlayerListener: YouTubePlayerListener, handleNetworkEvents: Boolean, playerOptions: IFramePlayerOptions?) {
        if (isYouTubePlayerReady)
            throw IllegalStateException("This YouTubePlayerView has already been initialized.")

        if (handleNetworkEvents) {
            // context.registerReceiver(networkCallback, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
            registerNetworkCallback()

        }

        initialize = {
            youTubePlayer.initialize({ it.addListener(youTubePlayerListener) }, playerOptions)
        }

        if (!handleNetworkEvents)
            initialize()
    }

    private fun registerNetworkCallback() {
        val manager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager

        val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()

        networkCallback = object : NetworkCallback() {
            override fun onAvailable(network: Network) {
                mainThreadHandler.post {
                    if (!isYouTubePlayerReady)
                        initialize()
                    else
                        playbackResumer.resume(youTubePlayer)
                }
            }

            override fun onUnavailable() {}
        }
        manager.registerNetworkCallback(request, networkCallback)
    }

    /**
     * Initialize the player.
     * @param handleNetworkEvents if set to true a broadcast receiver will be registered and network events will be handled automatically.
     * If set to false, you should handle network events with your own broadcast receiver.
     *
     * @see LegacyYouTubePlayerView.initialize
     */
    fun initialize(youTubePlayerListener: YouTubePlayerListener, handleNetworkEvents: Boolean) =
            initialize(youTubePlayerListener, handleNetworkEvents, null)

    /**
     * Initialize the player. Network events are automatically handled by the player.
     * @param youTubePlayerListener listener for player events
     *
     * @see LegacyYouTubePlayerView.initialize
     */
    private fun initialize(youTubePlayerListener: YouTubePlayerListener) =
            initialize(youTubePlayerListener, true)

    /**
     * Initialize a player using the web-base Ui instead pf the native Ui.
     * The default PlayerUiController will be removed and [LegacyYouTubePlayerView.getPlayerUiController] will throw exception.
     *
     * @see LegacyYouTubePlayerView.initialize
     */
    fun initializeWithWebUi(youTubePlayerListener: YouTubePlayerListener, handleNetworkEvents: Boolean) {
        val iFramePlayerOptions = IFramePlayerOptions.Builder().controls(1).build()
        inflateCustomPlayerUi(R.layout.ayp_empty_layout)
        initialize(youTubePlayerListener, handleNetworkEvents, iFramePlayerOptions)
    }

    /**
     * @param youTubePlayerCallback A callback that will be called when the YouTubePlayer is ready.
     * If the player is ready when the function is called, the callback is called immediately.
     * This function is called only once.
     */
    fun getYouTubePlayerWhenReady(youTubePlayerCallback: YouTubePlayerCallback) {
        if (isYouTubePlayerReady)
            youTubePlayerCallback.onYouTubePlayer(youTubePlayer)
        else
            youTubePlayerCallbacks.add(youTubePlayerCallback)
    }

    /**
     * Use this method to replace the default Ui of the player with a custom Ui.
     *
     * You will be responsible to manage the custom Ui from your application,
     * the default controller obtained through [LegacyYouTubePlayerView.getPlayerUiController] won't be available anymore.
     * @param layoutId the ID of the layout defining the custom Ui.
     * @return The inflated View
     */
    fun inflateCustomPlayerUi(@LayoutRes layoutId: Int): View {
        removeViews(1, childCount - 1)

        if (!isUsingCustomUi) {
            youTubePlayer.removeListener(defaultPlayerUiController)
            fullScreenHelper.removeFullScreenListener(defaultPlayerUiController)
        }

        isUsingCustomUi = true

        return View.inflate(context, layoutId, this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when {
            Lifecycle.Event.ON_DESTROY == event -> {
                release()
            }
            Lifecycle.Event.ON_RESUME == event -> {
                onResume()
            }
            Lifecycle.Event.ON_STOP == event -> {
                onStop()
            }
        }
    }

    /**
     * Call this method before destroying the host Fragment/Activity, or register this View as an observer of its host lifecycle
     */
    fun release() {
        removeView(youTubePlayer)
        youTubePlayer.removeAllViews()
        youTubePlayer.destroy()
        try {
            val manager = context.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            manager.unregisterNetworkCallback(networkCallback)
        } catch (ignore: Exception) {
        }
    }

    fun onResume() {
        playbackResumer.onLifecycleResume()
        canPlay = true
    }

    // W/cr_AwContents: Application attempted to call on a destroyed WebView
    fun onStop() {
        // W/cr_AwContents: Application attempted to call on a destroyed WebView
        if (youTubePlayer.isShown)
            youTubePlayer.pause()
        playbackResumer.onLifecycleStop()
        canPlay = false
    }

    /**
     * Checks whether the player is in an eligible state for playback in
     * respect of the {@link WebViewYouTubePlayer#isBackgroundPlaybackEnabled}
     * property.
     */
    internal fun isEligibleForPlayback(): Boolean {
        return canPlay || youTubePlayer.isBackgroundPlaybackEnabled
    }

    /**
     * Don't use this method if you want to publish your app on the PlayStore. Background playback is against YouTube terms of service.
     */
    fun enableBackgroundPlayback(enable: Boolean) {
        youTubePlayer.isBackgroundPlaybackEnabled = enable
    }

    fun getPlayerUiController(): PlayerUiController {
        if (isUsingCustomUi)
            throw RuntimeException("You have inflated a custom player Ui. You must manage it with your own controller.")

        return defaultPlayerUiController
    }

    fun enterFullScreen() = fullScreenHelper.enterFullScreen()

    fun exitFullScreen() = fullScreenHelper.exitFullScreen()

    fun toggleFullScreen() = fullScreenHelper.toggleFullScreen()

    fun isFullScreen(): Boolean = fullScreenHelper.isFullScreen

    fun addFullScreenListener(fullScreenListener: YouTubePlayerFullScreenListener): Boolean =
            fullScreenHelper.addFullScreenListener(fullScreenListener)

    fun removeFullScreenListener(fullScreenListener: YouTubePlayerFullScreenListener): Boolean =
            fullScreenHelper.removeFullScreenListener(fullScreenListener)
}
