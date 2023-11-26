// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import java.util.Arrays;

import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.mapcss.ExpressionFactory.MapCSSExpression;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.Token;

/**
 * Simple literal value, that does not depend on other expressions.
 * @since 5705
 */
public final class LiteralExpression extends MapCSSExpression {
    private final Object literal;

    /**
     * Constructa a new {@code LiteralExpression}.
     *
     * @param literal literal
     * @return the literal expression
     */
    public static LiteralExpression create(Object literal) {
        return new LiteralExpression(literal);
    }

    /**
     * Constructa a new {@code LiteralExpression}.
     *
     * @param literal literal
     * @param sheet the source stylesheet or null
     * @param token the parser token or null
     * @return the literal expression
     */
    public static LiteralExpression create(Object literal, MapCSSStyleSource sheet, Token token) {
        LiteralExpression exp = new LiteralExpression(literal);
        exp.setBeginPos(sheet, token);
        return exp;
    }

    /**
     * Constructs a new {@code LiteralExpression}.
     * @param literal literal
     */
    private LiteralExpression(Object literal) {
        this.literal = literal;
    }

    @Override
    public Object evaluate() {
        return literal;
    }

    @Override
    public Object evaluate(Environment env) {
        return literal;
    }

    @Override
    public <T> T evaluate(Class<T> klass, T def) {
        T result = Cascade.convertTo(literal, klass);
        return result != null ? result : def;
    }

    Object evalImpl(Environment environment) {
        return literal;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((literal == null) ? 0 : literal.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        LiteralExpression other = (LiteralExpression) obj;
        if (literal == null) {
            if (other.literal != null)
                return false;
        } else if (!literal.equals(other.literal))
            return false;
        return true;
    }

    @Override
    public String toString() {
        if (literal instanceof float[]) {
            return Arrays.toString((float[]) literal);
        }
        if (literal != null) {
            return literal.toString();
        }
        return "<null>";
    }
}
