// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.tagging.ac;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Font;
import java.util.function.Function;

import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import org.openstreetmap.josm.gui.tagging.TagTableModel.ValueType;
import org.openstreetmap.josm.gui.widgets.JosmListCellRenderer;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;

/**
 * A custom list cell renderer for autocompletion items that adds icons and the value count to some
 * items.
 * <p>
 * See also: {@link AutoCompletionPriority#compareTo}
 */
public class AutoCompItemCellRenderer extends JosmListCellRenderer<AutoCompletionItem> {
    Function<String, Integer> valueToCount;
    private static final ImageIcon iconEmpty = ImageProvider.getEmpty(ImageSizes.POPUPMENU);
    private static final ImageIcon iconDataSet = ImageProvider.get("in_dataset", ImageSizes.POPUPMENU);
    private static final ImageIcon iconStandard = ImageProvider.get("in_standard", ImageSizes.POPUPMENU);

    /**
     * Constructs the cell renderer.
     *
     * @param component The component the renderer is attached to. JComboBox or JList.
     * @param renderer The L&amp;F renderer. Usually obtained by calling {@code getRenderer()} on {@code component}.
     * @param valueToCount A function that maps from key to count (or null)
     */
    public AutoCompItemCellRenderer(Component component,
                                    ListCellRenderer<? super AutoCompletionItem> renderer,
                                    Function<String, Integer> valueToCount) {
        super(component, renderer);
        this.valueToCount = valueToCount;
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends AutoCompletionItem> list, AutoCompletionItem value,
                                                int index, boolean isSelected, boolean cellHasFocus) {

        if (value == null)
            value = new AutoCompletionItem(null, AutoCompletionPriority.IS_IN_STANDARD);

        Integer count = (valueToCount == null) ? 0 : valueToCount.apply(value.toString());

        if (value.getValue() == null)
            value = new AutoCompletionItem(ValueType.UNSET, AutoCompletionPriority.IS_IN_STANDARD);

        // if there is a value count add it to the text
        if (count > 0) {
            value = new AutoCompletionItem(tr("{0} ({1})", value.toString(), count), value.getPriority());
        }

        JLabel l = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        l.setIcon(iconEmpty);
        if (value.getPriority().isInDataSet()) {
            l.setIcon(iconDataSet);
        }
        if (value.getPriority().isInStandard()) {
            l.setIcon(iconStandard);
        }
        l.setComponentOrientation(component.getComponentOrientation());
        if (count > 0) {
            l.setFont(l.getFont().deriveFont(Font.ITALIC + Font.BOLD));
        }
        return l;
    }
}
