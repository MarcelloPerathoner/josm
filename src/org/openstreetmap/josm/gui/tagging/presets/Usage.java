// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.SortedMap;
import java.util.TreeMap;

import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.osm.Tagged;

/**
 * Usage information on a key
 *
 * TODO merge with {@link org.openstreetmap.josm.data.osm.TagCollection}
 */
public class Usage {
    /** Usage count for all values used for this key */
    public final SortedMap<String, Integer> map = new TreeMap<>();
    private boolean hadKeys;
    private boolean hadEmpty;
    private int selectedCount;

    /**
     * Computes the tag usage for the given key from the given primitives
     *
     * @param sel the primitives
     * @param key the key
     * @return the tag usage
     */
    public static Usage determineTextUsage(Collection<? extends Tagged> sel, String key) {
        Usage returnValue = new Usage();
        returnValue.selectedCount = sel.size();
        for (Tagged s : sel) {
            String v = s.get(key);
            if (v != null) {
                returnValue.map.merge(v, 1, Integer::sum);
            } else {
                returnValue.hadEmpty = true;
            }
            if (s.hasKeys()) {
                returnValue.hadKeys = true;
            }
        }
        return returnValue;
    }

    /**
     * Computes the tag usage for the given key if the key has a boolean value
     *
     * @param sel the primitives
     * @param key the key
     * @return the tag usage
     */
    public static Usage determineBooleanUsage(Collection<? extends Tagged> sel, String key) {
        Usage returnValue = new Usage();
        returnValue.selectedCount = sel.size();
        for (Tagged s : sel) {
            String booleanValue = OsmUtils.getNamedOsmBoolean(s.get(key));
            if (booleanValue != null) {
                returnValue.map.merge(booleanValue, 1, Integer::sum);
            }
        }
        return returnValue;
    }

    /**
     * Check if there is exactly one value for this key.
     * @return <code>true</code> if there was exactly one value.
     */
    public boolean hasUniqueValue() {
        return map.size() == 1 && !hadEmpty;
    }

    /**
     * Check if this key was not used in any primitive
     * @return <code>true</code> if it was unused.
     */
    public boolean unused() {
        return map.isEmpty();
    }

    /**
     * Get the first value available.
     * @return The first value
     * @throws NoSuchElementException if there is no such value.
     */
    public String getFirst() {
        return map.firstKey();
    }

    /**
     * Check if we encountered any primitive that had any keys
     * @return <code>true</code> if any of the primitives had any tags.
     */
    public boolean hadKeys() {
        return hadKeys;
    }

    /**
     * Returns the number of primitives selected.
     * @return the number of primitives selected.
     */
    public int getSelectedCount() {
        return selectedCount;
    }

    /**
     * Splits multiple values and adds their usage counts as single value.
     * <p>
     * A value of {@code regional;pizza} will increment the count of {@code regional} and of
     * {@code pizza}.
     */
    public void splitValues() {
        SortedMap<String, Integer> copy = new TreeMap<>(map);
        copy.forEach((value, count) -> {
            map.remove(value);
            for (String v : value.split(";", -1)) {
                map.merge(v, count, Integer::sum);
            }
        });
    }
}
