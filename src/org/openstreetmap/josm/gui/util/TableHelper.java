// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.util;

import java.awt.Component;
import java.awt.Font;
import java.util.stream.IntStream;

import javax.swing.Icon;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import org.openstreetmap.josm.gui.dialogs.IEnabledStateUpdating;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * The class that provide common JTable customization methods
 * @since 5785
 */
public final class TableHelper {

    private TableHelper() {
        // Hide default constructor for utils classes
    }

    /**
     * Wires <code>listener</code> to <code>listSelectionModel</code> in such a way, that
     * <code>listener</code> receives a {@link IEnabledStateUpdating#updateEnabledState()}
     * on every {@link ListSelectionEvent}.
     *
     * @param listener  the listener
     * @param listSelectionModel  the source emitting {@link ListSelectionEvent}s
     * @since 15226
     */
    public static void adaptTo(final IEnabledStateUpdating listener, ListSelectionModel listSelectionModel) {
        listSelectionModel.addListSelectionListener(e -> listener.updateEnabledState());
    }

    /**
     * Wires <code>listener</code> to <code>listModel</code> in such a way, that
     * <code>listener</code> receives a {@link IEnabledStateUpdating#updateEnabledState()}
     * on every {@link ListDataEvent}.
     *
     * @param listener the listener
     * @param listModel the source emitting {@link ListDataEvent}s
     * @since 15226
     */
    public static void adaptTo(final IEnabledStateUpdating listener, AbstractTableModel listModel) {
        listModel.addTableModelListener(e -> listener.updateEnabledState());
    }

    static int getColumnHeaderWidth(JTable tbl, int col) {
        TableColumn tableColumn = tbl.getColumnModel().getColumn(col);
        TableCellRenderer renderer = tableColumn.getHeaderRenderer();

        if (renderer == null && tbl.getTableHeader() != null)
            renderer = tbl.getTableHeader().getDefaultRenderer();

        if (renderer == null)
            return 0;

        Component c = renderer.getTableCellRendererComponent(tbl, tableColumn.getHeaderValue(), false, false, -1, col);
        return c.getPreferredSize().width;
    }

    static int getMaxWidth(JTable tbl, int col) {
        int maxwidth = getColumnHeaderWidth(tbl, col);
        for (int row = 0; row < tbl.getRowCount(); row++) {
            TableCellRenderer tcr = tbl.getCellRenderer(row, col);
            Object val = tbl.getValueAt(row, col);
            Component comp = tcr.getTableCellRendererComponent(tbl, val, false, false, row, col);
            maxwidth = Math.max(comp.getPreferredSize().width, maxwidth);
        }
        return maxwidth;
    }

    /**
     * adjust the preferred width of column col to the maximum preferred width of the cells (including header)
     * @param tbl table
     * @param col column index
     * @param resizable if true, resizing is allowed
     * @since 15176
     */
    public static void adjustColumnWidth(JTable tbl, int col, boolean resizable) {
        int maxwidth = getMaxWidth(tbl, col);
        TableColumn column = tbl.getColumnModel().getColumn(col);
        column.setPreferredWidth(maxwidth);
        column.setResizable(resizable);
        if (!resizable) {
            column.setMaxWidth(maxwidth);
        }
    }

    /**
     * adjust the preferred width of column col to the maximum preferred width of the cells (including header)
     * requires JTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
     * @param tbl table
     * @param col column index
     * @param maxColumnWidth maximum column width
     */
    public static void adjustColumnWidth(JTable tbl, int col, int maxColumnWidth) {
        int maxwidth = getMaxWidth(tbl, col);
        tbl.getColumnModel().getColumn(col).setPreferredWidth(Math.min(maxwidth+10, maxColumnWidth));
    }

    /**
     * adjust the table's columns to fit their content best
     * requires JTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
     * @param tbl table
     * @since 14476
     */
    public static void computeColumnsWidth(JTable tbl) {
        for (int column = 0; column < tbl.getColumnCount(); column++) {
            adjustColumnWidth(tbl, column, Integer.MAX_VALUE);
        }
    }

    /**
     * Returns an array of all of the selected indices in the selection model, in increasing order.
     * Unfortunately this method is not available in OpenJDK before version 11, see
     * https://bugs.openjdk.java.net/browse/JDK-8199395
     *
     * To be removed when we switch to Java 11 or later.
     *
     * @param selectionModel list selection model.
     *
     * @return all of the selected indices, in increasing order,
     *         or an empty array if nothing is selected
     * @see #selectedIndices(ListSelectionModel)
     * @since 15226
     */
    public static int[] getSelectedIndices(ListSelectionModel selectionModel) {
        return selectedIndices(selectionModel).toArray();
    }

    /**
     * Returns a stream of all of the selected indices in the selection model, in increasing order.
     *
     * @param selectionModel list selection model.
     *
     * @return all of the selected indices, in increasing order,
     *         or an empty stream if nothing is selected
     * @since 17773
     */
    public static IntStream selectedIndices(ListSelectionModel selectionModel) {
        if (selectionModel.isSelectionEmpty()) {
            return IntStream.empty();
        }
        return IntStream.rangeClosed(selectionModel.getMinSelectionIndex(), selectionModel.getMaxSelectionIndex())
                .filter(selectionModel::isSelectedIndex);
    }

    /**
     * Selects the given indices in the selection model
     * @param selectionModel list selection model.
     * @param indices the indices to select
     * @see ListSelectionModel#addSelectionInterval(int, int)
     * @since 16601
     */
    public static void setSelectedIndices(ListSelectionModel selectionModel, IntStream indices) {
        selectionModel.setValueIsAdjusting(true);
        selectionModel.clearSelection();
        indices.filter(i -> i >= 0).forEach(i -> selectionModel.addSelectionInterval(i, i));
        selectionModel.setValueIsAdjusting(false);
    }

    /**
     * Sets the table font size based on the font scaling from the preferences
     * @param table the table
     * @param parent the parent component used for determining the preference key
     * @see JTable#setFont(Font)
     * @see JTable#setRowHeight(int)
     * @see javax.swing.plaf.basic.BasicTableUI#installDefaults()
     */
    public static void setFont(JTable table, Class<? extends Component> parent) {
        double fontFactor = Config.getPref().getDouble("gui.scale.table.font",
                Config.getPref().getDouble("gui.scale.table." + parent.getSimpleName() + ".font", 1.0));

        Font font = table.getFont();
        if (fontFactor != 1.0) {
            table.setFont(font.deriveFont((float) (font.getSize2D() * fontFactor)));
        }
        // According to javax.swing.plaf.basic.BasicTableUI.installDefaults: "If the
        // developer changes the font, it's there[sic!] responsability[sic!] to update
        // the row height."
        setRowHeight(table);
    }

    /**
     * Sets an approximate row height for all table rows.
     * <p>
     * This is a fast and "close-enough" approach to setting the row height.  To set the
     * exact row height you'd have to render and measure all cells.  The row height is
     * set according to the font size alone.
     *
     * @param table the table with the font already set
     * @see #setRowHeights
     */
    public static void setRowHeight(JTable table) {
        int fontHeight = table.getFontMetrics(table.getFont()).getHeight();
        table.setRowHeight(fontHeight + table.getRowMargin());
    }

    /**
     * Sets an approximate row height for all table rows.
     * <p>
     * This is a fast and "close-enough" approach to setting the row height.  To set the
     * exact row height you'd have to render and measure all cells.
     * <p>
     * The given icon should be of the same size as the icons expected to populate the
     * table rows. The row height will be set to accomodate the font and the icon.
     *
     * @param table the table with the font already set
     * @param prototypeIcon an icon of the same size as those used in the table (or null)
     * @see #setRowHeights
     */
    public static void setRowHeight(JTable table, Icon prototypeIcon) {
        int fontHeight = table.getFontMetrics(table.getFont()).getHeight();
        int iconHeight = prototypeIcon != null ? prototypeIcon.getIconHeight() : 0;
        table.setRowHeight(Math.max(fontHeight, iconHeight) + table.getRowMargin());
    }

    /**
     * Sets an approximate row height for all table rows.
     * <p>
     * This is a fast and "close-enough" approach to setting the row height.  To set the
     * exact row height you'd have to render and measure all cells.
     * <p>
     * The given size should be the size of the icons expected to populate the table
     * rows. The row height will be set to accomodate the font and the icon.
     *
     * @param table the table with the font already set
     * @param size the same size as the icons used in the table
     * @see #setRowHeights
     */
    public static void setRowHeight(JTable table, ImageProvider.ImageSizes size) {
        int fontHeight = table.getFontMetrics(table.getFont()).getHeight();
        int iconHeight = size.getHeight();
        table.setRowHeight(Math.max(fontHeight, iconHeight) + table.getRowMargin());
    }

    /**
     * Sets the exact row height for each (variable height) table row.
     * <p>
     * This is the slow and exact approach to setting row heights and works with
     * variable height rows too.  It is slow because it renders and measures the whole
     * table.
     * <p>
     * This function must be called after every change in the table model.
     *
     * @param table the table with data already loaded
     */
    public static void setRowHeights(JTable table) {
        for (int row = 0; row < table.getRowCount(); row++) {
            int preferredHeight = 1;
            for (int col = 0; col < table.getColumnCount(); col++) {
                preferredHeight = Math.max(preferredHeight, table.prepareRenderer(
                    table.getCellRenderer(row, col), row, col).getPreferredSize().height);
            }
            table.setRowHeight(row, preferredHeight + table.getRowMargin());
        }
    }

    /**
     * Sets a fixed column width from existing table data.
     * <p>
     * The minimun and maximum column widths will be set to the preferred width of the
     * widest cell. The table will use exactly this column width.
     *
     * @param table the table with data already loaded
     * @param column the column index
     */
    public static void setFixedColumnWidth(JTable table, int column, int row) {
        int preferredWidth = table.prepareRenderer(
                    table.getCellRenderer(row, column), row, column).getPreferredSize().width;
        TableColumn col = table.getColumnModel().getColumn(column);
        col.setMinWidth(preferredWidth);
        col.setMaxWidth(preferredWidth);
    }

    /**
     * Sets a fixed column width from existing table data.
     * <p>
     * The minimun and maximum column widths will be set to the preferred width of the
     * widest cell. The table will use exactly this column width.
     *
     * @param table the table with data already loaded
     * @param column the column index
     */
    public static void setFixedColumnWidth(JTable table, int column) {
        int preferredWidth = 1;
        int rows = table.getRowCount();
        for (int row = 0; row < rows; row++) {
            preferredWidth = Math.max(preferredWidth, table.prepareRenderer(
                table.getCellRenderer(row, column), row, column).getPreferredSize().width);
        }
        TableColumn col = table.getColumnModel().getColumn(column);
        col.setMinWidth(preferredWidth);
        col.setMaxWidth(preferredWidth);
    }

    /**
     * Sets the preferred column width for a given table column from data.
     * <p>
     * The preferred column width will be set to the preferred width of the widest cell.
     * The table may still use a different width to accomodate constraints from other
     * columns.
     *
     * @param table the table with data already loaded
     * @param column the column index
     */
    public static void setPreferredColumnWidth(JTable table, int column) {
        int preferredWidth = 1;
        int rows = table.getRowCount();
        for (int row = 0; row < rows; row++) {
            preferredWidth = Math.max(preferredWidth, table.prepareRenderer(
                table.getCellRenderer(row, column), row, column).getPreferredSize().width);
        }
        table.getColumnModel().getColumn(column).setPreferredWidth(preferredWidth);
    }

    /**
     * Sets the preferred column width for each table column from data.
     *
     * @param table the table with data already loaded
     */
    public static void setPreferredColumnWidths(JTable table) {
        for (int col = 0; col < table.getColumnCount(); col++) {
            setPreferredColumnWidth(table, col);
        }
    }
}
