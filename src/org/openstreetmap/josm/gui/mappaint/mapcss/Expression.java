// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import org.openstreetmap.josm.gui.mappaint.Environment;

/**
 * A MapCSS Expression.
 *
 * Can be evaluated in a certain {@link Environment}. Usually takes
 * parameters, that are also Expressions and have to be evaluated first.
 *
 * Notes:
 *
 * The parser works on Expressions.
 * Arguments to an Expression are Expressions.
 * The Cascade must store something that can be evaluated without providing an environment.
 *
 * @since  3848 (creation)
 * @since 10600 (functional interface)
 * @since   xxx (cacheability)
 */
public interface Expression {
    /**
     * Evaluates the expression.
     * @return the result of the evaluation, it's type depends on the expression
     */
    Object evaluate(Environment environment);
    Object evaluate();
    <T> T evaluate(Class<T> klass, T def);

    /**
     * The behaviour of the function when repeatedly invoked.
     * <p>
     * Note: Keep this ordered from most cacheable to least cacheable.
     */
    enum Cacheability {
        /**
         * Always returns the same result when given the same argument values, eg.
         * sin(x), max(a, b)
         */
        IMMUTABLE,
        /**
         * Returns the same result when given the same argument values as long as the
         * underlying dataSet didn't change, eg. tag(name), heading(theta)
         */
        STABLE,
        /**
         * The returned value may change with every invocation, eg. metric(), date()
         */
        VOLATILE
    }

    /**
     * Returns the expected behaviour of the function when repeatedly invoked
     * <p>
     * This function is meant to be overridden.
     * <p>
     * Note: The default is IMMUTABLE to be compatible with the previous implementation
     * that evaluated all expressions immediately and stored the results in the
     * properties.
     *
     * @return the cacheability
     */
    default Cacheability getCacheability() {
        return Cacheability.IMMUTABLE;
    }

    default void setBeginPos(String sourceUrl, int beginLine, int beginColumn) {
    }

    default String getSourceLine() {
        return null;
    }

    default String getSourceUrl() {
        return null;
    }

    default int getBeginLine() {
        return -1;
    }

    default int getBeginColumn() {
        return -1;
    }
}
