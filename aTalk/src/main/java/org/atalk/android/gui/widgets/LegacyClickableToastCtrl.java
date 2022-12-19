/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.widgets;

import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;

import org.atalk.android.R;

/**
 * The controller used for displaying a custom toast that can be clicked.
 *
 * @author Pawel Domas
 */
public class LegacyClickableToastCtrl
{
    /**
     * How long the toast will be displayed.
     */
    private static final long DISPLAY_DURATION = 10000;

    /**
     * The toast <code>View</code> container.
     */
    protected View toastView;

    /**
     * The <code>TextView</code> displaying message text.
     */
    private TextView messageView;

    /**
     * Handler object used for hiding the toast if it's not clicked.
     */
    private Handler hideHandler = new Handler();

    /**
     * The listener that will be notified when the toast is clicked.
     */
    private View.OnClickListener clickListener;

    /**
     * State object for message text.
     */
    protected CharSequence toastMessage;

    /**
     * Creates new instance of <code>ClickableToastController</code>.
     *
     * @param toastView the <code>View</code> that will be animated. Must contain <code>R.id.toast_msg</code> <code>TextView</code>.
     * @param clickListener the click listener that will be notified when the toast is clicked.
     */
    public LegacyClickableToastCtrl(View toastView, View.OnClickListener clickListener)
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
    public LegacyClickableToastCtrl(View toastView, View.OnClickListener clickListener, int toastButtonId)
    {
        this.toastView = toastView;

        this.clickListener = clickListener;

        messageView = toastView.findViewById(R.id.toast_msg);

        toastView.findViewById(toastButtonId).setOnClickListener(view -> {
            hideToast(false);
            LegacyClickableToastCtrl.this.clickListener.onClick(view);
        });

        hideToast(true);
    }

    /**
     * Shows the toast.
     *
     * @param immediate if <code>true</code> there wil be no animation.
     * @param message the toast text to use.
     */
    public void showToast(boolean immediate, CharSequence message)
    {
        toastMessage = message;
        messageView.setText(toastMessage);

        hideHandler.removeCallbacks(hideRunnable);
        hideHandler.postDelayed(hideRunnable, DISPLAY_DURATION);
        toastView.setVisibility(View.VISIBLE);
    }

    /**
     * Hides the toast.
     *
     * @param immediate if <code>true</code> no animation will be used.
     */
    public void hideToast(boolean immediate)
    {
        hideHandler.removeCallbacks(hideRunnable);
        if (immediate) {
            onHide();
        }
    }

    /**
     * Performed to hide the toast view.
     */
    protected void onHide()
    {
        toastView.setVisibility(View.GONE);
        toastMessage = null;
    }

    /**
     * {@inheritDoc}
     */
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putCharSequence("toast_message", toastMessage);
    }

    /**
     * {@inheritDoc}
     */
    public void onRestoreInstanceState(Bundle savedInstanceState)
    {
        if (savedInstanceState != null) {
            toastMessage = savedInstanceState.getCharSequence("toast_message");

            if (!TextUtils.isEmpty(toastMessage)) {
                showToast(true, toastMessage);
            }
        }
    }

    /**
     * Hides the toast after delay.
     */
    private Runnable hideRunnable = () -> hideToast(false);
}
