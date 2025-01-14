// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs.relation;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.FlavorListener;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.TableModelListener;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.command.ChangeMembersCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.UndoRedoHandler.CommandQueueListener;
import org.openstreetmap.josm.data.osm.DefaultNameFormatter;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.tagging.ac.AutoCompItemCellRenderer;
import org.openstreetmap.josm.data.tagging.ac.AutoCompletionItem;
import org.openstreetmap.josm.data.tagging.ac.AutoCompletionPriority;
import org.openstreetmap.josm.data.validation.OsmValidator;
import org.openstreetmap.josm.data.validation.Severity;
import org.openstreetmap.josm.data.validation.tests.RelationChecker;
import org.openstreetmap.josm.gui.ConditionalOptionPaneUtil;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.ScrollViewport;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.gui.dialogs.relation.actions.AbstractRelationEditorAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.AddSelectedAfterSelection;
import org.openstreetmap.josm.gui.dialogs.relation.actions.AddSelectedAtEndAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.AddSelectedAtStartAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.AddSelectedBeforeSelection;
import org.openstreetmap.josm.gui.dialogs.relation.actions.ApplyAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.CancelAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.CopyMembersAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.DeleteCurrentRelationAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.DownloadIncompleteMembersAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.DownloadSelectedIncompleteMembersAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.DuplicateRelationAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.EditAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.IRelationEditorActionAccess;
import org.openstreetmap.josm.gui.dialogs.relation.actions.IRelationEditorActionGroup;
import org.openstreetmap.josm.gui.dialogs.relation.actions.MoveDownAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.MoveUpAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.OKAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.PasteMembersAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.RefreshAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.RemoveAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.RemoveSelectedAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.ReverseAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.SelectAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.SelectPrimitivesForSelectedMembersAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.SelectedMembersForSelectionAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.SetRoleAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.SortAction;
import org.openstreetmap.josm.gui.dialogs.relation.actions.SortBelowAction;
import org.openstreetmap.josm.gui.dialogs.validator.ValidatorListPanel;
import org.openstreetmap.josm.gui.help.ContextSensitiveHelpAction;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.tagging.DataHandlers.CloneDataSetHandler;
import org.openstreetmap.josm.gui.tagging.DataHandlers.DataSetHandler;
import org.openstreetmap.josm.gui.tagging.TagEditorPanel;
import org.openstreetmap.josm.gui.tagging.TagTable;
import org.openstreetmap.josm.gui.tagging.TagTableModel;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompComboBox;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompComboBoxModel;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompletionManager;
import org.openstreetmap.josm.gui.tagging.ac.DefaultAutoCompListener;
import org.openstreetmap.josm.gui.tagging.ac.TagTableUtils;
import org.openstreetmap.josm.gui.tagging.presets.PresetListPanel;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetType;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetValidator;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetValidator.EnableValidatorAction;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresets;
import org.openstreetmap.josm.gui.util.WindowGeometry;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.InputMapUtils;
import org.openstreetmap.josm.tools.ListenerList;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

/**
 * This dialog is for editing relations.
 * @since 343
 */
public class GenericRelationEditor extends RelationEditor implements CommandQueueListener, PropertyChangeListener {
    private static final String PREF_LASTROLE = "relation.editor.generic.lastrole";
    private static final String PREF_USE_ROLE_FILTER = "relation.editor.use_role_filter";

    private final PresetListPanel presetListPanel;
    private final TagEditorPanel tagEditorPanel;

    /** the parent relations table and its model */
    private final ReferringRelationsBrowser referrerBrowser;
    private final ReferringRelationsBrowserModel referrerModel;

    /** the member table and its model */
    private final MemberTable memberTable;
    private final MemberTableModel memberTableModel;

    /** the selection table and its model */
    private final SelectionTable selectionTable;
    private final SelectionTableModel selectionTableModel;

    private final AutoCompletionManager manager;
    private final AutoCompComboBox<AutoCompletionItem> cbRole;
    private final RelationEditorActionAccess actionAccess;

    // private final ValidatorTreePanel validatorPanel;
    private final ValidatorListPanel validatorListPanel;
    private final CloneDataSetHandler presetLinkHandler;
    private TaggingPresetValidator validator;
    private EnableValidatorAction enableValidatorAction;
    /** Data change listeners */
    private final ListenerList<ChangeListener> listeners = ListenerList.create();

    /**
     * the menu item in the windows menu. Required to properly hide on dialog close.
     */
    private JMenuItem windowMenuItem;
    /**
     * Action for performing the {@link RefreshAction}
     */
    private final RefreshAction refreshAction;
    /**
     * Action for performing the {@link ApplyAction}
     */
    private final ApplyAction applyAction;
    /**
     * Action for performing the {@link SelectAction}
     */
    private final SelectAction selectAction;
    /**
     * Action for performing the {@link DuplicateRelationAction}
     */
    private final DuplicateRelationAction duplicateAction;
    /**
     * Action for performing the {@link DeleteCurrentRelationAction}
     */
    private final DeleteCurrentRelationAction deleteAction;
    /**
     * Action for performing the {@link OKAction}
     */
    private final OKAction okAction;
    /**
     * Action for performing the {@link CancelAction}
     */
    private final CancelAction cancelAction;
    /**
     * A list of listeners that need to be notified on clipboard content changes.
     */
    private final ArrayList<FlavorListener> clipboardListeners = new ArrayList<>();

    private Component selectedTabPane;
    private JTabbedPane tabbedPane;
    private JCheckBox btnFilter = new JCheckBox(tr("Filter"));

    /**
     * The data handler we pass to a preset dialog opened from here.
     * <p>
     * This handler sets the tags and members of the relation according to the current
     * values in the tag and member tables.
     * <p>
     * Updates go only to the tag table. It is not possible to change the members
     * through this handler.
     */
    static class PresetLinkHandler extends DataSetHandler {
        final Relation relation;
        final TagEditorPanel tagEditorPanel;
        final MemberTableModel memberTableModel;

        PresetLinkHandler(Relation relation, TagEditorPanel tagEditorPanel, MemberTableModel memberTableModel) {
            this.relation = relation;
            this.tagEditorPanel = tagEditorPanel;
            this.memberTableModel = memberTableModel;
        }

        @Override
        public void update(String oldKey, String newKey, String value) {
            tagEditorPanel.getModel().put(oldKey, value);
        }

        @Override
        public Collection<OsmPrimitive> get() {
            // make a copy and set tags / members
            Relation r = new Relation(relation);
            memberTableModel.applyToRelation(r);
            r.setKeys(tagEditorPanel.getModel().getTags());
            return Collections.singletonList(r);
        }

        @Override
        public boolean isReadOnly() {
            return false;
        }
    }

    /**
     * Creates a new relation editor for the given relation. The relation will be saved if the user
     * selects "ok" in the editor.
     *
     * If no relation is given, will create an editor for a new relation.
     *
     * @param layer the {@link OsmDataLayer} the new or edited relation belongs to
     * @param relation relation to edit, or null to create a new one.
     * @param selectedMembers a collection of members which shall be selected initially
     */
    public GenericRelationEditor(OsmDataLayer layer, Relation relation, Collection<RelationMember> selectedMembers) {
        super(layer, relation);

        setRememberWindowGeometry(getClass().getName() + ".geometry",
                WindowGeometry.centerInWindow(MainApplication.getMainFrame(), new Dimension(700, 650)));

        if (relation == null)
            relation = new Relation();

        // init the various models
        //
        tagEditorPanel = new TagEditorPanel(null, 0);
        memberTableModel = new MemberTableModel(relation, getLayer(), tagEditorPanel.getModel());
        memberTableModel.register();
        selectionTableModel = new SelectionTableModel(getLayer());
        selectionTableModel.register();
        referrerModel = new ReferringRelationsBrowserModel(relation);

        validatorListPanel = new ValidatorListPanel();

        manager = AutoCompletionManager.of(this.getLayer().data);
        presetLinkHandler = new CloneDataSetHandler(new PresetLinkHandler(relation, tagEditorPanel, memberTableModel));

        populateModels(relation);

        presetListPanel = Config.getPref().getBoolean("relation.editor.presets.visible", true) ?
            new PresetListPanel() : null;
        if (presetListPanel != null) {
            updatePresets();
            tagEditorPanel.getModel().addTableModelListener(e -> updatePresets());
        }

        TagTableUtils tagTableUtils = new TagTableUtils(tagEditorPanel.getModel(), this::getContextKey);

        // setting up the tag table
        AutoCompComboBox<AutoCompletionItem> keyEditor = tagTableUtils.getKeyEditor(new KeyAutoCompListener());
        AutoCompComboBox<AutoCompletionItem> valueEditor = tagTableUtils.getValueEditor(new ValueAutoCompListener());

        TagTable tagTable = tagEditorPanel.getTable();
        tagTable.setKeyEditor(keyEditor);
        tagTable.setValueEditor(valueEditor);
        tagTable.setRowHeight(keyEditor.getEditorComponent().getPreferredSize().height);
        tagTableUtils.setTypes(Collections.singleton(relation));

        // setting up the member table
        AutoCompComboBox<AutoCompletionItem> cbRoleEditor = new AutoCompComboBox<>();
        RoleAutoCompManager roleAutoCompManager = new RoleAutoCompManager();
        cbRoleEditor.getEditorComponent().addAutoCompListener(roleAutoCompManager);
        cbRoleEditor.addPopupMenuListener(roleAutoCompManager);
        cbRoleEditor.getEditorComponent().enableUndoRedo(false);
        Insets insets = cbRoleEditor.getEditorComponent().getInsets();
        cbRoleEditor.getEditorComponent().setBorder(BorderFactory.createEmptyBorder(0, insets.left, 0, insets.right));
        cbRoleEditor.setToolTipText(tr("Select a role for this relation member"));
        cbRoleEditor.setRenderer(new AutoCompItemCellRenderer(cbRoleEditor, cbRoleEditor.getRenderer(), null));

        int height = cbRoleEditor.getEditorComponent().getPreferredSize().height;
        memberTable = new MemberTable(getLayer(), cbRoleEditor, memberTableModel);

        height = Math.max(height, memberTable.getColumnModel().getColumn(1).getCellRenderer().
            getTableCellRendererComponent(memberTable, new Node(), false, false, 0, 0).getPreferredSize().height);

        Logging.info("Row Height is {0}", height);

        memberTable.addMouseListener(new MemberTableDblClickAdapter());
        memberTable.setRowHeight(height);
        memberTableModel.addMemberModelListener(memberTable);

        selectionTable = new SelectionTable(selectionTableModel, memberTableModel);
        selectionTable.setRowHeight(height);

        LeftButtonToolbar leftButtonToolbar = new LeftButtonToolbar(new RelationEditorActionAccess());
        cbRole = new AutoCompComboBox<>();
        cbRole.getEditorComponent().addAutoCompListener(roleAutoCompManager);
        cbRole.addPopupMenuListener(roleAutoCompManager);
        cbRole.setText(Config.getPref().get(PREF_LASTROLE, ""));
        cbRole.setToolTipText(tr("Select a role"));
        cbRole.setRenderer(new AutoCompItemCellRenderer(cbRole, cbRole.getRenderer(), null));

        JSplitPane tagsAndMembersPane = buildSplitPane(
                buildTagEditorPanel(presetListPanel, tagEditorPanel),
                buildMemberEditorPanel(leftButtonToolbar),
                this);

        // int w = ImageProvider.adj(3);
        tagsAndMembersPane.setBorder(BorderFactory.createEmptyBorder());
        getContentPane().setLayout(new BorderLayout());
        tabbedPane = new JTabbedPane();
        tabbedPane.add(tr("Tags and Members"), tagsAndMembersPane);
        referrerBrowser = new ReferringRelationsBrowser(getLayer(), referrerModel);
        tabbedPane.add(tr("Parent Relations"), referrerBrowser);
        tabbedPane.add(tr("Child Relations"), new ChildRelationBrowser(getLayer(), relation));
        tabbedPane.add(tr("Errors"), validatorListPanel);
        selectedTabPane = tabbedPane.getSelectedComponent();
        tabbedPane.addChangeListener(e -> {
            JTabbedPane sourceTabbedPane = (JTabbedPane) e.getSource();
            int index = sourceTabbedPane.getSelectedIndex();
            String title = sourceTabbedPane.getTitleAt(index);
            if (title.equals(tr("Parent Relations"))) {
                referrerBrowser.init();
            }
            // see #20228
            boolean selIsTagsAndMembers = sourceTabbedPane.getSelectedComponent() == tagsAndMembersPane;
            if (selectedTabPane == tagsAndMembersPane && !selIsTagsAndMembers) {
                unregisterMain();
            } else if (selectedTabPane != tagsAndMembersPane && selIsTagsAndMembers) {
                registerMain();
            }
            selectedTabPane = sourceTabbedPane.getSelectedComponent();
        });

        buildValidator();
        tagEditorPanel.getModel().addTableModelListener(validator.getListener());
        memberTableModel.addTableModelListener(validator.getListener());

        actionAccess = new RelationEditorActionAccess();

        refreshAction = new RefreshAction(actionAccess);
        applyAction = new ApplyAction(actionAccess);
        selectAction = new SelectAction(actionAccess);
        duplicateAction = new DuplicateRelationAction(actionAccess);
        deleteAction = new DeleteCurrentRelationAction(actionAccess);

        this.memberTableModel.addTableModelListener(applyAction);
        this.tagEditorPanel.getModel().addTableModelListener(applyAction);

        addPropertyChangeListener(deleteAction);

        okAction = new OKAction(actionAccess);
        cancelAction = new CancelAction(actionAccess);

        JToolBar tb = buildToolBar(refreshAction, applyAction, selectAction, duplicateAction, deleteAction);
        tb.add(Box.createHorizontalGlue());
        tb.add(enableValidatorAction);

        getContentPane().add(tb, BorderLayout.NORTH);
        getContentPane().add(tabbedPane, BorderLayout.CENTER);
        getContentPane().add(buildOkCancelButtonPanel(okAction, deleteAction, cancelAction), BorderLayout.SOUTH);

        setSize(findMaxDialogSize());

        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowOpened(WindowEvent e) {
                        cleanSelfReferences(memberTableModel, getRelation());
                    }

                    @Override
                    public void windowClosing(WindowEvent e) {
                        cancel();
                    }
                }
        );
        InputMapUtils.addCtrlEnterAction(getRootPane(), okAction);
        // CHECKSTYLE.OFF: LineLength
        registerCopyPasteAction(tagTable.getActionMap().get("paste"), "PASTE_TAGS",
                Shortcut.registerShortcut("system:pastestyle", tr("Edit: {0}", tr("Paste Tags")), KeyEvent.VK_V, Shortcut.CTRL_SHIFT).getKeyStroke(),
                getRootPane(), memberTable, selectionTable);
        // CHECKSTYLE.ON: LineLength

        KeyStroke key = Shortcut.getPasteKeyStroke();
        if (key != null) {
            // handle uncommon situation, that user has no keystroke assigned to paste
            registerCopyPasteAction(new PasteMembersAction(actionAccess) {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    super.actionPerformed(e);
                    cbRole.requestFocusInWindow();
                }
            }, "PASTE_MEMBERS", key, getRootPane(), memberTable, selectionTable);
        }
        key = Shortcut.getCopyKeyStroke();
        if (key != null) {
            // handle uncommon situation, that user has no keystroke assigned to copy
            registerCopyPasteAction(new CopyMembersAction(actionAccess),
                    "COPY_MEMBERS", key, getRootPane(), memberTable, selectionTable);
        }
        selectionTable.setFocusable(false);
        memberTableModel.setSelectedMembers(selectedMembers);
        HelpUtil.setHelpContext(getRootPane(), ht("/Dialog/RelationEditor"));
        UndoRedoHandler.getInstance().addCommandQueueListener(this);
    }

    String getContextKey() {
        TagTable tagTable = tagEditorPanel.getTable();
        int row = tagTable.getEditingRow();
        if (row == -1)
            row = tagTable.getSelectedRow();
        return tagTable.getKey(row);
    }

    private void registerMain() {
        selectionTableModel.register();
        memberTableModel.register();
        memberTable.registerListeners();
    }

    private void unregisterMain() {
        selectionTableModel.unregister();
        memberTableModel.unregister();
        memberTable.unregisterListeners();
    }

    @Override
    public void reloadDataFromRelation() {
        setRelation(getRelation());
        populateModels(getRelation());
        refreshAction.updateEnabledState();
    }

    private void populateModels(Relation relation) {
        if (relation != null) {
            tagEditorPanel.getModel().initFromMap(relation.getKeys());
            memberTableModel.populate(relation);
            if (!getLayer().data.getRelations().contains(relation)) {
                // treat it as a new relation if it doesn't exist in the data set yet.
                setRelation(null);
            }
        } else {
            tagEditorPanel.getModel().clear();
            memberTableModel.populate(null);
        }
    }

    private void updatePresets() {
        presetListPanel.updatePresets(
            EnumSet.of(TaggingPresetType.RELATION),
            tagEditorPanel.getModel().getTags(),
            presetLinkHandler,
            manager
        );
        validate();
    }

    /**
     * Apply changes.
     * @see ApplyAction
     */
    public void apply() {
        applyAction.actionPerformed(null);
    }

    /**
     * Select relation.
     * @see SelectAction
     * @since 12933
     */
    public void select() {
        selectAction.actionPerformed(null);
    }

    /**
     * Cancel changes.
     * @see CancelAction
     */
    public void cancel() {
        cancelAction.actionPerformed(null);
    }

    /**
     * Adds a new change listener
     * @param listener the listener to add
     */
    public void addListener(ChangeListener listener) {
        listeners.addListener(listener);
    }

    /**
     * Adds a new change listener
     * @param listener the listener to add
     */
    public void removeListener(ChangeListener listener) {
        listeners.removeListener(listener);
    }

    /**
     * Notifies all listeners that a preset item input has changed.
     * @param source the source of this event
     */
    public void fireChangeEvent(JComponent source) {
        listeners.fireEvent(e -> e.stateChanged(new ChangeEvent(source)));
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if ("orientationAction".equals(evt.getPropertyName())) {
            applyComponentOrientation((ComponentOrientation) evt.getNewValue());
        }
        if (validator != null && "enableValidator".equals(evt.getPropertyName())) {
            boolean validatorEnabled = (Boolean) evt.getNewValue();
            validator.setEnabled(validatorEnabled);
            firePropertyChange("enableValidator", null, validatorEnabled);
        }
    }

    void buildValidator() {
        // final Color errorBackground = new NamedColorProperty(
        //    marktr("Input validation: error"), Color.RED).get();
        validator = new TaggingPresetValidator(
            OsmValidator.getEnabledTests(false),
            presetLinkHandler,
            errors -> {
                // validatorPanel.setErrors(errors);
                // validatorPanel.expandAll();
                validatorListPanel.setErrors(errors);

                Severity maxSeverity = errors.stream().map(e -> e.getSeverity())
                    .min(Comparator.comparing(Severity::getLevel)).orElse(null);
                int index = tabbedPane.indexOfComponent(validatorListPanel);
                if (maxSeverity != null) {
                    tabbedPane.setIconAt(index, ImageProvider.get("data", maxSeverity.getIcon()));
                } else {
                    tabbedPane.setIconAt(index, ImageProvider.get("preferences/validator"));
                }
            }
        );
        enableValidatorAction = new TaggingPresetValidator.EnableValidatorAction(this, false);
        validator.setEnabled(TaggingPresets.USE_VALIDATOR.get());
        validator.validate();
    }

    /**
     * Creates the toolbar
     * @param actions relation toolbar actions
     * @return the toolbar
     * @since 12933
     */
    protected static JToolBar buildToolBar(AbstractRelationEditorAction... actions) {
        JToolBar tb = new JToolBar();
        tb.setFloatable(false);
        for (AbstractRelationEditorAction action : actions) {
            tb.add(action);
        }
        return tb;
    }

    /**
     * builds the panel with the OK and the Cancel button
     * @param okAction OK action
     * @param deleteAction Delete Action
     * @param cancelAction Cancel action
     *
     * @return the panel with the OK and the Cancel button
     */
    protected final JPanel buildOkCancelButtonPanel(OKAction okAction, DeleteCurrentRelationAction deleteAction,
            CancelAction cancelAction) {
        final JPanel pnl = new JPanel(new FlowLayout(FlowLayout.CENTER));
        final JButton okButton = new JButton(okAction);
        final JButton deleteButton = new JButton(deleteAction);
        okButton.setPreferredSize(deleteButton.getPreferredSize());
        pnl.add(okButton);
        pnl.add(deleteButton);
        pnl.add(new JButton(cancelAction));
        pnl.add(new JButton(new ContextSensitiveHelpAction(ht("/Dialog/RelationEditor"))));
        // Keep users from saving invalid relations -- a relation MUST have at least a tag with the key "type"
        // AND must contain at least one other OSM object.
        final TableModelListener listener = l -> updateOkPanel(this.actionAccess.getChangedRelation(), okButton, deleteButton);
        listener.tableChanged(null);
        this.memberTableModel.addTableModelListener(listener);
        this.tagEditorPanel.getModel().addTableModelListener(listener);
        return pnl;
    }

    /**
     * Update the OK panel area
     * @param newRelation What the new relation would "look" like if it were to be saved now
     * @param okButton The OK button
     * @param deleteButton The delete button
     */
    private void updateOkPanel(IRelation<?> newRelation, JButton okButton, JButton deleteButton) {
        okButton.setVisible(newRelation.isUseful() || this.getRelationSnapshot() == null);
        deleteButton.setVisible(!newRelation.isUseful() && this.getRelationSnapshot() != null);
        if (this.getRelationSnapshot() == null && !newRelation.isUseful()) {
            okButton.setText(tr("Delete"));
        } else {
            okButton.setText(tr("OK"));
        }
    }

    /**
     * builds the panel with the tag editor
     * @param presetListPanel the preset list panel or null
     * @param tagEditorPanel tag editor panel
     *
     * @return the panel with the tag editor
     */
    protected static JPanel buildTagEditorPanel(PresetListPanel presetListPanel, TagEditorPanel tagEditorPanel) {
        JPanel pnl = new JPanel(new GridBagLayout());

        GBC gbc = GBC.eol().fill(GridBagConstraints.HORIZONTAL).anchor(GridBagConstraints.FIRST_LINE_START);

        if (presetListPanel != null) {
            pnl.add(presetListPanel, gbc.insets(0, 3, 0, 3));
        }

        pnl.add(new JLabel(tr("Tags")), gbc.insets(0));

        gbc.fill(GridBagConstraints.BOTH);
        pnl.add(tagEditorPanel, gbc.weight(1.0, 1.0));
        return pnl;
    }

    /**
     * builds the panel for the relation member editor
     * @param leftButtonToolbar left button toolbar
     *
     * @return the panel for the relation member editor
     */
    protected JPanel buildMemberEditorPanel(LeftButtonToolbar leftButtonToolbar) {
        final JPanel pnl = new JPanel(new GridBagLayout());
        final JScrollPane scrollPane = new JScrollPane(memberTable);

        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 2;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.FIRST_LINE_START;
        gc.weightx = 1.0;
        gc.weighty = 0.0;
        pnl.add(new JLabel(tr("Members")), gc);

        gc.gridx = 0;
        gc.gridy = 1;
        gc.gridheight = 2;
        gc.gridwidth = 1;
        gc.fill = GridBagConstraints.VERTICAL;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.weightx = 0.0;
        gc.weighty = 1.0;
        pnl.add(new ScrollViewport(leftButtonToolbar, ScrollViewport.VERTICAL_DIRECTION), gc);

        gc.gridx = 1;
        gc.gridy = 1;
        gc.gridheight = 1;
        gc.fill = GridBagConstraints.BOTH;
        gc.anchor = GridBagConstraints.CENTER;
        gc.weightx = 0.6;
        gc.weighty = 1.0;
        pnl.add(scrollPane, gc);

        // --- role editing
        JPanel p3 = new JPanel(new GridBagLayout());
        GBC gbc = GBC.std().fill(GridBagConstraints.NONE);
        JLabel lbl = new JLabel(tr("Role:"));
        p3.add(lbl, gbc);

        p3.add(cbRole, gbc.insets(3, 3, 0, 3).fill(GridBagConstraints.HORIZONTAL));

        SetRoleAction setRoleAction = new SetRoleAction(new RelationEditorActionAccess());
        memberTableModel.getSelectionModel().addListSelectionListener(setRoleAction);
        cbRole.getEditorComponent().getDocument().addDocumentListener(setRoleAction);
        cbRole.getEditorComponent().addActionListener(setRoleAction);
        memberTableModel.getSelectionModel().addListSelectionListener(
                e -> cbRole.setEnabled(memberTable.getSelectedRowCount() > 0)
        );
        cbRole.setEnabled(memberTable.getSelectedRowCount() > 0);

        JButton btnApply = new JButton(setRoleAction);
        int height = cbRole.getPreferredSize().height;
        btnApply.setPreferredSize(new Dimension(height, height));
        btnApply.setText("");
        p3.add(btnApply, gbc.weight(0, 0).fill(GridBagConstraints.NONE));

        btnFilter.setToolTipText(tr("Filter suggestions based on context"));
        btnFilter.setSelected(Config.getPref().getBoolean(PREF_USE_ROLE_FILTER, false));
        p3.add(btnFilter, gbc.span(GridBagConstraints.REMAINDER));

        //

        gc.gridx = 1;
        gc.gridy = 2;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.LAST_LINE_START;
        gc.weightx = 1.0;
        gc.weighty = 0.0;
        pnl.add(p3, gc);

        JPanel pnl2 = new JPanel(new GridBagLayout());

        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridheight = 1;
        gc.gridwidth = 3;
        gc.fill = GridBagConstraints.HORIZONTAL;
        gc.anchor = GridBagConstraints.FIRST_LINE_START;
        gc.weightx = 1.0;
        gc.weighty = 0.0;
        pnl2.add(new JLabel(tr("Selection")), gc);

        gc.gridx = 0;
        gc.gridy = 1;
        gc.gridheight = 1;
        gc.gridwidth = 1;
        gc.fill = GridBagConstraints.VERTICAL;
        gc.anchor = GridBagConstraints.NORTHWEST;
        gc.weightx = 0.0;
        gc.weighty = 1.0;
        pnl2.add(new ScrollViewport(buildSelectionControlButtonToolbar(new RelationEditorActionAccess()),
                ScrollViewport.VERTICAL_DIRECTION), gc);

        gc.gridx = 1;
        gc.gridy = 1;
        gc.weightx = 1.0;
        gc.weighty = 1.0;
        gc.fill = GridBagConstraints.BOTH;
        pnl2.add(buildSelectionTablePanel(selectionTable), gc);

        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        splitPane.setLeftComponent(pnl);
        splitPane.setRightComponent(pnl2);
        splitPane.setOneTouchExpandable(false);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                // has to be called when the window is visible, otherwise no effect
                splitPane.setDividerLocation(0.6);
            }
        });

        JPanel pnl3 = new JPanel(new BorderLayout());
        pnl3.add(splitPane, BorderLayout.CENTER);

        return pnl3;
    }

    /**
     * builds the panel with the table displaying the currently selected primitives
     * @param selectionTable selection table
     *
     * @return panel with current selection
     */
    protected static JPanel buildSelectionTablePanel(SelectionTable selectionTable) {
        JPanel pnl = new JPanel(new BorderLayout());
        pnl.add(new JScrollPane(selectionTable), BorderLayout.CENTER);
        return pnl;
    }

    /**
     * builds the {@link JSplitPane} which divides the editor in an upper and a lower half
     * @param top top panel
     * @param bottom bottom panel
     * @param re relation editor
     *
     * @return the split panel
     */
    protected static JSplitPane buildSplitPane(JPanel top, JPanel bottom, IRelationEditor re) {
        final JSplitPane pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        pane.setTopComponent(top);
        pane.setBottomComponent(bottom);
        pane.setOneTouchExpandable(true);
        if (re instanceof Window) {
            ((Window) re).addWindowListener(new WindowAdapter() {
                @Override
                public void windowOpened(WindowEvent e) {
                    // has to be called when the window is visible, otherwise no effect
                    pane.setDividerLocation(0.3);
                }
            });
        }
        return pane;
    }

    /**
     * The toolbar with the buttons on the left
     */
    static class LeftButtonToolbar extends JToolBar {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new {@code LeftButtonToolbar}.
         * @param editorAccess relation editor
         */
        LeftButtonToolbar(IRelationEditorActionAccess editorAccess) {
            setOrientation(SwingConstants.VERTICAL);
            setFloatable(false);

            List<IRelationEditorActionGroup> groups = new ArrayList<>();
            // Move
            groups.add(buildNativeGroup(10,
                    new MoveUpAction(editorAccess, "moveUp"),
                    new MoveDownAction(editorAccess, "moveDown")
                    ));
            // Edit
            groups.add(buildNativeGroup(20,
                    new EditAction(editorAccess),
                    new RemoveAction(editorAccess, "removeSelected")
                    ));
            // Sort
            groups.add(buildNativeGroup(30,
                    new SortAction(editorAccess),
                    new SortBelowAction(editorAccess)
                    ));
            // Reverse
            groups.add(buildNativeGroup(40,
                    new ReverseAction(editorAccess)
                    ));
            // Download
            groups.add(buildNativeGroup(50,
                    new DownloadIncompleteMembersAction(editorAccess, "downloadIncomplete"),
                    new DownloadSelectedIncompleteMembersAction(editorAccess)
                    ));
            groups.addAll(RelationEditorHooks.getMemberActions());

            IRelationEditorActionGroup.fillToolbar(this, groups, editorAccess);


            InputMap inputMap = editorAccess.getMemberTable().getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            inputMap.put((KeyStroke) new RemoveAction(editorAccess, "removeSelected")
                    .getValue(Action.ACCELERATOR_KEY), "removeSelected");
            inputMap.put((KeyStroke) new MoveUpAction(editorAccess, "moveUp")
                    .getValue(Action.ACCELERATOR_KEY), "moveUp");
            inputMap.put((KeyStroke) new MoveDownAction(editorAccess, "moveDown")
                    .getValue(Action.ACCELERATOR_KEY), "moveDown");
            inputMap.put((KeyStroke) new DownloadIncompleteMembersAction(
                    editorAccess, "downloadIncomplete").getValue(Action.ACCELERATOR_KEY), "downloadIncomplete");
        }
    }

    /**
     * build the toolbar with the buttons for adding or removing the current selection
     * @param editorAccess relation editor
     *
     * @return control buttons panel for selection/members
     */
    protected static JToolBar buildSelectionControlButtonToolbar(IRelationEditorActionAccess editorAccess) {
        JToolBar tb = new JToolBar(SwingConstants.VERTICAL);
        tb.setFloatable(false);

        List<IRelationEditorActionGroup> groups = new ArrayList<>();
        groups.add(buildNativeGroup(10,
                new AddSelectedAtStartAction(editorAccess),
                new AddSelectedBeforeSelection(editorAccess),
                new AddSelectedAfterSelection(editorAccess),
                new AddSelectedAtEndAction(editorAccess)
                ));
        groups.add(buildNativeGroup(20,
                new SelectedMembersForSelectionAction(editorAccess),
                new SelectPrimitivesForSelectedMembersAction(editorAccess)
                ));
        groups.add(buildNativeGroup(30,
                new RemoveSelectedAction(editorAccess)
                ));
        groups.addAll(RelationEditorHooks.getSelectActions());

        IRelationEditorActionGroup.fillToolbar(tb, groups, editorAccess);
        return tb;
    }

    private static IRelationEditorActionGroup buildNativeGroup(int order, AbstractRelationEditorAction... actions) {
        return new IRelationEditorActionGroup() {
            @Override
            public int order() {
                return order;
            }

            @Override
            public List<AbstractRelationEditorAction> getActions(IRelationEditorActionAccess editorAccess) {
                return Arrays.asList(actions);
            }
        };
    }

    @Override
    protected Dimension findMaxDialogSize() {
        return new Dimension(700, 650);
    }

    @Override
    public void setVisible(boolean visible) {
        if (isVisible() == visible) {
            return;
        }
        super.setVisible(visible);
        Clipboard clipboard = ClipboardUtils.getClipboard();
        if (visible) {
            RelationDialogManager.getRelationDialogManager().positionOnScreen(this);
            if (windowMenuItem == null) {
                windowMenuItem = addToWindowMenu(this, getLayer().getName());
            }
            tagEditorPanel.requestFocusInWindow();
            for (FlavorListener listener : clipboardListeners) {
                clipboard.addFlavorListener(listener);
            }
        } else {
            Config.getPref().put(PREF_LASTROLE, cbRole.getText());
            Config.getPref().putBoolean(PREF_USE_ROLE_FILTER, btnFilter.isSelected());

            // make sure all registered listeners are unregistered
            //
            memberTable.stopHighlighting();
            if (tabbedPane != null && tr("Tags and Members").equals(tabbedPane.getTitleAt(tabbedPane.getSelectedIndex()))) {
                unregisterMain();
            }
            if (windowMenuItem != null) {
                MainApplication.getMenu().windowMenu.remove(windowMenuItem);
                windowMenuItem = null;
            }
            for (FlavorListener listener : clipboardListeners) {
                clipboard.removeFlavorListener(listener);
            }
            dispose();
        }
    }

    /**
     * Adds current relation editor to the windows menu (in the "volatile" group)
     * @param re relation editor
     * @param layerName layer name
     * @return created menu item
     */
    protected static JMenuItem addToWindowMenu(IRelationEditor re, String layerName) {
        Relation r = re.getRelation();
        String name = r == null ? tr("New relation") : r.getLocalName();
        JosmAction focusAction = new JosmAction(
                tr("Relation Editor: {0}", name == null && r != null ? r.getId() : name),
                "dialogs/relationlist",
                tr("Focus Relation Editor with relation ''{0}'' in layer ''{1}''", name, layerName),
                null, false, false) {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                ((RelationEditor) getValue("relationEditor")).setVisible(true);
            }
        };
        focusAction.putValue("relationEditor", re);
        return MainMenu.add(MainApplication.getMenu().windowMenu, focusAction, MainMenu.WINDOW_MENU_GROUP.VOLATILE);
    }

    /**
     * checks whether the current relation has members referring to itself. If so,
     * warns the users and provides an option for removing these members.
     * @param memberTableModel member table model
     * @param relation relation
     */
    protected static void cleanSelfReferences(MemberTableModel memberTableModel, Relation relation) {
        List<OsmPrimitive> toCheck = new ArrayList<>();
        toCheck.add(relation);
        if (memberTableModel.hasMembersReferringTo(toCheck)) {
            int ret = ConditionalOptionPaneUtil.showOptionDialog(
                    "clean_relation_self_references",
                    MainApplication.getMainFrame(),
                    tr("<html>There is at least one member in this relation referring<br>"
                            + "to the relation itself.<br>"
                            + "This creates circular dependencies and is discouraged.<br>"
                            + "How do you want to proceed with circular dependencies?</html>"),
                            tr("Warning"),
                            JOptionPane.YES_NO_OPTION,
                            JOptionPane.WARNING_MESSAGE,
                            new String[]{tr("Remove them, clean up relation"), tr("Ignore them, leave relation as is")},
                            tr("Remove them, clean up relation")
            );
            switch(ret) {
            case ConditionalOptionPaneUtil.DIALOG_DISABLED_OPTION:
            case JOptionPane.CLOSED_OPTION:
            case JOptionPane.NO_OPTION:
                return;
            case JOptionPane.YES_OPTION:
                memberTableModel.removeMembersReferringTo(toCheck);
                break;
            default: // Do nothing
            }
        }
    }

    private void registerCopyPasteAction(Action action, Object actionName, KeyStroke shortcut,
            JRootPane rootPane, JTable... tables) {
        if (shortcut == null) {
            Logging.warn("No shortcut provided for the Paste action in Relation editor dialog");
        } else {
            int mods = shortcut.getModifiers();
            int code = shortcut.getKeyCode();
            if (code != KeyEvent.VK_INSERT && (mods == 0 || mods == InputEvent.SHIFT_DOWN_MASK)) {
                Logging.info(tr("Sorry, shortcut \"{0}\" can not be enabled in Relation editor dialog"), shortcut);
                return;
            }
        }
        rootPane.getActionMap().put(actionName, action);
        if (shortcut != null) {
            rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(shortcut, actionName);
            // Assign also to JTables because they have their own Copy&Paste implementation
            // (which is disabled in this case but eats key shortcuts anyway)
            for (JTable table : tables) {
                table.getInputMap(JComponent.WHEN_FOCUSED).put(shortcut, actionName);
                table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(shortcut, actionName);
                table.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(shortcut, actionName);
            }
        }
        if (action instanceof FlavorListener) {
            clipboardListeners.add((FlavorListener) action);
        }
    }

    @Override
    public void dispose() {
        refreshAction.destroy();
        UndoRedoHandler.getInstance().removeCommandQueueListener(this);
        super.dispose(); // call before setting relation to null, see #20304
        setRelation(null);
        selectedTabPane = null;
    }

    /**
     * Exception thrown when user aborts add operation.
     */
    public static class AddAbortException extends Exception {
    }

    /**
     * Asks confirmation before adding a primitive.
     * @param primitive primitive to add
     * @return {@code true} is user confirms the operation, {@code false} otherwise
     * @throws AddAbortException if user aborts operation
     */
    public static boolean confirmAddingPrimitive(OsmPrimitive primitive) throws AddAbortException {
        String msg = tr("<html>This relation already has one or more members referring to<br>"
                + "the object ''{0}''<br>"
                + "<br>"
                + "Do you really want to add another relation member?</html>",
                Utils.escapeReservedCharactersHTML(primitive.getDisplayName(DefaultNameFormatter.getInstance()))
            );
        int ret = ConditionalOptionPaneUtil.showOptionDialog(
                "add_primitive_to_relation",
                MainApplication.getMainFrame(),
                msg,
                tr("Multiple members referring to same object."),
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                null
        );
        switch(ret) {
        case ConditionalOptionPaneUtil.DIALOG_DISABLED_OPTION:
        case JOptionPane.YES_OPTION:
            return true;
        case JOptionPane.NO_OPTION:
        case JOptionPane.CLOSED_OPTION:
            return false;
        case JOptionPane.CANCEL_OPTION:
        default:
            throw new AddAbortException();
        }
    }

    /**
     * Warn about circular references.
     * @param primitive the concerned primitive
     */
    public static void warnOfCircularReferences(OsmPrimitive primitive) {
        warnOfCircularReferences(primitive, Collections.emptyList());
    }

    /**
     * Warn about circular references.
     * @param primitive the concerned primitive
     * @param loop list of relation that form the circular dependencies.
     *   Only used to report the loop if more than one relation is involved.
     * @since 16651
     */
    public static void warnOfCircularReferences(OsmPrimitive primitive, List<Relation> loop) {
        final String msg;
        DefaultNameFormatter df = DefaultNameFormatter.getInstance();
        if (loop.size() <= 2) {
            msg = tr("<html>You are trying to add a relation to itself.<br>"
                    + "<br>"
                    + "This generates a circular dependency of parent/child elements and is therefore discouraged.<br>"
                    + "Skipping relation ''{0}''.</html>",
                    Utils.escapeReservedCharactersHTML(primitive.getDisplayName(df)));
        } else {
            msg = tr("<html>You are trying to add a child relation which refers to the parent relation.<br>"
                    + "<br>"
                    + "This generates a circular dependency of parent/child elements and is therefore discouraged.<br>"
                    + "Skipping relation ''{0}''." + "<br>"
                    + "Relations that would generate the circular dependency:<br>{1}</html>",
                    Utils.escapeReservedCharactersHTML(primitive.getDisplayName(df)),
                    loop.stream().map(p -> Utils.escapeReservedCharactersHTML(p.getDisplayName(df)))
                            .collect(Collectors.joining(" -> <br>")));
        }
        JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                msg,
                tr("Warning"),
                JOptionPane.WARNING_MESSAGE);
    }

    /**
     * Adds primitives to a given relation.
     * @param orig The relation to modify
     * @param primitivesToAdd The primitives to add as relation members
     * @return The resulting command
     * @throws IllegalArgumentException if orig is null
     */
    public static Command addPrimitivesToRelation(final Relation orig, Collection<? extends OsmPrimitive> primitivesToAdd) {
        CheckParameterUtil.ensureParameterNotNull(orig, "orig");
        try {
            final Collection<TaggingPreset> presets = MainApplication.getTaggingPresets().getMatchingPresets(
                    EnumSet.of(TaggingPresetType.forPrimitive(orig)), orig.getKeys(), false);
            Relation target = new Relation(orig);
            boolean modified = false;
            for (OsmPrimitive p : primitivesToAdd) {
                if (p instanceof Relation) {
                    List<Relation> loop = RelationChecker.checkAddMember(target, (Relation) p);
                    if (!loop.isEmpty() && loop.get(0).equals(loop.get(loop.size() - 1))) {
                        warnOfCircularReferences(p, loop);
                        continue;
                    }
                } else if (MemberTableModel.hasMembersReferringTo(target.getMembers(), Collections.singleton(p))
                        && !confirmAddingPrimitive(p)) {
                    continue;
                }
                final Set<String> roles = findSuggestedRoles(presets, p);
                target.addMember(new RelationMember(roles.size() == 1 ? roles.iterator().next() : "", p));
                modified = true;
            }
            List<RelationMember> members = new ArrayList<>(target.getMembers());
            target.setMembers(null); // see #19885
            return modified ? new ChangeMembersCommand(orig, members) : null;
        } catch (AddAbortException ign) {
            Logging.trace(ign);
            return null;
        }
    }

    protected static Set<String> findSuggestedRoles(final Collection<TaggingPreset> presets, OsmPrimitive p) {
        return presets.stream()
                .map(preset -> preset.suggestRoleForOsmPrimitive(p))
                .filter(role -> !Utils.isEmpty(role))
                .collect(Collectors.toSet());
    }

    class MemberTableDblClickAdapter extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2) {
                new EditAction(actionAccess).actionPerformed(null);
            }
        }
    }

    private class RelationEditorActionAccess implements IRelationEditorActionAccess {

        @Override
        public MemberTable getMemberTable() {
            return memberTable;
        }

        @Override
        public MemberTableModel getMemberTableModel() {
            return memberTableModel;
        }

        @Override
        public SelectionTable getSelectionTable() {
            return selectionTable;
        }

        @Override
        public SelectionTableModel getSelectionTableModel() {
            return selectionTableModel;
        }

        @Override
        public IRelationEditor getEditor() {
            return GenericRelationEditor.this;
        }

        @Override
        public TagTableModel getTagModel() {
            return tagEditorPanel.getModel();
        }

        @Override
        public JTextField getTextFieldRole() {
            return cbRole.getEditorComponent();
        }
    }

    @Override
    public void commandChanged(int queueSize, int redoSize) {
        Relation r = getRelation();
        if (r != null && r.getDataSet() == null) {
            // see #19915
            setRelation(null);
            applyAction.updateEnabledState();
        }
    }

    private class KeyAutoCompListener extends DefaultAutoCompListener<AutoCompletionItem> {
        @Override
        protected void updateAutoCompModel(AutoCompComboBoxModel<AutoCompletionItem> model) {
            Map<String, String> keys = tagEditorPanel.getModel().getTags();
            Map<String, String> matchKeys = btnFilter.isSelected() ? keys : null;

            Map<String, AutoCompletionPriority> map = AutoCompletionManager.merge(
                AutoCompletionManager.toMap(manager.getPresetsAllKeys(EnumSet.of(TaggingPresetType.RELATION), matchKeys),
                    AutoCompletionPriority.IS_IN_STANDARD),
                AutoCompletionManager.toMap(manager.getKeysForRelation(matchKeys), AutoCompletionPriority.IS_IN_DATASET)
            );

            model.replaceAllElements(map.entrySet().stream().filter(e -> !keys.containsKey(e.getKey()))
                .map(e -> new AutoCompletionItem(e.getKey(), e.getValue()))
                .sorted(AutoCompletionManager.ALPHABETIC_COMPARATOR).collect(Collectors.toList()));
        }
    }

    private class ValueAutoCompListener extends DefaultAutoCompListener<AutoCompletionItem> {
        @Override
        protected void updateAutoCompModel(AutoCompComboBoxModel<AutoCompletionItem> model) {
            String key = getContextKey();
            Map<String, String> tags = btnFilter.isSelected() ? tagEditorPanel.getModel().getTags() : null;

            Map<String, AutoCompletionPriority> map = AutoCompletionManager.merge(
                AutoCompletionManager.toMap(manager.getPresetValues(EnumSet.of(TaggingPresetType.RELATION), tags, key),
                    AutoCompletionPriority.IS_IN_STANDARD),
                AutoCompletionManager.toMap(manager.getValuesForRelation(tags, key), AutoCompletionPriority.IS_IN_DATASET)
            );

            model.replaceAllElements(AutoCompletionManager.toList(map, AutoCompletionManager.PRIORITY_COMPARATOR));
        }
    }

    private class RoleAutoCompManager extends DefaultAutoCompListener<AutoCompletionItem> {
        @Override
        protected void updateAutoCompModel(AutoCompComboBoxModel<AutoCompletionItem> model) {
            Map<String, AutoCompletionPriority> map;
            Map<String, String> keys = btnFilter.isSelected() ? tagEditorPanel.getModel().getTags() : null;

            EnumSet<TaggingPresetType> selectedTypes = EnumSet.noneOf(TaggingPresetType.class);
            for (RelationMember member : memberTableModel.getSelectedMembers()) {
                selectedTypes.add(TaggingPresetType.forPrimitiveType(member.getDisplayType()));
            }

            map = AutoCompletionManager.merge(
                AutoCompletionManager.toMap(manager.getPresetRoles(keys, selectedTypes), AutoCompletionPriority.IS_IN_STANDARD),
                AutoCompletionManager.toMap(manager.getRolesForRelation(keys, selectedTypes), AutoCompletionPriority.IS_IN_DATASET),
                AutoCompletionManager.toMap(getCurrentRoles(selectedTypes), AutoCompletionPriority.IS_IN_SELECTION)
            );

            // turn into AutoCompletionItems
            model.replaceAllElements(map.entrySet().stream()
                .map(e -> new AutoCompletionItem(e.getKey(), e.getValue()))
                .sorted(AutoCompletionManager.ALPHABETIC_COMPARATOR).collect(Collectors.toList()));
        }

        /**
         * Returns the roles currently edited in the members table.
         * @param types the preset types to include, (node / way / relation ...) or null to include all types
         * @return the roles currently edited in the members table.
         */
        private Set<String> getCurrentRoles(Collection<TaggingPresetType> types) {
            Set<String> set = new HashSet<>();
            for (int i = 0; i < memberTableModel.getRowCount(); ++i) {
                RelationMember member = memberTableModel.getValue(i);
                if (types == null || types.contains(TaggingPresetType.forPrimitiveType(member.getDisplayType()))) {
                    set.add(member.getRole());
                }
            }
            return set;
        }
    }
}
