package org.atalk.android.gui.dialogs;

import org.atalk.android.R;

/**
 * The <tt>AttachOptionItem</tt> gives list items for optional attachments.
 *
 * @author Eng Chong Meng
 */
public enum AttachOptionItem {
	pic(R.string.attachOptionDialog_picture, R.drawable.ic_attach_photo),
    video(R.string.attachOptionDialog_Videos, R.drawable.ic_attach_video),
	camera(R.string.attachOptionDialog_camera, R.drawable.ic_attach_camera),
	video_record(R.string.attachOptionDialog_videoRecord, R.drawable.ic_attach_video_record),
//	audio_record(R.string.attachOptionDialog_audioRecord, R.drawable.ic_action_audio_record),
//	share_contact(R.string.attachOptionDialog_shareContact, R.drawable.ic_attach_contact),
	share_file(R.string.attachOptionDialog_shareFile,  R.drawable.ic_attach_file);
	
	private final int mIconId;
	private final int mTextId;
	
	AttachOptionItem(int textId, int iconId)
	{
		this.mTextId = textId;
		this.mIconId = iconId;
	}
	
	public int getTextId()
	{
		return mTextId;
	}
	public int getIconId()
	{
		return mIconId;
	}
}
