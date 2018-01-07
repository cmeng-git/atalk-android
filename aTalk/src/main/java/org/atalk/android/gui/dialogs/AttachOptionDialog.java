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
	private ListView mListView = null;
	private AttachOptionModeAdapter mAttachOptionAdapter = null;
	private AttachOptionItem mSelectedItem = null;
	private ChatActivity mParent = null;
	private final Contact mContact;

	public AttachOptionDialog(Context context, final Contact contact) {
		super(context);
		mContact = contact;
		mParent = (ChatActivity) context;
		setTitle(R.string.title_activity_attach_option_dialog);
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_attach_option_dialog);

		mListView = (ListView) this.findViewById(R.id.attach_optionlist);
		List<AttachOptionItem> items = new ArrayList<AttachOptionItem>(Arrays.asList(AttachOptionItem.values()));
		mAttachOptionAdapter = new AttachOptionModeAdapter(this.getContext(), R.layout.attach_option_child_row, items);
		mListView.setAdapter(mAttachOptionAdapter);
		mListView.setOnItemClickListener(new OnItemClickListener()
		{
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id)
			{
				mSelectedItem = mAttachOptionAdapter.getItem((int) id);
				mParent.sendAttachment(mSelectedItem, mContact);
				closeDialog();
			}
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
				holder.imgIcon = (ImageView) row.findViewById(R.id.attachOption_icon);
				holder.txtTitle = (TextView) row.findViewById(R.id.attachOption_screenname);

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
