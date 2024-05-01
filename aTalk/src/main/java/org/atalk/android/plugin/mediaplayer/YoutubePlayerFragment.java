package org.atalk.android.plugin.mediaplayer;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.YouTubePlayerFullScreenListener;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.utils.YouTubePlayerUtils;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView;
import com.pierfrancescosoffritti.androidyoutubeplayer.core.ui.menu.MenuItem;

import net.java.sip.communicator.util.UtilActivator;

import org.atalk.android.R;
import org.atalk.android.gui.chat.ChatActivity;
import org.atalk.service.configuration.ConfigurationService;
import org.atalk.util.FullScreenHelper;
import org.jetbrains.annotations.NotNull;

import timber.log.Timber;

public class YoutubePlayerFragment extends Fragment {
    // regression to check for valid youtube link
    public static final String URL_YOUTUBE = "http[s]*://[w.]*youtu[.]*be.*";

    private YouTubePlayerView youTubePlayerView;
    private FullScreenHelper fullScreenHelper;

    private String mediaUrl = null;
    // Array may contain the youtube url links or just videoId's
    private static List<String> mediaUrls = new ArrayList<>();

    static {
        mediaUrls.add("https://youtu.be/vCKCkc8llaM");
        mediaUrls.add("https://youtu.be/LvetJ9U_tVY");
        mediaUrls.add("https://youtu.be/S0Q4gqBUs7c");
        mediaUrls.add("https://youtu.be/9HPiBJBCOq8");
    }

    private String mVideoId = null;
    // Array contains only the youtube videoId's
    private static final List<String> mVideoIds = new ArrayList<>();

    // Youtube playlist ids for testing; set usePlayList to true
    private static final String[] playLists = {"RDMQTvg5EUgWU", "PLUh4W61bt_K5jmi1qbVACvLAPkudEmLKO"};
    private static final String PLAYLIST = "PL";
    private static final String SEPARATOR = ",";
    private final boolean usePlayList = false;

    // Will attempt to playback the given videoId as playlist if onError first encountered
    private boolean onErrorOnce = true;

    // Playback ratio of normal speed constants.
    public static final float rateMin = 0.25f;
    public static final float rateMax = 2.0f;
    private static final float rateStep = 0.25f;
    private float mSpeed = 1.0f;

    private FragmentActivity mContext;
    private static final ConfigurationService configService = UtilActivator.getConfigurationService();

    public YoutubePlayerFragment() {
    }

    @Override
    public void onAttach(@NonNull @NotNull Context context) {
        super.onAttach(context);
        mContext = (FragmentActivity) context;
        fullScreenHelper = new FullScreenHelper(mContext);
    }

    /**
     * Create a new instance of MediaExoPlayerFragment, providing "bundle" as an argument.
     */
    public static YoutubePlayerFragment getInstance(Bundle args) {
        YoutubePlayerFragment youtubePlayer = new YoutubePlayerFragment();
        youtubePlayer.setArguments(args);
        return youtubePlayer;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.youtube_player_fragment, container, false);
        youTubePlayerView = view.findViewById(R.id.youtube_player_view);

        Bundle args = getArguments();
        if (args != null) {
            mediaUrl = args.getString(MediaExoPlayerFragment.ATTR_MEDIA_URL);

            // Comment out the following to test loadPlaylist_videoIds()
            mediaUrls = args.getStringArrayList(MediaExoPlayerFragment.ATTR_MEDIA_URLS);
            if (mediaUrls != null && !mediaUrls.isEmpty()) {
                mVideoIds.clear();
                for (int i = 0; i < mediaUrls.size(); i++) {
                    String videoId = getVideoId(mediaUrls.get(i));
                    mVideoIds.add(videoId);
                }
            }
        }

        initYouTubePlayerView();
        return view;
    }

    private void initYouTubePlayerView() {
        // hymnchtv crashes when enabled
        // initPlayerMenu();

        // The player will automatically release itself when the fragment is destroyed.
        // The player will automatically pause when the fragment is stopped
        // If you don't add YouTubePlayerView as a lifecycle observer, you will have to release it manually.
        getLifecycle().addObserver(youTubePlayerView);

        youTubePlayerView.addYouTubePlayerListener(new AbstractYouTubePlayerListener() {
            @Override
            public void onReady(@NonNull YouTubePlayer youTubePlayer) {
                if (usePlayList) {
                    startPlaylist(youTubePlayer, playLists[0]);
                }
                else if (mediaUrls != null) {
                    // comma separated strings for playlist array conversation: i.e ['vCKCkc8llaM','LvetJ9U_tVY','S0Q4gqBUs7c','9HPiBJBCOq8'];
                    startPlaylist(youTubePlayer, TextUtils.join(SEPARATOR, mVideoIds));
                }
                else if (mediaUrl != null) {
                    mVideoId = getVideoId(mediaUrl);
                    if (mVideoId.toUpperCase().startsWith(PLAYLIST)) {
                        startPlaylist(youTubePlayer, mVideoId);
                    }
                    else {
                        onErrorOnce = true;
                        YouTubePlayerUtils.loadOrCueVideo(youTubePlayer, getLifecycle(), mVideoId, 0f);
                    }
                }
                addActionsToPlayer(youTubePlayer);
                initPlaybackSpeed(youTubePlayer);
                addFullScreenListenerToPlayer();
            }

            @Override
            public void onError(@NotNull YouTubePlayer youTubePlayer, @NotNull PlayerConstants.PlayerError error) {
                // Error message will be shown in player view by API
                Timber.w("Youtube url: %s, playback failed: %s", mediaUrl, error);

                // Try to load as playlist if onError
                if (onErrorOnce && error.equals(PlayerConstants.PlayerError.VIDEO_CONTENT_RESTRICTION_OR_UNAVAILABLE)) {
                    onErrorOnce = false;
                    startPlaylist(youTubePlayer, mVideoId);
                }
                else {
                    // Use external player if playlist playback failed
                    playVideoUrlExt(mediaUrl);
                }
            }

            @Override
            public void onVideoUrl(@NotNull YouTubePlayer youTubePlayer, @NotNull String videoUrl) {
                Timber.w("Youtube videoUrl: %s (%s)", mediaUrl, videoUrl);
            }

            @Override
            public void onPlaybackRateChange(@NotNull YouTubePlayer youTubePlayer, @NotNull String rate) {
                mSpeed = Float.parseFloat(rate);
                Toast.makeText(mContext, mContext.getString(R.string.playback_rate, rate), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Enable the Previous & Next button and start playlist playback
     *
     * @param youTubePlayer instance of youtube player
     * @param videoId video PL id or ids for playback
     */
    private void startPlaylist(YouTubePlayer youTubePlayer, String videoId) {
        Drawable nextActionIcon = ContextCompat.getDrawable(mContext, R.drawable.ic_next);
        assert nextActionIcon != null;

        // Set a click listener on the "Play next video" button
        youTubePlayerView.getPlayerUiController().setPreviousAction(nextActionIcon,
                view -> youTubePlayer.previousVideo());
        youTubePlayerView.getPlayerUiController().setNextAction(nextActionIcon,
                view -> youTubePlayer.nextVideo());

        if (videoId.contains(SEPARATOR)) {
            youTubePlayer.loadPlaylist_videoIds(videoId);
        }
        else {
            youTubePlayer.loadPlaylist(videoId, 0);
        }
    }

    /**
     * Extract the youtube videoId from the following string formats:
     * a. vCKCkc8llaM
     * b. https://youtu.be/vCKCkc8llaM
     * c. https://youtube.com/watch?v=14VrDQSnfzI&feature=share
     * d. https://www.youtube.com/playlist?list=PL0KROm2A3S8HaMLBxYPF5kuEEtTYvUJox\
     *
     * @param url Any of the above url string
     *
     * @return the youtube videoId
     */
    private String getVideoId(String url) {
        String mVideoId = url.substring(mediaUrl.lastIndexOf('/') + 1);
        if (mVideoId.contains("=")) {
            mVideoId = mVideoId.substring(mVideoId.indexOf("=") + 1).split("&")[0];
        }
        return mVideoId;
    }

    /**
     * This method adds a new custom action to the player.
     * Custom actions are shown next to the Play/Pause button in the middle of the player.
     */
    private void addActionsToPlayer(YouTubePlayer youTubePlayer) {
        Drawable rewindActionIcon = ContextCompat.getDrawable(mContext, R.drawable.ic_rewind);
        Drawable forwardActionIcon = ContextCompat.getDrawable(mContext, R.drawable.ic_forward);
        Drawable offYoutubeActionIcon = ContextCompat.getDrawable(mContext, R.drawable.ic_hide_screen);
        Drawable rateIcon = ContextCompat.getDrawable(mContext, R.drawable.ic_speed);

        assert rewindActionIcon != null;
        assert forwardActionIcon != null;
        assert offYoutubeActionIcon != null;
        assert rateIcon != null;

        youTubePlayerView.getPlayerUiController().setRewindAction(rewindActionIcon,
                view -> youTubePlayer.advanceTo(-5.0f));

        youTubePlayerView.getPlayerUiController().setForwardAction(forwardActionIcon,
                view -> youTubePlayer.advanceTo(15.0f));

        youTubePlayerView.getPlayerUiController().setRateIncAction(rateIcon,
                view -> {
                    float tmp = mSpeed + rateStep;
                    float rate = tmp <= rateMax ? tmp : mSpeed;
                    youTubePlayer.setPlaybackRate(rate);
                });

        youTubePlayerView.getPlayerUiController().setRateDecAction(rateIcon,
                view -> {
                    float tmp = mSpeed - rateStep;
                    float rate = tmp >= rateMin ? tmp : mSpeed;
                    youTubePlayer.setPlaybackRate(rate);
                });

        // End youtube player and hide UI
        youTubePlayerView.getPlayerUiController().setHideScreenAction(offYoutubeActionIcon,
                view -> {
                    ((ChatActivity) mContext).releasePlayer();
                }
        );
    }

    /**
     * Shows the menu button in the player and adds an item to it.
     */
    private void initPlayerMenu() {
        Objects.requireNonNull(youTubePlayerView.getPlayerUiController()
                        .showMenuButton(true)
                        .getMenu())
                .addItem(new MenuItem("menu item1", R.drawable.ic_speed,
                        view -> Toast.makeText(mContext, "item1 clicked", Toast.LENGTH_SHORT).show())
                )
                .addItem(new MenuItem("menu item2", R.drawable.ic_mood_black_24dp,
                        view -> Toast.makeText(mContext, "item2 clicked", Toast.LENGTH_SHORT).show())
                )
                .addItem(new MenuItem("menu item no icon",
                        view -> Toast.makeText(mContext, "item no icon clicked", Toast.LENGTH_SHORT).show())
                );
    }

    private void addFullScreenListenerToPlayer() {
        youTubePlayerView.addFullScreenListener(new YouTubePlayerFullScreenListener() {
            @Override
            public void onYouTubePlayerEnterFullScreen() {
                fullScreenHelper.enterFullScreen();
            }

            @Override
            public void onYouTubePlayerExitFullScreen() {
                fullScreenHelper.exitFullScreen();
            }
        });
    }

    /**
     * Initialize the Media Player playback speed to the user defined setting
     */
    public void initPlaybackSpeed(YouTubePlayer youTubePlayer) {
        mSpeed = (float) configService.getDouble(MediaExoPlayerFragment.PREF_PLAYBACK_SPEED, 1.0);
        youTubePlayer.setPlaybackRate(mSpeed);
    }

    /**
     * Play the specified videoUrl using android Intent.ACTION_VIEW
     *
     * @param videoUrl videoUrl not playable by ExoPlayer
     */
    private void playVideoUrlExt(String videoUrl) {
        // remove the youtube player fragment
        mContext.getSupportFragmentManager().beginTransaction().remove(this).commit();

        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    /**
     * Manually release the youtube player when user pressing backKey
     */
    public void release() {
        // Audio media player speed is (0.5 > mSpeed < 1.5)
        if (mSpeed >= rateMin && mSpeed <= rateMax) {
            configService.setProperty(MediaExoPlayerFragment.PREF_PLAYBACK_SPEED, mSpeed);
        }
        youTubePlayerView.release();
    }

    public void setPlayerVisible(boolean show) {
        youTubePlayerView.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    public boolean isPlayerVisible() {
        return youTubePlayerView.getVisibility() == View.VISIBLE;
    }
}
