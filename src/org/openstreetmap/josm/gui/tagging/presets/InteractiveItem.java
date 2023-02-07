// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JComponent;

import org.openstreetmap.josm.data.preferences.BooleanProperty;

/**
 * A preset item through which tags are edited.
 */
abstract class InteractiveItem extends KeyedItem {

    /** Last value of each key used in presets, used for prefilling corresponding fields */
    static final Map<String, String> LAST_VALUES = new HashMap<>();
    /** True if the default value should also be set on primitives that already have tags.  */
    static final BooleanProperty PROP_FILL_DEFAULT = new BooleanProperty("taggingpreset.fill-default-for-tagged-primitives", false);
    /** The constant value {@code "<different>"}. */
    static final String DIFFERENT = "<different>";
    /** Translation of {@code "<different>"}. */
    static final String DIFFERENT_I18N = tr(DIFFERENT);

    /** The length of the text box (number of characters allowed). */
    final int length;
    /**
     * Whether the last value is used as default.
     * <ul>
     * <li>false = 0: do not use the last value as default
     * <li>true = 1: use the last value as default for primitives without any tag
     * <li>force = 2: use the last value as default for all primitives.
     * </ul>
     * Default is "false".
     */
    final int useLastAsDefault;

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    InteractiveItem(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        useLastAsDefault = getUseLastAsDefault(attributes.get("use_last_as_default"));
        length = Integer.parseInt(attributes.getOrDefault("length", "0"));
    }

    abstract class Instance extends KeyedItem.Instance {
        /** The main component the user will interact with */
        private final JComponent component;

        Instance(Item item, Composite.Instance parent, JComponent component) {
            super(item, parent);
            this.component = component;
        }

        @Override
        public void highlight(Color color, String tooltip) {
            component.setToolTipText(tooltip == null ? getKeyTooltipText() : tooltip);
            if (color != null)
                getPresetInstance().getDialog().outline(component, color);
        }
    }

    /**
     * Sets whether the last value is used as default.
     * @param v Using "force" (2) enforces this behaviour also for already tagged objects. Default is "false" (0).
     * @return the value as int
     */
    private int getUseLastAsDefault(String v) {
        if ("force".equals(v)) {
            return 2;
        } else if ("true".equals(v)) {
            return 1;
        } else {
            return 0;
        }
    }

    /**
     * Returns true if the last entered value should be used as default.
     * <p>
     * Note: never used in {@code defaultpresets.xml}.
     *
     * @return true if the last entered value should be used as default.
     */
    boolean isUseLastAsDefault() {
        return useLastAsDefault > 0;
    }

    /**
     * Returns true if the last entered value should be used as default also on primitives that
     * already have tags.
     * <p>
     * Note: used for {@code addr:*} tags in {@code defaultpresets.xml}.
     *
     * @return true if see above
     */
    boolean isForceUseLastAsDefault() {
        return useLastAsDefault == 2;
    }

    @Override
    public String toString() {
        return "InteractiveItem [key=" + key + ", text=" + text
                + ", text_context=" + textContext + ", match=" + matchType
                + ']';
    }
}
