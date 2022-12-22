// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging;

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Collections;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.openstreetmap.josm.gui.dialogs.properties.HelpAction;
import org.openstreetmap.josm.gui.dialogs.properties.HelpTagAction;

/**
 * TagEditorPanel is a {@link JPanel} which can be embedded as UI component in
 * UIs. It provides a spreadsheet like tabular control for editing tag names
 * and tag values. Two action buttons are placed on the left, one for adding
 * a new tag and one for deleting the currently selected tags.
 * @since 2040
 */
public class TagEditorPanel extends JPanel {
    /** the tag editor model */
    private final TagTableModel model;
    /** the tag table */
    private final TagTable tagTable;

    /**
     * builds the panel with the table for editing tags
     *
     * @return the panel
     */
    protected JPanel buildTagTableEditorPanel() {
        JPanel pnl = new JPanel(new BorderLayout());
        pnl.add(new JScrollPane(tagTable), BorderLayout.CENTER);
        return pnl;
    }

    /**
     * builds the panel with the button row
     *
     * @return the panel
     */
    protected JPanel buildButtonsPanel() {
        JPanel pnl = new JPanel();
        pnl.setLayout(new BoxLayout(pnl, BoxLayout.Y_AXIS));

        ActionMap am = tagTable.getActionMap();
        buildButton(pnl, am.get("add"));
        buildButton(pnl, am.get("delete"));
        buildButton(pnl, am.get("paste"));

        return pnl;
    }

    private void buildButton(JPanel pnl, Action action) {
        JButton btn = new JButton(action);
        btn.setMargin(new Insets(0, 0, 0, 0));
        pnl.add(btn);
    }

    /**
     * builds the GUI
     */
    protected final void build() {
        setLayout(new GridBagLayout());
        JPanel tablePanel = buildTagTableEditorPanel();
        JPanel buttonPanel = buildButtonsPanel();

        GridBagConstraints gc = new GridBagConstraints();

        // -- buttons panel
        //
        gc.fill = GridBagConstraints.VERTICAL;
        gc.weightx = 0.0;
        gc.weighty = 1.0;
        gc.anchor = GridBagConstraints.NORTHWEST;
        add(buttonPanel, gc);

        // -- the panel with the editor table
        //
        gc.gridx = 1;
        gc.fill = GridBagConstraints.BOTH;
        gc.weightx = 1.0;
        gc.weighty = 1.0;
        gc.anchor = GridBagConstraints.CENTER;
        add(tablePanel, gc);
    }

    /**
     * Creates a new tag editor panel with a supplied model. If {@code model} is null, a new model is created.
     *
     * @param model the tag editor model
     * @param maxCharacters maximum number of characters allowed, 0 for unlimited
     */
    public TagEditorPanel(TagTableModel model, final int maxCharacters) {
        this.model = (model == null) ? new TagTableModel(null) : model;

        this.tagTable = new TagTable(this.model, maxCharacters);
        setupKeyboardShortcuts();
        build();
    }

    private void setupKeyboardShortcuts() {
        // F1 button = custom help action
        final HelpAction helpTagAction = new HelpTagAction(tagTable,
                viewRow -> tagTable.getKey(viewRow),
                viewRow -> Collections.singletonMap(model.get(tagTable.getKey(viewRow)).toString(), 1));
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(HelpAction.getKeyStroke(), "onHelp");
        getActionMap().put("onHelp", helpTagAction);
    }

    /**
     * Replies the tag editor model used by this panel.
     *
     * @return the tag editor model used by this panel
     */
    public TagTableModel getModel() {
        return model;
    }

    /**
     * Returns the JTable
     * @return the JTable
     */
    public TagTable getTable() {
        return tagTable;
    }

    @Override
    public void setEnabled(boolean enabled) {
        tagTable.setEnabled(enabled);
        super.setEnabled(enabled);
    }

    /**
     * Save all outstanding edits to the model.
     * @see org.openstreetmap.josm.gui.io.UploadDialog#saveEdits
     * @since 18173
     */
    public void saveEdits() {
        tagTable.endCellEditing();
    }
}
