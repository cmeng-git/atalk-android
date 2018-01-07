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

package org.jivesoftware.smackx.avatar.cache;

import org.jivesoftware.smack.util.stringencoder.*;
import org.jxmpp.jid.BareJid;

import java.io.*;
import java.util.logging.*;

import static android.R.attr.id;

/**
 * An implementation of an JidToHashCache which store the data of the filesystem.
 *
 * @author Eng Chong Meng
 */
public class JidToHashCacheFile implements JidToHashCache
{
	private static final Logger LOGGER = Logger.getLogger(JidToHashCacheFile.class.getName());
	private File mStoreDir;
	private StringEncoder filenameEncoder;

	/**
	 * Create a JidToHashCache.
	 * /**
	 * Creates a new directory for keeping JidToHashCache object.
	 * <p>
	 * Default filename encoder {@link Base32}, as this will work on all file systems, both case
	 * sensitive and case insensitive. It does however produce longer filenames.
	 *
	 * @param storeDir
	 * 		The directory used to store the data.
	 */
	public JidToHashCacheFile(final File storeDir)
	{
		if (storeDir.exists() && !storeDir.isDirectory())
			throw new IllegalArgumentException("The store directory must be a directory");
		mStoreDir = storeDir;
		mStoreDir.mkdirs();
		filenameEncoder = Base32.getStringEncoder();
	}

	@Override
	public void addHashByJid(BareJid bareJid, String hash)
	{
		File jidFile = getFileFor(bareJid);
		DataOutputStream dos = null;
		try {
			if (jidFile.exists() || jidFile.createNewFile()) {
				dos = new DataOutputStream(new FileOutputStream(jidFile));
				dos.writeUTF(hash);
			}
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Failed to write imageHash info to file: " + id, e);
		} finally {
			try {
				if (dos != null)
					dos.close();
			}
			catch (IOException e) {
				LOGGER.log(Level.WARNING, "Error while closing stream: " + id, e);
			}
		}
	}

	@Override
	public String getHashForJid(BareJid id)
	{
		String hash = null;
		File file = getFileFor(id);

		// No information available for the id, so just return
		if (!file.exists())
			return null;

		DataInputStream dis = null;
		try {
			dis = new DataInputStream(new FileInputStream(file));
			hash = dis.readUTF();
		}
		catch (IOException e) {
			LOGGER.log(Level.WARNING, "Could not restore photoHash from file: " + id, e);
		} finally {
			try {
				if (dis != null)
					dis.close();
			}
			catch (IOException e) {
				LOGGER.log(Level.WARNING, "Error while closing stream: " + id, e);
			}
		}
		return hash;
	}

	@Override
	public boolean contains(BareJid id)
	{
		File file = getFileFor(id);
		return file.exists();
	}

	/**
	 * Purge the obsoleted file from mStoreDir
	 */
	@Override
	public boolean purgeItemFor(BareJid id)
	{
		File file = getFileFor(id);
		return file.exists() && file.delete();
	}

	private File getFileFor(BareJid id)
	{
		String filename = filenameEncoder.encode(id.toString());
		return new File(mStoreDir, filename);
	}

	@Override
	public boolean emptyCache()
	{
		File[] files = mStoreDir.listFiles();
		boolean status = true;
		for (File file : files) {
			status = file.delete();
		}
		return status;
	}
}
