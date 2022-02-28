/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.util;

import android.content.Context;
import android.os.Handler;
import android.text.InputType;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import org.atalk.android.aTalkApp;

import timber.log.Timber;

/**
 * Utility class that encapsulates common operations on some <code>View</code> types.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ViewUtil
{
    /**
     * Sets given <code>text</code> on the <code>TextView</code> identified by the <code>id</code>. The
     * <code>TextView</code> must be inside <code>container</code> view hierarchy.
     *
     * @param container the <code>View</code> that contains the <code>TextView</code>.
     * @param id the id of <code>TextView</code> we want to edit.
     * @param text string value that will be set on the <code>TextView</code>.
     */
    public static void setTextViewValue(View container, int id, String text)
    {
        TextView tv = container.findViewById(id);
        tv.setText(text);
    }

    //	public static void setTextViewHtml(View container, int id, String text)
    //	{
    //		TextView tv = (TextView) container.findViewById(id);
    //		tv.setText(android.text.Html.fromHtml(text));
    //	}

    public static void setTextViewValue(View container, String tag, String text)
    {
        TextView tv = container.findViewWithTag(tag);
        tv.setText(text);
    }

    public static void setTextViewColor(View container, int id, int color)
    {
        TextView tv = container.findViewById(id);
        tv.setTextColor(aTalkApp.getAppResources().getColor(color));
    }

    public static void setTextViewAlpha(View container, int id, float alpha)
    {
        TextView tv = container.findViewById(id);
        tv.setAlpha(alpha);
    }

    public static String getTextViewValue(View container, int id)
    {
        return toString(container.findViewById(id));
    }

    public static boolean isCompoundChecked(View container, int id)
    {
        return ((CompoundButton) container.findViewById(id)).isChecked();
    }

    public static void setCompoundChecked(View container, int id, boolean isChecked)
    {
        ((CompoundButton) container.findViewById(id)).setChecked(isChecked);
    }

    public static void setCompoundChecked(View container, String tag, boolean isChecked)
    {
        ((CompoundButton) container.findViewWithTag(tag)).setChecked(isChecked);
    }

    /**
     * Sets image identified by <code>drawableId</code> resource id on the <code>ImageView</code>.
     * <code>ImageView</code> must exist in <code>container</code> view hierarchy.
     *
     * @param container the container <code>View</code>.
     * @param imageViewId id of <code>ImageView</code> that will be used.
     * @param drawableId the resource id of drawable that will be set.
     */
    public static void setImageViewIcon(View container, int imageViewId, int drawableId)
    {
        ImageView imageView = container.findViewById(imageViewId);
        imageView.setImageResource(drawableId);
    }

    /**
     * Ensures that the <code>View</code> is currently in visible or hidden state which depends on
     * <code>isVisible</code> flag.
     *
     * @param container parent <code>View</code> that contains displayed <code>View</code>.
     * @param viewId the id of <code>View</code> that will be shown/hidden.
     * @param isVisible flag telling whether the <code>View</code> has to be shown or hidden.
     */
    static public void ensureVisible(View container, int viewId, boolean isVisible)
    {
        View view = container.findViewById(viewId);
        if (isVisible && view.getVisibility() != View.VISIBLE) {
            view.setVisibility(View.VISIBLE);
        }
        else if (!isVisible && view.getVisibility() != View.GONE) {
            view.setVisibility(View.GONE);
        }
    }

    /**
     * Ensures that the <code>View</code> is currently in enabled or disabled state.
     *
     * @param container parent <code>View</code> that contains displayed <code>View</code>.
     * @param viewId the id of <code>View</code> that will be enabled/disabled.
     * @param isEnabled flag telling whether the <code>View</code> has to be enabled or disabled.
     */
    static public void ensureEnabled(View container, int viewId, boolean isEnabled)
    {
        View view = container.findViewById(viewId);
        if (isEnabled && !view.isEnabled()) {
            view.setEnabled(isEnabled);
        }
        else if (!isEnabled && view.isEnabled()) {
            view.setEnabled(isEnabled);
        }
    }

    /**
     * Ensures that the <code>View</code> is currently in enabled or disabled state.
     *
     * @param container parent <code>View</code> that contains displayed <code>View</code>.
     * @param tag the tag of <code>View</code> that will be enabled/disabled.
     * @param isEnabled flag telling whether the <code>View</code> has to be enabled or disabled.
     */
    static public void ensureEnabled(View container, String tag, boolean isEnabled)
    {
        View view = container.findViewWithTag(tag);
        if (isEnabled && !view.isEnabled()) {
            view.setEnabled(isEnabled);
        }
        else if (!isEnabled && view.isEnabled()) {
            view.setEnabled(isEnabled);
        }
    }

    /**
     * Sets given <code>view</code> visibility state using it's handler.
     *
     * @param view the view which visibility state will be changed.
     * @param visible new visibility state o set.
     */
    public static void setViewVisible(final View view, final boolean visible)
    {
        final int newState = visible ? View.VISIBLE : View.GONE;
        if (view.getVisibility() == newState) {
            return;
        }

        Handler viewHandler = view.getHandler();
        if (viewHandler == null) {
            Timber.w("Handler not available for view %s", view);
            return;
        }

        viewHandler.post(() -> view.setVisibility(newState));
    }

    /**
     * get the textView string value or null (length == 0)
     *
     * @param textView TextView or EditText
     * @return String or null
     */
    public static String toString(final TextView textView)
    {
        CharSequence editText = (textView == null) ? null : textView.getText();
        String text = (editText == null) ? null : editText.toString().trim();
        return ((text == null) || (text.length() == 0)) ? null : text;
    }

    /**
     * get the textView string value or null (length == 0)
     *
     * @param textView TextView or EditText
     * @return String or null
     */
    public static char[] toCharArray(final TextView textView)
    {
        String text = toString(textView);
        return (text == null) ? null : text.toCharArray();
    }

    /**
     * Show or hide password
     *
     * @param view the password EditText view
     * @param show <code>true</code> set password visible to user
     */
    public static void showPassword(final EditText view, final boolean show)
    {
        int cursorPosition = view.getSelectionStart();
        if (show) {
            view.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        else {
            view.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        view.setSelection(cursorPosition);
    }

    /**
     * Hide soft keyboard
     *
     * @param context context
     * @param view the reference view
     */
    public static void hideKeyboard(Context context, View view)
    {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null)
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
}
