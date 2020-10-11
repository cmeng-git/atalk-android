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

package org.atalk.android.gui.share;

import android.graphics.Point;
import android.view.*;
import android.widget.ImageView;
import android.widget.LinearLayout;

import org.atalk.android.MyGlideApp;
import org.atalk.android.R;
import org.atalk.android.databinding.MediaPreviewBinding;
import org.atalk.android.gui.chat.ChatActivity;
import org.atalk.persistance.FilePathHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.databinding.DataBindingUtil;
import androidx.recyclerview.widget.RecyclerView;

public class MediaPreviewAdapter extends RecyclerView.Adapter<MediaPreviewAdapter.MediaPreviewViewHolder>
{
    private final ArrayList<Attachment> mediaPreviews = new ArrayList<>();

    private final ChatActivity chatActivity;

    private final ImageView viewHolder;
    private LinearLayout.LayoutParams layoutParams;

    public MediaPreviewAdapter(ChatActivity fragment, ImageView imgPreview)
    {
        this.chatActivity = fragment;
        viewHolder = imgPreview;

        Point outSize = new Point();
        // Defined a square layout preview of screen width size
        fragment.getWindowManager().getDefaultDisplay().getSize(outSize);
        layoutParams = new LinearLayout.LayoutParams(outSize.x, outSize.x);
    }

    @NonNull
    @Override
    public MediaPreviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType)
    {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        MediaPreviewBinding binding = DataBindingUtil.inflate(layoutInflater, R.layout.media_preview, parent, false);
        return new MediaPreviewViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull MediaPreviewViewHolder holder, int position)
    {
        final Attachment attachment = mediaPreviews.get(position);
        final File file = new File(FilePathHelper.getPath(chatActivity, attachment));
        MyGlideApp.loadImage(holder.binding.mediaPreviewItem, file, true);

        holder.binding.deleteButton.setOnClickListener(v ->
        {
            final int pos = mediaPreviews.indexOf(attachment);
            mediaPreviews.remove(pos);
            notifyItemRemoved(pos);

            // update send button mode
            if (mediaPreviews.isEmpty())
                chatActivity.toggleInputMethod();
        });

        holder.binding.mediaPreviewItem.setOnClickListener(v -> {
            viewHolder.setLayoutParams(layoutParams);
            MyGlideApp.loadImage(viewHolder, file, true);
        });
    }

    public void addMediaPreviews(List<Attachment> attachments)
    {
        // mediaPreviews.clear();
        mediaPreviews.addAll(attachments);
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount()
    {
        return mediaPreviews.size();
    }

    public boolean hasAttachments()
    {
        return mediaPreviews.size() > 0;
    }

    public ArrayList<Attachment> getAttachments()
    {
        return mediaPreviews;
    }

    public void clearPreviews()
    {
        this.mediaPreviews.clear();
    }

    class MediaPreviewViewHolder extends RecyclerView.ViewHolder
    {

        private final MediaPreviewBinding binding;

        MediaPreviewViewHolder(MediaPreviewBinding binding)
        {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
