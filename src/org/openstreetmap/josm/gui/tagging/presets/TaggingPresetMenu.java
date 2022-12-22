// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.ActionEvent;
import java.util.Map;
import java.util.Objects;

import javax.swing.Action;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;

import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainFrame;
import org.openstreetmap.josm.gui.MenuScroller;
import org.openstreetmap.josm.tools.Logging;

/**
 * Menu that groups several presets from one topic.
 * <p>
 * Used to create the nested directory structure in the preset main menu entry.
 */
final class TaggingPresetMenu extends TaggingPresetBase {
    private TaggingPresetMenu(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
    }

    /**
     * Create a {@code TaggingPresetMenu} from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the {@code TaggingPresetMenu}
     * @throws IllegalArgumentException on attribute errors
     */
    static TaggingPresetMenu fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new TaggingPresetMenu(attributes);
    }

    @Override
    void fixup(Map<String, Chunk> chunks, Item parent) {
        super.fixup(chunks, parent);
        action = new TaggingPresetMenuAction();
        iconFuture = TaggingPresetUtils.loadIcon(getIconName(), action);
    }

    @Override
    void addToMenu(JMenu parentMenu) {
        JMenu subMenu = new JMenu(getAction());
        subMenu.setText(getLocaleName());
        parentMenu.add(subMenu);

        for (Item item : items) {
            item.addToMenu(subMenu);
        }
        if (subMenu.getItemCount() >= TaggingPresets.MIN_ELEMENTS_FOR_SCROLLER.get()) {
            MenuScroller.setScrollerFor(subMenu);
        }
    }

    @Override
    public String toString() {
        return "TaggingPresetMenu " + getName();
    }

    /**
     * {@code TaggingPresetMenu} are considered equivalent if (and only if) their {@link #getRawName()} match.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TaggingPresetMenu that = (TaggingPresetMenu) o;
        return Objects.equals(getRawName(), that.getRawName());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getRawName());
    }

    /**
     * An action that opens this menu as a popup in the toolbar.
     */
    private class TaggingPresetMenuAction extends TaggingPresetBase.TaggingPresetBaseAction {
        TaggingPresetMenuAction() {
            super();
            putValue(Action.NAME, getName());
            putValue("toolbar", "tagginggroup_" + getRawName());
            /** Tooltips should be shown for the toolbar buttons, but not in the menu. */
            putValue(OPTIONAL_TOOLTIP_TEXT, tr("Preset group ''{0}''", getLocaleFullName()));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (e.getSource() instanceof Component) {
                JMenu menu = new JMenu();
                for (Item item : items) {
                    item.addToMenu(menu);
                }
                JPopupMenu popupMenu = new JPopupMenu(getName());
                for (Component menuItem : menu.getMenuComponents()) {
                    popupMenu.add(menuItem);
                }
                try {
                    PointerInfo pointerInfo = MouseInfo.getPointerInfo();
                    if (pointerInfo != null) {
                        Point p = pointerInfo.getLocation();
                        MainFrame parent = MainApplication.getMainFrame();
                        if (parent.isShowing()) {
                            popupMenu.show(parent, p.x-parent.getX(), p.y-parent.getY());
                        }
                    }
                } catch (SecurityException ex) {
                    Logging.log(Logging.LEVEL_ERROR, "Unable to get mouse pointer info", ex);
                }
            }
        }
    }
}
