// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;

import javax.swing.JPanel;

/**
 * A {@code <switch>} statement.
 * <p>
 * This statement includes the first child that matches.
 *
 * <pre>{@code
 * <switch>
 *   <case map_css="*[oneway]">
 *     <text key="destination" text="Destination" />
 *   </case>
 *   <otherwise>
 *     <text key="destination:forward" text="Forward Destination" />
 *     <text key="destination:backward" text="Backward Destination" />
 *   </otherwise>
 * </switch>
 * }</pre>
 */
final class Switch extends Composite {
    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    Switch(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on illegal attributes
     */
    static Switch fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Switch(attributes);
    }

    @Override
    boolean addToPanel(JPanel p, Composite.Instance parentInstance) {
        for (Item item : items) {
            if (item instanceof Conditional) {
                Conditional cond = (Conditional) item;
                if (cond.matches(parentInstance.getPresetInstance().getHandler())) {
                    return item.addToPanel(p, parentInstance);
                }
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return "Switch";
    }
}
