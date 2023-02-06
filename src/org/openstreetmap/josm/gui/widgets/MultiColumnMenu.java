// License: GPL. For details, see LICENSE file.

package org.openstreetmap.josm.gui.widgets;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;

import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageResource;

/**
 * A multi-column menu that fills all the screen height before starting a new column.
 */
public class MultiColumnMenu extends JMenu {
    private Dimension iconSize;

    /**
     * Constructs a multi-column menu.
     *
     * @param action the action
     * @param iconSize the iconsize to use or null for default menu icon size
     */
    public MultiColumnMenu(Action action, Dimension iconSize) {
        super();
        this.iconSize = iconSize != null ? iconSize : ImageProvider.ImageSizes.SMALLICON.getImageDimension();
        setAction(action);
    }

    /**
     * Note: identical implementation in {@link JosmMenuItem}.
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

    /**
     * Fixes a bug in Swing where the menu overlaps the taskbar.
     * <p>
     * (At least on debian with cinnamon and JDK 19) If the menu is tall enough to
     * overlap the taskbar, you cannot click on the overlapping menu item(s) because
     * for security reasons the OS sends mouse events in the taskbar area
     * exclusively to the taskbar itself and not to the JOSM app. This hack moves
     * the menu so it does not overlap the taskbar.
     * <p>
     * @see JMenu#getPopupMenuOrigin
     * @see JPopupMenu#adjustPopupLocationToFitScreen
     * @see JPopupMenu#canPopupOverlapTaskBar
     */
    @Override
    protected Point getPopupMenuOrigin() {
        Point origin = super.getPopupMenuOrigin();
        Rectangle screenBounds = GuiHelper.getScreenBounds(this);
        getPopupMenu().setLayout(new MultiColumnMenuLayout(screenBounds.height, 10, 0));
        Dimension popupSize = getPopupMenu().getPreferredSize();
        Rectangle invokerBounds = new Rectangle(getLocationOnScreen(), getSize());
        /*
        Logging.info("Origin: {0}", origin.toString());
        Logging.info("pmSize: {0}", pmSize.toString());
        Logging.info("screenBounds: {0}", screenBounds.toString());
        Logging.info("menuBounds: {0}", menuBounds.toString());
        */
        // origin is in the coordinates of the invoker menu item
        int y = invokerBounds.y + origin.y;
        if (y < screenBounds.y) {
            // overlays top taskbar
            origin.y = screenBounds.y - invokerBounds.y;
        } else if (y + popupSize.height > screenBounds.y + screenBounds.height) {
            // overlays bottom taskbar
            origin.y = screenBounds.y + screenBounds.height - popupSize.height - invokerBounds.y;
        }
        return origin;
    }

    static class MultiColumnMenuLayout implements LayoutManager {
        final int maxHeight;
        final int hgap;
        final int vgap;

        public MultiColumnMenuLayout(int maxHeight, int hgap, int vgap) {
            this.maxHeight = maxHeight;
            this.hgap = hgap;
            this.vgap = vgap;
        }

        /**
         * Make all menu items in column the given width
         * @param column a list of menu items
         * @param width the given width
         */
        private void fixWidth(List<Component> column, int width) {
            column.forEach(c -> {
                Rectangle r = c.getBounds();
                r.width = width;
                c.setBounds(r);
            });
        }

        /**
         * Actually do the layout
         * @return the preferred size
         */
        private Dimension layout(Container parent) {
            synchronized (parent.getTreeLock()) {
                Dimension layoutSize = new Dimension(0, 0);
                Insets insets = parent.getInsets();
                int x = insets.left;
                int y = insets.top;
                List<Component> column = new ArrayList<>();
                for (Component component : parent.getComponents()) {
                    Dimension preferredSize = component.getPreferredSize();

                    if (y + preferredSize.height > maxHeight - insets.bottom) {
                        fixWidth(column, layoutSize.width - x);
                        column.clear();
                        // next column
                        x = layoutSize.width + hgap;
                        y = insets.top;
                    }
                    component.setBounds(x, y, preferredSize.width, preferredSize.height);
                    column.add(component);
                    y += preferredSize.height;
                    if (layoutSize.height < y)
                        layoutSize.height = y;
                    y += vgap;
                    if (layoutSize.width < x + preferredSize.width)
                        layoutSize.width = x + preferredSize.width;
                }
                fixWidth(column, layoutSize.width - x);
                layoutSize.width += insets.right;
                layoutSize.height += insets.bottom;
                return layoutSize;
            }
        }

        @Override
        public Dimension minimumLayoutSize(Container parent) {
            return layout(parent);
        }

        @Override
        public Dimension preferredLayoutSize(Container parent) {
            return layout(parent);
        }

        @Override
        public void layoutContainer(Container parent) {
            layout(parent);
        }

        @Override
        public void addLayoutComponent(String name, Component comp) {
            // do nothing
        }

        @Override
        public void removeLayoutComponent(Component comp) {
            // do nothing
        }
    }
}
