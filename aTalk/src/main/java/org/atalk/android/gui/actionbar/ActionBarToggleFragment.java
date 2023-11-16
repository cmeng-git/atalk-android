/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.actionbar;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.atalk.android.R;
import org.atalk.service.osgi.OSGiFragment;

/**
 * Fragment adds a toggle button to the action bar with text description to the right of it.
 * Button is handled through the <code>ActionBarToggleModel</code> which must be implemented by
 * parent <code>Activity</code>.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class ActionBarToggleFragment extends OSGiFragment
{
    /**
     * Text description's argument key
     */
    private static final String ARG_LABEL_TEXT = "text";

    /**
     * Button model
     */
    private ActionBarToggleModel model;

    /**
     * Menu instance used to update the button
     */
    private CompoundButton mToggleCB;

    /**
     * Creates new instance of <code>ActionBarToggleFragment</code>
     */
    public ActionBarToggleFragment()
    {
        setHasOptionsMenu(true);
    }

    /**
     * Creates new instance of <code>ActionBarToggleFragment</code> with given description(can be
     * empty but not <code>null</code>).
     *
     * @param labelText toggle button's description(can be empty, but not <code>null</code>).
     * @return new instance of <code>ActionBarToggleFragment</code> parametrized with description argument.
     */
    static public ActionBarToggleFragment newInstance(String labelText)
    {
        ActionBarToggleFragment fragment = new ActionBarToggleFragment();
        Bundle args = new Bundle();
        args.putString(ARG_LABEL_TEXT, labelText);
        fragment.setArguments(args);

        return fragment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(@NonNull Context context)
    {
        super.onAttach(context);
        this.model = (ActionBarToggleModel) context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.actionbar_toggle_menu, menu);

        // Binds the button
        mToggleCB = menu.findItem(R.id.toggleView).getActionView().findViewById(android.R.id.toggle);
        mToggleCB.setOnCheckedChangeListener((cb, checked) -> model.setChecked(checked));
        // Set label text
        ((TextView) menu.findItem(R.id.toggleView).getActionView().findViewById(android.R.id.text1))
                .setText(getArguments().getString(ARG_LABEL_TEXT));

        updateChecked();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume()
    {
        super.onResume();
        updateChecked();
    }

    /**
     * {@inheritDoc}
     */
    private void updateChecked()
    {
        if (mToggleCB != null) {
            mToggleCB.setChecked(model.isChecked());
        }
    }

    /**
     * Toggle button's model that has to be implemented by parent <code>Activity</code>.
     */
    public interface ActionBarToggleModel
    {
        /**
         * Return <code>true</code> if button's model is currently in checked state.
         *
         * @return <code>true</code> if button's model is currently in checked state.
         */
        boolean isChecked();

        /**
         * Method fired when the button is clicked.
         *
         * @param isChecked <code>true</code> if new button's state is checked.
         */
        void setChecked(boolean isChecked);
    }
}
