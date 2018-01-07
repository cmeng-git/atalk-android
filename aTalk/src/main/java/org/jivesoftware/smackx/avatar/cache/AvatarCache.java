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

/**
 * Interface for an VCardAvatarCache.
 *
 * @author Eng Chong Meng
 *
 */
public interface AvatarCache
{
	/**
	 * Put some data in cache.
	 *
	 * @param id
	 * 		the key id of the data
	 * @param data
	 * 		the data to cache
	 */
	void addAvatarByHash(String id, byte[] data);

	/**
	 * Put some data in cache.
	 *
	 * @param id
	 * 		the key id of the data
	 * @param data
	 * 		an InputStream to the data to cache
	 */
	void addAvatarByHash(String id, InputStream data);

	/**
	 * Get some data from the cache.
	 *
	 * @param id
	 * 		the id of the data to fetch
	 * @return the cached data
	 */
	byte[] getAvatarForHash(String id);

	/**
	 * Test if a data is in cache.
	 *
	 * @param id
	 * 		the id of the data
	 * @return true if data is in cache false otherwise
	 */
	boolean contains(String id);

	/**
	 * Purge obsoleted file in store
	 *
	 * @param file
	 * 		the photoHash, name of the file to be purge
	 * @return true if file purged is successful, false otherwise
	 */
	boolean purgeItemFor(String file);

	/**
	 * Purge all the files in store
	 *
	 */
	boolean emptyCache();
}
