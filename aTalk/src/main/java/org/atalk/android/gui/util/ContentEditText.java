package org.atalk.android.gui.util;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v13.view.inputmethod.*;
import android.support.v7.widget.AppCompatEditText;
import android.util.AttributeSet;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class ContentEditText extends AppCompatEditText
{
    private static final String MIME_TYPE_GIF = "image/gif";
    private static final String MIME_TYPE_PNG = "image/png";
    private static final String MIME_TYPE_WEBP = "image/webp";

    private CommitListener commitListener;

    public ContentEditText(Context context)
    {
        super(context);
    }

    public ContentEditText(Context context, AttributeSet attrs)
    {
        super(context, attrs);
    }

    public ContentEditText(Context context, AttributeSet attrs, int defStyleAttr)
    {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public InputConnection onCreateInputConnection(EditorInfo editorInfo)
    {
        final InputConnection inputConnection = super.onCreateInputConnection(editorInfo);
        EditorInfoCompat.setContentMimeTypes(editorInfo, new String[]{MIME_TYPE_GIF, MIME_TYPE_PNG, MIME_TYPE_WEBP});

        final InputConnectionCompat.OnCommitContentListener callback = new InputConnectionCompat.OnCommitContentListener()
        {
            @Override
            public boolean onCommitContent(InputContentInfoCompat inputContentInfo, int flags, Bundle opts)
            {
                // read and display inputContentInfo asynchronously
                if ((Build.VERSION.SDK_INT >= 25)
                        && (flags & InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) != 0) {
                    try {
                        inputContentInfo.requestPermission();
                    } catch (Exception e) {
                        // return false if failed
                        return false;
                    }
                }
                // read and display inputContentInfo asynchronously.
                // call inputContentInfo.releasePermission() as needed.

                if (commitListener != null) {
                    commitListener.onCommitContent(inputContentInfo);
                }
                // return true if succeeded
                return true;
            }
        };
        return InputConnectionCompat.createWrapper(inputConnection, editorInfo, callback);
    }

    public void setCommitListener(CommitListener listener)
    {
        this.commitListener = listener;
    }

    public interface CommitListener
    {
        void onCommitContent(InputContentInfoCompat info);
    }
}
