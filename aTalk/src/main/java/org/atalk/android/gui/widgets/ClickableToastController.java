/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.widgets;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import org.atalk.android.R;

/**
 * Animated version of {@link LegacyClickableToastCtrl}
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ClickableToastController extends LegacyClickableToastCtrl
{
    /**
     * Show animation length
     */
    private static final long SHOW_DURATION = 2000;

    /**
     * Hide animation length
     */
    private static final long HIDE_DURATION = 2000;

    /**
     * The animator object used to animate toast <code>View</code> alpha property.
     */
    private final ObjectAnimator mToastAnimator;

    /**
     * Creates new instance of <code>ClickableToastController</code>.
     *
     * @param toastView the <code>View</code> that will be animated. Must contain <code>R.id.toast_msg</code> <code>TextView</code>.
     * @param clickListener the click listener that will be notified when the toast is clicked.
     */
    public ClickableToastController(View toastView, View.OnClickListener clickListener)
    {
        this(toastView, clickListener, R.id.toast_msg);
    }

    /**
     * Creates new instance of <code>ClickableToastController</code>.
     *
     * @param toastView the <code>View</code> that will be animated. Must contain <code>R.id.toast_msg</code> <code>TextView</code>.
     * @param clickListener the click listener that will be notified when the toast is clicked.
     * @param toastButtonId the id of <code>View</code> contained in <code>toastView
     * </code> that will be used as a button.
     */
    public ClickableToastController(View toastView, View.OnClickListener clickListener, int toastButtonId)
    {
        super(toastView, clickListener, toastButtonId);

        // Initialize animator
        mToastAnimator = new ObjectAnimator();
        mToastAnimator.setPropertyName("alpha");
        mToastAnimator.setTarget(toastView);
    }

    /**
     * Shows the toast.
     *
     * @param immediate if <code>true</code> there wil be no animation.
     * @param message the toast text to use.
     */
    public void showToast(boolean immediate, CharSequence message)
    {
        // Must process in UI thread as caller can be from background
        new Handler(Looper.getMainLooper()).post(() -> {
            super.showToast(immediate, message);
            if (!immediate) {
                mToastAnimator.cancel();
                mToastAnimator.setFloatValues(0, 1);
                mToastAnimator.setDuration(SHOW_DURATION);
                mToastAnimator.start();
            }
        });
    }

    /**
     * Hides the toast.
     *
     * @param immediate if <code>true</code> no animation will be used.
     */
    public void hideToast(boolean immediate)
    {
        super.hideToast(immediate);
        if (!immediate) {
            mToastAnimator.cancel();
            mToastAnimator.setFloatValues(1, 0);
            mToastAnimator.setDuration(HIDE_DURATION);
            mToastAnimator.start();
            mToastAnimator.addListener(new Animator.AnimatorListener()
            {
                public void onAnimationStart(Animator animation)
                {
                }

                public void onAnimationEnd(Animator animation)
                {
                    onHide();
                }

                public void onAnimationCancel(Animator animation)
                {
                }

                public void onAnimationRepeat(Animator animation)
                {
                }
            });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onHide()
    {
        super.onHide();
        toastView.setAlpha(0);
    }
}
