// License: GPL. For details, see LICENSE file.

package org.openstreetmap.josm.gui.widgets;

import java.awt.Dimension;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JMenuItem;

import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageResource;

/**
 * A Menu item that shows a custom-sized icon.
 */
public class JosmMenuItem extends JMenuItem {
    private Dimension iconSize;

    /**
     * Constructs a multi-column menu.
     *
     * @param action the action
     * @param iconSize the iconsize to use or null for default menu icon size
     */
    public JosmMenuItem(Action action, Dimension iconSize) {
        super();
        this.iconSize = iconSize != null ? iconSize : ImageProvider.ImageSizes.SMALLICON.getImageDimension();
        setAction(action);
    }

    /**
     * Note: identical implementation in {@link MultiColumnMenu}.
     */
    private void setIconFromResource(Action action) {
        ImageResource imageResource = (ImageResource) action.getValue(ImageResource.IMAGE_RESOURCE_KEY);
        if (imageResource != null) {
            Icon icon = imageResource.getPaddedIcon(iconSize);
            setIcon(icon);
            setIconTextGap(icon.getIconWidth() / 4);
        }
    }

    @Override
    protected void configurePropertiesFromAction(Action action) {
        super.configurePropertiesFromAction(action);
        setIconFromResource(action);
    }

    @Override
    protected void actionPropertyChanged(Action action, String propertyName) {
        super.actionPropertyChanged(action, propertyName);
        if (ImageResource.IMAGE_RESOURCE_KEY.equals(propertyName)) {
            setIconFromResource(action);
        }
    }
}
