// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JScrollPane;

import org.openstreetmap.josm.gui.widgets.OrientationAction;
import org.openstreetmap.josm.tools.GBC;

/**
 * Multi-select list type.
 */
final class MultiSelect extends ComboMultiSelect {
    /**
     * Number of rows to display (positive integer, optional).
     */
    private final int rows;

    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    private MultiSelect(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        rows = Integer.parseInt(attributes.getOrDefault("rows", "0"));
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on invalid attributes
     */
    static MultiSelect fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new MultiSelect(attributes);
    }

    @Override
    String getDefaultDelimiter() {
        return ";";
    }

    private void addEntry(DefaultListModel<PresetListEntry.Instance> model, PresetListEntry.Instance instance) {
        if (!seenValues.containsKey(instance.getValue())) {
            model.addElement(instance);
            seenValues.put(instance.getValue(), instance);
        }
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        TaggingPreset.Instance presetInstance = parentInstance.getPresetInstance();
        Usage usage = Usage.determineTextUsage(presetInstance.getSelected(), key);
        seenValues.clear();

        DefaultListModel<PresetListEntry.Instance> model = new DefaultListModel<>();
        JList<PresetListEntry.Instance> list = new JList<>(model);
        Instance instance = new Instance(this, parentInstance, list, usage);

        // disable if the selected primitives have different values
        list.setEnabled(usage.hasUniqueValue() || usage.unused());

        // Add values from the preset.
        presetListEntries.forEach(e -> addEntry(model, e.newInstance(instance)));

        parentInstance.putInstance(this, instance);

        // Add all values used in the selected primitives. This also adds custom values and makes
        // sure we won't lose them.
        usage.splitValues();
        for (String value: usage.map.keySet()) {
            addEntry(model, new PresetListEntry(this, value).newInstance(instance));
        }

        instance.setValue(instance.getInitialValue());

        PresetListEntry.CellRenderer renderer = new PresetListEntry.CellRenderer(list, list.getCellRenderer(), 200);
        list.setCellRenderer(renderer);
        JLabel label = addLabel(p);
        label.setLabelFor(list);
        JScrollPane sp = new JScrollPane(list);

        if (rows > 0) {
            list.setVisibleRowCount(rows);
            // setVisibleRowCount() only works when all cells have the same height, but sometimes we
            // have icons of different sizes. Calculate the size of the first {@code rows} entries
            // and size the scrollpane accordingly.
            Rectangle r = list.getCellBounds(0, Math.min(rows, model.size() - 1));
            if (r != null) {
                Insets insets = list.getInsets();
                r.width += insets.left + insets.right;
                r.height += insets.top + insets.bottom;
                insets = sp.getInsets();
                r.width += insets.left + insets.right;
                r.height += insets.top + insets.bottom;
                sp.setPreferredSize(new Dimension(r.width, r.height));
            }
        }
        p.add(sp, GBC.eol().fill(GridBagConstraints.HORIZONTAL));

        list.addListSelectionListener(l -> presetInstance.fireChangeEvent(instance));
        list.setToolTipText(getKeyTooltipText());
        list.applyComponentOrientation(OrientationAction.getValueOrientation(key));

        seenValues.clear();
        return true;
    }

    @Override
    boolean isInteractive() {
        return true;
    }

    class Instance extends ComboMultiSelect.Instance {
        JList<PresetListEntry.Instance> list;

        Instance(Item item, Composite.Instance parent, JList<PresetListEntry.Instance> list, Usage usage) {
            super(item, parent, list, usage);
            this.list = list;
        }

        @Override
        PresetListEntry.Instance getSelectedItem() {
            return new PresetListEntry(MultiSelect.this, list.getSelectedValuesList()
                .stream().map(e -> e.getValue()).distinct().sorted()
                .collect(Collectors.joining(";"))).newInstance(this);
        }

        /**
         * Inserts missing list entries, then selects list entries.
         */
        @Override
        void setValue(String newValue) {
            if (!newValue.isEmpty() && !DIFFERENT.equals(newValue)) {
                for (String value : newValue.split(";", -1)) {
                    PresetListEntry.Instance pi = new PresetListEntry(MultiSelect.this, value).newInstance(this);
                    DefaultListModel<PresetListEntry.Instance> model =
                        (DefaultListModel<PresetListEntry.Instance>) list.getModel();
                    addEntry(model, pi);
                    int i = model.indexOf(pi);
                    list.addSelectionInterval(i, i);
                }
            }
        }
    }
}
