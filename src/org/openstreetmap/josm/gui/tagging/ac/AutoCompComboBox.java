// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.ac;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.awt.im.InputContext;
import java.util.EventObject;
import java.util.Locale;

import javax.swing.ComboBoxEditor;
import javax.swing.JTable;
import javax.swing.event.CellEditorListener;
import javax.swing.table.TableCellEditor;

import org.openstreetmap.josm.gui.util.CellEditorSupport;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.tools.Logging;

/**
 * An auto-completing ComboBox.
 * <p>
 * When the user starts typing, this combobox will suggest the
 * {@link AutoCompComboBoxModel#findBestCandidate best matching item} from its list.  The items can
 * be of any type while the items' {@code toString} values are shown in the combobox and editor.
 *
 * @author guilhem.bonnefille@gmail.com
 * @author marcello@perathoner.de
 * @param <E> the type of the combobox entries
 * @since 18173
 */
public class AutoCompComboBox<E> extends JosmComboBox<E> implements TableCellEditor, AutoCompListener {

    /** force a different keyboard input locale for the editor */
    private boolean useFixedLocale;
    private final transient InputContext privateInputContext = InputContext.getInstance();
    /** Disables firing of action events when true */
    protected boolean disableActionEvents = false;

    /**
     * Constructs an {@code AutoCompletingComboBox}.
     */
    public AutoCompComboBox() {
        this(new AutoCompComboBoxModel<>());
    }

    /**
     * Constructs an {@code AutoCompletingComboBox} with a supplied {@link AutoCompComboBoxModel}.
     *
     * @param model the model
     */
    public AutoCompComboBox(AutoCompComboBoxModel<E> model) {
        super(model);
        setEditor(new AutoCompComboBoxEditor<E>());
        setEditable(true);
        getEditorComponent().setModel(model);
        getEditorComponent().addAutoCompListener(this);
        tableCellEditorSupport = new CellEditorSupport(this);
    }

    /**
     * Returns the {@link AutoCompComboBoxModel} currently used.
     *
     * @return the model or null
     */
    @Override
    public AutoCompComboBoxModel<E> getModel() {
        return (AutoCompComboBoxModel<E>) dataModel;
    }

    @Override
    public void setEditor(ComboBoxEditor newEditor) {
        if (editor != null) {
            editor.getEditorComponent().removePropertyChangeListener(this);
        }
        super.setEditor(newEditor);
        if (editor != null) {
            // listen to orientation changes in the editor
            editor.getEditorComponent().addPropertyChangeListener(this);
        }
    }

    /**
     * Returns the editor component
     *
     * @return the editor component
     * @see ComboBoxEditor#getEditorComponent()
     * @since 18221
     */
    @Override
    @SuppressWarnings("unchecked")
    public AutoCompTextField<E> getEditorComponent() {
        return getEditor() == null ? null : (AutoCompTextField<E>) getEditor().getEditorComponent();
    }

    /**
     * Selects the autocompleted item in the dropdown.
     *
     * @param item the item selected for autocomplete
     */
    private void autocomplete(Object item) {
        // Save the text in case item is null, because setSelectedItem will erase it.
        String savedText = getText();
        setSelectedItem(item);
        if (item == null)
            setText(savedText);
    }

    /**
     * Enables or disables the autocompletion.
     *
     * @param enabled {@code true} to enable autocompletion
     * @return {@code true} if autocomplete was enabled before calling this
     * @since 18173 (signature)
     */
    public boolean setAutocompleteEnabled(boolean enabled) {
        return getEditorComponent().setAutocompleteEnabled(enabled);
    }

    /**
     * {@inheritDoc}
     *
     * Sometimes we need to disable overly zealous action events, eg. when programmatically filling
     * the drop down list before displaying the popup.
     */
    @Override
    protected void fireActionEvent() {
        if (!disableActionEvents) {
            super.fireActionEvent();
        }
    }

    /**
     * Fixes the locale for keyboard input to US-English.
     * <p>
     * If the locale is fixed, English keyboard layout will be used by default for this combobox.
     * All other components can still have different keyboard layout selected.
     *
     * @param f if {@code true} use fixed locale
     */
    public void setFixedLocale(boolean f) {
        useFixedLocale = f;
        if (useFixedLocale) {
            Locale oldLocale = privateInputContext.getLocale();
            Logging.info("Using English input method");
            if (!privateInputContext.selectInputMethod(Locale.of("en", "US"))) {
                // Unable to use English keyboard layout, disable the feature
                Logging.warn("Unable to use English input method");
                useFixedLocale = false;
                if (oldLocale != null) {
                    Logging.info("Restoring input method to " + oldLocale);
                    if (!privateInputContext.selectInputMethod(oldLocale)) {
                        Logging.warn("Unable to restore input method to " + oldLocale);
                    }
                }
            }
        }
    }

    @Override
    public InputContext getInputContext() {
        if (useFixedLocale) {
            return privateInputContext;
        }
        return super.getInputContext();
    }

    /* ------------------------------------------------------------------------------------ */
    /* AutoCompListener interface                                                           */
    /* ------------------------------------------------------------------------------------ */

    @Override
    public void autoCompBefore(AutoCompEvent e) {
        // empty
    }

    @Override
    public void autoCompPerformed(AutoCompEvent e) {
        boolean oldDisableActionEvents = disableActionEvents;
        disableActionEvents = true;
        autocomplete(e.getItem());
        disableActionEvents = oldDisableActionEvents;
    }

    /* ------------------------------------------------------------------------------------ */
    /* TableCellEditor interface                                                            */
    /* ------------------------------------------------------------------------------------ */

    private transient CellEditorSupport tableCellEditorSupport;
    private String originalValue;

    @Override
    public void addCellEditorListener(CellEditorListener l) {
        tableCellEditorSupport.addCellEditorListener(l);
    }

    protected void rememberOriginalValue(String value) {
        this.originalValue = value;
    }

    protected void restoreOriginalValue() {
        setText(originalValue);
    }

    @Override
    public void removeCellEditorListener(CellEditorListener l) {
        tableCellEditorSupport.removeCellEditorListener(l);
    }

    @Override
    public void cancelCellEditing() {
        restoreOriginalValue();
        tableCellEditorSupport.fireEditingCanceled();
    }

    @Override
    public Object getCellEditorValue() {
        return getText();
    }

    /**
    * Returns true if <code>anEvent</code> is <b>not</b> a <code>MouseEvent</code>.  Otherwise, it
    * returns true if the necessary number of clicks have occurred, and returns false otherwise.
    *
    * @param   anEvent         the event
    * @return  true  if cell is ready for editing, false otherwise
    * @see #shouldSelectCell
    */
    @Override
    public boolean isCellEditable(EventObject anEvent) {
        if (anEvent instanceof MouseEvent) {
            return ((MouseEvent) anEvent).getClickCount() >= 1;
        }
        return true;
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent) {
        if (anEvent instanceof MouseEvent) {
            MouseEvent e = (MouseEvent) anEvent;
            return e.getID() != MouseEvent.MOUSE_DRAGGED;
        }
        return true;
    }

    @Override
    public boolean stopCellEditing() {
        tableCellEditorSupport.fireEditingStopped();
        return true;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
        setText(value == null ? "" : value.toString());
        rememberOriginalValue(getText());
        return this;
    }
}
