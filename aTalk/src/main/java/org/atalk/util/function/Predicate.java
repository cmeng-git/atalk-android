package org.atalk.util.function;

/**
 * Represents a predicate of one argument. This is a poor man's backport of the
 * <tt>Predicate</tt> interface found in Java 1.8. (required API-24)
 *
 * @author George Politis
 * // @deprecated Use {@link java.util.function.Predicate} but required API-24
 */
// @Deprecated
// cmeng - needed by aTalk with API-19 min
public interface Predicate<T> {

    /**
     * Evaluates this predicate on the given argument.
     *
     * @param t the input argument
     * @return {@code true} if the input argument matches the predicate, otherwise {@code false}
     */
    boolean test(T t);
}
