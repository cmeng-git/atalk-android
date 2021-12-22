/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.content.Context;
import android.media.MediaCodec;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.atalk.android.aTalkApp;
import org.atalk.impl.neomedia.codec.video.AndroidDecoder;

import java.awt.Dimension;

import timber.log.Timber;

/**
 * Layout that aligns remote video <tt>View</tt> by stretching it to max screen width or height.
 * It also controls whether call control buttons group should be auto hidden or stay visible all the time.
 * This layout will work only with <tt>VideoCallActivity</tt>.
 *
 * IMPORTANT: it can't be done from <tt>Activity</tt>, because just after the views are created,
 * we don't know their sizes yet(return 0 or invalid).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class RemoteVideoLayout extends LinearLayout
{
    /**
     * Last saved preferred video size used to calculate the max screen scaling.
     * Must set to null for sizeChange detection on first layout init; and when the remote view is removed
     */
    protected Dimension preferredSize = null;

    /**
     * Flag indicates any size change on new request. Always forces to requestLayout state if true
     */
    private boolean preferredSizeChanged = false;

    /**
     * Stores last child count.
     */
    private int lastChildCount = -1;

    public RemoteVideoLayout(Context context)
    {
        super(context);
    }

    public RemoteVideoLayout(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public RemoteVideoLayout(Context context, AttributeSet attrs, int defStyle)
    {
        super(context, attrs, defStyle);
    }

    /**
     * SizeChange algorithm uses preferredSize and videoSize ratio compare algorithm for full screen video;
     * Otherwise, non-null remoteVideoView will also return false when remote video dimension changes.
     * Note: use ratio compare algorithm to avoid unnecessary doAlignRemoteVideo reDraw unless there is a ratio change
     *
     * @param videoSize received video stream size
     * @param requestLayout true to force relayout request
     * @return <tt>false</tt> if no change is required for remoteVideoViewContainer dimension update
     * to playback the newly received video size:
     * @see AndroidDecoder#configureMediaCodec(MediaCodec, String)
     */
    public boolean setVideoPreferredSize(Dimension videoSize, boolean requestLayout)
    {
        preferredSizeChanged = requestLayout || (preferredSize == null)
               || Math.abs(preferredSize.width / preferredSize.height - videoSize.width / videoSize.height) > 0.01f;

        preferredSize = videoSize;
        requestLayout();
        return preferredSizeChanged;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int childCount = getChildCount();
        if ((childCount == lastChildCount) && !preferredSizeChanged) {
            return;
        }

        // Store values to prevent from too many calculations
        lastChildCount = childCount;
        preferredSizeChanged = false;

        Context ctx = getContext();
        if (!(ctx instanceof VideoCallActivity)) {
            return;
        }

        VideoCallActivity videoActivity = (VideoCallActivity) ctx;
        if (childCount > 0) {
            /*
             * MeasureSpec.getSize() is determined by previous layout dimension, any may not in full screen size;
             * So force to use the device default display full screen dimension.
             * // int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
             * // int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
             */
            int parentWidth = aTalkApp.mDisplaySize.width;
            int parentHeight = aTalkApp.mDisplaySize.height;
            if (!aTalkApp.isPortrait) {
                parentWidth = aTalkApp.mDisplaySize.height;
                parentHeight = aTalkApp.mDisplaySize.width;
            }

            double width;
            double height;
            if (preferredSize != null) {
                width = preferredSize.width;
                height = preferredSize.height;
            }
            else {
                // NullPointerException from the field? so give it a default
                width = VideoHandlerFragment.DEFAULT_WIDTH;
                height = VideoHandlerFragment.DEFAULT_HEIGHT;
            }

            // Stretch to match height
            if (parentHeight <= parentWidth) {
                // Timber.i("Stretch to device max height: %s", parentHeight);
                double ratio = width / height;
                height = parentHeight;
                // width = height * ratio;
                width = Math.ceil((height * ratio) / 16.0) * 16;
                videoActivity.ensureAutoHideFragmentAttached();
            }
            // Stretch to match width
            else {
                // Timber.i("Stretch to device max width: %s", parentWidth);
                double ratio = height / width;
                width = parentWidth;
                height = Math.ceil((width * ratio) / 16.0) * 16;
                videoActivity.ensureAutoHideFragmentDetached();
            }

            Timber.i("Remote video view dimension: [%s x %s]", width, height);
            this.setMeasuredDimension((int) width, (int) height);

            ViewGroup.LayoutParams params = getLayoutParams();
            params.width = (int) width;
            params.height = (int) height;
            this.setLayoutParams(params);

            for (int i = 0; i < childCount; i++) {
                View child = getChildAt(i);
                ViewGroup.LayoutParams chP = child.getLayoutParams();
                chP.width = params.width;
                chP.height = params.height;
                child.setLayoutParams(chP);
            }
        }
        else {
            ViewGroup.LayoutParams params = getLayoutParams();
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
            params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            this.setLayoutParams(params);
            videoActivity.ensureAutoHideFragmentDetached();
        }
    }
}
