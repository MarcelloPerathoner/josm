// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

import javax.swing.JComponent;

import org.openstreetmap.josm.tools.Logging;

/**
 * An if-statement.
 *
 * <pre>
 * {@code
 * <if map_css="!area:closed">
 *   <text key="width" text="Width" />
 * </if>
 * }
 * </pre>
 */
final class If extends Case {

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    If(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on illegal attributes
     */
    static If fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new If(attributes);
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        if (matches(parentInstance.getPresetInstance().getHandler())) {
            Logging.info("Matched map_css: {0}", mapCss);
            return super.addToPanel(p, parentInstance);
        }
        return false;
    }

    @Override
    public String toString() {
        return "If";
    }
}
