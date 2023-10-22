// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.tagging.ac;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Objects;

/**
 * Represents an immutable entry in the set of auto completion values.
 * <p>
 * An AutoCompletionItem has a <em>priority</em> and a <em>value</em>.
 * <p>
 * The priority helps to sort the auto completion items according to their importance. For instance,
 * in an auto completion set for tag names, standard tag names would be assigned a higher priority
 * than arbitrary tag names present in the current data set. There are three priority levels,
 * {@link AutoCompletionPriority}.
 * <p>
 * The value is a string which will be displayed in the auto completion list.
 *
 * @since 12859 (copied from {@code gui.tagging.ac.AutoCompletionListItem})
 */
public class AutoCompletionItem {
    public static final String UNSET = "<unset>";
    /*
     * The string shown to the user instead of the empty string if the value is
     * unset in one or more primitives.
     */
    public static final String UNSET_I18N = tr("<unset>"); // NOSONAR leave this literal string for translation

    /** the priority of this item */
    private final AutoCompletionPriority priority;
    /** the value of this item */
    private final String value;

    /**
     * Constructs a new {@code AutoCompletionItem} with the given value and priority.
     * @param value The value
     * @param priority The priority
     */
    public AutoCompletionItem(String value, AutoCompletionPriority priority) {
        this.value = value;
        this.priority = priority;
    }

    /**
     * Constructs a new {@code AutoCompletionItem} with the given value and unknown priority.
     * @param value The value
     */
    public AutoCompletionItem(String value) {
        this(value, AutoCompletionPriority.UNKNOWN);
    }

    /**
     * Returns the value.
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * Returns the value cooked for user display.
     * <p>
     * Note: Here we return the value instead of a representation of the inner object
     * state because both
     * {@link javax.swing.plaf.basic.BasicComboBoxEditor#setItem(Object)} and
     * {@link javax.swing.DefaultListCellRenderer#getListCellRendererComponent} expect
     * it this way.  There is no need for specialized Editor and CellRenderer classes if
     * we just want to display the value.
     */
    @Override
    public String toString() {
        return value != null ? value : UNSET_I18N;
    }

    /**
     * Returns the priority.
     * @return the priority
     */
    public AutoCompletionPriority getPriority() {
        return priority;
    }

    @Override
    public int hashCode() {
        return Objects.hash(priority, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof AutoCompletionItem))
            return false;
        final AutoCompletionItem other = (AutoCompletionItem) obj;
        if (value == null ? other.value != null : !value.equals(other.value))
            return false;
        return priority == null ? other.priority == null : priority.equals(other.priority);
    }
}
