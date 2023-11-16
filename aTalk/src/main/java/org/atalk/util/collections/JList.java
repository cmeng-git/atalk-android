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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Provides helpers for creating a List easily in place.
 * Java 9 has these features under 'List', but I scoped them
 * under 'JList' ('J' for 'Jitsi') to avoid conflicts with
 * java.util.List
 *
 * Note that these helpers are in Java 9 and this should be removed
 * once we migrate to Java 9.
 */
public class JList
{
    /**
     * Some notes from https://docs.oracle.com/javase/9/docs/api/java/util/List.html:
     * The List.of() static factory methods provide a convenient way to create immutable lists.
     * The List instances created by these methods have the following characteristics:
     *
     * They are structurally immutable. Elements cannot be added, removed, or replaced.
     *  Calling any mutator method will always cause UnsupportedOperationException to be thrown.
     *  However, if the contained elements are themselves mutable, this may cause the List's
     *  contents to appear to change.
     * They disallow null elements. Attempts to create them with null elements result in NullPointerException.
     * They are serializable if all elements are serializable.
     * The order of elements in the list is the same as the order of the provided arguments,
     *  or of the elements in the provided array.
     * They are value-based. Callers should make no assumptions about the identity of the returned
     *  instances. Factories are free to create new instances or reuse existing ones.
     *  Therefore, identity-sensitive operations on these instances (reference equality (==),
     *  identity hash code, and synchronization) are unreliable and should be avoided.
     * They are serialized as specified on the Serialized Form page.
     *
     * Note(brian): the implementation here differs from the one in the Java 9 lib, but I've
     * tried to enforce the characteristics above.
     *
     * @param elements the elements to add to the list
     * @param <E> the element type
     * @return an immutable list containing the given elements
     */
    @SafeVarargs
    public static <E> List<E> of(E... elements)
    {
        Objects.requireNonNull(elements);
        ArrayList<E> list = new ArrayList<>();

        for (E element : elements)
        {
            Objects.requireNonNull(element);
            list.add(element);
        }

        return Collections.unmodifiableList(list);
    }
}
