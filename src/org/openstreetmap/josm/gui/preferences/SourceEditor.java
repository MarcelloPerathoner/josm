// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EventObject;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.data.preferences.NamedColorProperty;
import org.openstreetmap.josm.data.preferences.sources.ExtendedSourceEntry;
import org.openstreetmap.josm.data.preferences.sources.SourceEntry;
import org.openstreetmap.josm.data.preferences.sources.SourcePrefHelper;
import org.openstreetmap.josm.data.preferences.sources.SourceProvider;
import org.openstreetmap.josm.data.preferences.sources.SourceType;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.HelpAwareOptionPane;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.util.DocumentAdapter;
import org.openstreetmap.josm.gui.util.FileFilterAllFiles;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.util.ReorderableTableModel;
import org.openstreetmap.josm.gui.util.TableHelper;
import org.openstreetmap.josm.gui.widgets.AbstractFileChooser;
import org.openstreetmap.josm.gui.widgets.FileChooserManager;
import org.openstreetmap.josm.gui.widgets.FilterField;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.io.NetworkManager;
import org.openstreetmap.josm.io.OnlineResource;
import org.openstreetmap.josm.io.OsmTransferException;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageOverlay;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;
import org.openstreetmap.josm.tools.LanguageInfo;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;
import org.xml.sax.SAXException;

/**
 * Editor for JOSM extensions source entries.
 * @since 1743
 */
public abstract class SourceEditor extends JPanel {

    /** the type of source entry **/
    protected final SourceType sourceType;
    /** there will be a checkbox if true **/
    protected final boolean canEnable;

    /** the table of active sources **/
    protected final JTable tblActiveSources;
    /** the underlying model of active sources **/
    protected final ActiveSourcesModel activeSourcesModel;
    /** the list of available sources **/
    protected final JTable tblAvailableSources;
    /** the underlying model of available sources **/
    protected final AvailableSourcesModel availableSourcesModel;
    /** the URL from which the available sources are fetched **/
    protected final String availableSourcesUrl;
    /** the list of source providers **/
    protected final transient List<SourceProvider> sourceProviders;

    private JTable tblIconPaths;
    private IconPathTableModel iconPathsModel;

    /** determines if the source providers have been initially loaded **/
    protected boolean sourcesInitiallyLoaded;

    /**
     * Constructs a new {@code SourceEditor}.
     * @param sourceType the type of source managed by this editor
     * @param availableSourcesUrl the URL to the list of available sources
     * @param sourceProviders the list of additional source providers, from plugins
     * @param handleIcons {@code true} if icons may be managed, {@code false} otherwise
     */
    protected SourceEditor(SourceType sourceType, String availableSourcesUrl, List<SourceProvider> sourceProviders, boolean handleIcons) {

        this.sourceType = sourceType;
        this.canEnable = sourceType == SourceType.MAP_PAINT_STYLE || sourceType == SourceType.TAGCHECKER_RULE;

        DefaultListSelectionModel selectionModel = new DefaultListSelectionModel();

        this.availableSourcesModel = new AvailableSourcesModel();
        this.tblAvailableSources = new JTable();
        availableSourcesModel.addTableModelListener(e ->
            TableHelper.setFixedColumnWidth(tblAvailableSources, 0)
        );
        // Note: The model must be set after the setColumnWidth listener. See #20849
        this.tblAvailableSources.setModel(availableSourcesModel);
        this.tblAvailableSources.setAutoCreateRowSorter(true);
        TableHelper.setRowHeight(tblAvailableSources, ImageProvider.ImageSizes.TABLE);

        this.tblAvailableSources.setSelectionModel(selectionModel);
        final FancySourceEntryTableCellRenderer availableSourcesEntryRenderer = new FancySourceEntryTableCellRenderer();
        this.tblAvailableSources.getColumnModel().getColumn(0).setCellRenderer(availableSourcesEntryRenderer);
        GuiHelper.extendTooltipDelay(tblAvailableSources);
        this.availableSourcesUrl = availableSourcesUrl;
        this.sourceProviders = sourceProviders;

        selectionModel = new DefaultListSelectionModel();
        activeSourcesModel = new ActiveSourcesModel(selectionModel);
        tblActiveSources = new JTable(activeSourcesModel);
        tblActiveSources.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        tblActiveSources.setSelectionModel(selectionModel);
        tblActiveSources.setAutoscrolls(false);
        TableHelper.setRowHeight(tblActiveSources, ImageProvider.ImageSizes.TABLE); // make both tables same row height
        Stream.of(tblAvailableSources, tblActiveSources).forEach(t -> {
            t.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            t.setShowGrid(false);
            t.setIntercellSpacing(new Dimension(0, 0));
            t.setTableHeader(null);
            t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
            t.setFillsViewportHeight(true);
        });
        SourceEntryTableCellRenderer sourceEntryRenderer = new SourceEntryTableCellRenderer();
        TableColumnModel cm = tblActiveSources.getColumnModel(); // columns: source, active
        if (canEnable) {
            cm.moveColumn(1, 0); // put the checkbox before the text
            cm.getColumn(0).setPreferredWidth(40);
            cm.getColumn(0).setResizable(false);
            cm.getColumn(1).setCellRenderer(sourceEntryRenderer);
        } else {
            cm.removeColumn(cm.getColumn(1)); // remove the checkbox column
            cm.getColumn(0).setCellRenderer(sourceEntryRenderer);
        }

        activeSourcesModel.addTableModelListener(e -> {
            availableSourcesEntryRenderer.updateSources(activeSourcesModel.getSources());
            TableHelper.setFixedColumnWidth(tblActiveSources, canEnable ? 1 : 0);
            tblAvailableSources.repaint();
        });
        tblActiveSources.addPropertyChangeListener(evt -> {
            availableSourcesEntryRenderer.updateSources(activeSourcesModel.getSources());
            tblAvailableSources.repaint();
        });
        activeSourcesModel.setActiveSources(getInitialSourcesList());

        final EditActiveSourceAction editActiveSourceAction = new EditActiveSourceAction();
        tblActiveSources.getSelectionModel().addListSelectionListener(editActiveSourceAction);
        tblActiveSources.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = tblActiveSources.rowAtPoint(e.getPoint());
                    int col = tblActiveSources.columnAtPoint(e.getPoint());
                    if (row < 0 || row >= tblActiveSources.getRowCount())
                        return;
                    if (canEnable && col != 1)
                        return;
                    editActiveSourceAction.actionPerformed(null);
                }
            }
        });

        RemoveActiveSourcesAction removeActiveSourcesAction = new RemoveActiveSourcesAction();
        tblActiveSources.getSelectionModel().addListSelectionListener(removeActiveSourcesAction);
        tblActiveSources.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        tblActiveSources.getActionMap().put("delete", removeActiveSourcesAction);

        MoveUpDownAction moveUp = null;
        MoveUpDownAction moveDown = null;
        if (sourceType == SourceType.MAP_PAINT_STYLE) {
            moveUp = new MoveUpDownAction(false);
            moveDown = new MoveUpDownAction(true);
            tblActiveSources.getSelectionModel().addListSelectionListener(moveUp);
            tblActiveSources.getSelectionModel().addListSelectionListener(moveDown);
            activeSourcesModel.addTableModelListener(moveUp);
            activeSourcesModel.addTableModelListener(moveDown);
        }

        ActivateSourcesAction activateSourcesAction = new ActivateSourcesAction();
        tblAvailableSources.getSelectionModel().addListSelectionListener(activateSourcesAction);
        JButton activate = new JButton(activateSourcesAction);

        setLayout(new GridBagLayout());
        JPanel stylesPanel = new JPanel(new GridBagLayout());

        stylesPanel.add(new JLabel(getStr(I18nString.AVAILABLE_SOURCES)), GBC.std().span(2));
        stylesPanel.add(new JLabel(getStr(I18nString.ACTIVE_SOURCES)), GBC.eol());

        JPanel availableStylesPanel = new JPanel(new GridBagLayout());
        FilterField availableSourcesFilter = new FilterField().filter(tblAvailableSources, availableSourcesModel);
        JScrollPane sp1 = new JScrollPane(tblAvailableSources);
        availableStylesPanel.add(availableSourcesFilter, GBC.eol().fill(GridBagConstraints.HORIZONTAL));
        availableStylesPanel.add(sp1, GBC.eol().fill());
        stylesPanel.add(availableStylesPanel, GBC.std().fill());

        JToolBar middleTB = createVerticalToolbar();
        middleTB.add(activate);
        stylesPanel.add(middleTB, GBC.std().anchor(GridBagConstraints.CENTER));

        JScrollPane sp = new JScrollPane(tblActiveSources);
        stylesPanel.add(sp, GBC.std().fill());
        sp.setColumnHeaderView(null);

        JToolBar sideButtonTB = createVerticalToolbar();
        sideButtonTB.add(new NewActiveSourceAction());
        sideButtonTB.add(editActiveSourceAction);
        sideButtonTB.add(removeActiveSourcesAction);
        if (sourceType == SourceType.MAP_PAINT_STYLE) {
            sideButtonTB.addSeparator();
            sideButtonTB.add(moveUp);
            sideButtonTB.add(moveDown);
        }
        stylesPanel.add(sideButtonTB, GBC.eop().fill(GridBagConstraints.VERTICAL));

        JToolBar bottomTB = new JToolBar(); // has a BoxLayout
        bottomTB.setFloatable(false);
        bottomTB.setBorderPainted(false);
        bottomTB.setOpaque(false);
        bottomTB.add(new ReloadSourcesAction(availableSourcesUrl, sourceProviders));
        bottomTB.add(GBC.glue(1, 0));
        bottomTB.add(new ResetAction());
        stylesPanel.add(bottomTB, GBC.eol().span(3).fill(GridBagConstraints.HORIZONTAL));

        // hack: make the panels' heights exactly 4 to 1
        stylesPanel.setPreferredSize(new Dimension(1, 1));
        add(stylesPanel, GBC.eop().fill().weight(1, 4)); // weight must be last

        // Icon configuration
        if (handleIcons) {
            add(new JSeparator(), GBC.eop().fill(GridBagConstraints.HORIZONTAL));
            JPanel iconsPanel = buildIconsPanel();
            // hack: make the panels' heights exactly 4 to 1
            iconsPanel.setPreferredSize(new Dimension(1, 1));
            add(iconsPanel, GBC.eop().fill().weight(1, 1)); // weight must be last
        }
    }

    private JPanel buildIconsPanel() {
        JPanel iconsPanel = new JPanel(new GridBagLayout());

        DefaultListSelectionModel selectionModel = new DefaultListSelectionModel();
        iconPathsModel = new IconPathTableModel(selectionModel);
        tblIconPaths = new JTable(iconPathsModel);
        TableHelper.setFont(tblIconPaths, getClass());
        tblIconPaths.setSelectionModel(selectionModel);
        tblIconPaths.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tblIconPaths.setTableHeader(null);
        tblIconPaths.getColumnModel().getColumn(0).setCellEditor(new FileOrUrlCellEditor(false));
        tblIconPaths.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        iconPathsModel.setIconPaths(getInitialIconPathsList());

        EditIconPathAction editIconPathAction = new EditIconPathAction();
        tblIconPaths.getSelectionModel().addListSelectionListener(editIconPathAction);

        RemoveIconPathAction removeIconPathAction = new RemoveIconPathAction();
        tblIconPaths.getSelectionModel().addListSelectionListener(removeIconPathAction);
        tblIconPaths.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete");
        tblIconPaths.getActionMap().put("delete", removeIconPathAction);

        iconsPanel.add(new JLabel(tr("Icon paths:")), GBC.eop());

        JScrollPane sp = new JScrollPane(tblIconPaths);
        iconsPanel.add(sp, GBC.std().fill());
        sp.setColumnHeaderView(null);

        JToolBar sideButtonTBIcons = createVerticalToolbar();
        sideButtonTBIcons.add(new NewIconPathAction());
        sideButtonTBIcons.add(editIconPathAction);
        sideButtonTBIcons.add(removeIconPathAction);
        iconsPanel.add(sideButtonTBIcons, GBC.eop().fill(GridBagConstraints.VERTICAL));

        iconsPanel.setPreferredSize(new Dimension(1, 1));
        return iconsPanel;
    }

    private JToolBar createVerticalToolbar() {
        JToolBar toolbar = new JToolBar(SwingConstants.VERTICAL);
        toolbar.setFloatable(false);
        toolbar.setBorderPainted(false);
        toolbar.setOpaque(false);
        return toolbar;
    }

    /**
     * Load the list of source entries that the user has configured.
     * @return list of source entries that the user has configured
     */
    public abstract List<SourceEntry> getInitialSourcesList();

    /**
     * Load the list of configured icon paths.
     * @return list of configured icon paths
     */
    public abstract Collection<String> getInitialIconPathsList();

    /**
     * Get the default list of entries (used when resetting the list).
     * @return default list of entries
     */
    public abstract Collection<ExtendedSourceEntry> getDefault();

    /**
     * Save the settings after user clicked "Ok".
     * @return true if restart is required
     */
    public abstract boolean finish();

    /**
     * Default implementation of {@link #finish}.
     * @param prefHelper Helper class for specialized extensions preferences
     * @param iconPref icons path preference
     * @return true if restart is required
     */
    protected boolean doFinish(SourcePrefHelper prefHelper, String iconPref) {
        boolean changed = prefHelper.put(activeSourcesModel.getSources());

        if (tblIconPaths != null) {
            List<String> iconPaths = iconPathsModel.getIconPaths();

            if (!iconPaths.isEmpty()) {
                if (Config.getPref().putList(iconPref, iconPaths)) {
                    changed = true;
                }
            } else if (Config.getPref().putList(iconPref, null)) {
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Provide the GUI strings. (There are differences for MapPaint, Preset and TagChecker Rule)
     * @param ident any {@link I18nString} value
     * @return the translated string for {@code ident}
     */
    protected abstract String getStr(I18nString ident);

    /**
     * A table that does not scroll the selected cell into view horizontally
     * <p>
     * Normally a table would scroll the selected cell into view both horizontally and
     * vertically.  But that would have the effect of scrolling the checkbox column out
     * of view whenever a user clicks on the text.
     */
    static final class ScrollHackTable extends JTable {
        ScrollHackTable(TableModel dm) {
            super(dm);
        }

        // some kind of hack to prevent the table from scrolling slightly to the right when clicking on the text
        @Override
        public void scrollRectToVisible(Rectangle aRect) {
            super.scrollRectToVisible(new Rectangle(0, aRect.y, aRect.width, aRect.height));
        }
    }

    /**
     * Identifiers for strings that need to be provided.
     */
    public enum I18nString {
        /** Available (styles|presets|rules) */
        AVAILABLE_SOURCES,
        /** Active (styles|presets|rules) */
        ACTIVE_SOURCES,
        /** Add a new (style|preset|rule) by entering filename or URL */
        NEW_SOURCE_ENTRY_TOOLTIP,
        /** New (style|preset|rule) entry */
        NEW_SOURCE_ENTRY,
        /** Remove the selected (styles|presets|rules) from the list of active (styles|presets|rules) */
        REMOVE_SOURCE_TOOLTIP,
        /** Edit the filename or URL for the selected active (style|preset|rule) */
        EDIT_SOURCE_TOOLTIP,
        /** Add the selected available (styles|presets|rules) to the list of active (styles|presets|rules) */
        ACTIVATE_TOOLTIP,
        /** Reloads the list of available (styles|presets|rules) */
        RELOAD_ALL_AVAILABLE,
        /** Loading (style|preset|rule) sources */
        LOADING_SOURCES_FROM,
        /** Failed to load the list of (style|preset|rule) sources */
        FAILED_TO_LOAD_SOURCES_FROM,
        /** /Preferences/(Styles|Presets|Rules)#FailedToLoad(Style|Preset|Rule)Sources */
        FAILED_TO_LOAD_SOURCES_FROM_HELP_TOPIC,
        /** Illegal format of entry in (style|preset|rule) list */
        ILLEGAL_FORMAT_OF_ENTRY
    }

    /**
     * Determines whether the list of active sources has changed.
     * @return {@code true} if the list of active sources has changed, {@code false} otherwise
     */
    public boolean hasActiveSourcesChanged() {
        List<SourceEntry> prev = getInitialSourcesList();
        List<SourceEntry> cur = activeSourcesModel.getSources();
        if (prev.size() != cur.size())
            return true;
        Iterator<SourceEntry> p = prev.iterator();
        Iterator<SourceEntry> c = cur.iterator();
        while (p.hasNext()) {
            SourceEntry pe = p.next();
            SourceEntry ce = c.next();
            if (!Objects.equals(pe.url, ce.url) || !Objects.equals(pe.name, ce.name) || pe.active != ce.active)
                return true;
        }
        return false;
    }

    /**
     * Returns the table model
     * @return the model
     */
    public ActiveSourcesModel getModel() {
        return activeSourcesModel;
    }

    /**
     * Returns the list of active sources.
     * @return the list of active sources
     */
    public List<SourceEntry> getActiveSources() {
        return activeSourcesModel.getSources();
    }

    /**
     * Synchronously loads available sources and returns the parsed list.
     * @return list of available sources
     * @throws OsmTransferException in case of OSM transfer error
     * @throws IOException in case of any I/O error
     * @throws SAXException in case of any SAX error
     */
    public final Collection<ExtendedSourceEntry> loadAndGetAvailableSources() throws SAXException, IOException, OsmTransferException {
        final SourceLoader loader = new SourceLoader(availableSourcesUrl, sourceProviders);
        loader.realRun();
        return loader.sources;
    }

    /**
     * Remove sources associated with given indexes from active list.
     * @param idxs indexes of sources to remove
     */
    public void removeSources(Collection<Integer> idxs) {
        activeSourcesModel.removeIdxs(idxs);
    }

    /**
     * Reload available sources.
     * @param url the URL from which the available sources are fetched
     * @param sourceProviders the list of source providers
     */
    protected void reloadAvailableSources(String url, List<SourceProvider> sourceProviders) {
        MainApplication.worker.submit(new SourceLoader(url, sourceProviders));
    }

    /**
     * Performs the initial loading of source providers. Does nothing if already done.
     */
    public void initiallyLoadAvailableSources() {
        if (!sourcesInitiallyLoaded && !NetworkManager.isOffline(OnlineResource.CACHE_UPDATES)) {
            reloadAvailableSources(availableSourcesUrl, sourceProviders);
        }
        sourcesInitiallyLoaded = true;
    }

    /**
     * List model of available sources.
     */
    protected static class AvailableSourcesModel extends AbstractTableModel {
        private final transient List<ExtendedSourceEntry> data;

        /**
         * Constructs a new {@code AvailableSourcesListModel}
         */
        public AvailableSourcesModel() {
            data = new ArrayList<>();
        }

        /**
         * Sets the source list.
         * @param sources source list
         */
        public void setSources(List<ExtendedSourceEntry> sources) {
            data.clear();
            if (sources != null) {
                data.addAll(sources);
            }
            fireTableDataChanged();
        }

        public ExtendedSourceEntry getValueAt(int rowIndex) {
            return data.get(rowIndex);
        }

        @Override
        public ExtendedSourceEntry getValueAt(int rowIndex, int ignored) {
            return getValueAt(rowIndex);
        }

        @Override
        public int getRowCount() {
            if (data == null) return 0;
            return data.size();
        }

        @Override
        public int getColumnCount() {
            return 1;
        }
    }

    /**
     * Table model of active sources.
     * <p>
     * This model always has 2 columns.   It returns the SourceEntry in column 0 and the
     * active state in column 1.  If you are not interested in the active state, remove
     * the column from your table.
     */
    public class ActiveSourcesModel extends AbstractTableModel implements ReorderableTableModel<SourceEntry> {
        private transient List<SourceEntry> data;
        private final DefaultListSelectionModel selectionModel;

        /**
         * Constructs a new {@code ActiveSourcesModel}.
         * @param selectionModel selection model
         */
        public ActiveSourcesModel(DefaultListSelectionModel selectionModel) {
            this.selectionModel = selectionModel;
            this.data = new ArrayList<>();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public int getRowCount() {
            return data == null ? 0 : data.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            if (columnIndex == 1)
                return data.get(rowIndex).active;
            else
                return data.get(rowIndex);
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 1;
        }

        @Override
        public Class<?> getColumnClass(int column) {
            if (column == 1)
                return Boolean.class;
            else return SourceEntry.class;
        }

        @Override
        public void setValueAt(Object aValue, int row, int column) {
            if (row < 0 || row >= getRowCount() || aValue == null)
                return;
            if (column == 1) {
                data.get(row).active = !data.get(row).active;
            }
        }

        /**
         * Sets active sources.
         * @param sources active sources
         */
        public void setActiveSources(Collection<? extends SourceEntry> sources) {
            data.clear();
            if (sources != null) {
                for (SourceEntry e : sources) {
                    data.add(new SourceEntry(e));
                }
            }
            fireTableDataChanged();
        }

        /**
         * Adds an active source.
         * @param entry source to add
         */
        public void addSource(SourceEntry entry) {
            if (entry == null) return;
            data.add(entry);
            fireTableDataChanged();
            int idx = data.indexOf(entry);
            if (idx >= 0) {
                selectionModel.setSelectionInterval(idx, idx);
            }
        }

        /**
         * Removes an active source.
         * @param entry source to remove
         */
        public void removeSource(SourceEntry entry) {
            data = data.stream().filter(e -> !e.equals(entry)).collect(Collectors.toList());
            fireTableDataChanged();
        }

        /**
         * Removes the selected sources.
         */
        public void removeSelected() {
            Iterator<SourceEntry> it = data.iterator();
            int i = 0;
            while (it.hasNext()) {
                it.next();
                if (selectionModel.isSelectedIndex(i)) {
                    it.remove();
                }
                i++;
            }
            fireTableDataChanged();
        }

        /**
         * Removes the sources at given indexes.
         * @param idxs indexes to remove
         */
        public void removeIdxs(Collection<Integer> idxs) {
            data = IntStream.range(0, data.size())
                    .filter(i -> !idxs.contains(i))
                    .mapToObj(i -> data.get(i))
                    .collect(Collectors.toList());
            fireTableDataChanged();
        }

        /**
         * Adds multiple sources.
         * @param sources source entries
         */
        public void addExtendedSourceEntries(List<ExtendedSourceEntry> sources) {
            if (sources == null) return;
            for (ExtendedSourceEntry info: sources) {
                data.add(new SourceEntry(info.type, info.url, info.name, info.getDisplayName(), true));
            }
            fireTableDataChanged();
            TableHelper.setSelectedIndices(selectionModel, sources.stream().mapToInt(data::indexOf));
        }

        /**
         * Returns the active sources.
         * @return the active sources
         */
        public List<SourceEntry> getSources() {
            return new ArrayList<>(data);
        }

        @Override
        public DefaultListSelectionModel getSelectionModel() {
            return selectionModel;
        }

        @Override
        public SourceEntry getValue(int index) {
            return data.get(index);
        }

        @Override
        public SourceEntry setValue(int index, SourceEntry value) {
            return data.set(index, value);
        }
    }

    private static void prepareFileChooser(String url, AbstractFileChooser fc) {
        if (Utils.isBlank(url)) return;
        URL sourceUrl;
        try {
            sourceUrl = new URL(url);
        } catch (MalformedURLException e) {
            File f = new File(url);
            if (f.isFile()) {
                f = f.getParentFile();
            }
            if (f != null) {
                fc.setCurrentDirectory(f);
            }
            return;
        }
        if (sourceUrl.getProtocol().startsWith("file")) {
            File f = new File(sourceUrl.getPath());
            if (f.isFile()) {
                f = f.getParentFile();
            }
            if (f != null) {
                fc.setCurrentDirectory(f);
            }
        }
    }

    /**
     * Dialog to edit a source entry.
     */
    protected class EditSourceEntryDialog extends ExtendedDialog {

        private final JosmTextField tfTitle;
        private final JosmTextField tfURL;
        private JCheckBox cbActive;

        /**
         * Constructs a new {@code EditSourceEntryDialog}.
         * @param parent parent component
         * @param title dialog title
         * @param e source entry to edit
         */
        public EditSourceEntryDialog(Component parent, String title, SourceEntry e) {
            super(parent, title, tr("Ok"), tr("Cancel"));

            JPanel p = new JPanel(new GridBagLayout());

            tfTitle = new JosmTextField(60);
            p.add(new JLabel(tr("Name (optional):")), GBC.std().insets(15, 0, 5, 5));
            p.add(tfTitle, GBC.eol().insets(0, 0, 5, 5));

            tfURL = new JosmTextField(60);
            p.add(new JLabel(tr("URL / File:")), GBC.std().insets(15, 0, 5, 0));
            p.add(tfURL, GBC.std().insets(0, 0, 5, 5));
            JButton fileChooser = new JButton(new LaunchFileChooserSourceTypeAction());
            fileChooser.setMargin(new Insets(0, 0, 0, 0));
            p.add(fileChooser, GBC.eol().insets(0, 0, 5, 5));

            if (e != null) {
                if (e.title != null) {
                    tfTitle.setText(e.title);
                }
                tfURL.setText(e.url);
            }

            if (canEnable) {
                cbActive = new JCheckBox(tr("active"), e == null || e.active);
                p.add(cbActive, GBC.eol().insets(15, 0, 5, 0));
            }
            setButtonIcons("ok", "cancel");
            setContent(p);

            // Make OK button enabled only when a file/URL has been set
            tfURL.getDocument().addDocumentListener(DocumentAdapter.create(ignore -> updateOkButtonState()));
        }

        private void updateOkButtonState() {
            buttons.get(0).setEnabled(!Utils.isStripEmpty(tfURL.getText()));
        }

        @Override
        public void setupDialog() {
            super.setupDialog();
            updateOkButtonState();
        }

        class LaunchFileChooserSourceTypeAction extends AbstractAction {
            LaunchFileChooserSourceTypeAction() {
                new ImageProvider("open").getResource().attachImageIcon(this);
                putValue(SHORT_DESCRIPTION, tr("Launch a file chooser to select a file"));
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                FileFilter ff;
                switch (sourceType) {
                case MAP_PAINT_STYLE:
                    ff = new ExtensionFileFilter("xml,mapcss,css,zip", "xml", tr("Map paint style file (*.xml, *.mapcss, *.zip)"));
                    break;
                case TAGGING_PRESET:
                    ff = new ExtensionFileFilter("xml,zip", "xml", tr("Preset definition file (*.xml, *.zip)"));
                    break;
                case TAGCHECKER_RULE:
                    ff = new ExtensionFileFilter("validator.mapcss,zip", "validator.mapcss", tr("Tag checker rule (*.validator.mapcss, *.zip)"));
                    break;
                default:
                    Logging.error("Unsupported source type: "+sourceType);
                    return;
                }
                FileChooserManager fcm = new FileChooserManager(true)
                        .createFileChooser(true, null, Arrays.asList(ff, FileFilterAllFiles.getInstance()), ff, JFileChooser.FILES_ONLY);
                prepareFileChooser(tfURL.getText(), fcm.getFileChooser());
                AbstractFileChooser fc = fcm.openFileChooser(GuiHelper.getFrameForComponent(SourceEditor.this));
                if (fc != null) {
                    tfURL.setText(fc.getSelectedFile().toString());
                }
            }
        }

        @Override
        public String getTitle() {
            return tfTitle.getText();
        }

        /**
         * Returns the entered URL / File.
         * @return the entered URL / File
         */
        public String getURL() {
            return tfURL.getText();
        }

        /**
         * Determines if the active combobox is selected.
         * @return {@code true} if the active combobox is selected
         */
        public boolean active() {
            if (!canEnable)
                throw new UnsupportedOperationException();
            return cbActive.isSelected();
        }
    }

    class NewActiveSourceAction extends AbstractAction {
        NewActiveSourceAction() {
            putValue(NAME, tr("New"));
            putValue(SHORT_DESCRIPTION, getStr(I18nString.NEW_SOURCE_ENTRY_TOOLTIP));
            new ImageProvider("dialogs", "add").getResource().attachImageIcon(this);
        }

        @Override
        public void actionPerformed(ActionEvent evt) {
            EditSourceEntryDialog editEntryDialog = new EditSourceEntryDialog(
                    SourceEditor.this,
                    getStr(I18nString.NEW_SOURCE_ENTRY),
                    null);
            editEntryDialog.showDialog();
            if (editEntryDialog.getValue() == 1) {
                boolean active = true;
                if (canEnable) {
                    active = editEntryDialog.active();
                }
                final SourceEntry entry = new SourceEntry(sourceType,
                        editEntryDialog.getURL(),
                        null, editEntryDialog.getTitle(), active);
                entry.title = getTitleForSourceEntry(entry);
                activeSourcesModel.addSource(entry);
                activeSourcesModel.fireTableDataChanged();
            }
        }
    }

    class RemoveActiveSourcesAction extends AbstractAction implements ListSelectionListener {

        RemoveActiveSourcesAction() {
            putValue(NAME, tr("Remove"));
            putValue(SHORT_DESCRIPTION, getStr(I18nString.REMOVE_SOURCE_TOOLTIP));
            new ImageProvider("dialogs", "delete").getResource().attachImageIcon(this);
            updateEnabledState();
        }

        protected final void updateEnabledState() {
            setEnabled(tblActiveSources.getSelectedRowCount() > 0);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            activeSourcesModel.removeSelected();
        }
    }

    class EditActiveSourceAction extends AbstractAction implements ListSelectionListener {
        EditActiveSourceAction() {
            putValue(NAME, tr("Edit"));
            putValue(SHORT_DESCRIPTION, getStr(I18nString.EDIT_SOURCE_TOOLTIP));
            new ImageProvider("dialogs", "edit").getResource().attachImageIcon(this);
            updateEnabledState();
        }

        protected final void updateEnabledState() {
            setEnabled(tblActiveSources.getSelectedRowCount() == 1);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent evt) {
            int pos = tblActiveSources.getSelectedRow();
            if (pos < 0 || pos >= tblActiveSources.getRowCount())
                return;

            SourceEntry e = (SourceEntry) activeSourcesModel.getValueAt(pos, 0);

            EditSourceEntryDialog editEntryDialog = new EditSourceEntryDialog(
                    SourceEditor.this, tr("Edit source entry:"), e);
            editEntryDialog.showDialog();
            if (editEntryDialog.getValue() == 1) {
                if (e.title != null || !"".equals(editEntryDialog.getTitle())) {
                    e.title = editEntryDialog.getTitle();
                    e.title = getTitleForSourceEntry(e);
                }
                e.url = editEntryDialog.getURL();
                if (canEnable) {
                    e.active = editEntryDialog.active();
                }
                activeSourcesModel.fireTableRowsUpdated(pos, pos);
            }
        }
    }

    /**
     * The action to move the currently selected entries up or down in the list.
     */
    class MoveUpDownAction extends AbstractAction implements ListSelectionListener, TableModelListener {
        private final int increment;

        MoveUpDownAction(boolean isDown) {
            increment = isDown ? 1 : -1;
            new ImageProvider("dialogs", isDown ? "down" : "up").getResource().attachImageIcon(this, true);
            putValue(SHORT_DESCRIPTION, isDown ? tr("Move the selected entry one row down.") : tr("Move the selected entry one row up."));
            updateEnabledState();
        }

        public final void updateEnabledState() {
            setEnabled(activeSourcesModel.canMove(increment));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            activeSourcesModel.move(increment, tblActiveSources.getSelectedRows());
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        public void tableChanged(TableModelEvent e) {
            updateEnabledState();
        }
    }

    class ActivateSourcesAction extends AbstractAction implements ListSelectionListener {
        ActivateSourcesAction() {
            putValue(SHORT_DESCRIPTION, getStr(I18nString.ACTIVATE_TOOLTIP));
            new ImageProvider("preferences", "activate-right").getResource().attachImageIcon(this);
            updateEnabledState();
        }

        protected final void updateEnabledState() {
            setEnabled(tblAvailableSources.getSelectedRowCount() > 0);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            List<ExtendedSourceEntry> sources = Arrays.stream(tblAvailableSources.getSelectedRows())
                    .map(tblAvailableSources::convertRowIndexToModel)
                    .mapToObj(availableSourcesModel::getValueAt)
                    .collect(Collectors.toList());

            int josmVersion = Version.getInstance().getVersion();
            if (josmVersion != Version.JOSM_UNKNOWN_VERSION) {
                Collection<String> messages = new ArrayList<>();
                for (ExtendedSourceEntry entry : sources) {
                    if (entry.minJosmVersion != null && entry.minJosmVersion > josmVersion) {
                        messages.add(tr("Entry ''{0}'' requires JOSM Version {1}. (Currently running: {2})",
                                entry.title,
                                Integer.toString(entry.minJosmVersion),
                                Integer.toString(josmVersion))
                        );
                    }
                }
                if (!messages.isEmpty()) {
                    ExtendedDialog dlg = new ExtendedDialog(MainApplication.getMainFrame(), tr("Warning"), tr("Cancel"), tr("Continue anyway"));
                    dlg.setButtonIcons(
                        ImageProvider.get("cancel"),
                        new ImageProvider("ok").setMaxSize(ImageSizes.LARGEICON).addOverlay(
                                new ImageOverlay(new ImageProvider("warning-small"), 0.5, 0.5, 1.0, 1.0)).get()
                    );
                    dlg.setToolTipTexts(
                        tr("Cancel and return to the previous dialog"),
                        tr("Ignore warning and install style anyway"));
                    dlg.setContent("<html>" + tr("Some entries have unmet dependencies:") +
                            "<br>" + String.join("<br>", messages) + "</html>");
                    dlg.setIcon(JOptionPane.WARNING_MESSAGE);
                    if (dlg.showDialog().getValue() != 2)
                        return;
                }
            }
            activeSourcesModel.addExtendedSourceEntries(sources);
        }
    }

    class ResetAction extends AbstractAction {

        ResetAction() {
            putValue(NAME, tr("Reset"));
            putValue(SHORT_DESCRIPTION, tr("Reset to default"));
            new ImageProvider("preferences", "reset").getResource().attachImageIcon(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            activeSourcesModel.setActiveSources(getDefault());
        }
    }

    class ReloadSourcesAction extends AbstractAction {
        private final String url;
        private final transient List<SourceProvider> sourceProviders;

        ReloadSourcesAction(String url, List<SourceProvider> sourceProviders) {
            putValue(NAME, tr("Reload"));
            putValue(SHORT_DESCRIPTION, tr(getStr(I18nString.RELOAD_ALL_AVAILABLE), url));
            new ImageProvider("dialogs", "refresh").getResource().attachImageIcon(this);
            this.url = url;
            this.sourceProviders = sourceProviders;
            setEnabled(!NetworkManager.isOffline(OnlineResource.JOSM_WEBSITE));
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            CachedFile.cleanup(url);
            reloadAvailableSources(url, sourceProviders);
        }
    }

    /**
     * Table model for icons paths.
     */
    protected static class IconPathTableModel extends AbstractTableModel {
        private final List<String> data;
        private final DefaultListSelectionModel selectionModel;

        /**
         * Constructs a new {@code IconPathTableModel}.
         * @param selectionModel selection model
         */
        public IconPathTableModel(DefaultListSelectionModel selectionModel) {
            this.selectionModel = selectionModel;
            this.data = new ArrayList<>();
        }

        @Override
        public int getColumnCount() {
            return 1;
        }

        @Override
        public int getRowCount() {
            return data == null ? 0 : data.size();
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return data.get(rowIndex);
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            updatePath(rowIndex, (String) aValue);
        }

        /**
         * Sets the icons paths.
         * @param paths icons paths
         */
        public void setIconPaths(Collection<String> paths) {
            data.clear();
            if (paths != null) {
                data.addAll(paths);
            }
            sort();
            fireTableDataChanged();
        }

        /**
         * Adds an icon path.
         * @param path icon path to add
         */
        public void addPath(String path) {
            if (path == null) return;
            data.add(path);
            sort();
            fireTableDataChanged();
            int idx = data.indexOf(path);
            if (idx >= 0) {
                selectionModel.setSelectionInterval(idx, idx);
            }
        }

        /**
         * Updates icon path at given index.
         * @param pos position
         * @param path new path
         */
        public void updatePath(int pos, String path) {
            if (path == null) return;
            if (pos < 0 || pos >= getRowCount()) return;
            data.set(pos, path);
            sort();
            fireTableDataChanged();
            int idx = data.indexOf(path);
            if (idx >= 0) {
                selectionModel.setSelectionInterval(idx, idx);
            }
        }

        /**
         * Removes the selected path.
         */
        public void removeSelected() {
            Iterator<String> it = data.iterator();
            int i = 0;
            while (it.hasNext()) {
                it.next();
                if (selectionModel.isSelectedIndex(i)) {
                    it.remove();
                }
                i++;
            }
            fireTableDataChanged();
            selectionModel.clearSelection();
        }

        /**
         * Sorts paths lexicographically.
         */
        protected void sort() {
            data.sort((o1, o2) -> {
                    if (o1.isEmpty() && o2.isEmpty())
                        return 0;
                    if (o1.isEmpty()) return 1;
                    if (o2.isEmpty()) return -1;
                    return o1.compareTo(o2);
                });
        }

        /**
         * Returns the icon paths.
         * @return the icon paths
         */
        public List<String> getIconPaths() {
            return new ArrayList<>(data);
        }
    }

    class NewIconPathAction extends AbstractAction {
        NewIconPathAction() {
            putValue(NAME, tr("New"));
            putValue(SHORT_DESCRIPTION, tr("Add a new icon path"));
            new ImageProvider("dialogs", "add").getResource().attachImageIcon(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            iconPathsModel.addPath("");
            tblIconPaths.editCellAt(iconPathsModel.getRowCount() -1, 0);
        }
    }

    class RemoveIconPathAction extends AbstractAction implements ListSelectionListener {
        RemoveIconPathAction() {
            putValue(NAME, tr("Remove"));
            putValue(SHORT_DESCRIPTION, tr("Remove the selected icon paths"));
            new ImageProvider("dialogs", "delete").getResource().attachImageIcon(this);
            updateEnabledState();
        }

        protected final void updateEnabledState() {
            setEnabled(tblIconPaths.getSelectedRowCount() > 0);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            iconPathsModel.removeSelected();
        }
    }

    class EditIconPathAction extends AbstractAction implements ListSelectionListener {
        EditIconPathAction() {
            putValue(NAME, tr("Edit"));
            putValue(SHORT_DESCRIPTION, tr("Edit the selected icon path"));
            new ImageProvider("dialogs", "edit").getResource().attachImageIcon(this);
            updateEnabledState();
        }

        protected final void updateEnabledState() {
            setEnabled(tblIconPaths.getSelectedRowCount() == 1);
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            int row = tblIconPaths.getSelectedRow();
            tblIconPaths.editCellAt(row, 0);
        }
    }

    static class FancySourceEntryTableCellRenderer extends DefaultTableCellRenderer {

        private static final NamedColorProperty SOURCE_ENTRY_ACTIVE_BACKGROUND_COLOR = new NamedColorProperty(
                marktr("External resource entry: Active"),
                new Color(200, 255, 200));
        private static final NamedColorProperty SOURCE_ENTRY_INACTIVE_BACKGROUND_COLOR = new NamedColorProperty(
                marktr("External resource entry: Inactive"),
                new Color(200, 200, 200));

        private final Map<String, SourceEntry> entryByUrl = new HashMap<>();
        private final ImageSizes iconSize = ImageSizes.TABLE;
        private final ImageIcon iconEmpty = ImageProvider.getEmpty(iconSize);

        @Override
        public Component getTableCellRendererComponent(JTable list, Object object, boolean isSelected, boolean hasFocus, int row, int column) {
            super.getTableCellRendererComponent(list, object, isSelected, hasFocus, row, column);
            if (object instanceof ExtendedSourceEntry) {
                final ExtendedSourceEntry value = (ExtendedSourceEntry) object;
                String s = value.toString();
                setText(s);
                setIconTextGap(10);
                setToolTipText(value.getTooltip());
                if (!isSelected) {
                    final SourceEntry sourceEntry = entryByUrl.get(value.url);
                    GuiHelper.setBackgroundReadable(this, sourceEntry == null ? UIManager.getColor("Table.background") :
                        sourceEntry.active ? SOURCE_ENTRY_ACTIVE_BACKGROUND_COLOR.get() : SOURCE_ENTRY_INACTIVE_BACKGROUND_COLOR.get());
                }
                setIcon(value.icon == null ? iconEmpty :
                    value.icon.getPaddedIcon(iconSize.getImageDimension()));
            }
            return this;
        }

        public void updateSources(List<SourceEntry> sources) {
            synchronized (entryByUrl) {
                entryByUrl.clear();
                for (SourceEntry i : sources) {
                    entryByUrl.put(i.url, i);
                }
            }
        }
    }

    class SourceLoader extends PleaseWaitRunnable {
        private final String url;
        private final List<SourceProvider> sourceProviders;
        private CachedFile cachedFile;
        private boolean canceled;
        private final List<ExtendedSourceEntry> sources = new ArrayList<>();

        SourceLoader(String url, List<SourceProvider> sourceProviders) {
            super(tr(getStr(I18nString.LOADING_SOURCES_FROM), url));
            this.url = url;
            this.sourceProviders = sourceProviders;
        }

        @Override
        protected void cancel() {
            canceled = true;
            Utils.close(cachedFile);
        }

        protected void warn(Exception e) {
            String emsg = Utils.escapeReservedCharactersHTML(e.getMessage() != null ? e.getMessage() : e.toString());
            final String msg = tr(getStr(I18nString.FAILED_TO_LOAD_SOURCES_FROM), url, emsg);

            GuiHelper.runInEDT(() -> HelpAwareOptionPane.showOptionDialog(
                    MainApplication.getMainFrame(),
                    msg,
                    tr("Error"),
                    JOptionPane.ERROR_MESSAGE,
                    ht(getStr(I18nString.FAILED_TO_LOAD_SOURCES_FROM_HELP_TOPIC))
                    ));
        }

        @Override
        protected void realRun() throws SAXException, IOException, OsmTransferException {
            try {
                sources.addAll(getDefault());

                for (SourceProvider provider : sourceProviders) {
                    for (SourceEntry src : provider.getSources()) {
                        if (src instanceof ExtendedSourceEntry) {
                            sources.add((ExtendedSourceEntry) src);
                        }
                    }
                }
                readFile();
                if (sources.removeIf(extendedSourceEntry -> "xml".equals(extendedSourceEntry.styleType))) {
                    Logging.debug("Removing XML source entry");
                }
            } catch (IOException e) {
                if (canceled)
                    // ignore the exception and return
                    return;
                OsmTransferException ex = new OsmTransferException(e);
                ex.setUrl(url);
                warn(ex);
            }
        }

        protected void readFile() throws IOException {
            final String lang = LanguageInfo.getLanguageCodeXML();
            cachedFile = new CachedFile(url);
            try (BufferedReader reader = cachedFile.getContentReader()) {

                String line;
                ExtendedSourceEntry last = null;

                while ((line = reader.readLine()) != null && !canceled) {
                    if (line.trim().isEmpty()) {
                        continue; // skip empty lines
                    }
                    if (line.startsWith("\t")) {
                        Matcher m = Pattern.compile("^\t([^:]+): *(.+)$").matcher(line);
                        if (!m.matches()) {
                            Logging.error(tr(getStr(I18nString.ILLEGAL_FORMAT_OF_ENTRY), url, line));
                            continue;
                        }
                        if (last != null) {
                            String key = m.group(1);
                            String value = m.group(2);
                            if ("author".equals(key) && last.author == null) {
                                last.author = value;
                            } else if ("version".equals(key)) {
                                last.version = value;
                            } else if ("icon".equals(key) && last.icon == null) {
                                last.icon = new ImageProvider(value).setOptional(true).getResource();
                            } else if ("link".equals(key) && last.link == null) {
                                last.link = value;
                            } else if ("description".equals(key) && last.description == null) {
                                last.description = value;
                            } else if ((lang + "shortdescription").equals(key) && last.title == null) {
                                last.title = value;
                            } else if ("shortdescription".equals(key) && last.title == null) {
                                last.title = value;
                            } else if ((lang + "title").equals(key) && last.title == null) {
                                last.title = value;
                            } else if ("title".equals(key) && last.title == null) {
                                last.title = value;
                            } else if ("name".equals(key) && last.name == null) {
                                last.name = value;
                            } else if ((lang + "author").equals(key)) {
                                last.author = value;
                            } else if ((lang + "link").equals(key)) {
                                last.link = value;
                            } else if ((lang + "description").equals(key)) {
                                last.description = value;
                            } else if ("min-josm-version".equals(key)) {
                                try {
                                    last.minJosmVersion = Integer.valueOf(value);
                                } catch (NumberFormatException e) {
                                    // ignore
                                    Logging.trace(e);
                                }
                            } else if ("style-type".equals(key)) {
                                last.styleType = value;
                            }
                        }
                    } else {
                        last = null;
                        Matcher m = Pattern.compile("^(.+);(.+)$").matcher(line);
                        if (m.matches()) {
                            last = new ExtendedSourceEntry(sourceType, m.group(1), m.group(2));
                            sources.add(last);
                        } else {
                            Logging.error(tr(getStr(I18nString.ILLEGAL_FORMAT_OF_ENTRY), url, line));
                        }
                    }
                }
            }
        }

        @Override
        protected void finish() {
            Collections.sort(sources);
            availableSourcesModel.setSources(sources);
        }
    }

    static class SourceEntryTableCellRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value == null)
                return this;
            return super.getTableCellRendererComponent(table,
                    fromSourceEntry((SourceEntry) value), isSelected, hasFocus, row, column);
        }

        private static String fromSourceEntry(SourceEntry entry) {
            if (entry == null)
                return null;
            StringBuilder s = new StringBuilder(128).append("<html><b>");
            if (entry.title != null) {
                s.append(Utils.escapeReservedCharactersHTML(entry.title)).append("</b> <span color=\"gray\">");
            }
            s.append(entry.url);
            if (entry.title != null) {
                s.append("</span>");
            }
            s.append("</html>");
            return s.toString();
        }
    }

    class FileOrUrlCellEditor extends JPanel implements TableCellEditor {
        private final JosmTextField tfFileName = new JosmTextField();
        private final transient CopyOnWriteArrayList<CellEditorListener> listeners;
        private String value;
        private final boolean isFile;

        /**
         * build the GUI
         */
        protected final void build() {
            setLayout(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridy = 0;
            gc.fill = GridBagConstraints.BOTH;
            gc.weightx = 1.0;
            gc.weighty = 1.0;
            add(tfFileName, gc);

            gc.gridx = 1;
            gc.gridy = 0;
            gc.fill = GridBagConstraints.BOTH;
            gc.weightx = 0.0;
            gc.weighty = 1.0;
            add(new JButton(new LaunchFileChooserEditCellAction()));

            tfFileName.addFocusListener(
                    new FocusAdapter() {
                        @Override
                        public void focusGained(FocusEvent e) {
                            tfFileName.selectAll();
                        }
                    }
                    );
        }

        FileOrUrlCellEditor(boolean isFile) {
            this.isFile = isFile;
            listeners = new CopyOnWriteArrayList<>();
            build();
        }

        @Override
        public void addCellEditorListener(CellEditorListener l) {
            if (l != null) {
                listeners.addIfAbsent(l);
            }
        }

        protected void fireEditingCanceled() {
            for (CellEditorListener l: listeners) {
                l.editingCanceled(new ChangeEvent(this));
            }
        }

        protected void fireEditingStopped() {
            for (CellEditorListener l: listeners) {
                l.editingStopped(new ChangeEvent(this));
            }
        }

        @Override
        public void cancelCellEditing() {
            fireEditingCanceled();
        }

        @Override
        public Object getCellEditorValue() {
            return value;
        }

        @Override
        public boolean isCellEditable(EventObject anEvent) {
            if (anEvent instanceof MouseEvent)
                return ((MouseEvent) anEvent).getClickCount() >= 2;
            return true;
        }

        @Override
        public void removeCellEditorListener(CellEditorListener l) {
            listeners.remove(l);
        }

        @Override
        public boolean shouldSelectCell(EventObject anEvent) {
            return true;
        }

        @Override
        public boolean stopCellEditing() {
            value = tfFileName.getText();
            fireEditingStopped();
            return true;
        }

        public void setInitialValue(String initialValue) {
            this.value = initialValue;
            if (initialValue == null) {
                this.tfFileName.setText("");
            } else {
                this.tfFileName.setText(initialValue);
            }
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            setInitialValue((String) value);
            tfFileName.setBorder(BorderFactory.createEmptyBorder());
            tfFileName.selectAll();
            return this;
        }

        class LaunchFileChooserEditCellAction extends AbstractAction {
            LaunchFileChooserEditCellAction() {
                putValue(NAME, "...");
                putValue(SHORT_DESCRIPTION, tr("Launch a file chooser to select a file"));
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                FileChooserManager fcm = new FileChooserManager(true).createFileChooser();
                if (!isFile) {
                    fcm.getFileChooser().setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                }
                prepareFileChooser(tfFileName.getText(), fcm.getFileChooser());
                AbstractFileChooser fc = fcm.openFileChooser(GuiHelper.getFrameForComponent(SourceEditor.this));
                if (fc != null) {
                    tfFileName.setText(fc.getSelectedFile().toString());
                }
            }
        }
    }

    /**
     * Defers loading of sources to the first time the adequate tab is selected.
     * @param tab The preferences tab
     * @param component The tab component
     * @since 6670
     */
    public final void deferLoading(final DefaultTabPreferenceSetting tab, final Component component) {
        deferLoading(tab.getTabPane(), component);
    }

    /**
     * Defers loading of sources to the first time the adequate tab is selected.
     * @param tab The tabbed pane
     * @param component The tab component
     * @since 17161
     */
    public final void deferLoading(final JTabbedPane tab, final Component component) {
        tab.addChangeListener(e -> {
            if (tab.getSelectedComponent() == component) {
                initiallyLoadAvailableSources();
            }
        });
    }

    /**
     * Returns the title of the given source entry.
     * @param entry source entry
     * @return the title of the given source entry, or null if empty
     */
    protected String getTitleForSourceEntry(SourceEntry entry) {
        return "".equals(entry.title) ? null : entry.title;
    }
}
