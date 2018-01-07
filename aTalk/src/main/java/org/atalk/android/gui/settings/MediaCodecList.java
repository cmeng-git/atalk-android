/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.settings;

import java.util.ArrayList;

import org.atalk.impl.neomedia.codec.video.CodecInfo;
import org.atalk.android.R;
import org.atalk.service.osgi.OSGiActivity;

import android.content.res.Resources;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

/**
 * Activity that lists video <tt>MediaCodec</tt>s available in the system.
 * <p/>
 * Meaning of the colors:</br><br/>
 * blue - codec will be used in call<br/>
 * white - one of the codecs for particular media type, but it won't be used as there is another codec before it on the
 * list<br/>
 * grey - codec is banned and won't be used<br/>
 * <p/>
 * Click on codec to toggle it's banned state. Changes are not persistent between Jitsi restarts so restarting jitsi
 * restores default values.
 *
 * @author Pawel Domas
 */
public class MediaCodecList extends OSGiActivity implements AdapterView.OnItemClickListener
{

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.list_layout);

		ListView list = (ListView) findViewById(R.id.list);
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

		MediaCodecAdapter() {
			codecs = new ArrayList<CodecInfo>(CodecInfo.getSupportedCodecs());
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
			TextView row = (TextView) getLayoutInflater().inflate(android.R.layout.simple_list_item_1, parent, false);

			CodecInfo codec = codecs.get(position);
			String codecStr = codec.toString();
			row.setText(codecStr);

			Resources res = getResources();
			int color = codec.isBanned() ? R.color.grey : R.color.white;
			if (codec.isNominated()) {
				color = R.color.blue;
			}
			row.setTextColor(res.getColor(color));

			return row;
		}
	}
}
