// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.styleelement;

import java.awt.Color;
import java.awt.Rectangle;

import org.openstreetmap.josm.data.osm.visitor.paint.PaintColors;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.MultiCascade;
import org.openstreetmap.josm.gui.mappaint.StyleElementList;
import org.openstreetmap.josm.gui.mappaint.StyleKeys;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.spi.preferences.PreferenceChangedListener;

/**
 * Default element styles.
 * @since 14193
 */
public final class DefaultStyles implements StyleKeys {

    /**
	 * Class that keeps a cache of the configured node size and text color
	 */
	public static class PreferenceChangeListener implements PreferenceChangedListener {
	    static int size = 0;
	    static int size2 = 0;
	    static Rectangle rect;
	    static Color textColor;

	    PreferenceChangeListener() {
	        calcSize();
	        Config.getPref().addPreferenceChangeListener(this);
	    }

	    public static Rectangle getRect() {
	        return rect;
	    }

	    public static int getSize() {
	        return size;
	    }

	    public static int getSize2() {
	        return size2;
	    }

	    public static Color getTextColor() {
	        return textColor;
	    }

	    private static void calcSize() {
	        size = NodeElement.max(
	            Config.getPref().getInt("mappaint.node.selected-size", 5),
	            Config.getPref().getInt("mappaint.node.unselected-size", 3),
	            Config.getPref().getInt("mappaint.node.connection-size", 5),
	            Config.getPref().getInt("mappaint.node.tagged-size", 3)
	        );
            size2 = size / 2;
	        rect = new Rectangle(-size2, -size2, size, size);
	        textColor = PaintColors.TEXT.get();
	    }

	    @Override
	    public void preferenceChanged(PreferenceChangeEvent e) {
	        calcSize();
	    }
	}

    private static final PreferenceChangeListener prefListener = new PreferenceChangeListener();

	private DefaultStyles() {
        // Hide public constructor
    }

    /**
     * The style used for simple nodes
     */
    public static final NodeElement SIMPLE_NODE_ELEMSTYLE;

    static {
        MultiCascade mc = new MultiCascade();
        mc.getOrCreateCascade(MultiCascade.DEFAULT);
        SIMPLE_NODE_ELEMSTYLE = NodeElement.create(new Environment(null, mc, MultiCascade.DEFAULT, null), 4.1f, true);
        assert SIMPLE_NODE_ELEMSTYLE != null;
    }

    /**
     * The default styles that are used for nodes.
     * @see DefaultStyles#SIMPLE_NODE_ELEMSTYLE
     */
    public static final StyleElementList DEFAULT_NODE_STYLELIST = new StyleElementList(DefaultStyles.SIMPLE_NODE_ELEMSTYLE);
}
