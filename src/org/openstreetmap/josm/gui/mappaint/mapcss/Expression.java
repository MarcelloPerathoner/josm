// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import org.openstreetmap.josm.gui.mappaint.Environment;

/**
 * A MapCSS Expression.
 *
 * Can be evaluated in a certain {@link Environment}. Usually takes
 * parameters, that are also Expressions and have to be evaluated first.
 * @since  3848 (creation)
 * @since 10600 (functional interface)
 * @since   xxx (cacheability)
 */
public interface Expression {
    /**
     * Evaluate this expression in a given environment.
     * @param env The environment
     * @return the result of the evaluation, its type depends on the expression's content
     */
    Object evaluate(Environment env);

    /**
     * The behaviour of the function when repeatedly invoked.
     * <p>
     * Note: Keep this ordered from most cacheable to least cacheable.
     */
    enum Cacheability {
        /** always returns the same result when given the same argument values, eg. sin(x), max(a, b) */
        IMMUTABLE,
        /** returns the same result when given the same argument values
        as long as the underlying dataSet didn't change, eg. tag(), heading(theta) */
        STABLE,
        /** the returned value can change with every invocation, eg. zoom_level(), date() */
        VOLATILE
    }

    /**
     * Returns the expected behaviour of the function when repeatedly invoked
     * <p>
     * This function is meant to be overridden.
     * <p>
     * Note: The default is IMMUTABLE to be compatible with the previous implementation
     * that evaluated all expressions immediately.
     * @return the cacheability
     */
    default Cacheability getCacheability() {
        return Cacheability.IMMUTABLE;
    }
}
