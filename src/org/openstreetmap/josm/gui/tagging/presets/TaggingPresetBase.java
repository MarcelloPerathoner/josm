// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ImageIcon;

import org.openstreetmap.josm.actions.AdaptableAction;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.tools.ImageResource;

/**
 * Base class for templates to build preset menus.
 * <p>
 * This class is an immutable template class mainly used to build menues, toolbars and preset lists.
 *
 * @since xxx
 */
abstract class TaggingPresetBase extends Composite {
    /** The action key for optional tooltips */
    public static final String OPTIONAL_TOOLTIP_TEXT = "Optional tooltip text";
    /** Prefix of preset icon loading failure error message */
    public static final String PRESET_ICON_ERROR_MSG_PREFIX = "Could not get presets icon ";

    /**
     * The name of the tagging preset.
     * @see #getRawName()
     */
    private final String name;
    /** Translation context for name */
    private final String nameContext;
    /**
     * A cache for the local name. Should never be accessed directly.
     * @see #getLocaleName()
     */
    private final String localeName;
    /** The icon name assigned to this preset. */
    private final String iconName;

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

        name = attributes.get("name");
        nameContext = attributes.get("name_context");
        localeName = TaggingPresetUtils.buildLocaleString(attributes.get("locale_name"), name, nameContext);
        iconName = attributes.get("icon");
    }

    @Override
    void fixup(Map<String, Chunk> chunks, Item parent) {
        if (parent instanceof TaggingPresetBase) {
            TaggingPresetBase p = (TaggingPresetBase) parent;
            groupName = p.fullName;
            localeGroupName = p.localeFullName;
            fullName = p.fullName + "/" + name;
            localeFullName = p.localeFullName + "/" + localeName;
        } else {
            groupName = "";
            localeGroupName = "";
            fullName = name;
            localeFullName = localeName;
        }
        super.fixup(chunks, this);
    }

    @Override
    void destroy() {
        super.destroy();
        action.removeListener();
        action = null;
        iconFuture = null;
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
        return localeFullName;
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
     * Returns the preset icon (16 or 24px).
     * @param size Key determining icon size: {@code Action.SMALL_ICON} for 16x, {@code Action.LARGE_ICON_KEY} for 24px
     * @return The preset icon, or {@code null} if none defined
     * @since 10849
     */
    public final ImageIcon getIcon(String size) {
        Object icon = getAction().getValue(size);
        if (icon instanceof ImageIcon) {
            return (ImageIcon) icon;
        }
        return null;
    }

    /**
     * Returns the preset icon (16px).
     * @return The preset icon, or {@code null} if none defined
     * @since 6403
     */
    public final ImageIcon getIcon() {
        return getIcon(Action.SMALL_ICON);
    }

    /**
     * Returns the icon name
     * @return the icon name
     */
    public String getIconName() {
        return iconName;
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
     * An action that opens the preset dialog.
     */
    abstract static class TaggingPresetBaseAction extends AbstractAction implements ActiveLayerChangeListener, AdaptableAction {
        TaggingPresetBaseAction() {
            // Logging.info("Adding LayerChangeListener {0}", this);
            MainApplication.getLayerManager().addActiveLayerChangeListener(this);
            updateEnabledState();
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
