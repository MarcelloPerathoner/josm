// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.awt.Component;
import java.awt.Dimension;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.openstreetmap.josm.actions.AdaptableAction;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.tools.AlphanumComparator;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageResource;

/**
 * Base class for templates to build preset menus.
 * <p>
 * This class is an immutable template class mainly used to build menues, toolbars and
 * preset lists.
 *
 * @since xxx
 */
abstract class TaggingPresetBase extends Composite {
    /** Prefix of preset icon loading failure error message */
    public static final String PRESET_ICON_ERROR_MSG_PREFIX = "Could not get presets icon ";

    /**
     * The name of the tagging preset.
     * @see #getRawName()
     */
    private final String name;
    /** Translation context for this preset */
    private final String nameContext;
    /**
     * A cache for the local name. Should never be accessed directly.
     * @see #getLocaleName()
     */
    private final String localeName;
    /** The localized menu tooltip or null */
    private final String tooltip;
    /** The icon name assigned to this preset. */
    private final String iconName;
    /** A custom size for the displayed icon or null to use the default size. */
    private Dimension iconSize;
    /** Base directory for all icons in this group */
    private URI iconBase;

    final boolean bSortMenu;
    final boolean bNoMenu;

    /** The english full name of this preset, eg. {@code Highways/Streets/Motorway} */
    private String fullName;
    /** The localized full name of this preset, eg. {@code Straßen/Straßen/Autobahn} */
    private String localeFullName;
    /** The english group name of this preset, eg. {@code Highways/Streets} */
    private String groupName;
    /** The localized group name of this preset, eg. {@code Straßen/Straßen} */
    private String localeGroupName;
    /** An action that either opens a preset dialog or a preset menu. */
    TaggingPresetBaseAction action;
    /** The completable future task of asynchronous icon loading. Used for testing. */
    CompletableFuture<ImageResource> iconFuture;

    /**
     * Create an empty tagging preset. This will not have any items and
     * will be an empty string as text. createPanel will return null.
     * Use this as default item for "do not select anything".
     *
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on invalid attributes
     */
    TaggingPresetBase(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);

        nameContext = attributes.get("name_context");
        name = attributes.getOrDefault("name", "");
        localeName = TaggingPresetUtils.buildLocaleString(attributes.get("locale_name"), name, nameContext);
        fullName = name;
        localeFullName = localeName;
        tooltip = TaggingPresetUtils.buildLocaleString(
            attributes.get("locale_tooltip"), attributes.get("tooltip"), nameContext);
        iconName = attributes.get("icon");
        int s = Integer.parseInt(attributes.getOrDefault("icon_size", "-1"));
        if (s != -1) {
            iconSize = ImageProvider.adj(new Dimension(s, s));
        }
        try {
            if (attributes.get("icon_base") != null) {
                iconBase = new URI(attributes.get("base_uri")).resolve(attributes.get("icon_base"));
            }
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException(e);
        }
        bSortMenu = TaggingPresetUtils.parseBoolean(
            attributes.getOrDefault("sort_menu", TaggingPresets.SORT_VALUES.get() ? "on" : "off"));
        bNoMenu = TaggingPresetUtils.parseBoolean(attributes.getOrDefault("no_menu", "off"));
    }

    @Override
    void fixup(Map<String, Chunk> chunks, Item parent) {
        // Implements some inheritances from outer elements
        // FIXME: this is messy. Find a better way.
        if (parent == null) {
            // FIXME: we really should include a root name
            // like {@code defaultpresets.xml/Highways/Streets/Motorway}
            groupName = "";
            localeGroupName = "";
            fullName = "";
            localeFullName = "";
        } else if (parent instanceof Root p) {
            groupName = "";
            localeGroupName = "";
            fullName = name;
            localeFullName = localeName;
            if (iconBase == null && p.getIconBase() != null)
                iconBase = p.getIconBase();
        } else if (parent instanceof TaggingPresetBase p) {
            groupName = p.fullName;
            localeGroupName = p.localeFullName;
            fullName = p.fullName + "/" + name;
            localeFullName = p.localeFullName + "/" + localeName;
            // if there is no icon_base attribute then, for compatibility, icon_name is
            // relative to the classpath, if there is an icon_base attribute, it will be
            // resolved against the base_uri and the icon path will be {@code
            // icon_base/icon_name}
            if (iconBase == null && p.getIconBase() != null)
                iconBase = p.getIconBase();
            if (iconSize == null)
                iconSize = p.iconSize; // do not use getIconSize() !
        }
        super.fixup(chunks, parent);
    }

    @Override
    void destroy() {
        super.destroy();
        if (action != null)
            action.removeListener();
        action = null;
        iconFuture = null;
    }

    /**
     * Returns the menu or menu item if found. Compares by menu action.
     */
    JMenuItem findMenu(JMenu parentMenu) {
        for (Component c : parentMenu.getMenuComponents()) {
            if (c instanceof JMenuItem) {
                JMenuItem menu = (JMenuItem) c;
                if (getAction().equals(menu.getAction())) {
                    return menu;
                }
            }
        }
        return null;
    }

    /**
     * Returns the untranslated name. eg. {@code Motorway}
     *
     * @return the name
     */
    public String getBaseName() {
        return name;
    }

    /**
     * Returns the localized version of the name. eg. {@code Autobahn}
     *
     * @return The name that should be displayed to the user.
     */
    public String getLocaleName() {
        return localeName;
    }

    /**
     * Returns the localized full name of this preset, eg. {@code Straßen/Straßen/Autobahn}
     * @return the localized full name
     */
    public String getName() {
        return getLocaleFullName();
    }

    /**
     * Returns the localized full name of this preset, eg. {@code Straßen/Straßen/Autobahn}
     * @return the localized full name
     */
    public String getLocaleFullName() {
        return localeFullName;
    }

    /**
     * Returns the full name of this preset, in English, eg. {@code Highways/Streets/Motorway}
     * @return the full name
     */
    public String getRawName() {
        return fullName;
    }

    /**
     * Returns the group name of this preset, in English, eg. {@code Highways/Streets}
     * @return the group name
     */
    public String getGroupName() {
        return groupName;
    }

    /**
     * Returns the localized group name of this preset, eg. {@code Straßen/Straßen}
     * @return the localized group name
     */
    public String getLocaleGroupName() {
        return localeGroupName;
    }

    /**
     * Returns the tooltip
     * @return the tooltip
     */
    public String getTooltip() {
        return tooltip;
    }

    /**
     * Returns the preset icon in menu or toolbar size.
     * @param size {@code Action.SMALL_ICON} for menu size, {@code Action.LARGE_ICON_KEY} for toolbar size
     * @return The preset icon, or {@code null} if none defined
     * @since 10849
     */
    private ImageIcon getIcon(String size) {
        Object icon = getAction().getValue(size);
        if (icon instanceof ImageIcon) {
            return (ImageIcon) icon;
        }
        return null;
    }

    /**
     * Returns the preset icon in menu size.
     * @return The preset icon, or {@code null} if none defined
     * @since xxx
     */
    public final ImageIcon getSmallIcon() {
        return getIcon(Action.SMALL_ICON);
    }

    /**
     * Returns the preset icon in toolbar size.
     * @return The preset icon, or {@code null} if none defined
     * @since xxx
     */
    public final ImageIcon getLargeIcon() {
        return getIcon(Action.LARGE_ICON_KEY);
    }

    /**
     * Returns the preset icon as an image resource
     * <p>
     * To get the icon in any size, use:
     * <pre>
     * if (getIconResource() != null)
     *     icon = getIconResource().getPaddedIcon(dimension);
     * </pre>
     * @return The resource, or {@code null}
     * @since xxx
     */
    public final ImageResource getIconResource() {
        return ImageResource.getAttachedImageResource(getAction());
    }

    /**
     * Returns the icon name
     * @return the icon name
     */
    public String getIconName() {
        if (iconName == null)
            return null;
        return getIconBase() != null ? getIconBase().resolve(iconName).toString() : iconName;
    }

    /**
     * Returns the (custom) icon size to use in menus
     * @return the icon size
     */
    public Dimension getIconSize() {
        return iconSize != null ? iconSize : ImageProvider.ImageSizes.SMALLICON.getImageDimension();
    }

    /**
     * Returns the icon base
     * @return the icon base
     */
    URI getIconBase() {
        return iconBase;
    }

    /**
     * Returns the Action associated with this preset.
     * @return the Action
     */
    public AbstractAction getAction() {
        return action;
    }

    /**
     * Gets a string describing this preset that can be used for the toolbar
     * @return A String that can be passed on to the toolbar
     * @see ToolbarPreferences#addCustomButton(String, int, boolean)
     */
    public String getToolbarString() {
        ToolbarPreferences.ActionParser actionParser = new ToolbarPreferences.ActionParser(null);
        return actionParser.saveAction(new ToolbarPreferences.ActionDefinition(getAction()));
    }

    /**
     * Sorts the menu items using the local name.
     * <p>
     * This method sorts only runs of {@code TaggingPresetBase}, which may be
     * interrupted by separators (or other stuff like chunks).
     *
     * @param items the list of menu items to sort in place
     */
    @SuppressWarnings("unchecked")
    static void sortMenu(List<? extends Item> items) {
        MenuItemComparator comp = new MenuItemComparator();
        int startIndex = 0;
        int endIndex = 0;
        for (Item item : items) {
            if (!(item instanceof TaggingPresetBase)) {
                Collections.sort((List<TaggingPresetBase>) items.subList(startIndex, endIndex), comp);
                startIndex = endIndex + 1; // re-start sorting after this item
            }
            endIndex++;
        }
        Collections.sort((List<TaggingPresetBase>) items.subList(startIndex, endIndex), comp);
    }

    static class MenuItemComparator implements Comparator<TaggingPresetBase> {
        @Override
        public int compare(TaggingPresetBase b1, TaggingPresetBase b2) {
            return AlphanumComparator.getInstance().compare(b1.getLocaleName(), b2.getLocaleName());
        }
    }

    /**
     * An action that opens the preset dialog.
     */
    abstract class TaggingPresetBaseAction extends AbstractAction implements ActiveLayerChangeListener, AdaptableAction {
        TaggingPresetBaseAction() {
            // Logging.info("Adding LayerChangeListener {0}", this);
            MainApplication.getLayerManager().addActiveLayerChangeListener(this);
            updateEnabledState();
            putValue(SHORT_DESCRIPTION, getTooltip());
        }

        @Override
        public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
            updateEnabledState();
        }

        final void updateEnabledState() {
            setEnabled(OsmDataManager.getInstance().getEditDataSet() != null);
        }

        final void removeListener() {
            // Logging.info("Removing LayerChangeListener {0}", this);
            MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
        }
    }
}
