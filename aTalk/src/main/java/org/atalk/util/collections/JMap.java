/*
 * Copyright @ 2018 - present 8x8, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.atalk.util.collections;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides helpers for creating a Map easily in place.  Java 9 has
 * these features under 'Map' but I scoped them under 'JMap' ('J'
 * for 'Jitsi') to avoid conflicts with java.util.Map.
 *
 * Note that both styles--one taking a vararg of Entries and another
 * taking individual arguments--are in Java 9 and are provided here
 * for convenience.
 */
public class JMap
{
    /**
     * Helper function to create a Map from a list of entries
     *
     * NOTE: without @SafeVarargs, we get a complaint about heap
     * pollution, but I don't think it should be an issue here.
     *
     * @param entries the entries to add to the map
     * @param <K> the key type
     * @param <V> the value type
     * @return a map containing the given entries
     */
    @SafeVarargs
    public static <K, V> Map<K, V> ofEntries(Map.Entry<K, V>...entries)
    {
        Map<K, V> map = new HashMap<>();
        for (Map.Entry<K, V> entry : entries)
        {
            map.put(entry.getKey(), entry.getValue());
        }
        return map;
    }

    /**
     * Create a map with the given key and value
     * @param k1 the key
     * @param v1 the value
     * @param <K> the key type
     * @param <V> the value type
     * @return a map with the given keys and values
     */
    public static <K, V> Map<K, V> of(K k1, V v1)
    {
        Map<K, V> map = new HashMap<>();
        map.put(k1, v1);
        return map;
    }

    /**
     * Create a map with the given keys and values
     * @param k1 the first key
     * @param v1 the value for the first key
     * @param k2 the second  key
     * @param v2 the value for the second key
     * @param <K> the key type
     * @param <V> the value type
     * @return a map containing the given keys and values
     */
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2)
    {
        Map<K, V> map = of(k1, v1);
        map.put(k2, v2);
        return map;
    }

    /**
     * Create a map with the given keys and values
     * @param k1 the first key
     * @param v1 the value for the first key
     * @param k2 the second  key
     * @param v2 the value for the second key
     * @param k3 the third key
     * @param v3  the value for the third  key
     * @param <K> the key type
     * @param <V> the value type
     * @return a map containing the given keys and values
     */
    public static <K, V> Map<K, V> of(K k1, V v1, K k2, V v2, K k3, V v3)
    {
        Map<K, V> map = of(k1, v1, k2, v2);
        map.put(k3, v3);
        return map;
    }

    /**
     * A helper to easily create an instance of Map.Entry
     * @param key
     * @param value
     * @param <K>
     * @param <V>
     * @return
     */
    public static <K, V> Map.Entry<K, V> entry(K key, V value)
    {
        return new MapEntry<>(key, value);
    }
}
