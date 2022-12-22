// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;

import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.gui.util.LruCache;

/**
 * Enumeration of OSM primitive types associated with names and icons.
 * <p>
 * The types are: node, way, closedway, relation, multipolygon.
 *
 * @since 6068
 */
public enum TaggingPresetType {
    /** Node */
    NODE(/* ICON */ "Osm_element_node", "node"),
    /** Way */
    WAY(/* ICON */ "Osm_element_way", "way"),
    /** Relation */
    RELATION(/* ICON */ "Osm_element_relation", "relation"),
    /** Closed way */
    CLOSEDWAY(/* ICON */ "Osm_element_closedway", "closedway"),
    /** Multipolygon */
    MULTIPOLYGON(/* ICON */ "Osm_element_multipolygon", "multipolygon");
    private final String iconName;
    private final String name;

    /** LRU cache for the parsing of types */
    private static final Map<String, EnumSet<TaggingPresetType>> TYPE_CACHE = new LruCache<>(16);

    TaggingPresetType(String iconName, String name) {
        this.iconName = iconName + ".svg";
        this.name = name;
    }

    /**
     * Replies the SVG icon name.
     * @return the SVG icon name
     */
    public String getIconName() {
        return iconName;
    }

    /**
     * Replies the name, as used in XML presets.
     * @return the name: "node", "way", "relation" or "closedway"
     */
    public String getName() {
        return name;
    }

    /**
     * Determines the {@code TaggingPresetType} of a given primitive.
     * @param p The OSM primitive
     * @return the {@code TaggingPresetType} of {@code p}
     */
    public static TaggingPresetType forPrimitive(IPrimitive p) {
        return forPrimitiveType(p.getDisplayType());
    }

    /**
     * Determines the {@code TaggingPresetType} of a given primitive type.
     * @param type The OSM primitive type
     * @return the {@code TaggingPresetType} of {@code type}
     */
    public static TaggingPresetType forPrimitiveType(OsmPrimitiveType type) {
        if (type == OsmPrimitiveType.NODE)
            return NODE;
        if (type == OsmPrimitiveType.WAY)
            return WAY;
        if (type == OsmPrimitiveType.CLOSEDWAY)
            return CLOSEDWAY;
        if (type == OsmPrimitiveType.MULTIPOLYGON)
            return MULTIPOLYGON;
        if (type == OsmPrimitiveType.RELATION)
            return RELATION;
        throw new IllegalArgumentException("Unexpected primitive type: " + type);
    }

    /**
     * Determines the {@code TaggingPresetType} from a given string.
     * @param type The OSM primitive type as string ("node", "way", "relation" or "closedway")
     * @return the {@code TaggingPresetType} from {@code type}
     */
    public static TaggingPresetType fromString(String type) {
        return Arrays.stream(TaggingPresetType.values())
                .filter(t -> t.getName().equals(type))
                .findFirst().orElse(null);
    }

    /**
     * Returns a set of types
     *
     * @param types the types as comma-separated string. eg. "node,way,relation"
     * @param default_ the default value, returned if {@code types} is null
     * @return the types as set
     * @throws IllegalArgumentException on input error
     */
    public static EnumSet<TaggingPresetType> getOrDefault(String types, EnumSet<TaggingPresetType> default_) throws IllegalArgumentException {
        if (types == null)
            return default_;
        if (types.isEmpty())
            throw new IllegalArgumentException(tr("Unknown type: {0}", types));
        if (TYPE_CACHE.containsKey(types))
            return TYPE_CACHE.get(types);

        EnumSet<TaggingPresetType> result = EnumSet.noneOf(TaggingPresetType.class);
        for (String type : types.split(",", -1)) {
            try {
                TaggingPresetType presetType = fromString(type);
                if (presetType != null) {
                    result.add(presetType);
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(tr("Unknown type: {0}", type), e);
            }
        }
        TYPE_CACHE.put(types, result);
        return result;
    }
}
