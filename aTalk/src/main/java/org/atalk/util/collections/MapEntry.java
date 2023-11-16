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

import java.util.Map;

/**
 * A constructable implementation of Map.Entry (none is provided by the
 * stdlib by default)
 * @param <K> the key type
 * @param <V> the value type
 */
public class MapEntry<K, V> implements Map.Entry<K, V>
{
    protected final K key;
    protected V value;

    public MapEntry(K key, V value)
    {
        this.key = key;
        this.value = value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public K getKey()
    {
        return key;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V getValue()
    {
        return value;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public V setValue(V value)
    {
        V old = this.value;
        this.value = value;
        return old;
    }
}
