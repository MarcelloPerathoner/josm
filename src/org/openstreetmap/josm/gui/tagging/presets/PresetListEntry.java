// License: GPL. For details, see LICENSE file.

package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trc;

import java.awt.Component;
import java.awt.Font;
import java.util.Map;
import java.util.Objects;

import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import org.openstreetmap.josm.gui.widgets.JosmListCellRenderer;
import org.openstreetmap.josm.tools.AlphanumComparator;
import org.openstreetmap.josm.tools.Utils;

/**
 * Preset list entry.
 * <p>
 * Used for controls that offer a list of items to choose from like {@link Combo} and
 * {@link MultiSelect}.
 */
final class PresetListEntry extends Item {
    /** Used to display an entry matching several different values. */
    static final PresetListEntry ENTRY_DIFFERENT = new PresetListEntry(null, InteractiveItem.DIFFERENT);
    /** Used to display an empty entry used to clear values. */
    static final PresetListEntry ENTRY_EMPTY = new PresetListEntry(null, "");

    /**
     * This is the value that is going to be written to the tag on the selected primitive(s). Except
     * when the value is {@code "<different>"}, which is never written, or the value is empty, which
     * deletes the tag.  {@code value} is never translated.
     */
    private final String value;
    /** Text displayed to the user instead of {@link #value}. */
    private final String displayValue;
    /** Text to be displayed below {@link #displayValue} in the combobox list. */
    private final String shortDescription;
    /** The location of icon file to display */
    private final String icon;
    /** The size of displayed icon. If not set, default is size from icon file */
    private final int iconSize;

    /** The localized version of {@link #displayValue}. */
    private final String localeDisplayValue;
    /** The finallocalized version of {@link #shortDescription}. */
    private final String localeShortDescription;
    /** Tha cached icon */
    private ImageIcon cachedIcon;

    /**
     * Private constructor. Use {@link #fromXML} instead.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on attribute error
     */
    private PresetListEntry(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        value = attributes.get("value");
        displayValue = attributes.get("display_value");
        localeDisplayValue = attributes.get("locale_display_value");
        shortDescription = attributes.get("short_description");
        localeShortDescription = attributes.get("locale_short_description");
        icon = attributes.get("icon");
        iconSize = Integer.parseInt(attributes.getOrDefault("icon_size", "0"));
    }

    /**
     * Create this class from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the new instance
     * @throws IllegalArgumentException on invalid attributes
     */
    static PresetListEntry fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new PresetListEntry(attributes);
    }

    /**
     * Convenience constructor.  Constructs a new {@code PresetListEntry}, initialized with a value.
     *
     * @param value value
     * @param cms the ComboMultiSelect
     */
    PresetListEntry(ComboMultiSelect cms, String value) {
        super(ItemFactory.attributesToMap());
        this.value = value;
        this.displayValue = value;
        this.localeDisplayValue = value;
        this.shortDescription = "";
        this.localeShortDescription = "";
        this.icon = null;
        this.iconSize = 0;
    }

    /**
     * Returns the value
     * @return the value
     */
    String getValue() {
        return value;
    }

    /**
     * Returns the entry icon, if any.
     * @return the entry icon, or {@code null}
     */
    ImageIcon getIcon() {
        if (icon != null && cachedIcon == null) {
            cachedIcon = TaggingPresetUtils.loadImageIcon(icon, TaggingPresetReader.getZipIcons(), iconSize);
        }
        return cachedIcon;
    }

    /**
     * Returns the contents displayed in the current item view.
     * @param cms the ComboMultiSelect
     * @return the value to display
     */
    String getDisplayValue(ComboMultiSelect cms) {
        if (cms.valuesNoI18n) {
            return Utils.firstNonNull(PresetListEntry.this.value, " ");
        }
        return Utils.firstNonNull(
            localeDisplayValue,
            tr(displayValue),
            trc(cms.valuesContext, value),
            " "
        );
    }

    /**
     * Returns the short description to display.
     * @return the short description to display
     */
    String getShortDescription() {
        return Utils.firstNonNull(
            localeShortDescription,
            tr(shortDescription),
            ""
        );
    }

    /**
     * Returns the tooltip for this entry.
     * @param key the tag key
     * @return the tooltip
     */
    String getToolTipText(String key) {
        if (this.equals(ENTRY_DIFFERENT)) {
            return tr("Keeps the original values of the selected objects unchanged.");
        }
        if (value != null && !value.isEmpty()) {
            return tr("Sets the key ''{0}'' to the value ''{1}''.", key, value);
        }
        return tr("Clears the key ''{0}''.", key);
    }

    @Override
    boolean addToPanel(JComponent p, Composite.Instance parentInstance) {
        return false;
    }

    /**
     * Creates a new instance
     * @param cmsInstance The ComboMultiSelect.Instance
     * @return the new instance
     */
    Instance newInstance(ComboMultiSelect.Instance cmsInstance) {
        return new Instance(cmsInstance);
    }

    class Instance implements Comparable<Instance> {
        private String displayValue;
        private String shortDescription;
        private String toolTip;
        private ImageIcon icon;
        private Usage usage;

        Instance(ComboMultiSelect.Instance cmsInstance) {
            this.usage = cmsInstance.usage;
            this.icon = PresetListEntry.this.getIcon();
            ComboMultiSelect cms = cmsInstance.getTemplate();
            this.toolTip = PresetListEntry.this.getToolTipText(cms.getKey());

            displayValue = getDisplayValue(cms);
            shortDescription = getShortDescription();
        }

        Instance(ComboMultiSelect.Instance cmsInstance, String value) {
            this.usage = cmsInstance.usage;
            this.displayValue = value;
        }

        String getValue() {
            return PresetListEntry.this.getValue();
        }

        @Override
        public int compareTo(Instance o) {
            return AlphanumComparator.getInstance().compare(this.displayValue, o.displayValue);
        }

        // toString is mainly used to initialize the Editor
        @Override
        public String toString() {
            if (this.getValue().equals(InteractiveItem.DIFFERENT))
                return displayValue;
            return displayValue.replaceAll("\\s*<.*>\\s*", " "); // remove additional markup, e.g. <br>
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PresetListEntry.Instance that = (PresetListEntry.Instance) o;
            return Objects.equals(getValue(), that.getValue());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getValue());
        }

        /**
         * Returns how many selected primitives had this value set.
         * @return see above
         */
        int getCount() {
            Integer count = usage.map.get(value);
            return count == null ? 0 : count;
        }

        /**
         * Returns the contents displayed in the dropdown list.
         *
         * This is the contents that would be displayed in the current view plus a short description to
         * aid the user.  The whole contents is wrapped to {@code width}.
         *
         * @param width the width in px
         * @return HTML formatted contents
         */
        String getListDisplay(int width) {
            Integer count = getCount();
            String result = displayValue;

            if (count > 0 && usage.getSelectedCount() > 1) {
                result = tr("{0} ({1})", displayValue, count);
            }

            if (this.getValue().equals(InteractiveItem.DIFFERENT)) {
                return "<html><b>" + Utils.escapeReservedCharactersHTML(displayValue) + "</b></html>";
            }

            if (shortDescription.isEmpty()) {
                // avoids a collapsed list entry if value == ""
                if (result.isEmpty()) {
                    return " ";
                }
                return result;
            }

            // RTL not supported in HTML. See: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4866977
            return String.format("<html><div style=\"width: %d\"><b>%s</b><p style=\"padding-left: 10\">%s</p></div></html>",
                    width,
                    result,
                    Utils.escapeReservedCharactersHTML(shortDescription));
        }
    }

    /**
     * A list cell renderer that paints a short text in the current value pane and and a longer text
     * in the dropdown list.
     */
    static class CellRenderer extends JosmListCellRenderer<PresetListEntry.Instance> {
        int width;

        CellRenderer(Component component, ListCellRenderer<? super PresetListEntry.Instance> renderer, int width) {
            super(component, renderer);
            setWidth(width);
        }

        /**
         * Sets the width to format the dropdown list to
         *
         * Note: This is not the width of the list, but the width to which we format any multi-line
         * label in the list.  We cannot use the list's width because at the time the combobox
         * measures its items, it is not guaranteed that the list is already sized, the combobox may
         * not even be layed out yet.  Set this to {@code combobox.getWidth()}
         *
         * @param width the width
         */
        public void setWidth(int width) {
            if (width <= 0)
                width = 200;
            this.width = width - 20;
        }

        @Override
        public JLabel getListCellRendererComponent(
            JList<? extends PresetListEntry.Instance> list, PresetListEntry.Instance value,
                int index, boolean isSelected, boolean cellHasFocus) {

            JLabel l = (JLabel) renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            l.setComponentOrientation(component.getComponentOrientation());
            if (index != -1) {
                // index -1 is set when measuring the size of the cell and when painting the
                // editor-ersatz of a readonly combobox. fixes #6157
                l.setText(value.getListDisplay(width));
            }
            if (value.getCount() > 0) {
                l.setFont(l.getFont().deriveFont(Font.ITALIC + Font.BOLD));
            }
            l.setIcon(value.icon);
            l.setToolTipText(value.toolTip);
            return l;
        }
    }
}
