/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.call;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import org.atalk.android.util.java.awt.Dimension;

import timber.log.Timber;

/**
 * Layout that aligns remote video <tt>View</tt> by stretching it to screen width or height. It also controls whether
 * call control buttons group should be auto hidden or stay visible all the time. This layout will work only with
 * <tt>VideoCallActivity</tt>.<br/>
 * IMPORTANT: it can't be done from <tt>Activity</tt>, because just after the views are created we don't know their
 * sizes yet(return 0 or invalid).
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class RemoteVideoLayout extends LinearLayout
{
    /**
     * Preferred video size used to calculate the ratio.
     * Default to 640x480. (1x1) causes GLSurfaceView Invalid Operation
     */
    private Dimension preferredSize;

    /**
     * Stores last preferred size.
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
     * @param remoteVideoView current remoteVideoView container
     * @param videoSize received video stream size
     * @return <tt>false</tt> if no change is required for remoteVideoViewContainer dimension to playback
     * the new received video size
     */
    public boolean setVideoPreferredSize(View remoteVideoView, Dimension videoSize)
    {
        preferredSize = videoSize;
        preferredSizeChanged = true;
        requestLayout();

        boolean remoteVideoContainerSizeChange = true;
        if (remoteVideoView != null) {
            ViewGroup.LayoutParams params = remoteVideoView.getLayoutParams();
            ViewGroup.LayoutParams params2 = this.getLayoutParams();
            if (params != null) {
                remoteVideoContainerSizeChange = !(params.width == params2.width && params.height == params2.height);

                if (remoteVideoContainerSizeChange)
                    Timber.w("Params wxh compare: %s == %s; %s == %s",
                            params.width, params2.width, params.height, params2.height);
            }
        }
        return remoteVideoContainerSizeChange;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        int childCount = getChildCount();
        if (childCount == lastChildCount && !preferredSizeChanged) {
            // Do nothing
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }
        // Store values to prevent from too many calculations
        lastChildCount = childCount;
        preferredSizeChanged = false;

        Context ctx = getContext();
        if (!(ctx instanceof VideoCallActivity)) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        VideoCallActivity videoActivity = (VideoCallActivity) ctx;
        if (childCount > 0) {
            int parentWidth = MeasureSpec.getSize(widthMeasureSpec);
            int parentHeight = MeasureSpec.getSize(heightMeasureSpec);

            // NullPointerException from field? so just give it a default
            if (preferredSize == null)
                preferredSize = new Dimension(640, 480);

            double width = preferredSize.width;
            double height = preferredSize.height;

            // Stretch to match height
            if (parentHeight <= parentWidth) {
                Timber.i("Stretch to device max height");
                double ratio = width / height;
                height = parentHeight;
                // width = height * ratio;
                width = Math.ceil((height * ratio) / 16.0) * 16;
                videoActivity.ensureAutoHideFragmentAttached();
            }
            // Stretch to match width
            else {
                Timber.i("Stretch to device max width");
                double ratio = height / width;
                width = parentWidth;
                // height = width * ratio;
                height = Math.ceil((width * ratio) / 16.0) * 16;
                videoActivity.ensureAutoHideFragmentDetached();
            }

            Timber.i("Remote video view dimension size: %sx%s", width, height);
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
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
