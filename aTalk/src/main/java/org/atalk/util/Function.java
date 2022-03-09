/**
 *
 * Copyright 2019-2020 Eng Chong Meng
 *
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
package org.atalk.util;

// TODO: Replace with java.util.function.Supplier once aTalk's minimum Android SDK level is 24 or higher.
@FunctionalInterface
public interface Function<T, R> {
    R apply(T var1);

    default <V> java.util.function.Function<V, R> compose(java.util.function.Function<? super V, ? extends T> before) {
        throw new RuntimeException("Stub!");
    }

    default <V> java.util.function.Function<T, V> andThen(java.util.function.Function<? super R, ? extends V> after) {
        throw new RuntimeException("Stub!");
    }

    static <T> java.util.function.Function<T, T> identity() {
        throw new RuntimeException("Stub!");
    }
}