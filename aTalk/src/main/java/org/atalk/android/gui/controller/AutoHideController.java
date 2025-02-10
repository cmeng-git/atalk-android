/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.controller;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;

import androidx.annotation.Nullable;

import java.util.Timer;
import java.util.TimerTask;

import org.atalk.android.BaseFragment;
import org.atalk.android.R;

import timber.log.Timber;

/**
 * The fragment is a controller which hides the given <code>View</code> after specified delay interval. To reset
 * and prevent from hiding for another period of call <code>show</code> method. This method will also instantly
 * display controlled <code>View</code> if it's currently hidden.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class AutoHideController extends BaseFragment implements Animation.AnimationListener {
    /**
     * Argument key for the identifier of <code>View</code> that will be auto hidden. It must exist in the parent
     * <code>Activity</code> view hierarchy.
     */
    private static final String ARG_VIEW_ID = "view_id";
    /**
     * Argument key for the delay interval, before the <code>View</code> will be hidden
     */
    private static final String ARG_HIDE_TIMEOUT = "hide_timeout";

    // private Animation inAnimation;

    /**
     * Hide animation
     */
    private Animation outAnimation;

    /**
     * Controlled <code>View</code>
     */
    private View view;

    /**
     * Timer used for the hide task scheduling
     */
    private Timer autoHideTimer;

    /**
     * Hide <code>View</code> timeout
     */
    private long hideTimeout;

    /**
     * Listener object
     */
    private AutoHideListener listener;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Activity activity = getActivity();

        if (activity instanceof AutoHideListener) {
            listener = (AutoHideListener) getActivity();
        }

        view = activity.findViewById(getArguments().getInt(ARG_VIEW_ID));
        if (view == null)
            throw new NullPointerException("The view is null");

        hideTimeout = getArguments().getLong(ARG_HIDE_TIMEOUT);
        // inAnimation = AnimationUtils.loadAnimation(getActivity(),
        // R.anim.show_from_bottom);
        // inAnimation.setAnimationListener(this);

        outAnimation = AnimationUtils.loadAnimation(activity, R.anim.hide_to_bottom);
        outAnimation.setAnimationListener(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();
        show();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        super.onPause();
        cancelAutoHideTask();
    }

    /**
     * Makes sure that hide task is scheduled. Cancels the previous one if is currently scheduled.
     */
    private void reScheduleAutoHideTask() {
        // Cancel pending task if exists
        cancelAutoHideTask();

        autoHideTimer = new Timer();
        autoHideTimer.schedule(new AutoHideTask(), hideTimeout);
    }

    /**
     * Makes sure the hide task is cancelled.
     */
    private void cancelAutoHideTask() {
        if (autoHideTimer != null) {
            autoHideTimer.cancel();
            autoHideTimer = null;
        }
    }

    /**
     * Hides controlled <code>View</code>
     */
    public void hide() {
        if (!isViewVisible())
            return;

        // This call is required to clear the timer task
        cancelAutoHideTask();
        // Starts hide animation
        view.startAnimation(outAnimation);
    }

    /**
     * Shows controlled <code>View</code> and/or resets hide delay timer.
     */
    public void show() {
        if (view == null) {
            Timber.e("The view has not been created yet");
            return;
        }
        // This means that the View is hidden or animation is in progress
        if (autoHideTimer == null) {
            view.clearAnimation();
            // Need to re-layout the View
            view.setVisibility(View.GONE);
            view.setVisibility(View.VISIBLE);

            if (listener != null) {
                listener.onAutoHideStateChanged(this, View.VISIBLE);
            }
        }
        reScheduleAutoHideTask();
    }

    /**
     * Returns <code>true</code> if controlled <code>View</code> is currently visible.
     *
     * @return <code>true</code> if controlled <code>View</code> is currently visible.
     */
    private boolean isViewVisible() {
        return view.getVisibility() == View.VISIBLE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAnimationStart(Animation animation) {
        // if(animation == inAnimation)
        // {
        // view.setVisibility(View.VISIBLE);
        // reScheduleAutoHideTask();
        // }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAnimationEnd(Animation animation) {
        // If it's hide animation and the task wasn't cancelled
        if (animation == outAnimation && autoHideTimer == null) {
            view.setVisibility(View.GONE);

            if (listener != null) {
                listener.onAutoHideStateChanged(this, View.GONE);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAnimationRepeat(Animation animation) {
    }

    /**
     * Hide <code>View</code> timer task class.
     */
    class AutoHideTask extends TimerTask {
        @Override
        public void run() {
            runOnUiThread(AutoHideController.this::hide);
        }
    }

    /**
     * Interface which can be used for listening to controlled view visibility state changes. Must be implemented by
     * the parent <code>Activity</code>, which will be registered as a listener when this fragment is created.
     */
    public interface AutoHideListener {
        /**
         * Fired when controlled <code>View</code> visibility is changed by this controller.
         *
         * @param source the source <code>AutoHideController</code> of the event.
         * @param visibility controlled <code>View</code> visibility state.
         */
        void onAutoHideStateChanged(AutoHideController source, int visibility);
    }

    /**
     * Creates new parametrized instance of <code>AutoHideController</code>.
     *
     * @param viewId identifier of the <code>View</code> that will be auto hidden
     * @param hideTimeout auto hide delay in ms
     *
     * @return new parametrized instance of <code>AutoHideController</code>.
     */
    public static AutoHideController getInstance(int viewId, long hideTimeout) {
        AutoHideController ahCtrl = new AutoHideController();

        Bundle args = new Bundle();
        args.putInt(ARG_VIEW_ID, viewId);
        args.putLong(ARG_HIDE_TIMEOUT, hideTimeout);
        ahCtrl.setArguments(args);

        return ahCtrl;
    }
}
