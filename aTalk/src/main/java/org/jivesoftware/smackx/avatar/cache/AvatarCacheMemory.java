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

import org.jxmpp.util.cache.LruCache;

import java.io.*;

/**
 * An avatar cache which store the avatars in memory.
 *
 * @author Eng Chong Meng
 *
 */
public class AvatarCacheMemory implements AvatarCache
{
	private static final int BUFFER_SIZE = 1024;
	private LruCache<String, byte[]> mCache;

	/**
	 * Create a MemoryVCardAvatarCache.
	 *
	 * @param maxSize
	 * 		the maximum number of objects the cache will hold. -1 means the cache has no max size.
	 * @param maxLifeTime
	 * 		the maximum amount of time (in ms) objects can exist in cache before being deleted.
	 * 		-1 means objects never expire.
	 */
	public AvatarCacheMemory(final int maxSize, final long maxLifeTime)
	{
		// mCache = new LruCache<String, byte[]>(maxSize, maxLifeTime);
		mCache = new LruCache<String, byte[]>(maxSize);
	}

	@Override
	public void addAvatarByHash(String photoHash, byte[] data)
	{
		mCache.put(photoHash, data);
	}

	@Override
	public void addAvatarByHash(String photoHash, InputStream in)
	{
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		try {
			byte[] data = new byte[BUFFER_SIZE];
			int nbread;
			try {
				while ((nbread = in.read(data)) != -1) {
					os.write(data, 0, nbread);
				}
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		} finally {
			try {
				in.close();
				os.close();
			}
			catch (IOException e) {
				e.printStackTrace();
			}
		}
		mCache.put(photoHash, os.toByteArray());
	}

	@Override
	public byte[] getAvatarForHash(String photoHash)
	{
		return mCache.get(photoHash);
	}

	@Override
	public boolean contains(String photoHash)
	{
		return mCache.containsKey(photoHash);
	}

	@Override
	public boolean purgeItemFor(String photoHash)
	{
		return (mCache.remove(photoHash) != null);
	}

	@Override
	public boolean emptyCache() {
		mCache.clear();
		return true;
	}
}
