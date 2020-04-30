/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 *
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import android.content.res.Resources;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import org.atalk.android.R;
import org.atalk.android.gui.util.ThemeHelper;
import org.atalk.android.gui.util.ThemeHelper.Theme;
import org.atalk.impl.neomedia.codec.video.CodecInfo;
import org.atalk.service.osgi.OSGiActivity;

import java.util.ArrayList;

/**
 * Activity that lists video <tt>MediaCodec</tt>s available in the system.
 *
 * Meaning of the colors:</br><br/>
 * * blue - codec will be used in call<br/>
 * * white / black - one of the codecs for particular media type, but it won't be used
 * as there is another codec before it on the list<br/>
 * * grey500 - codec is banned and won't be used<br/>
 *
 * Click on codec to toggle it's banned state. Changes are not persistent between
 * aTalk restarts so restarting aTalk restores default values.
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class MediaCodecList extends OSGiActivity implements AdapterView.OnItemClickListener
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list_layout);

        ListView list = findViewById(R.id.list);
        list.setAdapter(new MediaCodecAdapter());
        list.setOnItemClickListener(this);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id)
    {
        MediaCodecAdapter adapter = (MediaCodecAdapter) parent.getAdapter();
        CodecInfo codec = (CodecInfo) adapter.getItem(position);

        // Toggle codec banned state
        codec.setBanned(!codec.isBanned());
        adapter.notifyDataSetChanged();
    }

    class MediaCodecAdapter extends BaseAdapter
    {
        private final ArrayList<CodecInfo> codecs;

        MediaCodecAdapter()
        {
            codecs = new ArrayList<>(CodecInfo.getSupportedCodecs());
        }

        @Override
        public int getCount()
        {
            return codecs.size();
        }

        @Override
        public Object getItem(int position)
        {
            return codecs.get(position);
        }

        @Override
        public long getItemId(int position)
        {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            TextView row = (TextView) convertView;
            if (row == null) {
                row = (TextView) getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);
            }

            row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
            CodecInfo codec = codecs.get(position);
            String codecStr = codec.toString();
            row.setText(codecStr);

            Resources res = getResources();
            int color = codec.isBanned() ? R.color.grey500 : R.color.textColorWhite;
            if (ThemeHelper.isAppTheme(Theme.LIGHT)) {
                color = codec.isBanned() ? R.color.grey500 : R.color.textColorBlack;
            }

            if (codec.isNominated()) {
                color = R.color.blue;
            }
            row.setTextColor(res.getColor(color));
            return row;
        }
    }
}
