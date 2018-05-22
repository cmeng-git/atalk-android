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

package org.atalk.android.util;

import java.util.*;

/**
 * Implementation of various Android API not available in lower Android API.
 * Lowest support API by aTalk is API-15 i.e. Objects not available, add in API level 19
 */

public class ApiLib
{
	/**
	 * Checks that the specified object reference is not {@code null} and
	 * throws a customized {@link NullPointerException} if it is. This method
	 * is designed primarily for doing parameter validation in methods and
	 * constructors with multiple parameters, as demonstrated below:
	 * <blockquote><pre>
	 * public Foo(Bar bar, Baz baz) {
	 *     this.bar = Objects.requireNonNull(bar, "bar must not be null");
	 *     this.baz = Objects.requireNonNull(baz, "baz must not be null");
	 * }
	 * </pre></blockquote>
	 *
	 * @param obj     the object reference to check for nullity
	 * @param message detail message to be used in the event that a {@code
	 *                NullPointerException} is thrown
	 * @param <T> the type of the reference
	 * @return {@code obj} if not {@code null}
	 * @throws NullPointerException if {@code obj} is {@code null}
	 *
	 * {@link java.util.Objects#requireNonNull(Object)}
	 */
	public static <T> T requireNonNull(T obj, String message) {
		if (obj == null)
			throw new NullPointerException(message);
		return obj;
	}

	public static <T> T requireNonNull(T obj) {
		return requireNonNull(obj, null);
	}


	/**
	 * Returns the hash code of a non-{@code null} argument and 0 for
	 * a {@code null} argument.
	 *
	 * @param o an object
	 * @return the hash code of a non-{@code null} argument and 0 for
	 * a {@code null} argument
	 * @see Object#hashCode
	 */
	public static int hashCode(Object o) {
		return o != null ? o.hashCode() : 0;
	}

	/**
	 * Generates a hash code for a sequence of input values. The hash
	 * code is generated as if all the input values were placed into an
	 * array, and that array were hashed by calling {@link
	 * Arrays#hashCode(Object[])}.
	 *
	 * <p>This method is useful for implementing {@link
	 * Object#hashCode()} on objects containing multiple fields. For
	 * example, if an object that has three fields, {@code x}, {@code
	 * y}, and {@code z}, one could write:
	 *
	 * <blockquote><pre>
	 * &#064;Override public int hashCode() {
	 *     return Objects.hash(x, y, z);
	 * }
	 * </pre></blockquote>
	 *
	 * <b>Warning: When a single object reference is supplied, the returned
	 * value does not equal the hash code of that object reference.</b> This
	 * value can be computed by calling {@link #hashCode(Object)}.
	 *
	 * @param values the values to be hashed
	 * @return a hash value of the sequence of input values
	 * @see Arrays#hashCode(Object[])
	 * @see List#hashCode
	 */
	public static int hash(Object... values) {
		return Arrays.hashCode(values);
	}
}
