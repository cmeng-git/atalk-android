package org.atalk.android.gui.dialogs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import net.java.sip.communicator.service.protocol.Contact;

import org.atalk.android.gui.chat.ChatActivity;
import org.atalk.android.R;

/**
 * The <tt>AttachOptionDialog</tt> provides user with optional attachments.
 *
 * @author Eng Chong Meng
 */
public class AttachOptionDialog extends Dialog
{
    private AttachOptionModeAdapter mAttachOptionAdapter = null;
	private AttachOptionItem mSelectedItem = null;
	private ChatActivity mParent = null;

	public AttachOptionDialog(Context context) {
		super(context);
		mParent = (ChatActivity) context;
		setTitle(R.string.service_gui_FILE_ATTACHMENT);
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_attach_option_dialog);

        ListView mListView = this.findViewById(R.id.attach_optionlist);
		List<AttachOptionItem> items = new ArrayList<>(Arrays.asList(AttachOptionItem.values()));
		mAttachOptionAdapter = new AttachOptionModeAdapter(this.getContext(), R.layout.attach_option_child_row, items);
		mListView.setAdapter(mAttachOptionAdapter);
		mListView.setOnItemClickListener((parent, view, position, id) -> {
            mSelectedItem = mAttachOptionAdapter.getItem((int) id);
            mParent.sendAttachment(mSelectedItem);
            closeDialog();
        });
	}

	public void closeDialog()
	{
		this.cancel();
	}

	public class AttachOptionModeAdapter extends ArrayAdapter<AttachOptionItem>
	{
		int layoutResourceId;
		List<AttachOptionItem> data;
		Context context;

		public AttachOptionModeAdapter(Context context, int textViewResourceId, List<AttachOptionItem> modes) {
			super(context, textViewResourceId, modes);

			this.context = context;
			this.layoutResourceId = textViewResourceId;
			this.data = modes;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent)
		{
			View row = convertView;
			AttachOptionHolder holder;
			if (row == null) {
				LayoutInflater inflater = getLayoutInflater();
				row = inflater.inflate(layoutResourceId, parent, false);

				holder = new AttachOptionHolder();
				holder.imgIcon = row.findViewById(R.id.attachOption_icon);
				holder.txtTitle = row.findViewById(R.id.attachOption_screenname);

				row.setTag(holder);
			}
			else {
				holder = (AttachOptionHolder) row.getTag();
			}

			// AttachOptionItem item = data.get(position);
			holder.txtTitle.setText(getItem(position).getTextId());
			holder.imgIcon.setImageResource(getItem(position).getIconId());
			return row;
		}
	}

	static class AttachOptionHolder
	{
		ImageView imgIcon;
		TextView txtTitle;
	}
}
