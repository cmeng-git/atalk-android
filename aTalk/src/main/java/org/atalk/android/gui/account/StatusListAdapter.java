package org.atalk.android.gui.account;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import net.java.sip.communicator.service.protocol.PresenceStatus;

import org.atalk.android.R;
import org.atalk.android.util.AppImageUtil;

import java.util.Iterator;
import java.util.List;

public class StatusListAdapter extends ArrayAdapter<PresenceStatus>
{
    private final LayoutInflater inflater;

    private ImageView statusIconView;

    /**
     * Creates new instance of {@link StatusListAdapter}
     *
     * @param objects {@link Iterator} for a set of {@link PresenceStatus}
     */
    public StatusListAdapter(@NonNull Context context, int resId, @NonNull List<PresenceStatus> objects)
    {
        super(context, resId, objects);
        inflater = LayoutInflater.from(context);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent)
    {
        final View statusItemView;

        // Retrieve views
        if (convertView == null) {
            statusItemView = inflater.inflate(R.layout.account_presence_status_row, parent, false);
        }
        else {
            statusItemView = convertView;
        }

        statusIconView = statusItemView.findViewById(R.id.presenceStatusIconView);
        TextView statusNameView = statusItemView.findViewById(R.id.presenceStatusNameView);

        // Set status name
        PresenceStatus presenceStatus = getItem(position);
        String statusName = presenceStatus.getStatusName();
        statusNameView.setText(statusName);

        // Set status icon
        Bitmap presenceIcon = AppImageUtil.bitmapFromBytes(presenceStatus.getStatusIcon());
        statusIconView.setImageBitmap(presenceIcon);
        return statusItemView;
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent)
    {
        return getView(position, convertView, parent);
    }

    public Drawable getStatusIcon()
    {
        return statusIconView.getDrawable();
    }
}
