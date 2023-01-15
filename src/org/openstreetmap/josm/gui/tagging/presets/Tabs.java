// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.awt.GridBagConstraints;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;

import org.openstreetmap.josm.tools.GBC;

/**
 * A container for tabs.
 * @since xxx
 */
final class Tabs extends Composite {
    /** Tab placement: TOP, LEFT, RIGHT, BOTTOM. */
    final int placement;

    /**
     * Private constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private Tabs(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        int p = SwingConstants.TOP;
        String pl = attributes.getOrDefault("placement", "top");
        if ("left".equals(pl))
            p = SwingConstants.LEFT;
        if ("right".equals(pl))
            p = SwingConstants.RIGHT;
        if ("bottom".equals(pl))
            p = SwingConstants.BOTTOM;
        placement = p;
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on invalid attributes
     */
    static Tabs fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new Tabs(attributes);
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        JTabbedPane tabs = new JTabbedPane(placement);
        p.add(tabs, GBC.eol().insets(0, 10, 0, 0).fill(GridBagConstraints.HORIZONTAL));

        boolean hasElements = false;
        for (Item item : items) {
            hasElements |= item.addToPanel(tabs, parentInstance);
        }
        return hasElements;
    }

    @Override
    public String toString() {
        return "Tabs";
    }
}
