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

import java.io.*;
import java.util.logging.*;

import static org.jivesoftware.smackx.privacy.packet.PrivacyItem.Type.jid;

/**
 * An implementation of an AvatarCache which stores the data of the filesystem.
 * The stored item is retrieve using an id usually the image Hash value
 *
 * @author Eng Chong Meng
 */
public class AvatarCacheFile implements AvatarCache
{
	private static final Logger LOGGER = Logger.getLogger(AvatarCacheFile.class.getName());
	private File mStoreDir;

	/**
	 * Create a FileVCardAvatarCache.
	 *
	 * @param storeDir
	 * 		The directory used to store the data.
	 */
	public AvatarCacheFile(final File storeDir)
	{
		if (storeDir.exists() && !storeDir.isDirectory())
			throw new IllegalArgumentException("The store directory must be a directory");
		mStoreDir = storeDir;
		mStoreDir.mkdirs();
	}

	/**
	 * Save the image data in cache.
	 *
	 * @param id
	 * 		the key id of the data usually the data hash
	 * @param data
	 * 		the byte of the data to cache
	*/
	@Override
	public void addAvatarByHash(String id, byte[] data)
	{
		File file = new File(mStoreDir, id);
		OutputStream os = null;
		try {
			os = new BufferedOutputStream(new FileOutputStream(file));
			// os == null if storage permission in not granted
			if ((os != null) && (data != null))
    			os.write(data);
		}
		catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Failed to write photo avatar to file", e);
		}
		try {
		} finally {
			if (os != null) {
				try {
					os.close();
				}
				catch (IOException e) {
					LOGGER.log(Level.WARNING, "Error while closing stream: " + jid, e);
				}
			}
		}
	}

	@Override
	public void addAvatarByHash(String id, final InputStream in)
	{
		File file = new File(mStoreDir, id);

		OutputStream os = null;
		try {
			byte[] data = new byte[1024];
			int nBread;
			try {
				os = new BufferedOutputStream(new FileOutputStream(file));
				while ((nBread = in.read(data)) != -1) {
					os.write(data, 0, nBread);
				}
			}
			catch (IOException e) {
				LOGGER.log(Level.SEVERE, "Failed to write photo avatar to file", e);
			}
		} finally {
			try {
				in.close();
				if (os != null)
					os.close();
			}
			catch (IOException e) {
				LOGGER.log(Level.WARNING, "Error while closing stream: " + jid, e);
			}
		}
	}

	@Override
	public byte[] getAvatarForHash(String id)
	{
		byte[] avatarImage = null;
		File file = new File(mStoreDir, id);
		if (!file.exists())
			return null;

		InputStream is = null;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			byte[] data = new byte[(int) file.length()];
			try {
				is = new BufferedInputStream(new FileInputStream(file));
				is.read(data);
				bos.write(data);
			}
			catch (IOException e) {
				LOGGER.log(Level.WARNING, "Could not restore photo avatar from file", e);
			}
		} finally {
			try {
				if (is != null)
					is.close();
			}
			catch (IOException ioe) {
				System.out.println("Error while closing stream: " + ioe);
			}
		}
		avatarImage = bos.toByteArray();
		return avatarImage;
	}

	@Override
	public boolean contains(String photoHash)
	{
		File file = new File(mStoreDir, photoHash);
		return file.exists();
	}

	@Override
	public boolean purgeItemFor(String photoHash)
	{
		File file = new File(mStoreDir, photoHash);
		return file.exists() && file.delete();
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
