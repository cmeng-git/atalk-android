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
 * Utility class that encapsulates common operations on some <tt>View</tt> types.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ViewUtil
{
    /**
     * Sets given <tt>text</tt> on the <tt>TextView</tt> identified by the <tt>id</tt>. The
     * <tt>TextView</tt> must be inside <tt>container</tt> view hierarchy.
     *
     * @param container the <tt>View</tt> that contains the <tt>TextView</tt>.
     * @param id the id of <tt>TextView</tt> we want to edit.
     * @param text string value that will be set on the <tt>TextView</tt>.
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
     * Sets image identified by <tt>drawableId</tt> resource id on the <tt>ImageView</tt>.
     * <tt>ImageView</tt> must exist in <tt>container</tt> view hierarchy.
     *
     * @param container the container <tt>View</tt>.
     * @param imageViewId id of <tt>ImageView</tt> that will be used.
     * @param drawableId the resource id of drawable that will be set.
     */
    public static void setImageViewIcon(View container, int imageViewId, int drawableId)
    {
        ImageView imageView = container.findViewById(imageViewId);
        imageView.setImageResource(drawableId);
    }

    /**
     * Ensures that the <tt>View</tt> is currently in visible or hidden state which depends on
     * <tt>isVisible</tt> flag.
     *
     * @param container parent <tt>View</tt> that contains displayed <tt>View</tt>.
     * @param viewId the id of <tt>View</tt> that will be shown/hidden.
     * @param isVisible flag telling whether the <tt>View</tt> has to be shown or hidden.
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
     * Ensures that the <tt>View</tt> is currently in enabled or disabled state.
     *
     * @param container parent <tt>View</tt> that contains displayed <tt>View</tt>.
     * @param viewId the id of <tt>View</tt> that will be enabled/disabled.
     * @param isEnabled flag telling whether the <tt>View</tt> has to be enabled or disabled.
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
     * Ensures that the <tt>View</tt> is currently in enabled or disabled state.
     *
     * @param container parent <tt>View</tt> that contains displayed <tt>View</tt>.
     * @param tag the tag of <tt>View</tt> that will be enabled/disabled.
     * @param isEnabled flag telling whether the <tt>View</tt> has to be enabled or disabled.
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
     * Sets given <tt>view</tt> visibility state using it's handler.
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
     * @param show <tt>true</tt> set password visible to user
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
