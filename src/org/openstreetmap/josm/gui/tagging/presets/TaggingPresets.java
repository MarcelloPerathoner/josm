// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.IOException;
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
import javax.swing.JOptionPane;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.preferences.ListProperty;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.MultiMap;
import org.xml.sax.SAXException;

/**
 * The Tagging Presets System.
 * <p>
 * The preset system has many uses:
 * <ul>
 * <li>it lets users edit osm primitives in an easy intuitive way,
 * <li>it can generate customized names for primitives, and
 * <li>it knows about the standard tags for a given primitive.
 * </ul>
 * If a plugin wishes to add new tagging preset sources it should call:
 * <pre>
 *   addSource(url, TaggingPresetReader.read(url, false));
 *   unInitializeMenus();
 *   initializeMenus();
 *   notifyListeners();
 * </pre>
 * To remove sources it should call:
 * <pre>
 *   removeSource(url);
 *   unInitializeMenus();
 *   clearCache();
 *   initCache();
 *   initializeMenus();
 *   notifyListeners();
 * </pre>
 * <p>
 * @implNote
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
 * Plugin authors should use {@link MainApplication#getTaggingPresets} instead of
 * {@code TaggingPresets}.
 *
 * @since xxx (non-static)
 */
public final class TaggingPresets implements Destroyable {
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
    private final Map<String, Root> rootElements = new LinkedHashMap<>();
    /** The registered listeners */
    private final Collection<TaggingPresetListener> listeners = new LinkedList<>();
    /** caches all presets fullname->preset */
    private final Map<String, TaggingPreset> presetCache = new LinkedHashMap<>();
    /** caches all presets with nametemplates fullname->preset */
    private final Map<String, TaggingPreset> presetWithNametemplateCache = new LinkedHashMap<>();
    /** caches the tags in all presets key -> values */
    private final MultiMap<String, String> presetTagCache = new MultiMap<>();
    /** caches the roles in all presets key -> role */
    private final Set<Role> presetRoleCache = new HashSet<>();

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
     * Free resources.
     * @since 15582
     */
    public void destroy() {
        rootElements.values().forEach(Root::destroy);
        rootElements.clear();
        clearCache();
        notifyListeners();
    }

    /**
     * Add a tagging preset sources from an url. May displays an error message.
     */
    public void addSourceFromUrl(String url) {
        String errorMessage = null;
        try {
            addSource(url, TaggingPresetReader.read(url, false));
        } catch (SAXException e) {
            errorMessage = tr("Tagging preset source {0} contains errors.", url);
        } catch (IOException e) {
            errorMessage = tr("Could not read tagging preset source: {0}", url);
        }
        if (errorMessage != null) {
            Logging.error(errorMessage);
            JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                errorMessage,
                tr("Error"),
                JOptionPane.ERROR_MESSAGE
            );
        }
    }

    /**
     * Initialize the cache from the known root elements
     */
    void initCache() {
        rootElements.values().forEach(this::cachePresets);
    }

    /**
     * Clears the cache
     */
    void clearCache() {
        presetCache.clear();
        presetWithNametemplateCache.clear();
        presetTagCache.clear();
        presetRoleCache.clear();
    }

    /**
     * Adds a new source of tagging presets
     * <p>
     * After adding new sources it is the caller's responsibility to call
     * {@link #reInit}.
     *
     * @param url the url of the XML resource
     * @param root the parsed root element of the XML resource
     */
    public void addSource(String url, Root root) {
        Map<String, Chunk> chunks = new HashMap<>();
        root.fixup(chunks, null);
        rootElements.put(url, root);
    }

    /**
     * Removes a source of tagging presets
     * <p>
     * After removing sources it is the caller's responsibility to call
     * {@link #reInit}.
     *
     * @param url the url of the XML resource
     */
    public void removeSource(String url) {
        Root root = rootElements.remove(url);
        if (root != null) {
            root.destroy();
        }
    }

    /**
     * Re-initializes the presets system after a change in sources.
     * <p>
     * This should run in the EDT.
     * @param presetsMenu the presets menu
     * @param toolbar the toolbar
     */
    public void reInit(JMenu presetsMenu, ToolbarPreferences toolbar) {
        clearCache();
        initCache();
        notifyListeners();
        if (presetsMenu != null) {
            TaggingPresetUtils.initializePresetsMenu();
            rootElements.values().forEach(
                root -> root.addToMenu(presetsMenu));
            finalizePresetsMenu(presetsMenu);
        }
        if (toolbar != null) {
            toolbar.unregisterPresets();
            getAllItems(TaggingPresetBase.class).forEach(tp -> {
                if (tp.getAction() != null)
                    toolbar.register(tp.getAction());
            });
            toolbar.refreshToolbarControl();
        }
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
        return Collections.unmodifiableCollection(presetCache.values());
    }

    /**
     * Returns every role found in any preset.
     * @return the roles
     */
    public Set<Role> getPresetRoles() {
        return Collections.unmodifiableSet(presetRoleCache);
    }

    /**
     * Returns all keys seen in all tagging presets.
     * @return the set of all keys
     */
    public Set<String> getPresetKeys() {
        return Collections.unmodifiableSet(presetTagCache.keySet());
    }

    /**
     * Returns all values seen in all presets for this key.
     * @param key the key
     * @return the set of all values
     */
    public Set<String> getPresetValues(String key) {
        Set<String> values = presetTagCache.get(key);
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
        return presetTagCache.get(key) != null;
    }

    /**
     * Replies a new collection of all presets matching the parameters.
     *
     * @param types the preset types to include
     * @return a new collection of all presets matching the parameters.
     * @since xxx
     */
    public Collection<TaggingPreset> getMatchingPresets(final Collection<TaggingPresetType> types) {
        return presetCache.values().stream().filter(preset -> preset.typeMatches(types)).collect(Collectors.toList());
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
        return presetCache.values().stream().filter(preset -> preset.matches(types, tags, onlyShowable)).collect(Collectors.toList());
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
        return presetCache.values().stream().filter(preset -> preset.test(primitive)).collect(Collectors.toList());
    }

    /**
     * Returns the first preset with a name_template matching the given primitive
     *
     * @param primitive the primitive to match
     * @return the preset or null
     */
    public TaggingPreset getFirstMatchingPresetWithNameTemplate(final IPrimitive primitive) {
        Collection<TaggingPresetType> type = EnumSet.of(TaggingPresetType.forPrimitive(primitive));
        for (TaggingPreset tp : presetWithNametemplateCache.values()) {
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
     * Returns all items that satisfy a given predicate.
     * @param p the predicate all items must satisfy
     * @return the items that satisfy the predicate
     */
    List<Item> getAllItems(Predicate<Item> p) {
        List<Item> list = new LinkedList<>();
        rootElements.values().forEach(r -> r.addToItemList(list, p, false));
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
        rootElements.values().forEach(r -> r.addToItemList(list, type, false));
        return list;
    }

    /**
     * Notifies all listeners that presets have changed.
     */
    private void notifyListeners() {
        listeners.forEach(TaggingPresetListener::taggingPresetsModified);
    }

    /**
     * Apply final touches to the presets menu after changes.
     * @param presetsMenu the presets menu
     */
    private void finalizePresetsMenu(JMenu presetsMenu) {
        // add presets and groups to the presets menu
        presetsMenu.setVisible(!rootElements.isEmpty());
    }

    /**
     * Caches various aspects of the given presets.
     *
     * @param root the root of the xml file
     */
    private void cachePresets(Root root) {
        for (TaggingPreset tp : root.getAllItems(TaggingPreset.class, false)) {
            presetCache.put(tp.getRawName(), tp);
            if (tp.getNameTemplate() != null)
                presetWithNametemplateCache.put(tp.getRawName(), tp);
        }

        presetRoleCache.addAll(root.getAllItems(Role.class, false));
        root.getAllItems(KeyedItem.class, false).forEach(item -> {
            if (item.key != null && item.getValues() != null) {
                presetTagCache.putAll(item.key, item.getValues());
            }
        });
    }
}
