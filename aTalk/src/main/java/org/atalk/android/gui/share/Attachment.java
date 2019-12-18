/*
 * Copyright (c) 2018, Daniel Gultsch All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.atalk.android.gui.share;

import android.content.*;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import org.atalk.persistance.FileBackend;

import java.io.File;
import java.util.*;

import timber.log.Timber;

public class Attachment implements Parcelable
{

    Attachment(Parcel in)
    {
        uri = in.readParcelable(Uri.class.getClassLoader());
        mime = in.readString();
        uuid = UUID.fromString(in.readString());
        type = Type.valueOf(in.readString());
    }

    @Override
    public void writeToParcel(Parcel dest, int flags)
    {
        dest.writeParcelable(uri, flags);
        dest.writeString(mime);
        dest.writeString(uuid.toString());
        dest.writeString(type.toString());
    }

    @Override
    public int describeContents()
    {
        return 0;
    }

    public static final Creator<Attachment> CREATOR = new Creator<Attachment>()
    {
        @Override
        public Attachment createFromParcel(Parcel in)
        {
            return new Attachment(in);
        }

        @Override
        public Attachment[] newArray(int size)
        {
            return new Attachment[size];
        }
    };

    public String getMime()
    {
        return mime;
    }

    public Type getType()
    {
        return type;
    }

    public enum Type
    {
        FILE, IMAGE, LOCATION, RECORDING
    }

    private final Uri uri;
    private final Type type;
    private final UUID uuid;
    private final String mime;

    private Attachment(UUID uuid, Uri uri, Type type, String mime)
    {
        this.uri = uri;
        this.type = type;
        this.mime = mime;
        this.uuid = uuid;
    }

    private Attachment(Uri uri, Type type, String mime)
    {
        this.uri = uri;
        this.type = type;
        this.mime = mime;
        this.uuid = UUID.randomUUID();
    }

    public static boolean canBeSendInband(final List<Attachment> attachments)
    {
        for (Attachment attachment : attachments) {
            if (attachment.type != Type.LOCATION) {
                return false;
            }
        }
        return true;
    }

    public static List<Attachment> of(final Context context, Uri uri, Type type)
    {
        final String mime = type == Type.LOCATION ? null : FileBackend.getMimeType(context, uri);
        return Collections.singletonList(new Attachment(uri, type, mime));
    }

    public static List<Attachment> of(final Context context, List<Uri> uris)
    {
        List<Attachment> attachments = new ArrayList<>();
        for (Uri uri : uris) {
            final String mime = FileBackend.getMimeType(context, uri);
            attachments.add(new Attachment(uri, mime != null && mime.startsWith("image/") ? Type.IMAGE : Type.FILE, mime));
        }
        return attachments;
    }

    public static Attachment of(UUID uuid, final File file, String mime)
    {
        return new Attachment(uuid, Uri.fromFile(file), mime != null && (mime.startsWith("image/") || mime.startsWith("video/")) ? Type.IMAGE : Type.FILE, mime);
    }

    public static List<Attachment> extractAttachments(final Context context, final Intent intent, Type type)
    {
        List<Attachment> uris = new ArrayList<>();
        if (intent == null) {
            return uris;
        }
        final String contentType = intent.getType();
        final Uri data = intent.getData();
        if (data == null) {
            final ClipData clipData = intent.getClipData();
            if (clipData != null) {
                for (int i = 0; i < clipData.getItemCount(); ++i) {
                    final Uri uri = clipData.getItemAt(i).getUri();
                    final String mime = FileBackend.getMimeType(context, uri, contentType);
                    Timber.d("uri = %s; contentType = %s; mime = %s", uri, contentType, mime);
                    uris.add(new Attachment(uri, type, mime));
                }
            }
        }
        else {
            // final String mime = MimeUtils.guessMimeTypeFromUriAndMime(context, data, contentType);
            final String mime = FileBackend.getMimeType(context, data, contentType);
            uris.add(new Attachment(data, type, mime));
        }
        return uris;
    }

    public boolean renderThumbnail()
    {
        return type == Type.IMAGE || (type == Type.FILE && mime != null && (mime.startsWith("video/") || mime.startsWith("image/")));
    }

    public Uri getUri()
    {
        return uri;
    }

    public UUID getUuid()
    {
        return uuid;
    }
}
