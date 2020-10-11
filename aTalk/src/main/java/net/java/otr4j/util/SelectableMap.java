/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.java.otr4j.util;

import java.util.*;

/**
 * Map wrapper that additionally stores which item is selected. The selection
 * may be null. If it is not null, then the key must exist in the provided base map.
 * @author Danny van Heumen
 *
 * @param <K> Key type
 * @param <V> Value type
 */
public class SelectableMap<K, V> implements Map<K, V>
{
    /**
     * Map instance that is at the basis of the selectable map.
     */
    private final Map<K, V> base;

    /**
     * Indicates that a selection is made.
     *
     * If true, then a selection is made, even if the selected key is null.
     */
    private volatile boolean selected;

    /**
     * The key of the selected entry in the map.
     */
    private volatile K selection;

    /**
     * Create a selectable map without initial selection.
     *
     * @param base the base map
     */
    public SelectableMap(Map<K, V> base)
    {
        if (base == null) {
            throw new NullPointerException("base");
        }
        this.base = base;
        this.selection = null;
        this.selected = false;
    }

    /**
     * Create a selectable map with initial selection.
     *
     * @param base the base map
     * @param selected the initially selected entry
     */
    public SelectableMap(Map<K, V> base, K selected)
    {
        this(base);
        select(selected);
    }

    @Override
    public int size()
    {
        return base.size();
    }

    @Override
    public boolean isEmpty()
    {
        return base.isEmpty();
    }

    @Override
    public boolean containsKey(Object key)
    {
        return base.containsKey(key);
    }

    @Override
    public boolean containsValue(Object value)
    {
        return base.containsValue(value);
    }

    @Override
    public V get(Object key)
    {
        return base.get(key);
    }

    @Override
    public V put(K key, V value)
    {
        return base.put(key, value);
    }

    @Override
    public V remove(Object key)
    {
        if (isSelected() && this.selection.equals(key)) {
            deselect();
        }
        return base.remove(key);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> m)
    {
        base.putAll(m);
    }

    @Override
    public void clear()
    {
        deselect();
        base.clear();
    }

    @Override
    public Set<K> keySet()
    {
        return Collections.unmodifiableSet(base.keySet());
    }

    @Override
    public Collection<V> values()
    {
        return Collections.unmodifiableCollection(base.values());
    }

    @Override
    public Set<Entry<K, V>> entrySet()
    {
        return Collections.unmodifiableSet(base.entrySet());
    }

    public final boolean isSelected()
    {
        return this.selected;
    }

    public final V getSelected()
    {
        if (!this.selected) {
            throw new IllegalStateException("no selection available");
        }
        return base.get(this.selection);
    }

    public final void select(K key)
    {
        // verify that key exists before changing selected
        if (!base.containsKey(key)) {
            throw new IllegalArgumentException("key is not in base map");
        }
        this.selection = key;
        this.selected = true;
    }

    public final void deselect()
    {
        this.selected = false;
    }
}
