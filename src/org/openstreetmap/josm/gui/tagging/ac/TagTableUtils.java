// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.ac;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Tagged;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.tagging.ac.AutoCompItemCellRenderer;
import org.openstreetmap.josm.data.tagging.ac.AutoCompletionItem;
import org.openstreetmap.josm.data.tagging.ac.AutoCompletionPriority;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.tagging.TagTableModel;
import org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetType;

/**
 * Contains various utilities for TagTable.
 */
public class TagTableUtils {
    private final TagTableModel tagTableModel;
    Supplier<String> keySupplier;

    private AutoCompletionManager manager = null;
    private Set<TaggingPresetType> types;
    private Set<String> categories = new HashSet<>();

    /** Autocomplete keys by default */
    public static final BooleanProperty AUTOCOMPLETE_KEYS = new BooleanProperty("properties.autocomplete-keys", true);
    /** Autocomplete values by default */
    public static final BooleanProperty AUTOCOMPLETE_VALUES = new BooleanProperty("properties.autocomplete-values", true);

    /**
     * Constructor
     * @param tagTableModel the associated tag table model
     * @param keySupplier a supplies the key context
     */
    public TagTableUtils(TagTableModel tagTableModel, Supplier<String> keySupplier) {
        this.tagTableModel = tagTableModel;
        this.keySupplier = keySupplier;

        DataSet dataSet = MainApplication.getLayerManager().getActiveDataSet();
        if (dataSet != null) {
            setManager(AutoCompletionManager.of(dataSet));
        }
    }

    /**
     * Returns a configured key editor component.
     * @param keyAutoCompListener an autocomplistener or null
     * @return a key editor component
     */
    public AutoCompComboBox<AutoCompletionItem> getKeyEditor(DefaultAutoCompListener<AutoCompletionItem> keyAutoCompListener) {
        AutoCompComboBox<AutoCompletionItem> keyEditor = new AutoCompComboBox<>();
        if (keyAutoCompListener == null)
            keyAutoCompListener = new KeyAutoCompListener();
        keyEditor.setAutocompleteEnabled(AUTOCOMPLETE_KEYS.get());
        keyEditor.getEditorComponent().setMaxTextLength(256);
        keyEditor.getEditorComponent().enableUndoRedo(false);
        keyEditor.getEditorComponent().addAutoCompListener(keyAutoCompListener);
        keyEditor.addPopupMenuListener(keyAutoCompListener);
        keyEditor.setRenderer(new AutoCompItemCellRenderer(keyEditor, keyEditor.getRenderer(), null));
        keyEditor.getModel().setComparator(AutoCompletionManager.PRIORITY_COMPARATOR);
        return keyEditor;
    }

    /**
     * Returns a configured value editor component.
     * @param valueAutoCompListener an autocomplistener or null
     * @return a value editor component
     */
    public AutoCompComboBox<AutoCompletionItem> getValueEditor(DefaultAutoCompListener<AutoCompletionItem> valueAutoCompListener) {
        AutoCompComboBox<AutoCompletionItem> valueEditor = new AutoCompComboBox<>();
        if (valueAutoCompListener == null)
            valueAutoCompListener = new ValueAutoCompListener();
        valueEditor.setAutocompleteEnabled(AUTOCOMPLETE_VALUES.get());
        valueEditor.getEditorComponent().setMaxTextLength(-1);
        valueEditor.getEditorComponent().enableUndoRedo(false);
        valueEditor.getEditorComponent().addAutoCompListener(valueAutoCompListener);
        valueEditor.addPopupMenuListener(valueAutoCompListener);
        valueEditor.setRenderer(new AutoCompItemCellRenderer(valueEditor, valueEditor.getRenderer(), new ValueToCount()));
        valueEditor.getModel().setComparator(AutoCompletionManager.PRIORITY_COMPARATOR);
        return valueEditor;
    }

    /**
     * Fills the combobox dropdown in the 'key' column of a tag table
     */
    class KeyAutoCompListener extends DefaultAutoCompListener<AutoCompletionItem> {
        @Override
        protected void updateAutoCompModel(AutoCompComboBoxModel<AutoCompletionItem> model) {
            Map<String, String> tags = tagTableModel.getTags();
            Map<String, AutoCompletionPriority> map;

            if (tags.isEmpty()) {
                map = AutoCompletionManager.toMap(manager.getPresetsFirstKey(types, tags), AutoCompletionPriority.IS_IN_STANDARD);
            } else {
                map = AutoCompletionManager.merge(
                    // standard
                    AutoCompletionManager.toMap(manager.getPresetsAllKeys(types, tags), AutoCompletionPriority.IS_IN_STANDARD),
                    // dataset
                    AutoCompletionManager.toMap(manager.getDataSetKeys(categories), AutoCompletionPriority.IS_IN_DATASET),
                    // history
                    AutoCompletionManager.toMap(AutoCompletionManager.getUserInputKeys(), AutoCompletionPriority.UNKNOWN)
                );
            }

            // don't suggest already present keys
            tags.forEach((k, v) -> map.remove(k));

            model.replaceAllElements(AutoCompletionManager.toList(map, AutoCompletionManager.PRIORITY_COMPARATOR));
        }
    }

    /**
     * Fills the combobox dropdown in the 'value' column of a tag table
     */
    class ValueAutoCompListener extends DefaultAutoCompListener<AutoCompletionItem> {
        @Override
        protected void updateAutoCompModel(AutoCompComboBoxModel<AutoCompletionItem> model) {
            String key = keySupplier.get();
            Map<String, String> tags = tagTableModel.getTags();

            Map<String, AutoCompletionPriority> map = AutoCompletionManager.merge(
                // standard
                AutoCompletionManager.toMap(manager.getPresetValues(types, tags, key), AutoCompletionPriority.IS_IN_STANDARD),
                // dataset
                AutoCompletionManager.toMap(manager.getDataSetValues(categories, key), AutoCompletionPriority.IS_IN_DATASET),
                // selection
                AutoCompletionManager.toMap(tagTableModel.getValueType(key).values(), AutoCompletionPriority.IS_IN_SELECTION),
                // history
                AutoCompletionManager.toMap(AutoCompletionManager.getUserInputValues(key), AutoCompletionPriority.UNKNOWN)
            );

            model.replaceAllElements(AutoCompletionManager.toList(map, AutoCompletionManager.PRIORITY_COMPARATOR));
        }
    }

    /**
     * Returns count for the edited key and given value.
     * Used in the suggestion dropdown.
     */
    public class ValueToCount implements ToIntFunction<String> {
        @Override
        public int applyAsInt(String value) {
            return tagTableModel.getValueType(keySupplier.get()).getCount(value);
        }
    }

    /**
     * Sets the key supplier function.
     * <p>
     * The key supplier should return the key of the tag that is being edited.
     *
     * @param keySupplier the key supplier function
     */
    public void setKeySupplier(Supplier<String> keySupplier) {
        this.keySupplier = keySupplier;
    }

    /**
     * Sets the autocompletion manager
     * @param manager the autocompletion manager
     */
    public void setManager(AutoCompletionManager manager) {
        this.manager = manager;
    }

    /**
     * Returns the autocompletion manager
     * @return the autocompletion manager
     */
    public AutoCompletionManager getManager() {
        return this.manager;
    }

    /**
     * Returns the tagging preset types
     * @return the tagging preset types
     */
    public Set<TaggingPresetType> getTypes() {
        return types;
    }

    /**
     * Sets the tagging preset types.
     * @param types the types
     */
    public void setTypes(Set<TaggingPresetType> types) {
        this.types = types;
    }

    /**
     * Sets the tagging preset types for a selection of primitives.
     * @param selection the selected primitives
     */
    public void setTypes(Collection<OsmPrimitive> selection) {
        types = EnumSet.noneOf(TaggingPresetType.class);
        categories = new HashSet<>();
        for (OsmPrimitive osm : selection) {
            types.add(TaggingPresetType.forPrimitive(osm));
            categories.addAll(AutoCompletionManager.classifyPrimitive(osm.getKeys()));
        }
    }

    /**
     * Sets the tagging preset types for a data handler.
     * @param handler the data handler
     */
    public void setTypes(TaggedHandler handler) {
        types = EnumSet.noneOf(TaggingPresetType.class);
        categories = new HashSet<>();
        for (Tagged tag : handler.get()) {
            if (tag instanceof IPrimitive) {
                types.add(TaggingPresetType.forPrimitive((IPrimitive) tag));
            }
            categories.addAll(AutoCompletionManager.classifyPrimitive(tag.getKeys()));
        }
    }
}
