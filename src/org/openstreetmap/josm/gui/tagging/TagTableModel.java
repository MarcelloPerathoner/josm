// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import javax.swing.table.DefaultTableModel;

import org.openstreetmap.josm.data.osm.AbstractPrimitive;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.Tagged;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.gui.dialogs.properties.TagEditHelper;
import org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompletionManager;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/**
 * TagTableModel is a table model customized for {@link TagTable}.
 * <p>
 * If a data handler is set, then all operations initiated by the cell editor of the TagTable will
 * call the handler.  Operations initiated programmatically must call the handler themselves.
 * <p>
 * Note: it would have been indicated to use
 * {@link org.openstreetmap.josm.data.osm.TagCollection TagCollection} as a data structure for this,
 * alas, {@code TagCollection} uses a {@code Map}, that does not keep insertion order intact, which
 * is essential for a table.
 *
 * @since xxx
 */
public class TagTableModel extends DefaultTableModel {
    String[] columnNames = new String[]{tr("Key"), tr("Value")};

    /** a data interface (or null if none shall be used) */
    private final TaggedHandler handler;

    private final BooleanProperty propDisplayDiscardableKeys = new BooleanProperty("display.discardable-keys", false);

    /**
     * Class that holds zero or more values with respective frequency count.
     */
    public static class ValueType {
        /** The string shown to the user if the selection contains more than one value for a given key. */
        public static final String DIFFERENT = tr("<different>");
        /** The string shown to the user instead of the empty string if the value is unset. */
        public static final String UNSET = tr("<unset>");
        /** our data: value -> count */
        private Map<String, Integer> map = new HashMap<>();

        ValueType() { }

        ValueType(String value) {
            map.put(value, 1);
        }

        ValueType(ValueType other) {
            map = new HashMap<>(other.map);
        }

        ValueType add(String value) {
            map.put(value, map.getOrDefault(value, 0) + 1);
            return this;
        }

        /**
         * Fills with null values to a size of {@code size}.
         * @param size the sum of all counts when filled
         * @return this
         */
        ValueType fill(int size) {
            int sum = map.values().stream().collect(Collectors.summingInt(v -> v));
            if (size - sum > 0) {
                map.put(null, (size - sum) + map.getOrDefault(null, 0));
            }
            return this;
        }

        /**
         * Returns the set of values for this key.
         * @return the set of values
         */
        public Set<String> values() {
            return map.keySet();
        }

        /**
         * Returns the map of values to count
         * @return map of value -> count
         */
        public Map<String, Integer> getMap() {
            return Collections.unmodifiableMap(map);
        }

        static ValueType merge(ValueType first, ValueType second) {
            ValueType result = new ValueType(first);
            second.map.forEach((k, v) -> result.map.merge(k, v, Integer::sum));
            return result;
        }

        /**
         * This is how the ValueType will be represented in the second column of the tag table.
         */
        @Override
        public String toString() {
            String s;
            switch (map.size()) {
                case 0: s = null; break;
                case 1: s = map.keySet().iterator().next(); break;
                default: s = DIFFERENT;
            }
            return s == null ? "" : s; // by contract toString should never return null
        }

        /**
         * Returns the count of a value
         * @param value the value
         * @return the count (0 for unknown values)
         */
        public Integer getCount(String value) {
            Integer count = map.get(value);
            return count != null ? count : 0;
        }

        /**
         * Return true if the value is empty
         * @return true if empty
         */
        public boolean isEmpty() {
            return toString().isEmpty();
        }

        /**
         * Returns true if there is more than one different value
         * @return true if values differ
         */
        public boolean isDifferent() {
            return map.size() > 1;
        }

        /**
         * Simulates an or operator like python has
         * @param v1 a string
         * @param v2 another string
         * @return v1 or v2 like in python
         */
        static String or(String v1, String v2) {
            return (v1 != null && !v1.isEmpty()) ? v1 : v2;
        }
    }

    /**
     * Constructor
     * <p>
     * If a handler is given, all edits go to the model and to the handler immediately, (eg. as soon
     * as the combobox leaves the edited cell).  This mode is used in the properties dialog.
     * <p>
     * If no handler is given you must retrieve the tags with {@link #getTags} at your convenience,
     * (eg. when the user hits an OK button).  This mode is used in the relation edit dialog.
     *
     * @param handler the data handler or null
     */
    public TagTableModel(TaggedHandler handler) {
        this.handler = handler;
    }

    /**
     * Initializes the model from the handler
     *
     * @return this
     */
    public TagTableModel initFromHandler() {
        if (handler != null)
            initFromTaggedCollection(handler.get());
        return this;
    }

    /**
     * Initializes the model from a collection of {@link Tagged}
     *
     * @param taggedCollection the collection of Tagged
     * @return this
     */
    public TagTableModel initFromTaggedCollection(Collection<? extends Tagged> taggedCollection) {
        final boolean displayDiscardableKeys = propDisplayDiscardableKeys.get();
        Set<String> discardableKeys = new HashSet<>(AbstractPrimitive.getDiscardableKeys());

        setRowCount(0);
        for (Tagged tagged : taggedCollection) {
            for (Entry<String, String> e : tagged.getKeys().entrySet()) {
                String key = e.getKey();
                if (displayDiscardableKeys || !discardableKeys.contains(key)) {
                    int row = getRowIndex(key);
                    String value = e.getValue();
                    if (row == -1) {
                        addRow(key, new ValueType(value));
                    } else {
                        getValueType(row).add(value);
                    }
                }
            }
        }
        // account for unset tags
        for (int row = 0; row < getRowCount(); ++row) {
            getValueType(row).fill(taggedCollection.size());
        }

        fireTableDataChanged();
        ensureTags();
        return this;
    }

    /**
     * Initializes the model with a map of key/value pairs
     *
     * @param map the tags of an OSM primitive
     * @return this
     */
    public TagTableModel initFromMap(Map<String, String> map) {
        setRowCount(0);
        map.forEach((k, v) -> addRow(k, new ValueType(v)));
        ensureTags();
        return this;
    }

    /**
     * Returns the TaggingPresetHandler or null
     * @return the handler
     */
    public TaggedHandler getHandler() {
        return handler;
    }

    /**
     * removes all tags in the model
     */
    public void clear() {
        setRowCount(0);
        ensureTags();
    }

    /**
     * Adds a row unconditionally. Use {@link #put} to add or update.
     *
     * @param key the key to add
     * @param value the value to add
     */
    public void addRow(String key, ValueType value) {
        addRow(new Object[]{key, value});
    }

    @Override
    public String getValueAt(int row, int column) {
        if (column == 1)
            return super.getValueAt(row, column).toString();
        return (String) super.getValueAt(row, column);
    }

    /**
     * {@inheritDoc}
     * <p>
     * This function gets called if the TableCellEditor changes data. See
     * {@link javax.swing.JTable#editingStopped}.  If there is a handler attached, all edits will go
     * to the handler too.
     */
    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (rowIndex < 0 || rowIndex >= getRowCount() || columnIndex < 0 || columnIndex >= getColumnCount())
            throw new ArrayIndexOutOfBoundsException();
        String key = getKey(rowIndex);
        if (key == null)
            return;
        ValueType value = getValueType(rowIndex);
        if (columnIndex == 0) {
            // edit the key
            final String newKey = Normalizer.normalize((String) aValue, Normalizer.Form.NFC);
            if (newKey.equals(key))
                return;

            if (keySet().contains(newKey) && !TagEditHelper.warnOverwriteKey (
                    tr("You changed the key from ''{0}'' to ''{1}''.", key, newKey) + "\n" +
                    tr("The new key is already used, overwrite values?"),
                    "overwriteEditKey"))
                return;
            Logging.info("Change tag key: {0}={1} to {2}={3}", key, value, newKey, value);
            super.setValueAt(newKey, rowIndex, columnIndex);
            if (!value.isEmpty()) {
                if (handler != null) {
                    handler.update(key, newKey, value.toString());
                }
                if (!value.isDifferent()) {
                    AutoCompletionManager.rememberUserInput(key, value.toString(), false);
                }
            }
        }
        if (columnIndex == 1) {
            // edit the value
            String newValue = (String) aValue;
            if (newValue.equals(value.toString()))
                return; // no change
            if (newValue.equals(ValueType.DIFFERENT))
                newValue = ""; // never write this
            newValue = Normalizer.normalize(newValue, Normalizer.Form.NFC);
            Logging.info("Change tag value: {0}={1} to {2}={3}", key, value, key, newValue);
            super.setValueAt(new ValueType(newValue), rowIndex, columnIndex);
            if (handler != null) {
                handler.update(key, key, newValue);
            }
            if (!newValue.isEmpty()) {
                AutoCompletionManager.rememberUserInput(key, newValue, false);
            }
        }
        fireTableRowsUpdated(rowIndex, rowIndex);
        ensureTags();
    }

    /**
     * Returns the value(s) of a key
     *
     * @param key the tag key
     * @return the value
     */
    public ValueType get(String key) {
        int row = getRowIndex(key);
        if (row == -1)
            return new ValueType();
        return getValueType(row);
    }

    /**
     * Convenience function that returns the key in a row
     * @param row the index of the row
     * @return the key or null
     */
    public String getKey(int row) {
        return (String) super.getValueAt(row, 0);
    }

    /**
     * Convenience function that returns the values(s) in a row
     * @param row the index of the row
     * @return the values or null
     */
    public ValueType getValueType(int row) {
        return (ValueType) super.getValueAt(row, 1);
    }

    /**
     * Returns all keys currently in the model.
     * @return the set of keys
     */
    public Set<String> keySet() {
        Set<String> keyset = new HashSet<>();
        for (int row = 0; row < getRowCount(); ++row) {
            keyset.add(getKey(row));
        }
        return keyset;
    }

    /**
     * Convenience function that returns the values(s) by key
     * @param key the key
     * @return the values
     */
    public ValueType getValueType(String key) {
        int row = getRowIndex(key);
        if (row == -1)
            return new ValueType();
        return getValueType(getRowIndex(key));
    }

    /**
     * Inserts/updates/deletes a tag.
     *
     * Existing keys are updated. Others are added. A value of {@code null}
     * deletes the key.
     *
     * NOTE: this function does not go through the handler.
     *
     * @param key The key of the tag to insert.
     * @param value The value of the tag to insert.
     */
    public void put(String key, String value) {
        int row = getRowIndex(key);
        if (value != null) {
            value = Utils.removeWhiteSpaces(value);
        }
        if ((value == null || value.isEmpty()) && row != -1) {
            // delete
            removeRow(row);
            return;
        }
        if (row == -1) {
            addRow(key, new ValueType(value));
        } else {
            // do not go thru the handler
            super.setValueAt(new ValueType(value), row, 1);
        }
    }

    /**
     * Inserts/updates/deletes all tags from {@code map}.
     *
     * Existing keys are updated. Others are added. A value of {@code null}
     * deletes the key.
     *
     * @param map a map of tags to insert or update
     */
    public void putAll(Map<String, String> map) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    /**
     * Returns tags.
     * @param keepEmpty {@code true} to keep tags with empty values
     * @return tags
     */
    public Map<String, String> getTags(boolean keepEmpty) {
        Map<String, String> map = new HashMap<>();
        for (int row = 0; row < getRowCount(); ++row) {
            String key = getKey(row);
            if (key.isEmpty())
                continue;
            String value = getValueType(row).toString();
            if (!keepEmpty && value.isEmpty())
                continue;
            map.put(key, value);
        }
        return map;
    }

    /**
     * Returns tags, without empty ones.
     * @return not-empty tags
     */
    public Map<String, String> getTags() {
        return getTags(false);
    }

    /**
     * Makes sure the model includes required tags
     * <p>
     * Override this to make sure a set of required tags are contained in the model, eg. the
     * created_by tag in the upload dialog. Don't delete tags or you will get in a loop.
     */
    public void ensureTags() {
        if (getRowIndex("") == -1) {
            addRow("", new ValueType());
        }
    }

    /**
     * Returns the row index for a given key
     *
     * @param key the key
     * @return the row index in model coordinates or -1
     */
    public int getRowIndex(String key) {
        for (int row = 0; row < getRowCount(); ++row) {
            if (getKey(row).equals(key))
                return row;
        }
        return -1;
    }

    /**
     * Returns the column index for a given column name
     *
     * @param name the coulumn name
     * @return the column index or -1
     */
    public int getColumnIndex(String name) {
        for (int index = 0; index < getColumnCount(); ++index) {
            if (getColumnName(index).equals(name)) {
                return index;
            }
        }
        return -1;
    }

    /**
     * Convert a {@code List<Tag>} into a {@code Map<String, String>}
     * @param tagList the list of tags
     * @return the map
     */
    public static Map<String, String> tagListToMap(List<Tag> tagList) {
        Map<String, String> map = new HashMap<>();
        for (Tag tag : tagList) {
            map.put(tag.getKey(), tag.getValue());
        }
        return map;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return true;
    }

    @Override
    public int getColumnCount() {
        return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
        if (column < 0 || column >= getColumnCount())
            return "";
        return columnNames[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        return String.class;
    }
}
