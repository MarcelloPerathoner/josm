// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.gui.datatransfer.OsmTransferHandler;
import org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.tools.AlphanumComparator;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

/**
 * This is the tabular editor component for OSM tags.
 * @since 1762
 */
public class TagTable extends JTable {
    final TagTableModel tagTableModel;
    private final TableRowSorter<TagTableModel> rowSorter;

    /** Set if the table was editing before the last keystroke reached the table. If set, moving the
     * selection automatically re-opens the editor (to save one keystroke). */
    private boolean keepEditing;

    /**
     * Creates a new tag table
     *
     * @param model the tag editor model
     * @param maxCharacters maximum number of characters allowed for keys and values, -1 for unlimited
     */
    public TagTable(TagTableModel model, final int maxCharacters) {
        super(model);
        this.tagTableModel = model;
        setAutoCreateRowSorter(false);
        setAutoResizeMode(JTable.AUTO_RESIZE_NEXT_COLUMN);
        setRowSelectionAllowed(true);
        setColumnSelectionAllowed(true);
        setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        // putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);
        setSurrendersFocusOnKeystroke(true);
        createDefaultColumnsFromModel();

        rowSorter = new TableRowSorter<>(tagTableModel);
        setRowSorter(rowSorter);
        rowSorter.setComparator(0, new EmptyLastKeyComparator(model).thenComparing(AlphanumComparator.getInstance()));
        rowSorter.setComparator(1, new EmptyLastComparator().thenComparing(AlphanumComparator.getInstance()));
        rowSorter.toggleSortOrder(0);
        rowSorter.sort();

        InputMap im = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), "add");
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "startEditing");

        getActionMap().put("add", new AddAction());
        getActionMap().put("delete", new DeleteAction());
        getActionMap().put("paste", new PasteAction());

        // this hack fixes some focus issues with the comboboxes, eg. when clicking a cell in the
        // place where the arrow button will later be shown it should always open the dropdown.
        // Also, it lets the user select a row by swishing from the key to the value cell. {@see
        // BasicTableUI#adjustSelection and mouseReleasedDND}
        try {
            setDragEnabled(true);
        // CHECKSTYLE.OFF: EmptyBlock
        } catch (java.awt.HeadlessException e) {
            // running in test rig
        }
        // CHECKSTYLE.ON: EmptyBlock
    }

    @Override
    public TagTableModel getModel() {
        return tagTableModel;
    }

    /**
     * Sets a TableCellEditor for the keys column.
     * @param editor the editor to set
     */
    public void setKeyEditor(TableCellEditor editor) {
        setupEditor(editor);
        getColumnModel().getColumn(0).setCellEditor(editor);
    }

    /**
     * Sets a TableCellEditor for the values column.
     * @param editor the editor to set
     */
    public void setValueEditor(TableCellEditor editor) {
        setupEditor(editor);
        getColumnModel().getColumn(1).setCellEditor(editor);
    }

    /**
     * Setup an editor
     * @param editor the editor to set up
     */
    private void setupEditor(TableCellEditor editor) {
        if (editor instanceof JComboBox) {
            JComboBox<?> combobox = (JComboBox<?>) editor;
            JTextField textfield = (JTextField) combobox.getEditor().getEditorComponent();
            textfield.setBorder(BorderFactory.createEmptyBorder(1, 1, 1, 1)); // set smaller border
            combobox.putClientProperty("JComboBox.isTableCellEditor", Boolean.TRUE);

            // The following hack makes ENTER in a combobox editor select the next row cell.
            // combobox.getInputMap().remove(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0));
            // combobox.getActionMap().put("enterPressed", new TextFieldEnterListener());
            textfield.addActionListener(new TextFieldEnterListener());
            if (textfield instanceof JosmTextField) {
                // this default JOSM behaviour conflicts with autoStartsEdit
                ((JosmTextField) textfield).setSelectAllOnFocusGained(false);
            }
        }
    }

    @Override
    public TableRowSorter<? extends TableModel> getRowSorter() {
        return rowSorter;
    }

    /**
     * Keeps the table in edit mode.
     * <p>
     * This function is called whenever the row or column selection changes.  If there is exactly
     * one cell selected it puts that cell into edit mode.
     *
     * @param e the event
     */
    private void selectionChanged(ListSelectionEvent e) {
        // gets both row and column events so it gets called twice on new line
        if (!e.getValueIsAdjusting()) {
            if (getSelectedColumnCount() == 1 && getSelectedRowCount() == 1) {
                int row = getSelectedRow();
                int col = getSelectedColumn();
                if (keepEditing && (row != getEditingRow() || col != getEditingColumn()))
                    editCellAt(row, col);
            }
        }
    }

    @Override
    public void valueChanged(ListSelectionEvent e) {
        super.valueChanged(e);
        selectionChanged(e);
    }

    @Override
    public void columnSelectionChanged(ListSelectionEvent e) {
        super.columnSelectionChanged(e);
        selectionChanged(e);
    }

    @Override
    public Component prepareEditor(TableCellEditor editor, int row, int col) {
        Logging.trace("prepareEditor row={0} col={1}", row, col);
        // always keep the selection in sync with the editor because the edit and delete actions act
        // on the selected cell
        changeSelection(row, col, false, false);
        Component comp = super.prepareEditor(editor, row, col);
        comp.requestFocus(); // so the user can start typing without having to press ENTER first
        return comp;
    }

    @Override
    public void editingStopped(ChangeEvent e) {
        // here we could get a peek at the old value
        // getValueAt(value, editingRow, editingColumn);
        // this call saves the edited value and removes the editor
        super.editingStopped(e);
    }

    @Override
    public void editingCanceled(ChangeEvent e) {
        // this call removes the editor
        super.editingCanceled(e);
    }

    @Override
    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        keepEditing = isEditing();
        return super.processKeyBinding(ks, e, condition, pressed);
    }

    /**
     * Returns the row index for a given key.
     *
     * @param key the key
     * @return the row number in view coordinates or -1
     */
    public int getRowIndex(String key) {
        int row = tagTableModel.getRowIndex(key);
        if (row == -1)
            return row;
        return convertRowIndexToView(row);
    }

    /**
     * Returns the key in the given row.
     *
     * @param row the row number in view coordinates
     * @return the key or null
     */
    public String getKey(int row) {
        if (row < 0 || row >= getRowCount())
            return null;
        return tagTableModel.getKey(convertRowIndexToModel(row));
    }

    /**
     * Returns the value in the given row.
     *
     * @param row the row number in view coordinates
     * @return the value or null
     */
    public TagTableModel.ValueType getValueType(int row) {
        if (row < 0 || row >= getRowCount())
            return null;
        return tagTableModel.getValueType(convertRowIndexToModel(row));
    }

    /**
     * Saves all outstanding edits
     */
    public void endCellEditing() {
        TableCellEditor cEditor = getCellEditor();
        if (cEditor != null) {
            // First attempt to commit. If this does not work, cancel.
            if (!cEditor.stopCellEditing()) {
                cEditor.cancelCellEditing();
            }
        }
    }

    /**
     * Saves the selection state of the table.
     * @return opaque state object
     */
    public SavedState saveState() {
        return new SavedState();
    }

    /**
     * Saves and restores the selection status of a table.
     */
    public class SavedState {
        String selectedKey;
        String selectedColumn;

        /**
         * Constructor
         */
        public SavedState() {
            selectedKey = getKey(getSelectedRow());
            selectedColumn = tagTableModel.getColumnName(getSelectedColumn());
        }

        /**
         * Restores the saved state
         */
        public void restore() {
            int row = getRowIndex(selectedKey);
            int col = tagTableModel.getColumnIndex(selectedColumn);
            if (row != -1 && col != -1)
                changeSelection(row, col, false, false);
        }
    }

    /**
     * Makes ENTER in comboboxes behave like TAB
     * <p>
     * Comboboxes do not pass ENTER to their parents, so we must listen for ENTER on the textfield
     * itself.
     */
    class TextFieldEnterListener extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            keepEditing = true;
            ActionEvent evt = new ActionEvent(TagTable.this, e.getID(), e.getActionCommand());
            SwingUtilities.invokeLater(() -> getActionMap().get("selectNextColumnCell").actionPerformed(evt));
        }
    }

    /**
     * A comparator that sorts empty strings last.
     */
    static class EmptyLastComparator implements Comparator<String> {
        @Override
        public int compare(String s1, String s2) {
            if (s1 == null || s1.isEmpty()) return 1;
            if (s2 == null || s2.isEmpty()) return -1;
            return 0;
        }
    }

    /**
     * A comparator of keys that sorts empty tags last.
     */
    static class EmptyLastKeyComparator implements Comparator<String> {
        private final TagTableModel model;

        EmptyLastKeyComparator(TagTableModel model) {
            this.model = model;
        }

        @Override
        public int compare(String key1, String key2) {
            if (key1 == null || key1.isEmpty()) return 1;
            if (key2 == null || key2.isEmpty()) return -1;
            if (model.get(key1).isEmpty()) return 1;
            if (model.get(key2).isEmpty()) return 1;
            return 0;
        }
    }

    /**
     * Base class for actions
     */
    private abstract class TagTableAction extends AbstractAction implements PropertyChangeListener {

        protected void updateEnabledState() {
            setEnabled(TagTable.this.isEnabled());
        }

        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            updateEnabledState();
        }
    }

    /**
     * Action that starts editing in the first column of the empty row
     */
    private class AddAction extends TagTableAction {
        AddAction() {
            new ImageProvider("dialogs", "add").getResource().attachImageIcon(this);
            putValue(SHORT_DESCRIPTION, tr("Add Tag"));
            TagTable.this.addPropertyChangeListener(this);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            TagTable.this.getModel().ensureTags();
            editCellAt(getRowIndex(""), 0);
        }
    }

    /**
     * Action that deletes rows from the table, for instance by pressing DEL.
     *
     * This action listens to the table selection. It becomes enabled when the selection is
     * non-empty, otherwise it is disabled.
     */
    class DeleteAction extends TagTableAction implements ListSelectionListener {

        DeleteAction() {
            new ImageProvider("dialogs", "delete").getResource().attachImageIcon(this);
            putValue(SHORT_DESCRIPTION, tr("Delete the selection in the tag table"));
            getSelectionModel().addListSelectionListener(this);
            getColumnModel().getSelectionModel().addListSelectionListener(this);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            endCellEditing();
            List<Integer> rows = new ArrayList<>();
            for (int row : getSelectedRows()) {
                rows.add(convertRowIndexToModel(row));
            }
            rows.sort(Comparator.reverseOrder());

            TaggedHandler handler = tagTableModel.getHandler();
            if (handler != null) {
                handler.begin();
            }
            for (int row : rows) {
                if (handler != null) {
                    String key = tagTableModel.getKey(row);
                    handler.update(key, key, null);
                }
                tagTableModel.removeRow(row);
            }
            if (handler != null) {
                handler.commit(tr("Delete {0} tags", rows.size()));
            }
        }

        /**
         * listens to the table selection model
         */
        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        protected final void updateEnabledState() {
            setEnabled(getSelectedColumnCount() >= 1 && getSelectedRowCount() >= 1);
        }
    }

    /**
     * Action to be run when the user wants to paste tags from buffer
     */
    class PasteAction extends TagTableAction {
        PasteAction() {
            new ImageProvider("pastetags").getResource().attachImageIcon(this);
            putValue(SHORT_DESCRIPTION, tr("Paste Tags"));
            TagTable.this.addPropertyChangeListener(this);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Relation relation = new Relation();
            relation.setKeys(tagTableModel.getTags());
            new OsmTransferHandler().pasteTags(Collections.singleton(relation));
            tagTableModel.initFromMap(relation.getKeys());
            TaggedHandler handler = tagTableModel.getHandler();
            if (handler != null) {
                handler.begin();
                relation.getKeys().forEach((k, v) -> {
                    handler.update(k, k, v);
                });
                handler.commit(tr("Paste {0} tags", relation.getKeys().size()));
            }
        }
    }
}
