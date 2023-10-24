// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import java.util.Arrays;

import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.Keyword;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.Token;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.gui.mappaint.StyleKeys;

/**
 * A MapCSS Instruction.
 *
 * For example a simple assignment like <code>width: 3;</code>, but may also
 * be a set instruction (<code>set highway;</code>).
 * A MapCSS {@link Declaration} is a list of instructions.
 */
@FunctionalInterface
public interface Instruction extends StyleKeys {

    /**
     * Execute the instruction in the given environment.
     * @param env the environment
     */
    void execute(Environment env);

    /**
     * An instruction that assigns a given value to a variable on evaluation
     */
    class AssignmentInstruction implements Instruction {
        public final String key;
        public final boolean isSetInstruction;
        private Object val;

        public AssignmentInstruction(String key, Object literal, boolean isSetInstruction,
                MapCSSStyleSource sheet, Token token) {
            this.key = key.intern();
            this.val = literal;
            this.isSetInstruction = isSetInstruction;

            // Logging.info("{0}: {1}", key, expression.toString());

            // Note: The mapcss parser always returns an Expression. Keywords and Strings
            // are returned inside LiteralExpressions.
            if (val instanceof LiteralExpression) {
                val = ((LiteralExpression) val).evaluate();
                if (Keyword.NONE.equals(val)) {
                    val = null;
                    return;
                }
            }

            if (TEXT.equals(key)) {
                /* Special case for declaration 'text: ...'
                 *
                 * - Treat the value 'auto' as keyword.
                 * - Treat any other literal value 'litval' as as reference to tag with key 'litval'
                 *
                 * - Accept function expressions as is. This allows for
                 *     tag(a_tag_name)                 value of a tag
                 *     eval("a static text")           a static text
                 *     parent_tag(a_tag_name)          value of a tag of a parent relation
                 */

                if (Keyword.AUTO.equals(val)) {
                    val = ExpressionFactory.createFunctionExpression("auto_text",
                        Arrays.asList(), sheet, token
                    );
                } else if (!(val instanceof Expression)) {
                    // tag(name)
                    val = ExpressionFactory.createFunctionExpression("tag",
                        Arrays.asList(LiteralExpression.create(val)),
                        sheet, token
                    );
                }
            }
            else if (ICON_ROTATION.equals(key) && Keyword.WAY.equals(val)) {
                // special case icon-rotation: way
                val = ExpressionFactory.createFunctionExpression("heading",
                    Arrays.asList(), sheet, token
                );
            } else if (ICON_IMAGE.equals(key) || FILL_IMAGE.equals(key) || REPEAT_IMAGE.equals(key)) {
                val = ExpressionFactory.createFunctionExpression("icon_reference",
                    Arrays.asList(
                        val instanceof Expression ? (Expression) val : LiteralExpression.create(val),
                        LiteralExpression.create(sheet)
                    ), sheet, token
                );
            }
            // Logging.info("{2} -> {0}: {1}", key, val.toString(), literal.toString());
        }

        @Override
        public void execute(Environment env) {
            try {
                if (val instanceof Expression) {
                    env.mc.getOrCreateCascade(env.layer).putOrClear(key, ((Expression) val).evaluate(env));
                } else {
                    env.mc.getOrCreateCascade(env.layer).putOrClear(key, val);
                }
            } catch (MapCSSException e) {
                Logging.error(e.getMessage());
            }
        }

        public Object getValue() {
            return val;
        }

        @Override
        public String toString() {
            return key + ": " + (val instanceof float[] ? Arrays.toString((float[]) val) :
                (val instanceof String ? ("String<"+val+'>') : val)) + ';';
        }
    }
}
