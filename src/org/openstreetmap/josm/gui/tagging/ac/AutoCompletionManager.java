// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.ac;

import java.text.Collator;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListener;
import org.openstreetmap.josm.data.osm.event.NodeMovedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesAddedEvent;
import org.openstreetmap.josm.data.osm.event.PrimitivesRemovedEvent;
import org.openstreetmap.josm.data.osm.event.RelationMembersChangedEvent;
import org.openstreetmap.josm.data.osm.event.TagsChangedEvent;
import org.openstreetmap.josm.data.osm.event.WayNodesChangedEvent;
import org.openstreetmap.josm.data.tagging.ac.AutoCompletionItem;
import org.openstreetmap.josm.data.tagging.ac.AutoCompletionPriority;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.tagging.presets.Role;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetType;
import org.openstreetmap.josm.tools.MultiMap;

/**
 * AutoCompletionManager holds a cache of keys with a list of possible auto completion values for
 * each key.
 * <p>
 * Each DataSet can be assigned one AutoCompletionManager instance such that
 * <ol>
 *   <li>any key used in a tag in the data set is part of the key list in the cache</li>
 *   <li>any value used in a tag for a specific key is part of the autocompletion list of this key</li>
 * </ol>
 *
 * Building up auto completion lists should not slow down tabbing from input field to input field.
 * Looping through the complete data set in order to build up the auto completion list for a
 * specific input field is not efficient enough, hence this cache.
 */
public class AutoCompletionManager implements DataSetListener {

    /**
     * Data class to remember tags that the user has entered.
     */
    public static class UserInputTag {
        private final String key;
        private final String value;
        private final boolean defaultKey;

        /**
         * Constructor.
         *
         * @param key the tag key
         * @param value the tag value
         * @param defaultKey true, if the key was not really entered by the
         * user, e.g. for preset text fields.
         * In this case, the key will not get any higher priority, just the value.
         */
        public UserInputTag(String key, String value, boolean defaultKey) {
            this.key = key;
            this.value = value;
            this.defaultKey = defaultKey;
        }

        @Override
        public int hashCode() {
            return Objects.hash(key, value, defaultKey);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            final UserInputTag other = (UserInputTag) obj;
            return this.defaultKey == other.defaultKey
                && Objects.equals(this.key, other.key)
                && Objects.equals(this.value, other.value);
        }
    }

    /**
     * A MultiMap that on default returns an empty set instead of null.
     * @param <A> the key type
     * @param <B> the value type
     */
    static class BetterMultiMap<A, B> extends MultiMap<A, B> {
        @Override
        public Set<B> get(A key) {
            Set<B> s = super.get(key);
            return s == null ? Collections.emptySet() : s;
        }
    }

    private static Collator currentLocaleCollator = Collator.getInstance();

    /**
     * Compares two AutoCompletionItems alphabetically.
     */
    public static final Comparator<AutoCompletionItem> ALPHABETIC_COMPARATOR =
        (ac1, ac2) -> currentLocaleCollator.compare(ac1.toString(), ac2.toString());

    /**
     * Compares two AutoCompletionItems on priority, then alphabetically.
     */
    public static final Comparator<AutoCompletionItem> PRIORITY_COMPARATOR =
        Comparator.<AutoCompletionItem, AutoCompletionPriority>comparing(
                AutoCompletionItem::getPriority, AutoCompletionPriority::altCompare)
            .reversed()
            .thenComparing(ALPHABETIC_COMPARATOR);

    /** If the dirty flag is set true, a rebuild is necessary. */
    private boolean dirty;
    /** The data set that is managed */
    private DataSet ds;
    /**
     * The tag cache subdivided for {@link TaggingPresetType tagging preset type} and key.
     */
    private final EnumMap<TaggingPresetType, MultiMap<String, String>> presetTagCache = new EnumMap<>(TaggingPresetType.class);
    /**
     * The primitive cache subdivided for {@link #classifyPrimitive(Map) classification} and tag key.
     */
    private final Map<String, MultiMap<String, String>> categoryTagCache = new HashMap<>();
    /**
     * The cached relations further subdivided by {@link #classifyRelation(Map) relation type}.
     */
    private final MultiMap<String, Relation> relationCache = new BetterMultiMap<>();
    /**
     * Cache for tags that have been entered by the user.
     */
    private static final Set<UserInputTag> USER_INPUT_TAG_CACHE = new LinkedHashSet<>();

    /** Cached instances of AutoCompletionManager for each DataSet */
    private static final Map<DataSet, AutoCompletionManager> INSTANCES = new HashMap<>();

    /** All known primitive types. This is an attempt to categorize primitives starting at the most `rootish´ tag. */
    private static Set<String> allPrimitiveTypes = new HashSet<>(Arrays.asList(
        "advertising",
        "aerialway",
        "aeroway",
        "airmark",
        "amenity",
        "attraction",
        "barrier",
        "bicycle_road",
        "boundary",
        "building",
        "bridge",
        "cemetery",
        /* "covered", */
        "craft",
        "cycleway",
        "emergency",
        "geological",
        "golf",
        "healthcare",
        "highway",
        "historic",
        /* "junction", */
        "landuse",
        "leisure",
        "man_made",
        "military",
        "mountain_pass",
        "natural",
        "office",
        "pipeline",
        "place",
        "power",
        "public_transport",
        "railway",
        "route",
        "shop",
        /* "sport", */
        "telecom",
        "tourism",
        "traffic_calming",
        "traffic_sign",
        "tunnel",
        "waterway"
    ));

    /**
     * Constructs a new {@code AutoCompletionManager}.
     * @param ds data set
     * @throws NullPointerException if ds is null
     */
    public AutoCompletionManager(DataSet ds) {
        this.ds = Objects.requireNonNull(ds);
        this.dirty = true;
        currentLocaleCollator.setStrength(Collator.TERTIARY); // aka. case-independent
    }

    Map<TaggingPresetType, MultiMap<String, String>> getPresetTagCache() {
        rebuild();
        return presetTagCache;
    }

    MultiMap<String, String> getPresetTagCache(TaggingPresetType type) {
        return getPresetTagCache().getOrDefault(type, new BetterMultiMap<>());
    }

    MultiMap<String, String> getCategoryTagCache(String category) {
        rebuild();
        return categoryTagCache.getOrDefault(category, new BetterMultiMap<>());
    }

    MultiMap<String, Relation> getRelationCache() {
        rebuild();
        return relationCache;
    }

    /**
     * initializes the cache from the primitives in the dataset
     */
    void rebuild() {
        if (dirty) {
            presetTagCache.clear();
            categoryTagCache.clear();
            relationCache.clear();
            cachePrimitives(ds.allNonDeletedCompletePrimitives());
            dirty = false;
        }
    }

    /**
     * Cache tags of a collection of primitives.
     *
     * @param primitives the primitives to cache
     */
    void cachePrimitives(Collection<? extends OsmPrimitive> primitives) {
        for (OsmPrimitive primitive : primitives) {
            cachePrimitive(primitive);
            if (primitive instanceof Relation) {
                relationCache.put(classifyRelation(primitive.getKeys()), (Relation) primitive);
            }
        }
    }

    /**
     * Cache the tags of one primitive.
     *
     * @param primitive an OSM primitive
     */
    void cachePrimitive(OsmPrimitive primitive) {
        MultiMap<String, String> mmap =
            presetTagCache.computeIfAbsent(TaggingPresetType.forPrimitive(primitive), key -> new BetterMultiMap<>());
        primitive.visitKeys((p, key, value) -> mmap.put(key, value));
        classifyPrimitive(primitive.getKeys()).forEach(category -> {
            MultiMap<String, String> mmap2 = categoryTagCache.computeIfAbsent(category, key -> new BetterMultiMap<>());
            primitive.visitKeys((p, key, value) -> mmap2.put(key, value));
        });
    }

    /**
     * Returns the primitive type.
     * <p>
     * This is used to categorize the primitives in the dataset.  A primitive with the keys:
     * <ul>
     * <li>highway=service
     * </ul>
     * will return a primitive type of {@code "highway"}.
     *
     * @param tags the tags on the primitive
     * @return the primitive type or {@code ""}
     */
    public static Set<String> classifyPrimitive(Map<String, String> tags) {
        return intersection(tags.keySet(), allPrimitiveTypes);
    }

    /**
     * Returns the relation type.
     * <p>
     * This is used to categorize the relations in the dataset.  A relation with the keys:
     * <ul>
     * <li>type=route
     * <li>route=hiking
     * </ul>
     * will return a relation type of {@code "route.hiking"}.
     *
     * @param tags the tags on the relation
     * @return the relation type or {@code ""}
     */
    public String classifyRelation(Map<String, String> tags) {
        if (tags == null) return "";
        String type = tags.get("type");
        if (type == null) return "";
        String subtype = tags.get(type);
        if (subtype == null) return type;
        return type + "." + subtype;
    }

    /**
     * Construct a role out of a relation member
     *
     * @param member the relation member
     * @return the Role
     */
    private Role mkRole(RelationMember member) {
        return new Role(member.getRole(), EnumSet.of(TaggingPresetType.forPrimitiveType(member.getDisplayType())));
    }

    /**
     * Remembers user input for the given key/value.
     * @param key Tag key
     * @param value Tag value
     * @param defaultKey true, if the key was not really entered by the user, e.g. for preset text fields
     */
    public static void rememberUserInput(String key, String value, boolean defaultKey) {
        UserInputTag tag = new UserInputTag(key, value, defaultKey);
        USER_INPUT_TAG_CACHE.remove(tag); // re-add, so it gets to the last position of the LinkedHashSet
        USER_INPUT_TAG_CACHE.add(tag);
    }

    /**
     * Returns the user input history.
     * @return the keys in the user input history
     */
    public static Collection<String> getUserInputKeys() {
        List<String> keys = USER_INPUT_TAG_CACHE.stream()
                .filter(tag -> !tag.defaultKey)
                .map(tag -> tag.key)
                .collect(Collectors.toList());
        Collections.reverse(keys);
        return new LinkedHashSet<>(keys);
    }

    /**
     * Returns the user input history.
     * @param key the key for which to retrieve values
     * @return the values in the user input history
     */
    public static Collection<String> getUserInputValues(String key) {
        List<String> values = USER_INPUT_TAG_CACHE.stream()
                .filter(tag -> Objects.equals(key, tag.key))
                .map(tag -> tag.value)
                .collect(Collectors.toList());
        Collections.reverse(values);
        return new LinkedHashSet<>(values);
    }

    /**
     * Returns a collection of all member roles in the dataset.
     * <p>
     * Member roles are distinct on role name and primitive type they apply to. So there will be a
     * role "platform" for nodes and a role "platform" for ways.
     *
     * @return the collection of member roles
     */
    public Set<Role> getAllMemberRoles() {
        return getRelationCache().getAllValues().stream()
            .flatMap(rel -> rel.getMembers().stream()).map(this::mkRole).collect(Collectors.toSet());
    }

    /**
     * Returns all cached {@link AutoCompletionItem}s for all member roles.
     * @return the currently cached roles, sorted by priority and alphabet
     * @since xxx
     */
    public List<AutoCompletionItem> getAllRoles() {
        Map<String, AutoCompletionPriority> map = new HashMap<>();

        MainApplication.getTaggingPresets().getPresetRoles().forEach(role -> map.merge(role.getKey(),
            AutoCompletionPriority.IS_IN_STANDARD, AutoCompletionPriority::mergeWith));
        getAllMemberRoles().forEach(role -> map.merge(role.getKey(),
            AutoCompletionPriority.IS_IN_DATASET, AutoCompletionPriority::mergeWith));

        return toList(map, PRIORITY_COMPARATOR);
    }

    /**
     * Returns a collection of all roles in the dataset for one relation type.
     * <p>
     * Member roles are distinct on role name and primitive type they apply to. So there will be a
     * role "platform" for nodes and a role "platform" for ways.
     *
     * @param relationType the {@link #classifyRelation(Map) relation type}
     * @return the collection of member roles
     */
    public Set<Role> getMemberRoles(String relationType) {
        Set<Relation> relations = getRelationCache().get(relationType);
        if (relations == null)
            return Collections.emptySet();
        return relations.stream().flatMap(rel -> rel.getMembers().stream()).map(this::mkRole).collect(Collectors.toSet());
    }

    /**
     * Returns key suggestions for a given relation type.
     * <p>
     * Returns all keys in the dataset used on a given {@link #classifyRelation(Map) relation type}.
     *
     * @param tags current tags in the tag editor panel, used to determine the relation type
     * @return the suggestions
     */
    public Set<String> getKeysForRelation(Map<String, String> tags) {
        Set<String> set = new HashSet<>();
        Set<Relation> relations = tags != null ? getRelationCache().get(classifyRelation(tags)) : getRelationCache().getAllValues();
        if (relations == null)
            return set;
        return relations.stream().flatMap(rel -> rel.getKeys().entrySet().stream()).map(Entry::getKey).collect(Collectors.toSet());
    }

    /**
     * Returns value suggestions for a given relation type and key.
     * <p>
     * Returns all values in the dataset used with a given key on a given
     * {@link #classifyRelation(Map) relation type}.
     * <p>
     * {@code tags} are used to determine the relation type. If {@code null} then the
     * values found on all relations are returned.
     *
     * @param tags current tags in the tag editor panel, or null
     * @param key the key to get values for
     * @return the suggestions
     */
    public Set<String> getValuesForRelation(Map<String, String> tags, String key) {
        Set<String> result = new HashSet<>();
        String category = classifyRelation(tags);
        Set<Relation> relations = category != null ? getRelationCache().get(category) : getRelationCache().getAllValues();

        Set<String> altKeys = new HashSet<>();
        for (TaggingPreset preset : getPresets(EnumSet.of(TaggingPresetType.RELATION), tags)) {
            altKeys.addAll(preset.getAlternativeAutocompleteKeys(key));
        }

        relations.forEach(rel -> {
            result.add(rel.get(key));
            for (String altKey : altKeys) {
                result.add(rel.get(altKey));
            }
        });

        result.remove(null);
        return result;
    }

    /**
     * Returns role suggestions for a given relation type.
     * <p>
     * Returns all roles in the dataset for a given {@link TaggingPresetType role type} used with a given
     * {@link #classifyRelation(Map) relation type}.
     *
     * @param tags current tags in the tag editor panel, used to determine the relation type
     * @param roleTypes all roles returned will match all of the types in this set.
     * @return the suggestions
     */
    public Set<String> getRolesForRelation(Map<String, String> tags, Set<TaggingPresetType> roleTypes) {
        Set<String> set = new HashSet<>();
        Set<Relation> relations = tags != null ? getRelationCache().get(classifyRelation(tags)) : getRelationCache().getAllValues();
        if (relations == null)
            return set;
        return relations.stream().flatMap(rel -> rel.getMembers().stream())
            .map(this::mkRole).filter(role -> role.appliesToAll(roleTypes)).map(Object::toString)
            .collect(Collectors.toSet());
    }

    /**
     * Returns all presets of type {@code types} matched by {@code tags}.
     *
     * @param types the preset types to include, (node / way / relation ...) or null to include all types
     * @param tags match presets using these tags or null to match all presets
     * @return the matched presets
     */
    private Collection<TaggingPreset> getPresets(Collection<TaggingPresetType> types, Map<String, String> tags) {
        if (tags == null || tags.isEmpty())
            return MainApplication.getTaggingPresets().getMatchingPresets(types);
        return MainApplication.getTaggingPresets().getMatchingPresets(types, tags, false);
    }

    /**
     * Returns the first key found in each of the presets matched by {@code tags}.
     *
     * @param types the preset types to include, (node / way / relation ...) or null to include all types
     * @param tags match presets using these tags or null to match all presets
     * @return the suggested keys
     * @since xxx
     */
    public Set<String> getPresetsFirstKey(Collection<TaggingPresetType> types, Map<String, String> tags) {
        Set<String> set = new HashSet<>();

        for (TaggingPreset preset : getPresets(types, tags)) {
            Collection<String> keys = preset.getAllKeys();
            if (!keys.isEmpty())
                set.add(keys.iterator().next());
        }
        return set;
    }

    /**
     * Returns all keys found in the presets matched by {@code tags}.
     *
     * @param types the preset types to include, (node / way / relation ...) or null to include all types
     * @param tags match presets using these tags or null to match all presets
     * @return the suggested keys
     * @since xxx
     */
    public Set<String> getPresetsAllKeys(Collection<TaggingPresetType> types, Map<String, String> tags) {
        Set<String> set = new HashSet<>();

        for (TaggingPreset preset : getPresets(types, tags)) {
            set.addAll(preset.getAllKeys());
        }
        return set;
    }

    /**
     * Returns all values for {@code key} found in the presets matched by {@code tags}.
     *
     * @param types the preset types to include, (node / way / relation ...) or null to include all types
     * @param tags match presets using these tags or null to match all presets
     * @param key the key to return values for
     * @return the suggested values
     * @since xxx
     */
    public Set<String> getPresetValues(Collection<TaggingPresetType> types, Map<String, String> tags, String key) {
        Set<String> set = new HashSet<>();
        for (TaggingPreset preset : getPresets(types, tags)) {
            set.addAll(preset.getAllValues(key));
        }
        return set;
    }

    /**
     * Returns all roles found in the presets matched by {@code tags}.
     *
     * @param tags match presets using these tags or null to match all presets
     * @param roleTypes the role types to include, (node / way / relation ...) or null to include all types
     * @return the suggested roles
     * @since xxx
     */
    public Set<String> getPresetRoles(Map<String, String> tags, Collection<TaggingPresetType> roleTypes) {
        Set<String> set = new HashSet<>();

        for (TaggingPreset preset : getPresets(EnumSet.of(TaggingPresetType.RELATION), tags)) {
            for (Role role : preset.getAllRoles()) {
                if (role.appliesToAll(roleTypes))
                    set.add(role.getKey());
            }
        }
        return set;
    }

    /**
     * Returns all keys used in the dataset.
     * @return the keys
     */
    public Collection<String> getDataSetKeys() {
        Set<String> result = new HashSet<>();
        getPresetTagCache().values().forEach(mm -> result.addAll(mm.keySet()));
        return result;
    }

    /**
     * Returns all keys common to all given {@link #classifyPrimitive(Map) primitive categories}.
     * <p>
     * For each given preset type:
     *   find the union of all keys used on any primitive of that type
     * then return the intersection of all unions thus found.
     * <p>
     * Use case: suggest keys the user may put on a given selection of primitives.
     *
     * @param categories the categories
     * @return the keys
     * @since xxx
     */
    public Set<String> getDataSetKeys(Collection<String> categories) {
        return categories.stream().map(t -> getCategoryTagCache(t).keySet())
            // intersect all key sets
            .reduce((a, b) -> intersection(a, b)).orElse(Collections.emptySet());
    }

    /**
     * Returns all values used in the dataset for the given key.
     * @param key the key
     * @return the values
     */
    public Collection<String> getDataSetValues(String key) {
        Set<String> result = new HashSet<>();
        getPresetTagCache().values().forEach(mm -> result.addAll(mm.getValues(key)));
        return result;
    }

    /**
     * Returns all values of the given key on the given {@link #classifyPrimitive(Map) primitive categories}.
     * <p>
     * Use case: suggest values for a given selection of primitives and a given key
     *
     * @param categories the categories
     * @param key the key
     * @return the values
     * @since xxx
     */
    public Set<String> getDataSetValues(Collection<String> categories, String key) {
        Set<String> result = new HashSet<>();
        categories.forEach(cat -> result.addAll(getCategoryTagCache(cat).get(key)));
        return result;
    }

    /**
     * Fills a combobox dropdown with all known keys.
     */
    public class NaiveKeyAutoCompManager extends DefaultAutoCompListener<AutoCompletionItem> {
        @Override
        protected void updateAutoCompModel(AutoCompComboBoxModel<AutoCompletionItem> model) {
            Map<String, AutoCompletionPriority> map = merge(
                toMap(MainApplication.getTaggingPresets().getPresetKeys(), AutoCompletionPriority.IS_IN_STANDARD),
                toMap(getDataSetKeys(), AutoCompletionPriority.IS_IN_DATASET),
                toMap(getUserInputKeys(), AutoCompletionPriority.UNKNOWN)
            );
            map.merge("source", AutoCompletionPriority.IS_IN_STANDARD, AutoCompletionPriority::mergeWith);
            model.replaceAllElements(toList(map, ALPHABETIC_COMPARATOR));
        }
    }

    /**
     * Autocompletes with all known values for a given key or given keys.
     * <p>
     * A supplier function may be used to dynamically provide the key.
     * <pre>{@code
     * AutoCompletionManager am = AutoCompletionManager.of(dataSet);
     * AutoCompTextField<String> textField = new AutoCompTextField<>();
     * textField.addAutoCompListener(am.new NaiveValueAutoCompManager("addr:street"));
     * }</pre>
     */
    public class NaiveValueAutoCompManager extends DefaultAutoCompListener<AutoCompletionItem> {
        Supplier<Collection<String>> keysSupplier;

        /**
         * Constructor
         * @param key The given key
         */
        public NaiveValueAutoCompManager(String key) {
            Set<String> keySet;
            keySet = new HashSet<>();
            keySet.add(key);
            this.keysSupplier = () -> keySet;
        }

        /**
         * Constructor
         * @param keys The given keys
         */
        public NaiveValueAutoCompManager(Collection<String> keys) {
            Set<String> keySet;
            keySet = new HashSet<>(keys);
            this.keysSupplier = () -> keySet;
        }

        /**
         * Constructor
         * @param keySupplier A supplier of a key that gets called before autocompletion.
         */
        public NaiveValueAutoCompManager(Supplier<String> keySupplier) {
            this.keysSupplier = () -> Collections.singletonList(keySupplier.get());
        }

        @Override
        protected void updateAutoCompModel(AutoCompComboBoxModel<AutoCompletionItem> model) {
            Map<String, AutoCompletionPriority> map = new HashMap<>();

            for (String key : keysSupplier.get()) {
                map = merge(
                    map,
                    toMap(MainApplication.getTaggingPresets().getPresetValues(key), AutoCompletionPriority.IS_IN_STANDARD),
                    toMap(getDataSetValues(key), AutoCompletionPriority.IS_IN_DATASET),
                    toMap(getUserInputValues(key), AutoCompletionPriority.UNKNOWN)
                );
            }
            model.replaceAllElements(toList(map, ALPHABETIC_COMPARATOR));
        }
    }

    /*
     * Implementation of the DataSetListener interface
     */

    @Override
    public void primitivesAdded(PrimitivesAddedEvent event) {
        if (dirty)
            return;
        cachePrimitives(event.getPrimitives());
    }

    @Override
    public void primitivesRemoved(PrimitivesRemovedEvent event) {
        dirty = true;
    }

    @Override
    public void tagsChanged(TagsChangedEvent event) {
        if (dirty)
            return;
        Map<String, String> newKeys = event.getPrimitive().getKeys();
        Map<String, String> oldKeys = event.getOriginalKeys();

        if (!newKeys.keySet().containsAll(oldKeys.keySet())) {
            // Some keys removed, might be the last instance of key, rebuild necessary
            dirty = true;
        } else {
            for (Entry<String, String> oldEntry: oldKeys.entrySet()) {
                if (!oldEntry.getValue().equals(newKeys.get(oldEntry.getKey()))) {
                    // Value changed, might be last instance of value, rebuild necessary
                    dirty = true;
                    return;
                }
            }
            cachePrimitives(Collections.singleton(event.getPrimitive()));
        }
    }

    @Override
    public void nodeMoved(NodeMovedEvent event) {/* ignored */}

    @Override
    public void wayNodesChanged(WayNodesChangedEvent event) {/* ignored */}

    @Override
    public void relationMembersChanged(RelationMembersChangedEvent event) {
        dirty = true; // TODO: it is not necessary to rebuild everything if a member is added
    }

    @Override
    public void otherDatasetChange(AbstractDatasetChangedEvent event) {/* ignored */}

    @Override
    public void dataChanged(DataChangedEvent event) {
        dirty = true;
    }

    private AutoCompletionManager registerListeners() {
        ds.addDataSetListener(this);
        MainApplication.getLayerManager().addLayerChangeListener(new LayerChangeListener() {
            @Override
            public void layerRemoving(LayerRemoveEvent e) {
                if (e.getRemovedLayer() instanceof OsmDataLayer
                        && ((OsmDataLayer) e.getRemovedLayer()).data == ds) {
                    INSTANCES.remove(ds);
                    ds.removeDataSetListener(AutoCompletionManager.this);
                    MainApplication.getLayerManager().removeLayerChangeListener(this);
                    dirty = true;
                    presetTagCache.clear();
                    categoryTagCache.clear();
                    relationCache.clear();
                    ds = null;
                }
            }

            @Override
            public void layerOrderChanged(LayerOrderChangeEvent e) {
                // Do nothing
            }

            @Override
            public void layerAdded(LayerAddEvent e) {
                // Do nothing
            }
        });
        return this;
    }

    /**
     * Returns the {@code AutoCompletionManager} for the given data set.
     * @param dataSet the data set
     * @return the {@code AutoCompletionManager} for the given data set
     * @since 12758
     */
    public static AutoCompletionManager of(DataSet dataSet) {
        return INSTANCES.computeIfAbsent(dataSet, ds -> new AutoCompletionManager(ds).registerListeners());
    }

    /****************************************/
    /* Helper and data conversion functions */
    /****************************************/

    /**
     * Return the intersection of two sets.
     *
     * @param <T> the type of the sets
     * @param set1 the first set
     * @param set2 the second set
     * @return the intersection of both sets
     */
    public static <T> Set<T> intersection(Set<T> set1, Set<T> set2) {
        return set1.stream().filter(set2::contains).collect(Collectors.toSet());
    }

    /**
     * Returns the union of two sets.
     *
     * @param <T> the type of the sets
     * @param set1 the first set
     * @param set2 the second set
     * @return the union of both sets
     */
    public static <T> Set<T> union(Set<T> set1, Set<T> set2) {
        return Stream.concat(set1.stream(), set2.stream()).collect(Collectors.toSet());
    }

    /**
     * Merges two or more {@code Map<String, AutoCompletionPriority>}. The result will have the
     * priorities merged.
     *
     * @param maps two or more maps to merge
     * @return the merged map
     */
    @SafeVarargs
    public static final Map<String, AutoCompletionPriority> merge(
            Map<String, AutoCompletionPriority>... maps) {
        return Stream.of(maps).flatMap(m -> m.entrySet().stream())
            .collect(Collectors.toMap(Entry::getKey, Entry::getValue, AutoCompletionPriority::mergeWith));
    }

    /**
     * Turns a {@code Map<String, AutoCompletionPriority>} into a sorted {@code List<AutoCompletionItem>}.
     * @param map the input map
     * @param comparator the sort order
     * @return the list of items
     */
    public static List<AutoCompletionItem> toList(
            Map<String, AutoCompletionPriority> map, Comparator<AutoCompletionItem> comparator) {
        return map.entrySet().stream().map(e -> new AutoCompletionItem(e.getKey(), e.getValue()))
            .sorted(comparator).collect(Collectors.toList());
    }

    /**
     * Turns a collection of String into a collection of AutoCompletionItems with a given priority.
     * @param values the values
     * @param priority the priority
     * @return the list of items
     */
    public static Map<String, AutoCompletionPriority> toMap(
            Collection<String> values, AutoCompletionPriority priority) {
        return values.stream().collect(Collectors.toMap(k -> k, v -> priority));
    }
}
