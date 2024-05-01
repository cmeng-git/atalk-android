package org.atalk.android.gui.dialogs;

import org.atalk.android.R;

/**
 * The <code>AttachOptionItem</code> gives list items for optional attachments.
 *
 * @author Eng Chong Meng
 */
public enum AttachOptionItem {
    pic(R.string.attach_picture, R.drawable.ic_attach_photo),
    video(R.string.attach_video, R.drawable.ic_attach_video),
    camera(R.string.attach_take_picture, R.drawable.ic_attach_camera),
    video_record(R.string.attach_record_video, R.drawable.ic_attach_video_record),
    //	audio_record(R.string.attachOptionDialog_audioRecord, R.drawable.ic_action_audio_record),
//	share_contact(R.string.attachOptionDialog_shareContact, R.drawable.ic_attach_contact),
    share_file(R.string.attach_file, R.drawable.ic_attach_file);

    private final int mIconId;
    private final int mTextId;

    AttachOptionItem(int textId, int iconId) {
        this.mTextId = textId;
        this.mIconId = iconId;
    }

    public int getTextId() {
        return mTextId;
    }

    public int getIconId() {
        return mIconId;
    }
}
