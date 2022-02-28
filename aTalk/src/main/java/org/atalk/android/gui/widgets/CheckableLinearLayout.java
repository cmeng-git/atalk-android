/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.widgets;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.*;

/*
 * This class implements <code>Checkable</code> interface in order to provide custom <code>ListView</code> row layouts that can
 * be checked. The layout retrieves first child <code>CheckedTextView</code> and serves as a proxy between the ListView.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class CheckableLinearLayout extends LinearLayout implements Checkable
{

    /**
     * Instance of <code>CheckedTextView</code> to which this layout delegates <code>Checkable</code> interface calls.
     */
    private CheckedTextView checkbox;

    /**
     * Creates new instance of <code>CheckableRelativeLayout</code>.
     *
     * @param context the context
     * @param attrs attributes set
     */
    public CheckableLinearLayout(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    /**
     * Overrides in order to retrieve <code>CheckedTextView</code>.
     */
    @Override
    protected void onFinishInflate()
    {
        super.onFinishInflate();

        int chCount = getChildCount();
        for (int i = 0; i < chCount; ++i) {
            View v = getChildAt(i);
            if (v instanceof CheckedTextView) {
                checkbox = (CheckedTextView) v;
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean isChecked()
    {
        return (checkbox != null) && checkbox.isChecked();
    }

    /**
     * {@inheritDoc}
     */
    public void setChecked(boolean checked)
    {
        if (checkbox != null) {
            checkbox.setChecked(checked);
        }
    }

    /**
     * {@inheritDoc}
     */
    public void toggle()
    {
        if (checkbox != null) {
            checkbox.toggle();
        }
    }
}
