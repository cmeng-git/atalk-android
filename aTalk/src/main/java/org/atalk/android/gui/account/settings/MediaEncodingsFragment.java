/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.account.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import org.atalk.android.R;
import org.atalk.android.gui.widgets.TouchInterceptor;
import org.atalk.service.osgi.OSGiPreferenceFragment;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.List;

/**
 * The fragment allows user to edit encodings and their priorities.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MediaEncodingsFragment extends OSGiPreferenceFragment implements TouchInterceptor.DropListener
{
    /**
     * Argument key for list of encodings as strings (see {@link MediaEncodingActivity} for utility methods.)
     */
    public static final String ARG_ENCODINGS = "arg.encodings";

    /**
     * Argument key for encodings priorities.
     */
    public static final String ARG_PRIORITIES = "arg.priorities";

    /**
     * Adapter encapsulating manipulation of encodings list and their priorities
     */
    private OrderListAdapter adapter;

    /**
     * List of encodings
     */
    private List<String> encodings;

    /**
     * List of priorities
     */
    private List<Integer> priorities;

    /**
     * Flag holding enabled status for the fragment. All views will be grayed out if the fragment is not enabled.
     */
    private boolean isEnabled = true;

    /**
     * Flag tells us if there were any changes made.
     */
    private boolean hasChanges = false;

    /**
     * Sets enabled status for this fragment.
     *
     * @param isEnabled <code>true</code> to enable the fragment.
     */
    public void setEnabled(boolean isEnabled)
    {
        this.isEnabled = isEnabled;
        adapter.invalidate();
    }

    /**
     * Returns <code>true</code> if this fragment is holding any uncommitted changes
     *
     * @return <code>true</code> if this fragment is holding any uncommitted changes
     */
    public boolean hasChanges()
    {
        return hasChanges;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        Bundle bundle = savedInstanceState == null ? getArguments() : savedInstanceState;

        encodings = (List<String>) bundle.get(ARG_ENCODINGS);
        priorities = (List<Integer>) bundle.get(ARG_PRIORITIES);

        if (encodings.contains("VP8/90000"))
            setPrefTitle(R.string.service_gui_settings_VIDEO_CODECS_TITLE);
        else
            setPrefTitle(R.string.service_gui_settings_AUDIO_CODECS_TITLE);

        View content = inflater.inflate(R.layout.encoding, container, false);

        /**
         * The {@link TouchInterceptor} widget that allows user to drag items to set their order
         */
        TouchInterceptor listWidget = (TouchInterceptor) content.findViewById(R.id.encodingList);
        this.adapter = new OrderListAdapter(R.layout.encoding_item);

        listWidget.setAdapter(adapter);
        listWidget.setDropListener(this);
        return content;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSaveInstanceState(@NotNull Bundle outState)
    {
        super.onSaveInstanceState(outState);

        outState.putSerializable(ARG_ENCODINGS, (Serializable) encodings);
        outState.putSerializable(ARG_PRIORITIES, (Serializable) priorities);
    }

    /**
     * Implements {@link TouchInterceptor.DropListener}
     *
     * @param from index indicating source position
     * @param to index indicating destination position
     */
    public void drop(int from, int to)
    {
        adapter.swapItems(from, to);
        hasChanges = true;
    }

    /**
     * Function used to calculate priority based on item index
     *
     * @param idx the index of encoding on the list
     * @return encoding priority value for given <code>idx</code>
     */
    static public int calcPriority(List<?> encodings, int idx)
    {
        return encodings.size() - idx;
    }

    /**
     * Utility method for calculating encodings priorities.
     *
     * @param idx encoding index in the list
     * @return the priority value for given encoding index.
     */
    private int calcPriority(int idx)
    {
        return calcPriority(encodings, idx);
    }

    /**
     * Creates new <code>EncodingsFragment</code> for given list of encodings and priorities.
     *
     * @param encodings list of encodings as strings.
     * @param priorities list of encodings priorities.
     * @return parametrized instance of <code>EncodingsFragment</code>.
     */
    static public MediaEncodingsFragment newInstance(List<String> encodings, List<Integer> priorities)
    {
        MediaEncodingsFragment fragment = new MediaEncodingsFragment();

        Bundle args = new Bundle();
        args.putSerializable(ARG_ENCODINGS, (Serializable) encodings);
        args.putSerializable(ARG_PRIORITIES, (Serializable) priorities);

        fragment.setArguments(args);
        return fragment;
    }

    /**
     * Returns encodings strings list.
     *
     * @return encodings strings list.
     */
    public List<String> getEncodings()
    {
        return encodings;
    }

    /**
     * Returns encodings priorities list.
     *
     * @return encodings priorities list.
     */
    public List<Integer> getPriorities()
    {
        return priorities;
    }

    /**
     * Class implements encodings model for the list widget. Enables/disables each encoding and sets its priority.
     * It is also responsible for creating Views for list rows.
     */
    class OrderListAdapter extends BaseAdapter
    {
        /**
         * ID of the list row layout
         */
        private final int viewResId;

        /**
         * Creates a new instance of {@link OrderListAdapter}.
         *
         * @param viewResId ID of the list row layout
         */
        public OrderListAdapter(int viewResId)
        {
            this.viewResId = viewResId;
        }

        /**
         * Swaps encodings on the list and changes their priorities
         *
         * @param from source item position
         * @param to destination items position
         */
        void swapItems(int from, int to)
        {
            // Swap positions
            String swap = encodings.get(from);
            int swapPrior = priorities.get(from);
            encodings.remove(from);
            priorities.remove(from);

            // Swap priorities
            encodings.add(to, swap);
            priorities.add(to, swapPrior);

            for (int i = 0; i < encodings.size(); i++) {
                priorities.set(i, priorities.get(i) > 0 ? calcPriority(i) : 0);
            }

            // Update the UI
            invalidate();
        }

        /**
         * Refresh the list on UI thread
         */
        public void invalidate()
        {
            getActivity().runOnUiThread(this::notifyDataSetChanged);
        }

        public int getCount()
        {
            return encodings.size();
        }

        public Object getItem(int i)
        {
            return encodings.get(i);
        }

        public long getItemId(int i)
        {
            return i;
        }

        public View getView(final int i, View view, ViewGroup viewGroup)
        {
            // Creates the list row view
            ViewGroup gv = (ViewGroup) getActivity().getLayoutInflater().inflate(this.viewResId, viewGroup, false);
            // Creates the enable/disable button
            CompoundButton cb = gv.findViewById(android.R.id.checkbox);
            cb.setChecked(priorities.get(i) > 0);
            cb.setOnCheckedChangeListener((cButton, isChecked) -> {
                priorities.set(i, isChecked ? calcPriority(i) : 0);
                hasChanges = true;
            });

            // Create string for given format entry
            String mf = encodings.get(i);
            TextView tv = gv.findViewById(android.R.id.text1);
            tv.setText(mf);
            // Creates the drag handle view(used to grab list entries)
            ImageView iv = gv.findViewById(R.id.dragHandle);
            if (!isEnabled)
                gv.removeView(iv);
            cb.setEnabled(isEnabled);
            tv.setEnabled(isEnabled);

            return gv;
        }
    }
}
