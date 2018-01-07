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
package org.atalk.crypto;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.view.*;
import android.widget.*;

import net.java.sip.communicator.plugin.otr.*;
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact;
import net.java.sip.communicator.service.contactlist.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.Logger;
import net.java.sip.communicator.util.account.AccountUtils;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.util.ViewUtil;
import org.atalk.crypto.omemo.*;
import org.atalk.persistance.DatabaseBackend;
import org.atalk.service.osgi.OSGiActivity;
import org.atalk.util.CryptoHelper;
import org.jivesoftware.smackx.omemo.OmemoFingerprint;
import org.jivesoftware.smackx.omemo.internal.OmemoDevice;
import org.jivesoftware.smackx.omemo.signal.SignalOmemoService;

import java.util.*;

import static org.atalk.android.R.id.fingerprint;

/**
 * Settings screen with known buddy fingerprints for all buddyFPs.
 *
 * @author Eng Chong Meng
 */
public class CryptoBuddyFingerPrints extends OSGiActivity
{
	/**
	 * The logger
	 */
	private static final Logger logger = Logger.getLogger(CryptoBuddyFingerPrints.class);

	private static final String OTR = "OTR:";
	private static final String OMEMO = "OMEMO:";

	private SQLiteDatabase mDB;
	private SQLiteOmemoStore mOmemoStore;
	private ScOtrKeyManager keyManager = OtrActivator.scOtrKeyManager;

	/**
	 * Fingerprints adapter instance.
	 */
	private FingerprintListAdapter fpListAdapter;

	private final HashMap<String, Contact> contactList = new HashMap<>();
	private final Map<String, List<String>> buddyFingerprints = new TreeMap<>();

	private final LinkedHashMap<String, List<FingerprintStatus>> buddyOmemoFPStatus
			= new LinkedHashMap<>();

	private String bareJid;
	private Contact contact;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		mDB = DatabaseBackend.getReadableDB();
		mOmemoStore = (SQLiteOmemoStore) SignalOmemoService.getInstance().getOmemoStoreBackend();
		setContentView(R.layout.list_layout);

		fpListAdapter = new FingerprintListAdapter(getBuddyFingerPrints());
		ListView fingerprintsList = ((ListView) findViewById(R.id.list));
		fingerprintsList.setAdapter(fpListAdapter);
		registerForContextMenu(fingerprintsList);
	}

	/**
	 * Gets the list of all known buddyFPs.
	 *
	 * @return the list of all known buddyFPs.
	 */
	Map<String, List<String>> getBuddyFingerPrints()
	{
		// Get the protocol providers and meta-contactList service
		Collection<ProtocolProviderService> providers = AccountUtils.getRegisteredProviders();
		MetaContactListService mclService = AndroidGUIActivator.getContactListService();
		List<String> fpList = new ArrayList<>();

		// Populate buddyFPs' fingerPrints.
		for (ProtocolProviderService pps : providers) {
			Iterator<MetaContact> metaContacts = mclService.findAllMetaContactsForProvider(pps);
			while (metaContacts.hasNext()) {
				MetaContact metaContact = metaContacts.next();
				Iterator<Contact> contacts = metaContact.getContacts();
				while (contacts.hasNext()) {
					contact = contacts.next();

					bareJid = OMEMO + contact.getAddress();
					fpList = getFingerprintStatuses(contact.getAddress());
					if ((fpList != null) && !fpList.isEmpty()) {
						buddyFingerprints.put(bareJid, fpList);
					}

					bareJid = OTR + contact.getAddress();
					if (!contactList.containsKey(bareJid)) {
						contactList.put(bareJid, contact);
						fpList = keyManager.getAllRemoteFingerprints(contact);
						if ((fpList != null) && !fpList.isEmpty()) {
							buddyFingerprints.put(bareJid, fpList);
						}
					}
				}
			}
		}
		return buddyFingerprints;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo)
	{
		boolean isVerified = false;
		boolean keyExists = true;

		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.fingerprint_ctx_menu, menu);

		ListView.AdapterContextMenuInfo ctxInfo = (AdapterView.AdapterContextMenuInfo) menuInfo;
		int pos = ctxInfo.position;

		String remoteFingerprint = fpListAdapter.getFingerprintFromRow(pos);
		bareJid = fpListAdapter.getBareJidFromRow(pos);
		if (bareJid.startsWith(OMEMO)) {
			isVerified = isOmemoFPVerified(bareJid, remoteFingerprint);
		}
		else if (bareJid.startsWith(OTR)) {
			Contact contact = contactList.get(bareJid);
			isVerified = keyManager.isVerified(contact, remoteFingerprint);
			keyExists = keyManager.getAllRemoteFingerprints(contact) != null;
		}
		menu.findItem(R.id.trust).setEnabled(!isVerified && keyExists);
		menu.findItem(R.id.distrust).setEnabled(isVerified);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		AdapterView.AdapterContextMenuInfo info
				= (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();

		int pos = info.position;
		bareJid = fpListAdapter.getBareJidFromRow(pos);
		Contact contact = contactList.get(bareJid);
		OtrContact otrContact = OtrContactManager.getOtrContact(contact, null);
		String remoteFingerprint = fpListAdapter.getFingerprintFromRow(pos);

		int id = item.getItemId();
		switch (id) {
			case R.id.trust:
				if (bareJid.startsWith(OMEMO)) {
					trustOmemoFingerPrint(bareJid, remoteFingerprint);
					String msg = getString(R.string.crypto_toast_OMEMO_TRUST_MESSAGE_RESUME, bareJid);
					Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
				}
				else
					keyManager.verify(otrContact, remoteFingerprint);
				fpListAdapter.notifyDataSetChanged();
				return true;

			case R.id.distrust:
				if (bareJid.startsWith(OMEMO)) {
					distrustOmemoFingerPrint(bareJid, remoteFingerprint);
					String msg = getString(R.string.crypto_toast_OMEMO_DISTRUST_MESSAGE_STOP, bareJid);
					Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
				}
				else
					keyManager.unverify(otrContact, remoteFingerprint);
				fpListAdapter.notifyDataSetChanged();
				return true;

			case R.id.copy:
				ClipboardManager cbManager
						= (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
				cbManager.setPrimaryClip(ClipData.newPlainText(null,
						CryptoHelper.prettifyFingerprint(remoteFingerprint)));
				Toast.makeText(this, R.string.crypto_toast_FINGERPRINT_COPY, Toast.LENGTH_SHORT)
						.show();
				return true;
            case R.id.cancel:
                return true;
		}
		return super.onContextItemSelected(item);
	}

	/**
	 * Adapter displays fingerprints for given list of <tt>Contact</tt>s.
	 */
	private class FingerprintListAdapter extends BaseAdapter
	{
		/**
		 * The list of currently displayed buddy FingerPrints.
		 */
		private final Map<String, List<String>> buddyFPs;

		/**
		 * Creates new instance of <tt>FingerprintListAdapter</tt>.
		 *
		 * @param linkedHashMap
		 * 		list of <tt>Contact</tt> for which OTR fingerprints will be displayed.
		 */
		FingerprintListAdapter(Map<String, List<String>> linkedHashMap)
		{
			buddyFPs = linkedHashMap;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public int getCount()
		{
			int fpSize = 0;
			for (List<String> fps : buddyFPs.values()) {
				fpSize += fps.size();
			}
			return fpSize;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public Object getItem(int position)
		{
			return getBareJidFromRow(position);
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public long getItemId(int position)
		{
			return position;
		}

		/**
		 * {@inheritDoc}
		 */
		@Override
		public View getView(int position, View rowView, ViewGroup parent)
		{
			if (rowView == null)
				rowView = getLayoutInflater().inflate(R.layout.crypto_fingerprint_row, parent,
					false);

			boolean isVerified = false;
			bareJid = getBareJidFromRow(position);
			contact = contactList.get(bareJid);
			String remoteFingerprint = getFingerprintFromRow(position);

			ViewUtil.setTextViewValue(rowView, R.id.protocolProvider, bareJid);
			ViewUtil.setTextViewValue(rowView, fingerprint,
					CryptoHelper.prettifyFingerprint(remoteFingerprint));

			if (bareJid.startsWith(OMEMO)) {
				isVerified = isOmemoFPVerified(bareJid, remoteFingerprint);
			}
			else if (bareJid.startsWith(OTR)) {
				isVerified = keyManager.isVerified(contact, remoteFingerprint);
			}
			int stringRes = isVerified ? R.string.crypto_text_FINGERPRINT_VERIFIED_TRUE
					: R.string.crypto_text_FINGERPRINT_VERIFIED_FALSE;

			String verifyStr = getString(R.string.plugin_otr_configform_VERIFY_STATUS,
					getString(stringRes));
			ViewUtil.setTextViewValue(rowView, R.id.fingerprint_status, verifyStr);
			return rowView;
		}

		String getBareJidFromRow(int row)
		{
			int index = -1;
			for (Map.Entry<String, List<String>> entry : buddyFingerprints.entrySet()) {
				boolean found = false;
				bareJid = entry.getKey();
				List<String> fingerprints = entry.getValue();
				for (String f : fingerprints) {
					index++;
					if (index == row) {
						found = true;
						break;
					}
				}
				if (found)
					break;
			}
			return bareJid;
		}

		String getFingerprintFromRow(int row)
		{
			int index = -1;
			String fingerprint = null;
			for (Map.Entry<String, List<String>> entry : buddyFingerprints.entrySet()) {
				boolean found = false;
				List<String> fingerprints = entry.getValue();
				for (String f : fingerprints) {
					index++;
					if (index == row) {
						fingerprint = f;
						found = true;
						break;
					}
				}
				if (found)
					break;
			}
			return fingerprint;
		}
	}

	// ============== OMEMO Buddy FingerPrints Handlers ================== //
	private List<String> getFingerprintStatuses(String bareJid)
	{
		List<String> fpList = new ArrayList<>();
		List<FingerprintStatus> fpStatusList = new ArrayList<>();
		FingerprintStatus fpStatus;

		String[] args = {bareJid};
		Cursor cursor = mDB.query(SQLiteOmemoStore.IDENTITIES_TABLE_NAME, null,
				SQLiteOmemoStore.BARE_JID + "=?", args, null, null, null);

		ArrayList<String> selectionArgs = new ArrayList<>(3);
		String[] columns = {SQLiteOmemoStore.TRUST, SQLiteOmemoStore.ACTIVE,
				SQLiteOmemoStore.LAST_ACTIVATION, SQLiteOmemoStore.IDENTITY_KEY};

		while (cursor.moveToNext()) {
			fpList.add(cursor.getString(cursor.getColumnIndex(SQLiteOmemoStore.FINGERPRINT)));
			fpStatus = FingerprintStatus.fromCursor(cursor);
			fpStatusList.add(fpStatus);
		}
		cursor.close();
		buddyOmemoFPStatus.put(OMEMO + bareJid, fpStatusList);
		return fpList;
	}

	private boolean isOmemoFPVerified(String bareJid, String fingerprint)
	{
		OmemoDevice omemoDevice = getOmemoDevice(bareJid, fingerprint);
		FingerprintStatus fpStatus = mOmemoStore.getFingerprintStatus(omemoDevice, fingerprint);
		return ((fpStatus != null) && fpStatus.isTrusted());
	}

	/**
	 * Trust an OmemoIdentity. This involves marking the key as trusted.
	 *
	 * @param bareJid
	 * 		bareJid
	 * @param remoteFingerprint
	 * 		fingerprint
	 */
	private void trustOmemoFingerPrint(String bareJid, String remoteFingerprint)
	{
		OmemoDevice omemoDevice = getOmemoDevice(bareJid, remoteFingerprint);
		OmemoFingerprint omemoFingerprint = new OmemoFingerprint(remoteFingerprint);
		mOmemoStore.trustOmemoIdentity(null, omemoDevice, omemoFingerprint);
	}

	/**
	 * Distrust an OmemoIdentity. This involved marking the key as distrusted.
	 *
	 * @param bareJid
	 * 		bareJid
	 * @param remoteFingerprint
	 * 		fingerprint
	 */
	private void distrustOmemoFingerPrint(String bareJid, String remoteFingerprint)
	{
		OmemoDevice omemoDevice = getOmemoDevice(bareJid, remoteFingerprint);
		OmemoFingerprint omemoFingerprint = new OmemoFingerprint(remoteFingerprint);
		mOmemoStore.distrustOmemoIdentity(null, omemoDevice, omemoFingerprint);
	}

	private OmemoDevice getOmemoDevice(String bareJid, String fingerprint)
	{
		OmemoDevice omemoDevice = null;
		List<FingerprintStatus> fpStatusList = buddyOmemoFPStatus.get(bareJid);
		for (FingerprintStatus fpStatus : fpStatusList) {
			if (fingerprint.equals(fpStatus.getFingerPrint())) {
				omemoDevice = fpStatus.getOmemoDevice();
				break;
			}
		}
		return omemoDevice;
	}
}
