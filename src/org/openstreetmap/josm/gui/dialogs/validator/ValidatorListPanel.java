// License: GPL. For details, see LICENSE file.

package org.openstreetmap.josm.gui.dialogs.validator;

import java.awt.Component;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.SwingConstants;

import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.TestError;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Utils;

public class ValidatorListPanel extends JList<TestError> {
    public ValidatorListPanel() {
        super(new DefaultListModel<>());
        this.setCellRenderer(new ValidatorListCellRenderer());
        this.addComponentListener(new ComponentListener(this));
    }

    public void setErrors(List<TestError> errors) {
        DefaultListModel<TestError> model = ((DefaultListModel<TestError>) getModel());
        model.clear();
        if (errors != null)
            model.addAll(errors);
    }

    /**
     * Cell renderer for displaying a simple list of errors
     */
    public static class ValidatorListCellRenderer extends DefaultListCellRenderer {
        int layoutWidth;

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
        public void setLayoutWidth(int width) {
            if (width <= 0)
                this.layoutWidth = 180;
            else
                this.layoutWidth = width - 20;
        }

        /**
         * Returns the contents displayed in the list.
         *
         * @param error the error to display
         * @param width the width in px
         * @return HTML formatted contents
         */
        String getDisplayText(TestError error, int width) {
            // RTL not supported in HTML. See: http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4866977
            return String.format("<html><div style=\"width: %d\"><b>%s</b><p>%s</p></div></html>",
                    width,
                    Utils.escapeReservedCharactersHTML(error.getMessage()),
                    Utils.escapeReservedCharactersHTML(error.getDescription()));
        }

        @Override
        public Component getListCellRendererComponent(
            JList<?> list,
            Object value,
            int index,
            boolean isSelected,
            boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            setVerticalTextPosition(SwingConstants.TOP);
            TestError error = (TestError) value;
            Severity s = error.getSeverity();
            setIcon(ImageProvider.get("data", s.getIcon()));
            setText(getDisplayText(error, layoutWidth));

            return this;
        }
    }

    static class ComponentListener extends ComponentAdapter {
        JList<TestError> list;

        ComponentListener(JList<TestError> list) {
            this.list = list;
        }

        @Override
        public void componentResized(ComponentEvent e) {
            // Make multi-line JLabels the correct size
            // Only needed if there is any short_description
            JComponent component = (JComponent) e.getSource();
            int width = component.getWidth();
            if (width == 0)
                width = 200;
            Insets insets = component.getInsets();
            width -= insets.left + insets.right + 10;
            ValidatorListCellRenderer renderer = (ValidatorListCellRenderer) list.getCellRenderer();
            renderer.setLayoutWidth(width);
            list.setCellRenderer(null); // needed to make prop change fire
            list.setCellRenderer(renderer);
        }
    }
}
