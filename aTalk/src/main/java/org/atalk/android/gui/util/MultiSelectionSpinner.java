/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.android.gui.util;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.util.AttributeSet;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

import org.atalk.android.R;

import java.util.*;

import timber.log.Timber;

/**
 * Implementation of a multiple selection spinner interface:
 * The dropdown list is implemented as a dialog in which items are kept separately from the spinner list.
 * Displaying of user selection is by setting spinner list to single item built from user selected items
 *
 * Note: Do not change extends Spinner, ignored android error highlight
 *
 * @author Eng Chong Meng
 */
public class MultiSelectionSpinner extends Spinner implements OnMultiChoiceClickListener, OnCancelListener
{
    private static String selected_ALL = "ALL";
    private static String selected_NONE = "NONE";

    private List<String> items;
    private boolean[] mSelected = null;
    private MultiSpinnerListener listener;
    ArrayAdapter<String> mAdapter;

    public MultiSelectionSpinner(Context context)
    {
        super(context);
        mAdapter = new ArrayAdapter<>(context, R.layout.simple_spinner_item);
        super.setAdapter(mAdapter);
    }

    public MultiSelectionSpinner(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        mAdapter = new ArrayAdapter<>(context, R.layout.simple_spinner_item);
        super.setAdapter(mAdapter);
    }

    public void onClick(DialogInterface dialog, int which, boolean isChecked)
    {
        if (mSelected != null && which < mSelected.length) {
            mSelected[which] = isChecked;
        }
        else {
            Timber.w("IllegalArgument Exception - 'which' is out of bounds. %s", which);
        }
    }

    /**
     * Internal call and when exit MultiSelectionSpinner:
     * update spinner UI and return call back to registered listener for action
     *
     * @param dialog
     */
    @Override
    public void onCancel(DialogInterface dialog)
    {
        updateSpinnerSelection();

        if (listener != null)
            listener.onItemsSelected(this, mSelected);
    }

    /**
     * Build the dropdown list alert for user selection

     * @return true always.
     */
    @Override
    public boolean performClick()
    {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setMultiChoiceItems(items.toArray(new CharSequence[0]), mSelected, this);

        builder.setPositiveButton(android.R.string.ok, (dialog, which) -> dialog.cancel());
        builder.setOnCancelListener(this);

        builder.show();
        return true;
    }

    /**
     * Build the dropdown list for user selection
     *
     * @param items list of options for user selection
     * @param listener callback when user exit the dialog
     */
    public void setItems(List<String> items, MultiSpinnerListener listener)
    {
        this.items = items;
        this.listener = listener;

        // Default all to unselected; until setSelection()
        mSelected = new boolean[items.size()];
        Arrays.fill(mSelected, false);

        mAdapter = new ArrayAdapter<>(getContext(), R.layout.simple_spinner_item, new String[]{"NONE"});
        setAdapter(mAdapter);
    }

    /**
     * init the default selected options; usually followed by called to setItems
     *
     * @param selection default selected options
     */
    public void setSelection(List<String> selection)
    {
        for (int i = 0; i < mSelected.length; i++) {
            mSelected[i] = false;
        }
        for (String sel : selection) {
            for (int j = 0; j < items.size(); ++j) {
                if (items.get(j).equals(sel)) {
                    mSelected[j] = true;
                }
            }
        }
        updateSpinnerSelection();
    }

    /**
     * Select single option as per the given index
     *
     * @param index index to the items list
     */
    public void setSelection(int index)
    {
        for (int i = 0; i < mSelected.length; i++) {
            mSelected[i] = false;
        }
        if (index >= 0 && index < mSelected.length) {
            mSelected[index] = true;
        }
        else {
            throw new IllegalArgumentException("Index " + index + " is out of bounds.");
        }
        updateSpinnerSelection();
    }

    /**
     * Select options as per the given indices
     *
     * @param index indices to the items list
     */
    public void setSelection(int[] selectedIndices)
    {
        for (int i = 0; i < mSelected.length; i++) {
            mSelected[i] = false;
        }
        for (int index : selectedIndices) {
            if (index >= 0 && index < mSelected.length) {
                mSelected[index] = true;
            }
            else {
                throw new IllegalArgumentException("Index " + index + " is out of bounds.");
            }
        }
        updateSpinnerSelection();
    }

    /**
     * Return the selected options in List<String>
     *
     * @return List of selected options
     */
    public List<String> getSelectedStrings()
    {
        List<String> selection = new LinkedList<>();
        for (int i = 0; i < items.size(); ++i) {
            if (mSelected[i]) {
                selection.add(items.get(i));
            }
        }
        return selection;
    }

    /**
     * Return the selected options indices in List<Integer>
     *
     * @return List of selected options indeces
     */

    public List<Integer> getSelectedIndices()
    {
        List<Integer> selection = new LinkedList<>();
        for (int i = 0; i < items.size(); ++i) {
            if (mSelected[i]) {
                selection.add(i);
            }
        }
        return selection;
    }

    /**
     * Build the user selected options as "," separated String
     *
     * @return selected options as String
     */
    public String getSelectedItemsAsString()
    {
        StringBuilder sb = new StringBuilder();
        int selectedItem = 0;

        for (int i = 0; i < items.size(); ++i) {
            if (mSelected[i]) {
                if (selectedItem != 0) {
                    sb.append(", ");
                }
                sb.append(items.get(i));
                selectedItem++;
            }
        }

        if (selectedItem == 0) {
            return selected_NONE;
        }
        else if (selectedItem == items.size()) {
            return selected_ALL;
        }
        return sb.toString();
    }

    /**
     * Display the selected options in spinner UI
     */
    public void updateSpinnerSelection()
    {
        String spinnerText = getSelectedItemsAsString();
        mAdapter = new ArrayAdapter<>(getContext(), R.layout.simple_spinner_item, new String[]{spinnerText});
        setAdapter(mAdapter);
    }

    /**
     * call back when use has exited the MultiSelectionSpinner
     * Note: the returned selected checks must be in the same order as the given items
     */
    public interface MultiSpinnerListener
    {
        void onItemsSelected(MultiSelectionSpinner multiSelectionSpinner, boolean[] selected);
    }
}