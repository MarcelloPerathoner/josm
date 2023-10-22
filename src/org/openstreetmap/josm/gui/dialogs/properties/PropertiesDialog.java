// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs.properties;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.gui.tagging.presets.InteractiveItem.DIFFERENT_I18N;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.InputMap;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.RowSorterEvent;
import javax.swing.event.RowSorterListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.relation.DeleteRelationsAction;
import org.openstreetmap.josm.actions.relation.DuplicateRelationAction;
import org.openstreetmap.josm.actions.relation.EditRelationAction;
import org.openstreetmap.josm.command.ChangeMembersCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DefaultNameFormatter;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.IRelationMember;
import org.openstreetmap.josm.data.osm.KeyValueVisitor;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmData;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.Tags;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager.FireMode;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.data.osm.search.SearchCompiler;
import org.openstreetmap.josm.data.osm.search.SearchSetting;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.CachingProperty;
import org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeEvent;
import org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeListener;
import org.openstreetmap.josm.data.tagging.ac.AutoCompletionItem;
import org.openstreetmap.josm.gui.ConditionalOptionPaneUtil;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.PopupMenuHandler;
import org.openstreetmap.josm.gui.PrimitiveHoverListener;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.dialogs.relation.RelationEditor;
import org.openstreetmap.josm.gui.dialogs.relation.RelationPopupMenus;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.tagging.TagTable;
import org.openstreetmap.josm.gui.tagging.TagTableModel;
import org.openstreetmap.josm.gui.tagging.DataHandlers.DataSetHandler;
import org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompComboBox;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompletionManager;
import org.openstreetmap.josm.gui.tagging.ac.TagTableUtils;
import org.openstreetmap.josm.gui.tagging.presets.PresetListPanel;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetListener;
import org.openstreetmap.josm.gui.util.AbstractTag2LinkPopupListener;
import org.openstreetmap.josm.gui.util.HighlightHelper;
import org.openstreetmap.josm.gui.util.TableHelper;
import org.openstreetmap.josm.gui.widgets.CompileSearchTextDecorator;
import org.openstreetmap.josm.gui.widgets.DisableShortcutsOnFocusGainedTextField;
import org.openstreetmap.josm.gui.widgets.FilterField;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.gui.widgets.PopupMenuLauncher;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.spi.preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.InputMapUtils;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.TaginfoRegionalInstance;
import org.openstreetmap.josm.tools.Territories;
import org.openstreetmap.josm.tools.Utils;

/**
 * This dialog displays the tags of the current selected primitives.
 *
 * If no object is selected, the dialog list is empty.
 * If only one is selected, all tags of this object are selected.
 * If more than one object is selected, the sum of all tags is displayed. If the
 * different objects share the same tag, the shared value is displayed. If they have
 * different values, all of them are put in a combo box and the string "{@code <different>}"
 * is displayed in italic.
 *
 * Below the list, the user can click on an add, modify and delete tag button to
 * edit the table selection value.
 *
 * The command is applied to all selected entries.
 *
 * @author imi
 */
public class PropertiesDialog extends ToggleDialog
implements PropertyChangeListener, DataSelectionListener, ActiveLayerChangeListener,
        DataSetListenerAdapter.Listener, TaggingPresetListener, PrimitiveHoverListener {
    private final BooleanProperty PROP_DISPLAY_DISCARDABLE_KEYS = new BooleanProperty("display.discardable-keys", false);

    /**
     * hook for roadsigns plugin to display a small button in the upper right corner of this dialog
     */
    public static final JPanel pluginHook = new JPanel();

    /**
     * The tag data of selected objects.
     */
    private final JosmTextField tagTableFilter;

    /*
     * The tag editor table.
     */
    private final ActiveDataSetHandler taggedHandler = new ActiveDataSetHandler();
    private final TagTableModel tagTableModel = new TagTableModel(taggedHandler);
    private final TagTable tagTable = new TagTable(tagTableModel, 255);
    private final TagTableUtils tagTableUtils = new TagTableUtils(tagTableModel, this::getContextKey);

    /*
     * The membership table.
     */
    private final MembershipTableModel membershipTableModel = new MembershipTableModel();
    private final JTable membershipTable = new JTable(membershipTableModel);

    /** JPanel containing both previous tables */
    private final JPanel bothTables;

    // Popup menus
    private final JPopupMenu tagMenu;
    private final JPopupMenu membershipMenu = new JPopupMenu();
    private final JPopupMenu blankSpaceMenu = new JPopupMenu();

    // Popup menu handlers
    private final transient PopupMenuHandler membershipMenuHandler = new PopupMenuHandler(membershipMenu);
    private final transient PopupMenuHandler blankSpaceMenuHandler = new PopupMenuHandler(blankSpaceMenu);

    private final List<JMenuItem> tagMenuTagInfoNatItems = new ArrayList<>();
    private final List<JMenuItem> membershipMenuTagInfoNatItems = new ArrayList<>();

    /**
     * This sub-object is responsible for all adding and editing of tags
     */
    private final transient TagEditHelper editHelper = new TagEditHelper();

    private final transient DataSetListenerAdapter dataChangedAdapter = new DataSetListenerAdapter(this);
    private final HelpAction helpTagAction = new HelpTagAction(tagTable, tagTable::getKey, row -> tagTable.getValueType(row).getMap());
    private final HelpAction helpRelAction = new HelpMembershipAction(membershipTable,
            x -> (IRelation<?>) membershipTableModel.getValueAt(x, 0));
    private final TaginfoAction taginfoAction = new TaginfoAction(
            tagTable, tagTable::getKey, row -> tagTable.getValueType(row).getMap(),
            membershipTable, x -> (IRelation<?>) membershipTableModel.getValueAt(x, 0));
    private final TaginfoAction tagHistoryAction = taginfoAction.toTagHistoryAction();
    private final Collection<TaginfoAction> taginfoNationalActions = new ArrayList<>();
    private transient int taginfoNationalHash;
    private final PasteValueAction pasteValueAction = new PasteValueAction();
    private final CopyValueAction copyValueAction = new CopyValueAction(
            tagTable, tagTable::getKey, OsmDataManager.getInstance()::getInProgressISelection);
    private final CopyKeyValueAction copyKeyValueAction = new CopyKeyValueAction(
            tagTable, tagTable::getKey, OsmDataManager.getInstance()::getInProgressISelection);
    private final CopyAllKeyValueAction copyAllKeyValueAction = new CopyAllKeyValueAction(
            tagTable, tagTable::getKey, OsmDataManager.getInstance()::getInProgressISelection).registerShortcut(); /* NO-SHORTCUT */
    private final SearchAction searchActionSame = new SearchAction(true);
    private final SearchAction searchActionAny = new SearchAction(false);
    private final AddAction addAction = new AddAction();
    private final EditAction editAction = new EditAction();
    private final DeleteAction deleteAction = new DeleteAction();
    private final JosmAction[] josmActions = {addAction, editAction, deleteAction};

    private final transient HighlightHelper highlightHelper = new HighlightHelper();

    /**
     * The Add button (needed to be able to disable it)
     */
    private final SideButton btnAdd = new SideButton(addAction);
    /**
     * The Edit button (needed to be able to disable it)
     */
    private final SideButton btnEdit = new SideButton(editAction);
    /**
     * The Delete button (needed to be able to disable it)
     */
    private final SideButton btnDel = new SideButton(deleteAction);
    /**
     * Matching preset display class
     */
    private final PresetListPanel presets = new PresetListPanel();

    /**
     * Text to display when nothing selected.
     */
    private final JLabel selectSomething = new JLabel("<html><p>"
            + tr("Select objects for which to change tags.") + "</p></html>");

    /**
     * Show tags and relation memberships of objects in the properties dialog when hovering over them with the mouse pointer
     * @since 18574
     */
    public static final BooleanProperty PROP_PREVIEW_ON_HOVER = new BooleanProperty("propertiesdialog.preview-on-hover", true);
    private final HoverPreviewPropListener hoverPreviewPropListener = new HoverPreviewPropListener();
    private IPrimitive hovered;

    /**
     * Always show information for selected objects when something is selected instead of the hovered object
     * @since 18574
     */
    public static final CachingProperty<Boolean> PROP_PREVIEW_ON_HOVER_PRIORITIZE_SELECTION =
        new BooleanProperty("propertiesdialog.preview-on-hover.always-show-selected", true).cached();
    private final HoverPreviewPreferSelectionPropListener hoverPreviewPrioritizeSelectionPropListener =
        new HoverPreviewPreferSelectionPropListener();

    private static final BooleanProperty PROP_AUTORESIZE_TAGS_TABLE = new BooleanProperty("propertiesdialog.autoresizeTagsTable", false);

    private static class ActiveDataSetHandler extends DataSetHandler {
        private Collection<OsmPrimitive> selection;

        @Override
        public DataSet getDataSet() {
            return OsmDataManager.getInstance().getActiveDataSet();
        }

        public void setSelection(Collection<OsmPrimitive> selection) {
            this.selection = selection;
        }

        /**
         * Returns the collection of primitives to display or edit.
         * @return the collection to display
         */
        @Override
        public Collection<OsmPrimitive> get() {
            return selection;
        }
    }

    /**
     * Create a new PropertiesDialog
     */
    public PropertiesDialog() {
        super(tr("Tags/Memberships"), "propertiesdialog", tr("Tags for selected objects."),
                Shortcut.registerShortcut("subwindow:properties", tr("Windows: {0}", tr("Tags/Memberships")), KeyEvent.VK_P,
                        Shortcut.ALT_SHIFT), 150, true);

        bothTables = new JPanel(new GridBagLayout());
        tagMenu = buildTagMenu();
        buildTagTable();
        tagTable.addMouseListener(new PopupMenuLauncher(tagMenu));

        setupMembershipMenu();
        buildMembershipTable();

        tagTableFilter = setupFilter();

        // combine both tables and wrap them in a scrollPane
        boolean top = Config.getPref().getBoolean("properties.presets.top", true);
        boolean presetsVisible = Config.getPref().getBoolean("properties.presets.visible", true);
        if (presetsVisible && top) {
            bothTables.add(presets, GBC.std().fill(GBC.HORIZONTAL).insets(5, 2, 5, 2).anchor(GBC.NORTHWEST));
            double epsilon = Double.MIN_VALUE; // need to set a weight or else anchor value is ignored
            bothTables.add(pluginHook, GBC.eol().insets(0, 1, 1, 1).anchor(GBC.NORTHEAST).weight(epsilon, epsilon));
        }
        bothTables.add(selectSomething, GBC.eol().fill().insets(10, 10, 10, 10));
        bothTables.add(tagTableFilter, GBC.eol().fill(GBC.HORIZONTAL));

        bothTables.add(tagTable.getTableHeader(), GBC.eol().fill(GBC.HORIZONTAL));
        bothTables.add(tagTable, GBC.eol().fill(GBC.BOTH));

        bothTables.add(membershipTable.getTableHeader(), GBC.eol().fill(GBC.HORIZONTAL));
        bothTables.add(membershipTable, GBC.eol().fill(GBC.BOTH));
        if (presetsVisible && !top) {
            bothTables.add(presets, GBC.eol().fill(GBC.HORIZONTAL).insets(5, 2, 5, 2));
        }

        setupBlankSpaceMenu();
        setupKeyboardShortcuts();

        // Let the actions know when selection in the tables change
        tagTable.getSelectionModel().addListSelectionListener(editAction);
        membershipTable.getSelectionModel().addListSelectionListener(editAction);
        tagTable.getSelectionModel().addListSelectionListener(deleteAction);
        membershipTable.getSelectionModel().addListSelectionListener(deleteAction);

        JScrollPane scrollPane = (JScrollPane) createLayout(bothTables, true,
                Arrays.asList(this.btnAdd, this.btnEdit, this.btnDel));

        MouseClickWatch mouseClickWatch = new MouseClickWatch();
        tagTable.addMouseListener(mouseClickWatch);
        membershipTable.addMouseListener(mouseClickWatch);
        scrollPane.addMouseListener(mouseClickWatch);
        tagTable.setFillsViewportHeight(true);

        selectSomething.setPreferredSize(scrollPane.getSize());
        presets.setSize(scrollPane.getSize());

        editHelper.loadTagsIfNeeded();

        MainApplication.getTaggingPresets().addTaggingPresetListener(this);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener(this);
        PROP_PREVIEW_ON_HOVER.addListener(hoverPreviewPropListener);
        PROP_PREVIEW_ON_HOVER_PRIORITIZE_SELECTION.addListener(hoverPreviewPrioritizeSelectionPropListener);
    }

    @Override
    public void destroy() {
        membershipMenuHandler.setPrimitives(Collections.emptyList());
        destroyTaginfoNationalActions();

        MainApplication.getTaggingPresets().removeTaggingPresetListener(this);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener(this);
        PROP_PREVIEW_ON_HOVER.removeListener(hoverPreviewPropListener);
        PROP_PREVIEW_ON_HOVER_PRIORITIZE_SELECTION.removeListener(hoverPreviewPrioritizeSelectionPropListener);
        Container parent = pluginHook.getParent();
        if (parent != null) {
            parent.remove(pluginHook);
        }
        super.destroy();
    }

    /**
     * Returns the tag menu
     * <p>
     * For plugins (eg. Mapillary) that need to add menu entries.
     */
    public JPopupMenu getTagMenu() {
        return tagMenu;
    }

    public TagTable getTagTable() {
        return tagTable;
    }

    /**
     * Create a TagTableModel to edit the current selection in the current layer.
     * <p>
     * We cannot use the built-in tagTableModel because of:
     * <a href="https://josm.openstreetmap.de/ticket/23191">#23191</a>
     */
    public TagTableModel createTagTableModel() {
        // return tagTableModel; // no good: see #23191
        TaggedHandler handler = new DataSetHandler().setDataSet(OsmDataManager.getInstance().getActiveDataSet());
        return new TagTableModel(handler).initFromHandler();
    }

    String getContextKey() {
        int row = tagTable.getEditingRow();
        if (row == -1)
            row = tagTable.getSelectedRow();
        return tagTable.getKey(row);
    }

    @Override
    public String helpTopic() {
        return HelpUtil.ht("/Dialog/TagsMembership");
    }

    private void buildTagTable() {
        // setting up the tags table
        TableHelper.setFont(tagTable, getClass());

        final RemoveHiddenSelection removeHiddenSelection = new RemoveHiddenSelection();
        tagTable.getSelectionModel().addListSelectionListener(removeHiddenSelection);
        tagTable.getRowSorter().addRowSorterListener(removeHiddenSelection);

        AutoCompComboBox<AutoCompletionItem> keyEditor = tagTableUtils.getKeyEditor(null);
        AutoCompComboBox<AutoCompletionItem> valueEditor = tagTableUtils.getValueEditor(null);

        tagTable.setKeyEditor(keyEditor);
        tagTable.setValueEditor(valueEditor);
        tagTable.setRowHeight(keyEditor.getEditorComponent().getPreferredSize().height);
    }

    private void buildMembershipTable() {
        MembershipTableCellRenderer cellRenderer = new MembershipTableCellRenderer();

        TableColumnModel cm = membershipTable.getColumnModel();
        cm.removeColumn(cm.getColumn(0));
        cm.removeColumn(cm.getColumn(0));

        cm.getColumn(0).setCellRenderer(cellRenderer);
        cm.getColumn(1).setCellRenderer(cellRenderer);
        cm.getColumn(2).setCellRenderer(cellRenderer);

        cm.getColumn(0).setPreferredWidth(200);
        cm.getColumn(1).setPreferredWidth(40);
        cm.getColumn(2).setPreferredWidth(20);

        TableHelper.setFont(membershipTable, getClass());
        membershipTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        membershipTable.getTableHeader().setReorderingAllowed(true);
    }

    /**
     * Creates the popup menu @field blankSpaceMenu and its launcher on main panel.
     */
    private void setupBlankSpaceMenu() {
        if (Config.getPref().getBoolean("properties.menu.add_edit_delete", true)) {
            blankSpaceMenuHandler.addAction(addAction);
            PopupMenuLauncher launcher = new BlankSpaceMenuLauncher(blankSpaceMenu);
            bothTables.addMouseListener(launcher);
            tagTable.addMouseListener(launcher);
        }
    }

    private void destroyTaginfoNationalActions() {
        membershipMenuTagInfoNatItems.forEach(membershipMenu::remove);
        membershipMenuTagInfoNatItems.clear();
        tagMenuTagInfoNatItems.forEach(tagMenu::remove);
        tagMenuTagInfoNatItems.clear();
        taginfoNationalActions.clear();
    }

    private void setupTaginfoNationalActions(Collection<? extends IPrimitive> newSel) {
        if (newSel.isEmpty()) {
            return;
        }
        final LatLon center = newSel.iterator().next().getBBox().getCenter();
        List<TaginfoRegionalInstance> regionalInstances = Territories.getRegionalTaginfoUrls(center);
        int newHashCode = regionalInstances.hashCode();
        if (newHashCode == taginfoNationalHash) {
            // taginfoNationalActions are still valid
            return;
        }
        taginfoNationalHash = newHashCode;
        destroyTaginfoNationalActions();
        regionalInstances.stream()
                .map(taginfo -> taginfoAction.withTaginfoUrl(tr("Go to Taginfo ({0})", taginfo.toString()), taginfo.getUrl()))
                .forEach(taginfoNationalActions::add);
        taginfoNationalActions.stream().map(membershipMenu::add).forEach(membershipMenuTagInfoNatItems::add);
        taginfoNationalActions.stream().map(tagMenu::add).forEach(tagMenuTagInfoNatItems::add);
    }

    /**
     * Creates the popup menu @field membershipMenu and its launcher on membership table.
     */
    private void setupMembershipMenu() {
        // setting up the membership table
        if (Config.getPref().getBoolean("properties.menu.add_edit_delete", true)) {
            membershipMenuHandler.addAction(editAction);
            membershipMenuHandler.addAction(deleteAction);
            membershipMenu.addSeparator();
        }
        RelationPopupMenus.setupHandler(membershipMenuHandler,
                EditRelationAction.class, DuplicateRelationAction.class, DeleteRelationsAction.class);
        membershipMenu.addSeparator();
        membershipMenu.add(helpRelAction);
        membershipMenu.add(taginfoAction);

        membershipMenu.addPopupMenuListener(new AbstractTag2LinkPopupListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                getSelectedMembershipRelations().forEach(relation ->
                        relation.visitKeys((primitive, key, value) -> addLinks(membershipMenu, key, value)));
            }
        });

        membershipTable.addMouseListener(new PopupMenuLauncher(membershipMenu) {
            @Override
            protected int checkTableSelection(JTable table, Point p) {
                int row = super.checkTableSelection(table, p);
                List<IRelation<?>> rels = Arrays.stream(table.getSelectedRows())
                        .mapToObj(i -> (IRelation<?>) table.getModel().getValueAt(i, 0))
                        .collect(Collectors.toList());
                membershipMenuHandler.setPrimitives(rels);
                return row;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                //update highlights
                if (MainApplication.isDisplayingMapView()) {
                    int row = membershipTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && highlightHelper.highlightOnly((Relation) membershipTableModel.getValueAt(row, 0))) {
                        MainApplication.getMap().mapView.repaint();
                    }
                }
                super.mouseClicked(e);
            }

            @Override
            public void mouseExited(MouseEvent me) {
                highlightHelper.clear();
            }
        });
    }

    /**
     * Creates the popup menu @field tagMenu and its launcher on tag table.
     * @return the tag menu
     */
    private JPopupMenu buildTagMenu() {
        JPopupMenu tagMenu = new JPopupMenu();
        if (Config.getPref().getBoolean("properties.menu.add_edit_delete", true)) {
            tagMenu.add(addAction);
            tagMenu.add(editAction);
            tagMenu.add(deleteAction);
            tagMenu.addSeparator();
        }
        tagMenu.add(pasteValueAction);
        tagMenu.add(copyValueAction);
        tagMenu.add(copyKeyValueAction);
        tagMenu.addPopupMenuListener(copyKeyValueAction);
        tagMenu.add(copyAllKeyValueAction);
        tagMenu.addSeparator();
        tagMenu.add(searchActionAny);
        tagMenu.add(searchActionSame);
        tagMenu.addSeparator();
        tagMenu.add(helpTagAction);
        tagMenu.add(tagHistoryAction);
        tagMenu.add(taginfoAction);
        tagMenu.addPopupMenuListener(new AbstractTag2LinkPopupListener() {
            @Override
            public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                visitSelectedProperties((primitive, key, value) -> addLinks(tagMenu, key, value));
            }
        });
        return tagMenu;
    }

    /**
     * Sets a filter to restrict the displayed properties.
     * @param filter the filter
     * @since 8980
     */
    public void setFilter(final SearchCompiler.Match filter) {
        tagTable.getRowSorter().setRowFilter(new SearchBasedRowFilter(filter));
    }

    /**
     * Assigns all needed keys like Enter and Spacebar to most important actions.
     */
    private void setupKeyboardShortcuts() {
        // ENTER = editAction, open "edit" dialog
        InputMapUtils.addEnterActionWhenAncestor(membershipTable, editAction);

        InputMap im = getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        // CTRL+ENTER
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_DOWN_MASK), "openEditDialog");
        getActionMap().put("openEditDialog", editAction);

        // CTRL+INSERT
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, KeyEvent.CTRL_DOWN_MASK), "openAddDialog");
        getActionMap().put("openAddDialog", addAction);

        // DEL button = deleteAction
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        getActionMap().put("delete", deleteAction);

        // ESC
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "escape");
        getActionMap().put("escape", new FocusMainWindowAction());

        // unassign some standard shortcuts for JTable to allow upload / download / image browsing
        InputMapUtils.unassignCtrlShiftUpDown(tagTable, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        InputMapUtils.unassignPageUpDown(tagTable, WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        // unassign some standard shortcuts for correct copy-pasting, fix #8508
        tagTable.setTransferHandler(null);

        im.put(Shortcut.getCopyKeyStroke(), "onCopy");
        tagTable.getActionMap().put("onCopy", copyKeyValueAction);

        // allow using enter to add tags for all look&feel configurations
        InputMapUtils.enableEnter(this.btnAdd);

        // F1 button = custom help action
        im.put(HelpAction.getKeyStroke(), "onHelp");
        getActionMap().put("onHelp", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (tagTable.getSelectedRowCount() == 1) {
                    helpTagAction.actionPerformed(e);
                    return;
                }
                if (membershipTable.getSelectedRowCount() == 1) {
                    helpRelAction.actionPerformed(e);
                    return;
                }
            }
        });
    }

    private JosmTextField setupFilter() {
        final JosmTextField f = new DisableShortcutsOnFocusGainedTextField();
        FilterField.setSearchIcon(f);
        f.setToolTipText(tr("Tag filter"));
        final CompileSearchTextDecorator decorator = CompileSearchTextDecorator.decorate(f);
        f.addPropertyChangeListener("filter", evt -> setFilter(decorator.getMatch()));
        return f;
    }

    /**
     * This simply fires up an {@link RelationEditor} for the relation shown; everything else
     * is the editor's business.
     *
     * @param row position
     */
    private void editMembership(int row) {
        Relation relation = (Relation) membershipTableModel.getValueAt(row, 0);
        MemberInfo info = (MemberInfo) membershipTableModel.getValueAt(row, 1);
        MainApplication.getMap().relationListDialog.selectRelation(relation);
        OsmDataLayer layer = MainApplication.getLayerManager().getActiveDataLayer();
        if (!layer.isLocked()) {
            List<RelationMember> members = info.role.stream()
                    .filter(rm -> rm instanceof RelationMember)
                    .map(rm -> (RelationMember) rm)
                    .collect(Collectors.toList());
            RelationEditor.getEditor(layer, relation, members).setVisible(true);
        }
    }

    private static int findViewRow(JTable table, TableModel model, Object value) {
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.getValueAt(i, 0).equals(value))
                return table.convertRowIndexToView(i);
        }
        return -1;
    }

    /**
     * Update selection status, call {@link #selectionChanged} function.
     */
    private void updateSelection() {
        // Parameter is ignored in this class
        selectionChanged(null);
    }

    @Override
    public void showNotify() {
        DatasetEventManager.getInstance().addDatasetListener(dataChangedAdapter, FireMode.IN_EDT_CONSOLIDATED);
        SelectionEventManager.getInstance().addSelectionListenerForEdt(this);
        MainApplication.getLayerManager().addActiveLayerChangeListener(this);
        if (Boolean.TRUE.equals(PROP_PREVIEW_ON_HOVER.get()))
            MainApplication.getMap().mapView.addPrimitiveHoverListener(this);
        for (JosmAction action : josmActions) {
            MainApplication.registerActionShortcut(action);
        }
        updateSelection();
    }

    @Override
    public void hideNotify() {
        DatasetEventManager.getInstance().removeDatasetListener(dataChangedAdapter);
        SelectionEventManager.getInstance().removeSelectionListener(this);
        MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
        MainApplication.getMap().mapView.removePrimitiveHoverListener(this);
        for (JosmAction action : josmActions) {
            MainApplication.unregisterActionShortcut(action);
        }
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        if (b && MainApplication.getLayerManager().getActiveData() != null) {
            updateSelection();
        }
    }

    @Override
    public void primitiveHovered(PrimitiveHoverEvent e) {
        hovered = e.getHoveredPrimitive();
        selectionChanged(null);
    }

    @Override
    public void selectionChanged(SelectionChangeEvent event /*ignored*/) {
        if (tagTable == null)
            return; // selection changed may be received in base class constructor before init
        if (!isVisible())
            return;

        // save the eventual open edit to the *old* selection
        TagTable.SavedState state = tagTable.saveState();
        if (tagTable.isEditing())
            tagTable.endCellEditing(); // save edit

        // Start working with the  new selection.  In draw mode this is not the same as
        // the current selection, see comment at: {@link
        // org.openstreetmap.josm.actions.mapmode.DrawAction#getInProgressSelection
        // DrawAction.getInProgressSelection}
        Collection<OsmPrimitive> newSel = OsmDataManager.getInstance().getInProgressSelection();

        if (hovered != null) {
            if (Boolean.FALSE.equals(PROP_PREVIEW_ON_HOVER_PRIORITIZE_SELECTION.get()) || newSel.isEmpty())
                newSel = Collections.singleton((OsmPrimitive) hovered);
        }

        taggedHandler.setSelection(newSel);
        tagTableUtils.setTypes(newSel);

        //
        // init the tag table
        //
        tagTableModel.initFromHandler();
        state.restore();

        //
        // init the membership table
        //
        int newSelSize = newSel.size();
        IRelation<?> selectedRelation = null;
        if (membershipTable.getSelectedRowCount() == 1) {
            selectedRelation = (IRelation<?>) membershipTableModel.getValueAt(membershipTable.getSelectedRow(), 0);
        }

        final Map<IRelation<?>, MemberInfo> roles = new HashMap<>();
        membershipTableModel.setRowCount(0);

        final Collection<OsmPrimitive> effectivelyFinalNewSel = newSel;
        for (IPrimitive primitive: newSel) {
            for (IPrimitive ref: primitive.getReferrers(true)) {
                if (ref instanceof IRelation && !ref.isIncomplete() && !ref.isDeleted()) {
                    IRelation<?> r = (IRelation<?>) ref;
                    MemberInfo mi = roles.computeIfAbsent(r, ignore -> new MemberInfo(effectivelyFinalNewSel));
                    int i = 1;
                    for (IRelationMember<?> m : r.getMembers()) {
                        if (m.getMember() == primitive) {
                            mi.add(m, i);
                        }
                        ++i;
                    }
                }
            }
        }

        List<IRelation<?>> sortedRelations = new ArrayList<>(roles.keySet());
        sortedRelations.sort((o1, o2) -> {
            int comp = Boolean.compare(o1.isDisabledAndHidden(), o2.isDisabledAndHidden());
            return comp != 0 ? comp : DefaultNameFormatter.getInstance().getRelationComparator().compare(o1, o2);
        });

        for (IRelation<?> r: sortedRelations) {
            membershipTableModel.addRow(new Object[]{r, roles.get(r)});
        }
        TableHelper.setRowHeights(membershipTable);
        membershipTable.getTableHeader().setVisible(membershipTableModel.getRowCount() > 0);
        membershipTable.setVisible(membershipTableModel.getRowCount() > 0);

        //
        // init the preset list
        //
        presets.updatePresets(tagTableUtils.getTypes(), tagTableModel.getTags(),
            taggedHandler, tagTableUtils.getManager());

        //
        // other stuff
        //
        OsmData<?, ?, ?, ?> ds = MainApplication.getLayerManager().getActiveData();
        int tags = tagTableModel.getRowCount() - 1;
        boolean isReadOnly = ds != null && ds.isLocked();
        boolean hasSelection = !newSel.isEmpty();
        boolean hasTags = hasSelection && tags > 0;
        boolean hasMemberships = hasSelection && membershipTableModel.getRowCount() > 0;

        addAction.setEnabled(!isReadOnly && hasSelection);
        editAction.setEnabled(!isReadOnly && (hasTags || hasMemberships));
        deleteAction.setEnabled(!isReadOnly && (hasTags || hasMemberships));
        tagTable.setVisible(hasSelection);
        tagTable.getTableHeader().setVisible(hasTags);
        tagTableFilter.setVisible(hasTags);
        selectSomething.setVisible(!hasSelection);
        pluginHook.setVisible(hasSelection);

        setupTaginfoNationalActions(newSel);
        autoresizeTagTable();

        int selectedIndex;
        if (selectedRelation != null && (selectedIndex = findViewRow(membershipTable, membershipTableModel, selectedRelation)) != -1) {
            membershipTable.changeSelection(selectedIndex, 0, false, false);
        } else if (hasMemberships) {
            membershipTable.changeSelection(0, 0, false, false);
        }

        if (tags != 0 || membershipTableModel.getRowCount() != 0) {
            if (newSelSize > 1) {
                setTitle(tr("Objects: {2} / Tags: {0} / Memberships: {1}",
                    tags, membershipTableModel.getRowCount(), newSelSize));
            } else {
                setTitle(tr("Tags: {0} / Memberships: {1}",
                    tags, membershipTableModel.getRowCount()));
            }
        } else {
            setTitle(tr("Tags/Memberships"));
        }
    }

    private void autoresizeTagTable() {
        if (PROP_AUTORESIZE_TAGS_TABLE.get()) {
            // resize table's columns to fit content
            TableHelper.computeColumnsWidth(tagTable);
        }
    }

    /**
     * Returns true if the focus owner is the container or any one of its children
     * @param container the container
     * @return true if focus in inside container
     */
    private static boolean isFocusIn(Component container) {
        Component f = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        while (f != null) {
            if (f == container)
                return true;
            f = f.getParent();
        }
        return false;
    }

    /**
     * Function that prevents keystrokes intended for text-editing from also triggering
     * global shortcuts.
     * <p>
     * When the panel is docked, there is no JFrame between the table and the main
     * window, so normally all keystrokes used for editing go further up and may trigger
     * global shortcuts.  This function keeps all keys from going further if they have
     * no modifier or a 'shift' modifier only.
     * <p>
     * A downside of this approach is that while any child of this panel has focus, the
     * global shortcuts do not work.  We must thus take pains to not keep focus in this
     * panel longer that strictly necessary.
     * <p>
     * See also: #8710
     */
    @Override
    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed) {
        if (super.processKeyBinding(ks, e, condition, pressed))
            return true; // we actually processed it, in an editor etc.
        // Logging.info("processKeyBindings: {0}", e.paramString());

        // if tagtable is editing ...

        int modifiers = e.getModifiersEx(); // *_DOWN_MASK modifiers
        if (modifiers == 0 || modifiers == KeyEvent.SHIFT_DOWN_MASK)
            return true; // lie about having processed it, so the components further up won't get it
        return false;
    }

    /* ---------------------------------------------------------------------------------- */
    /* PropertyChangedListener                                                            */
    /* ---------------------------------------------------------------------------------- */

    @Override
    public void propertyChange(PropertyChangeEvent e) {
        if (Layer.VISIBLE_PROP.equals(e.getPropertyName())) {
            boolean isVisible = (boolean) e.getNewValue();

            // Disable hover preview when layer is invisible
            if (isVisible && Boolean.TRUE.equals(PROP_PREVIEW_ON_HOVER.get())) {
                MainApplication.getMap().mapView.addPrimitiveHoverListener(this);
            } else {
                MainApplication.getMap().mapView.removePrimitiveHoverListener(this);
            }
        }

        if ("focusOwner".equals(e.getPropertyName())) {
            // change table background color when table gets focus
            Color background = UIManager.getColor("TextField.inactiveBackground");
            Color focusedBackground = UIManager.getColor("Table.background");

            if (isFocusIn(tagTable)) {
                membershipTable.clearSelection();
                tagTable.setBackground(focusedBackground);
            } else {
                tagTable.setBackground(background);
            }
            if (isFocusIn(membershipTable)) {
                tagTable.clearSelection();
                membershipTable.setBackground(focusedBackground);
            } else {
                membershipTable.setBackground(background);
            }
            if (isFocusIn(MainApplication.getMap().mapView)) {
                tagTable.clearSelection();
                membershipTable.clearSelection();
            }
        }
    }

    /* ---------------------------------------------------------------------------------- */
    /* PreferenceChangedListener                                                          */
    /* ---------------------------------------------------------------------------------- */

    /**
     * Reloads data when the {@code display.discardable-keys} preference changes
     */
    @Override
    public void preferenceChanged(PreferenceChangeEvent e) {
        super.preferenceChanged(e);
        if (PROP_DISPLAY_DISCARDABLE_KEYS.getKey().equals(e.getKey())) {
            if (MainApplication.getLayerManager().getActiveData() != null) {
                updateSelection();
            }
        }
    }

    /* ---------------------------------------------------------------------------------- */
    /* TaggingPresetListener                                                              */
    /* ---------------------------------------------------------------------------------- */

    /**
     * Updates the preset list when Presets preference changes.
     */
    @Override
    public void taggingPresetsModified() {
        if (MainApplication.getLayerManager().getActiveData() != null) {
            updateSelection();
        }
    }

    /* ---------------------------------------------------------------------------------- */
    /* ActiveLayerChangeListener                                                          */
    /* ---------------------------------------------------------------------------------- */
    @Override
    public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
        if (e.getSource().getEditLayer() == null) {
            editHelper.saveTagsIfNeeded();
        }

        DataSet dataSet = e.getSource().getActiveDataSet();
        if (dataSet != null) {
            tagTableUtils.setManager(AutoCompletionManager.of(dataSet));
        }
        Layer newLayer = e.getSource().getActiveDataLayer();
        if (newLayer != null) {
            newLayer.addPropertyChangeListener(this);
            if (newLayer.isVisible() && Boolean.TRUE.equals(PROP_PREVIEW_ON_HOVER.get())) {
                MainApplication.getMap().mapView.addPrimitiveHoverListener(this);
            } else {
                MainApplication.getMap().mapView.removePrimitiveHoverListener(this);
            }
        }

        // it is time to save history of tags
        updateSelection();
    }

    @Override
    public void processDatasetEvent(AbstractDatasetChangedEvent event) {
        updateSelection();
    }

    /**
     * Returns the selected tag. Value is empty if several tags are selected for a given key.
     * @return The current selected tag
     */
    public Tag getSelectedProperty() {
        Tags tags = getSelectedProperties();
        return tags == null ? null : new Tag(
                tags.getKey(),
                tags.getValues().size() > 1 ? "" : tags.getValues().iterator().next());
    }

    /**
     * Returns the selected tags. Contains all values if several are selected for a given key.
     * @return The current selected tags
     * @since 15376
     */
    public Tags getSelectedProperties() {
        int row = tagTable.getSelectedRow();
        if (row == -1)
            return null;
        return new Tags(tagTable.getKey(row), tagTable.getValueType(row).values());
    }

    /**
     * Visits all combinations of the selected keys/values.
     * @param visitor the visitor
     * @since 15707
     */
    public void visitSelectedProperties(KeyValueVisitor visitor) {
        for (int row : tagTable.getSelectedRows()) {
            final String key = tagTable.getKey(row);
            tagTable.getValueType(row).values().forEach(value -> visitor.visitKeyValue(null, key, value));
        }
    }

    /**
     * Replies the membership popup menu handler.
     * @return The membership popup menu handler
     */
    public PopupMenuHandler getMembershipPopupMenuHandler() {
        return membershipMenuHandler;
    }

    /**
     * Returns the selected relation membership.
     * @return The current selected relation membership
     */
    public IRelation<?> getSelectedMembershipRelation() {
        int row = membershipTable.getSelectedRow();
        return row > -1 ? (IRelation<?>) membershipTableModel.getValueAt(row, 0) : null;
    }

    /**
     * Returns all selected relation memberships.
     * @return The selected relation memberships
     * @since 15707
     */
    public Collection<IRelation<?>> getSelectedMembershipRelations() {
        return Arrays.stream(membershipTable.getSelectedRows())
                .mapToObj(row -> (IRelation<?>) membershipTableModel.getValueAt(row, 0))
                .collect(Collectors.toList());
    }

    /**
     * Table cell renderer
     *
     * Displays an icon for the relation type.
     * Uses italics if the relation is disabled.
     */
    static final class MembershipTableCellRenderer extends DefaultTableCellRenderer {
        private boolean disabled;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                boolean isSelected, boolean hasFocus, int row, int column) {
            Component c = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
            if (c instanceof JLabel) {
                JLabel label = (JLabel) c;

                // add icon to relations
                if (value instanceof Relation) {
                    Relation relation = (Relation) value;
                    label.setText(relation.getDisplayName(DefaultNameFormatter.getInstance()));
                    label.setIcon(ImageProvider.getPadded((OsmPrimitive) value, ImageSizes.SMALLICON));
                    label.setIconTextGap(8);
                    // this will disable the whole table row
                    disabled = relation.isDisabledAndHidden();
                } else {
                    label.setText((String) value);
                    label.setIcon(null);
                }
                label.setFont(label.getFont().deriveFont(disabled ? Font.ITALIC : Font.PLAIN));
            }
            return c;
        }
    }

    static final class BlankSpaceMenuLauncher extends PopupMenuLauncher {
        BlankSpaceMenuLauncher(JPopupMenu menu) {
            super(menu);
        }

        @Override
        protected boolean checkSelection(Component component, Point p) {
            if (component instanceof JTable) {
                return ((JTable) component).rowAtPoint(p) == -1;
            }
            return true;
        }
    }

    /**
     * Class that watches for mouse clicks
     * @author imi
     */
    public class MouseClickWatch extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() < 2)
                return;
            if (e.getSource() == membershipTable) {
                int row = membershipTable.convertRowIndexToModel(membershipTable.rowAtPoint(e.getPoint()));
                int col = membershipTable.convertColumnIndexToModel(membershipTable.columnAtPoint(e.getPoint()));
                if (row < 0)
                    return;
                if (col == 3) {
                    final Relation relation = (Relation) membershipTableModel.getValueAt(row, 0);
                    final MemberInfo info = (MemberInfo) membershipTableModel.getValueAt(row, 1);
                    RelationRoleEditor.editRole(relation, info);
                } else {
                    editMembership(row);
                }
            } else {
                // double-click in the empty area of tag table
                // (on the rows it is consumed by the combo box)
                editHelper.addTag(createTagTableModel());
            }
        }
    }

    /**
     * Requests focus in main window in order to enable the expected keyboard shortcuts (see #8710).
     */
    static class FocusMainWindowAction extends AbstractAction {
        @Override
        public void actionPerformed(ActionEvent ae) {
            // tagTable.clearSelection();
            // membershipTable.clearSelection();
            MainApplication.getMap().mapView.requestFocus();
        }
    }

    static class MemberInfo {
        private final List<IRelationMember<?>> role = new ArrayList<>();
        private Set<IPrimitive> members = new HashSet<>();
        private List<Integer> position = new ArrayList<>();
        private Collection<? extends IPrimitive> selection;
        private String positionString;
        private String roleString;

        MemberInfo(Collection<? extends IPrimitive> selection) {
            this.selection = selection;
        }

        void add(IRelationMember<?> r, Integer p) {
            role.add(r);
            members.add(r.getMember());
            position.add(p);
        }

        String getPositionString() {
            if (positionString == null) {
                positionString = Utils.getPositionListString(position);
                // if not all objects from the selection are member of this relation
                if (selection.stream().anyMatch(p -> !members.contains(p))) {
                    positionString += ",\u2717";
                }
                members = null;
                position = null;
                selection = null;
            }
            return Utils.shortenString(positionString, 20);
        }

        List<IRelationMember<?>> getRole() {
            return Collections.unmodifiableList(role);
        }

        String getRoleString() {
            if (roleString == null) {
                for (IRelationMember<?> r : role) {
                    if (roleString == null) {
                        roleString = r.getRole();
                    } else if (!roleString.equals(r.getRole())) {
                        roleString = DIFFERENT_I18N;
                        break;
                    }
                }
            }
            return roleString;
        }

        @Override
        public String toString() {
            return "MemberInfo{" +
                    "roles='" + roleString + '\'' +
                    ", positions='" + positionString + '\'' +
                    '}';
        }
    }

    /**
     * Action handling delete button press in properties dialog.
     */
    class DeleteAction extends JosmAction implements ListSelectionListener {

        private static final String DELETE_FROM_RELATION_PREF = "delete_from_relation";

        DeleteAction() {
            super(tr("Delete"), /* ICON() */ "dialogs/delete", tr("Delete the selected key in all objects"),
                    Shortcut.registerShortcut("properties:delete", tr("Delete Tags"), KeyEvent.VK_D,
                            Shortcut.ALT_CTRL_SHIFT), false);
            updateEnabledState();
        }

        protected void deleteFromRelation(int row) {
            Relation cur = (Relation) membershipTableModel.getValueAt(row, 0);

            Relation nextRelation = null;
            int rowCount = membershipTable.getRowCount();
            if (rowCount > 1) {
                nextRelation = (Relation) membershipTableModel.getValueAt(row + 1 < rowCount ? row + 1 : row - 1, 0);
            }

            ExtendedDialog ed = new ExtendedDialog(MainApplication.getMainFrame(),
                    tr("Change relation"),
                    tr("Delete from relation"), tr("Cancel"));
            ed.setButtonIcons("dialogs/delete", "cancel");
            ed.setContent(tr("Really delete selection from relation {0}?", cur.getDisplayName(DefaultNameFormatter.getInstance())));
            ed.toggleEnable(DELETE_FROM_RELATION_PREF);

            if (ed.showDialog().getValue() != 1)
                return;

            List<RelationMember> members = cur.getMembers();
            for (OsmPrimitive primitive: OsmDataManager.getInstance().getInProgressSelection()) {
                members.removeIf(rm -> rm.getMember() == primitive);
            }
            UndoRedoHandler.getInstance().add(new ChangeMembersCommand(cur, members));

            tagTable.clearSelection();
            if (nextRelation != null) {
                membershipTable.changeSelection(findViewRow(membershipTable, membershipTableModel, nextRelation), 0, false, false);
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (tagTable.getSelectedRowCount() > 0) {
                tagTable.getActionMap().get("delete").actionPerformed(e);
            } else if (membershipTable.getSelectedRowCount() > 0) {
                ConditionalOptionPaneUtil.startBulkOperation(DELETE_FROM_RELATION_PREF);
                int[] rows = membershipTable.getSelectedRows();
                // delete from last relation to conserve row numbers in the table
                for (int i = rows.length-1; i >= 0; i--) {
                    deleteFromRelation(rows[i]);
                }
                ConditionalOptionPaneUtil.endBulkOperation(DELETE_FROM_RELATION_PREF);
            }
        }

        @Override
        protected final void updateEnabledState() {
            DataSet ds = OsmDataManager.getInstance().getActiveDataSet();
            setEnabled(ds != null && !ds.isLocked() &&
                    ((tagTable != null && tagTable.getSelectedRowCount() >= 1)
                    || (membershipTable != null && membershipTable.getSelectedRowCount() > 0)
                    ));
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }
    }

    /**
     * Action handling add button press in properties dialog.
     */
    class AddAction extends JosmAction {
        AtomicBoolean isPerforming = new AtomicBoolean(false);
        AddAction() {
            super(tr("Add"), /* ICON() */ "dialogs/add", tr("Add a new key/value pair to all objects"),
                    Shortcut.registerShortcut("properties:add", tr("Add Tag"), KeyEvent.VK_A,
                            Shortcut.ALT), false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!/*successful*/isPerforming.compareAndSet(false, true)) {
                return;
            }
            try {
                editHelper.addTag(createTagTableModel());
                // btnAdd.requestFocusInWindow();
            } finally {
                isPerforming.set(false);
            }
        }
    }

    /**
     * Action handling edit button press in properties dialog.
     */
    class EditAction extends JosmAction implements ListSelectionListener {
        AtomicBoolean isPerforming = new AtomicBoolean(false);
        EditAction() {
            super(tr("Edit"), /* ICON() */ "dialogs/edit", tr("Edit the value of the selected key for all objects"),
                    Shortcut.registerShortcut("properties:edit", tr("Edit: {0}", tr("Edit Tags")), KeyEvent.VK_S,
                            Shortcut.ALT), false);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!/*successful*/isPerforming.compareAndSet(false, true)) {
                return;
            }
            try {
                if (tagTable.getSelectedRowCount() == 1) {
                    int row = tagTable.getSelectedRow();
                    String key = tagTable.getKey(row);
                    if (key.isEmpty()) {
                        editHelper.addTag(createTagTableModel());
                    } else {
                        editHelper.editTag(createTagTableModel(), key, false);
                    }
                } else if (membershipTable.getSelectedRowCount() == 1) {
                    int row = membershipTable.getSelectedRow();
                    editMembership(row);
                }
            } finally {
                isPerforming.set(false);
            }
        }

        @Override
        protected void updateEnabledState() {
            DataSet ds = OsmDataManager.getInstance().getActiveDataSet();
            setEnabled(ds != null && !ds.isLocked() &&
                    ((tagTable != null && tagTable.getSelectedRowCount() == 1)
                    ^ (membershipTable != null && membershipTable.getSelectedRowCount() == 1)
                    ));
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }
    }

    class PasteValueAction extends AbstractAction {
        PasteValueAction() {
            putValue(NAME, tr("Paste Value"));
            putValue(SHORT_DESCRIPTION, tr("Paste the value of the selected tag from clipboard"));
            new ImageProvider("paste").getResource().attachImageIcon(this, true);
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            if (tagTable.getSelectedRowCount() != 1)
                return;
            String key = tagTable.getKey(tagTable.getSelectedRow());
            Collection<? extends OsmPrimitive> sel = taggedHandler.get();
            String clipboard = ClipboardUtils.getClipboardStringContent();
            if (sel.isEmpty() || clipboard == null || sel.iterator().next().getDataSet().isLocked())
                return;
            UndoRedoHandler.getInstance().add(new ChangePropertyCommand(sel, key, Utils.strip(clipboard)));
        }
    }

    class SearchAction extends AbstractAction {
        private final boolean sameType;

        SearchAction(boolean sameType) {
            this.sameType = sameType;
            if (sameType) {
                putValue(NAME, tr("Search Key/Value/Type"));
                putValue(SHORT_DESCRIPTION, tr("Search with the key and value of the selected tag, restrict to type (i.e., node/way/relation)"));
                new ImageProvider("dialogs/search").getResource().attachImageIcon(this, true);
            } else {
                putValue(NAME, tr("Search Key/Value"));
                putValue(SHORT_DESCRIPTION, tr("Search with the key and value of the selected tag"));
                new ImageProvider("dialogs/search").getResource().attachImageIcon(this, true);
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (tagTable.getSelectedRowCount() != 1)
                return;
            String key = tagTable.getKey(tagTable.getSelectedRow());
            Collection<? extends IPrimitive> sel = OsmDataManager.getInstance().getInProgressISelection();
            if (sel.isEmpty())
                return;
            final SearchSetting ss = createSearchSetting(key, sel, sameType);
            org.openstreetmap.josm.actions.search.SearchAction.searchStateless(ss);
        }
    }

    static SearchSetting createSearchSetting(String key, Collection<? extends IPrimitive> sel, boolean sameType) {
        String sep = "";
        StringBuilder s = new StringBuilder();
        Set<String> consideredTokens = new TreeSet<>();
        for (IPrimitive p : sel) {
            String val = p.get(key);
            if (val == null || (!sameType && consideredTokens.contains(val))) {
                continue;
            }
            String t = "";
            if (!sameType) {
                t = "";
            } else if (p instanceof Node) {
                t = "type:node ";
            } else if (p instanceof Way) {
                t = "type:way ";
            } else if (p instanceof Relation) {
                t = "type:relation ";
            }
            String token = new StringBuilder(t).append(val).toString();
            if (consideredTokens.add(token)) {
                s.append(sep).append('(').append(t).append(SearchCompiler.buildSearchStringForTag(key, val)).append(')');
                sep = " OR ";
            }
        }

        final SearchSetting ss = new SearchSetting();
        ss.text = s.toString();
        ss.caseSensitive = true;
        return ss;
    }

    /**
     * Clears the row selection when it is filtered away by the row sorter.
     */
    private class RemoveHiddenSelection implements ListSelectionListener, RowSorterListener {

        void removeHiddenSelection() {
            try {
                tagTable.getRowSorter().convertRowIndexToModel(tagTable.getSelectedRow());
            } catch (IndexOutOfBoundsException ignore) {
                Logging.trace(ignore);
                Logging.trace("Clearing tagTable selection");
                tagTable.clearSelection();
            }
        }

        @Override
        public void valueChanged(ListSelectionEvent event) {
            removeHiddenSelection();
        }

        @Override
        public void sorterChanged(RowSorterEvent e) {
            removeHiddenSelection();
        }
    }

    private class HoverPreviewPropListener implements ValueChangeListener<Boolean> {
        @Override
        public void valueChanged(ValueChangeEvent<? extends Boolean> e) {
            if (Boolean.TRUE.equals(e.getProperty().get()) && isDialogShowing()) {
                MainApplication.getMap().mapView.addPrimitiveHoverListener(PropertiesDialog.this);
            } else if (Boolean.FALSE.equals(e.getProperty().get())) {
                MainApplication.getMap().mapView.removePrimitiveHoverListener(PropertiesDialog.this);
            }
        }
    }

    /*
     * Ensure HoverListener is re-added when selection priority is disabled while something is selected.
     * Otherwise user would need to change selection to see the preference change take effect.
     */
    private class HoverPreviewPreferSelectionPropListener implements ValueChangeListener<Boolean> {
        @Override
        public void valueChanged(ValueChangeEvent<? extends Boolean> e) {
            if (Boolean.FALSE.equals(e.getProperty().get()) &&
                Boolean.TRUE.equals(PROP_PREVIEW_ON_HOVER.get()) &&
                isDialogShowing()) {
                MainApplication.getMap().mapView.addPrimitiveHoverListener(PropertiesDialog.this);
            }
        }
    }
}
