// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.JMenu;

import org.openstreetmap.josm.actions.PreferencesAction;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.preferences.ListProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.gui.preferences.map.TaggingPresetPreference;
import org.openstreetmap.josm.tools.MultiMap;

/**
 * The Tagging Presets System.
 * <p>
 * The preset system has many uses:
 * <ul>
 * <li>To let users edit osm primitives in an easy intuitive way,
 * <li>to generate custom names for primitives, and
 * <li>to give information about standard tags for a given primitive.
 * </ul>
 * <p>
 * TaggingPresets used to be a global static class, but that caused troubles with unit
 * testing, especially:
 * <p>
 * While unit testing {@code JOSMTestRules} clears all layers from the Layer Manager
 * before and after <i>every</i> test it runs.  Tests execute in parallel so the
 * layerChangeListeners that TaggingPresets has registered in one test run will just go
 * "poof" at random moments, and when TaggingPresets later attempts to unregister them
 * it gets the infamous: "Attempted to remove listener that was not in list" message
 * followed by a stack trace. Many tests load defaultpresets.xml, with ~873 items in it,
 * so we get 873 stack traces in the output, which takes JUint forever to process.
 * <p>
 * Plugin authors should use {@code MainApplication.getTaggingPresets()} instead of
 * {@code TaggingPresets}.
 *
 * @since xxx (non-static)
 */
public final class TaggingPresets {
    /**
     * Defines whether the validator should be active in the preset dialog
     * @see TaggingPresetValidator
     */
    public static final BooleanProperty USE_VALIDATOR = new BooleanProperty("taggingpreset.validator", false);
    /** Sort preset values alphabetically in combos and menus */
    public static final BooleanProperty SORT_VALUES = new BooleanProperty("taggingpreset.sortvalues", true);
    /** No. of items a menu must have before using a scroller  */
    public static final IntegerProperty MIN_ELEMENTS_FOR_SCROLLER = new IntegerProperty("taggingpreset.min-elements-for-scroller", 15);
    /** Custom icon sources */
    public static final ListProperty ICON_SOURCES = new ListProperty("taggingpreset.icon.sources", null);

    /** The root elements of all XML files. */
    private final Collection<Root> rootElements = new LinkedList<>();
    /** The registered listeners */
    private final Collection<TaggingPresetListener> listeners = new LinkedList<>();
    /** caches all presets fullname->preset */
    private final Map<String, TaggingPreset> PRESET_CACHE = new LinkedHashMap<>();
    /** caches all presets with nametemplates fullname->preset */
    private final Map<String, TaggingPreset> PRESET_WITH_NAMETEMPLATE_CACHE = new LinkedHashMap<>();
    /** caches the tags in all presets key -> values */
    private final MultiMap<String, String> PRESET_TAG_CACHE = new MultiMap<>();
    /** caches the roles in all presets key -> role */
    private final Set<Role> PRESET_ROLE_CACHE = new HashSet<>();

    /**
     * Returns all tagging presets. Only for plugin compatibility.
     * @return all tagging presets
     * @deprecated use {@code MainApplication.getTaggingPresets().getAllPresets();}
     */
    @Deprecated
    public static Collection<TaggingPreset> getTaggingPresets() {
        return MainApplication.getTaggingPresets().getAllPresets();
    }

    /**
     * Adds a tagging preset listener. Only for plugin compatibility.
     * @param listener The listener to add
     * @deprecated use {@code MainApplication.getTaggingPresets().addTaggingPresetListener(listener);}
     */
    @Deprecated
    public static void addListener(TaggingPresetListener listener) {
        MainApplication.getTaggingPresets().addTaggingPresetListener(listener);
    }

    /**
     * Removes a tagging preset listener. Only for plugin compatibility.
     * @param listener The listener to remove
     * @deprecated use {@code MainApplication.getTaggingPresets().removeTaggingPresetListener(listener);}
     */
    @Deprecated
    public static void removeListener(TaggingPresetListener listener) {
        MainApplication.getTaggingPresets().removeTaggingPresetListener(listener);
    }

    /**
     * Standard initialization during app startup. Obeys users prefs.
     */
    public void initialize() {
        initFromUserPrefs();
        initializeMenus();
    }

    /**
     * Initializes tagging presets from user preferences.
     */
    public void initFromUserPrefs() {
        TaggingPresetReader.readFromPreferences(false, false).forEach(this::addRoot);
        notifyListeners();
    }

    // Cannot implement Destroyable since this is static
    /**
     * Call to deconstruct the TaggingPresets menus and other information so that it
     * can be rebuilt later.
     *
     * @since 15582
     */
    public void destroy() {
        unInitializeMenus();
        cleanUp();
    }

    /**
     * Adds a tagging preset listener.
     * @param listener The listener to add
     */
    public void addTaggingPresetListener(TaggingPresetListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a tagging preset listener.
     * @param listener The listener to remove
     */
    public void removeTaggingPresetListener(TaggingPresetListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Returns all tagging presets.
     * @return all tagging presets
     */
    public Collection<TaggingPreset> getAllPresets() {
        return Collections.unmodifiableCollection(PRESET_CACHE.values());
    }

    /**
     * Returns every role found in any preset.
     * @return the roles
     */
    public Set<Role> getPresetRoles() {
        return Collections.unmodifiableSet(PRESET_ROLE_CACHE);
    }

    /**
     * Returns all keys seen in all tagging presets.
     * @return the set of all keys
     */
    public Set<String> getPresetKeys() {
        return Collections.unmodifiableSet(PRESET_TAG_CACHE.keySet());
    }

    /**
     * Returns all values seen in all presets for this key.
     * @param key the key
     * @return the set of all values
     */
    public Set<String> getPresetValues(String key) {
        Set<String> values = PRESET_TAG_CACHE.get(key);
        if (values != null)
            return Collections.unmodifiableSet(values);
        return Collections.emptySet();
    }

    /**
     * Determines if the given key is in the loaded presets.
     * @param key key
     * @return {@code true} if the given key in the loaded presets
     * @since 18281
     */
    public boolean isKeyInPresets(String key) {
        return PRESET_TAG_CACHE.get(key) != null;
    }

    /**
     * Replies a new collection of all presets matching the parameters.
     *
     * @param types the preset types to include
     * @return a new collection of all presets matching the parameters.
     * @since xxx
     */
    public Collection<TaggingPreset> getMatchingPresets(final Collection<TaggingPresetType> types) {
        return PRESET_CACHE.values().stream().filter(preset -> preset.typeMatches(types)).collect(Collectors.toList());
    }

    /**
     * Replies a new collection of all presets matching the parameters.
     * <p>
     * To open a dialog, request only showable presets. To obtain information about
     * standard tags request all presets.
     *
     * @param types the preset types (or null to match any preset type)
     * @param tags the tags to perform matching on
     * @param onlyShowable whether the preset must be showable
     * @return a collection of all presets matching the parameters.
     *
     * @see TaggingPreset#matches(Collection, Map, boolean)
     * @see TaggingPreset#typeMatches(Collection)
     * @see KeyedItem#matches(Map)
     * @see TaggingPreset#isShowable()
     * @since 9266
     */
    public Collection<TaggingPreset> getMatchingPresets(final Collection<TaggingPresetType> types,
                                                            final Map<String, String> tags, final boolean onlyShowable) {
        return PRESET_CACHE.values().stream().filter(preset -> preset.matches(types, tags, onlyShowable)).collect(Collectors.toList());
    }

    /**
     * Replies a new collection of all presets matching the given preset.
     *
     * @param primitive the primitive
     * @return a new collection of all presets matching the given primitive.
     * @see TaggingPreset#test(IPrimitive)
     * @since 13623 (signature)
     */
    public Collection<TaggingPreset> getMatchingPresets(final IPrimitive primitive) {
        return PRESET_CACHE.values().stream().filter(preset -> preset.test(primitive)).collect(Collectors.toList());
    }

    /**
     * Returns the first preset with a name_template matching the given primitive
     *
     * @param primitive the primitive to match
     * @return the preset or null
     */
    public TaggingPreset getFirstMatchingPresetWithNameTemplate(final IPrimitive primitive) {
        Collection<TaggingPresetType> type = EnumSet.of(TaggingPresetType.forPrimitive(primitive));
        for (TaggingPreset tp : PRESET_WITH_NAMETEMPLATE_CACHE.values()) {
            if (tp.typeMatches(type)) {
                if (tp.getNameTemplateFilter() != null) {
                    if (tp.getNameTemplateFilter().match(primitive))
                        return tp;
                } else if (tp.matches(type, primitive.getKeys(), false)) {
                    return tp;
                }
            }
        }
        return null;
    }

    /**
     * Add a new root element
     * @param root the new root element
     */
    void addRoot(Root root) {
        Map<String, Chunk> chunks = new HashMap<>();
        root.fixup(chunks, root);
        rootElements.add(root);
        cachePresets(root);
    }

    /**
     * Returns all items that satisfy a given predicate.
     * @param p the predicate all items must satisfy
     * @return the items that satisfy the predicate
     */
    List<Item> getAllItems(Predicate<Item> p) {
        List<Item> list = new LinkedList<>();
        rootElements.forEach(r -> r.addToItemList(list, p, false));
        return list;
    }

    /**
     * Returns all items of a type.
     * @param <E> the type
     * @param type the type
     * @return the list of all items
     */
    <E> List<E> getAllItems(Class<E> type) {
        List<E> list = new LinkedList<>();
        rootElements.forEach(r -> r.addToItemList(list, type, false));
        return list;
    }

    /**
     * Notifies all listeners that presets have changed.
     */
    void notifyListeners() {
        listeners.forEach(TaggingPresetListener::taggingPresetsModified);
    }

    /**
     * Initializes the preset menu and toolbar.
     * <p>
     * Builds the tagging presets menu and registers all preset actions with the application
     * toolbar.
     */
    private void initializeMenus() {
        MainMenu mainMenu = MainApplication.getMenu();
        JMenu presetsMenu = mainMenu.presetsMenu;
        if (presetsMenu.getComponentCount() == 0) {
            MainMenu.add(presetsMenu, mainMenu.presetSearchAction);
            MainMenu.add(presetsMenu, mainMenu.presetSearchPrimitiveAction);
            MainMenu.add(presetsMenu, PreferencesAction.forPreferenceTab(tr("Preset preferences..."),
                    tr("Click to open the tagging presets tab in the preferences"), TaggingPresetPreference.class));
            presetsMenu.addSeparator();
        }

        // register all presets with the application toolbar
        ToolbarPreferences toolBar = MainApplication.getToolbar();
        getAllItems(TaggingPresetBase.class).forEach(tp -> toolBar.register(tp.getAction()));
        toolBar.refreshToolbarControl();

        // add presets and groups to the presets menu
        if (rootElements.isEmpty()) {
            presetsMenu.setVisible(false);
        } else {
            rootElements.forEach(e -> e.addToMenu(presetsMenu));
            if (TaggingPresets.SORT_VALUES.get()) {
                TaggingPresetUtils.sortMenu(presetsMenu);
            }
        }
    }

    private void unInitializeMenus() {
        ToolbarPreferences toolBar = MainApplication.getToolbar();
        if (toolBar != null)
            getAllItems(TaggingPresetBase.class).forEach(tp -> toolBar.unregister(tp.getAction()));
        MainMenu menu = MainApplication.getMenu();
        if (menu != null)
            menu.presetsMenu.removeAll();
    }

    private void cleanUp() {
        PRESET_CACHE.clear();
        PRESET_WITH_NAMETEMPLATE_CACHE.clear();
        PRESET_TAG_CACHE.clear();
        PRESET_ROLE_CACHE.clear();
        rootElements.forEach(Root::destroy);
        rootElements.clear();
    }

    /**
     * Initialize the cache with presets.
     *
     * @param root the root of the xml file
     */
    private void cachePresets(Root root) {
        for (TaggingPreset tp : root.getAllItems(TaggingPreset.class, false)) {
            PRESET_CACHE.put(tp.getRawName(), tp);
            if (tp.getNameTemplate() != null)
                PRESET_WITH_NAMETEMPLATE_CACHE.put(tp.getRawName(), tp);
        }

        PRESET_ROLE_CACHE.addAll(root.getAllItems(Role.class, false));
        root.getAllItems(KeyedItem.class, false).forEach(item -> {
            if (item.key != null && item.getValues() != null) {
                PRESET_TAG_CACHE.putAll(item.key, item.getValues());
            }
        });
    }
}
