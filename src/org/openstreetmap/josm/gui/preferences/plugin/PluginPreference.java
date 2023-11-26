// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.plugin;

import static java.awt.GridBagConstraints.BOTH;
import static java.awt.GridBagConstraints.HORIZONTAL;
import static org.openstreetmap.josm.gui.preferences.PreferenceUtils.SMALLGAP;
import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trc;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.awt.Component;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.RowFilter;
import javax.swing.UIManager;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.actions.ExpertToggleAction.ExpertModeChangeListener;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.gui.HelpAwareOptionPane;
import org.openstreetmap.josm.gui.HelpAwareOptionPane.ButtonSpec;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.preferences.ExtensibleTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.progress.swing.ProgMon.ProgMonNotification;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.util.TableHelper;
import org.openstreetmap.josm.gui.widgets.FilterField;
import org.openstreetmap.josm.gui.widgets.HtmlPanel;
import org.openstreetmap.josm.plugins.JarDownloadTask;
import org.openstreetmap.josm.plugins.PluginHandler;
import org.openstreetmap.josm.plugins.PluginInformation;
import org.openstreetmap.josm.plugins.ScanPluginsTask;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;

/**
 * Preference settings for plugins.
 * @since 168
 */
@SuppressWarnings("java:S1192")
public final class PluginPreference extends ExtensibleTabPreferenceSetting {
    Icon downloadIcon = ImageProvider.get("dialogs/refresh", ImageProvider.ImageSizes.XLARGEICON);

    /**
     * Plugin installation status, used to filter plugin preferences model.
     * @since 13799
     */
    public enum PluginInstallation {
        /** Plugins loaded into memory */
        LOADED,
        /** Plugins installed and selected */
        CONFIGURED,
        /** Installed plugins */
        INSTALLED,
        /** Not installed plugins */
        AVAILABLE,
        /** All plugins */
        ALL,
        /** Plugins with updates */
        UPDATEABLE
    }

    /**
     * A row filter for name and installation status.
     */
    class PluginRowFilter extends RowFilter<PluginPreferenceModel, Integer> {
        String filter = "";
        PluginInstallation status = PluginInstallation.AVAILABLE;

        void setFilter(String filter) {
            this.filter = filter;
            rowSorter.sort();
        }

        void setStatus(PluginInstallation status) {
            this.status = status;
            rowSorter.sort();
        }

        private boolean matchesInstallationStatus(PluginPreferenceModel.TableEntry e, PluginInstallation status) {
            switch(status) {
                case LOADED : return getNames(PluginHandler.getLoadedPlugins()).contains(e.getName());
                case CONFIGURED : return model.getSelectedPluginNames().contains(e.getName());
                case INSTALLED : return getNames(model.getInstalledPlugins()).contains(e.getName());
                case AVAILABLE : return !model.getSelectedPluginNames().contains(e.getName());
                case UPDATEABLE : return e.isSelected() && e.hasUpdate();
                default: return true;
            }
        }

        /**
         * Replies true if either the name, the description, or the version match (case insensitive)
         * one of the words in filter. Replies true if filter is null.
         *
         * @param e the table exntry to match
         * @param filterExpression the filter expression
         * @return true if this plugin info matches with the filter
         */
        private boolean matchesExpression(PluginPreferenceModel.TableEntry e, String filterExpression) {
            String[] words = filterExpression.split("\\s+", -1);
            for (String word: words) {
                if (e.matches(word))
                    return true;
            }
            return false;
        }

        /* called by the rowFilter */
        @Override
        public boolean include(Entry<? extends PluginPreferenceModel, ? extends Integer> entry) {
            PluginPreferenceModel.TableEntry e = model.getTableEntryAt(entry.getIdentifier());
            boolean compatible = (e.installed != null) || (e.update != null && e.update.isCompatible()) ||
                (e.latest != null && e.latest.isCompatible());
            return compatible && matchesExpression(e, filter) && matchesInstallationStatus(e, status);
        }
    }

    /**
     * Factory used to create a new {@code PluginPreference}.
     */
    public static class Factory implements PreferenceSettingFactory {
        @Override
        public PreferenceSetting createPreferenceSetting() {
            return new PluginPreference();
        }
    }

    class ExpertTable extends JTable implements ExpertModeChangeListener {
        @Override
        public void expertChanged(boolean isExpert) {
            createDefaultColumnsFromModel();
            TableColumnModel cm = getColumnModel();
            cm.getColumn(0).setHeaderValue("");
            cm.getColumn(1).setHeaderValue("");
            cm.getColumn(2).setHeaderValue(tr("Plugin Name"));
            cm.getColumn(3).setHeaderValue(tr("Loaded"));
            cm.getColumn(4).setHeaderValue(tr("Installed"));
            cm.getColumn(5).setHeaderValue(tr("Compatible"));
            cm.getColumn(6).setHeaderValue(tr("Latest"));
            if (!isExpert) {
                cm.removeColumn(cm.getColumn(3));
            }
            setColumnWidths();
        }
    }

    static List<String> getNames(List<PluginInformation> plugins) {
        return plugins.stream().map(PluginInformation::getName).toList();
    }

    private ExpertTable table;
    private PluginRowFilter rowFilter;
    private TableRowSorter<PluginPreferenceModel> rowSorter;
    private JScrollPane infoScrollPane;
    private HtmlPanel infoPanel;
    private JSplitPane splitPane;
    private final PluginPreferenceModel model = new PluginPreferenceModel();
    private PluginUpdatePolicyPanel pnlPluginUpdatePolicy;
    /** For easier testing. True while background tasks are still active. */
    boolean working;

    /**
     * True if this preference pane has been activated by the user at least once
     */
    private boolean pluginPreferencesActivated;

    private PluginPreference() {
        super(/* ICON(preferences/) */ "plugin", tr("Plugins"), tr("Configure available plugins."), false);

        /*
         * Initialize the model
         */
        List<CompletableFuture<ScanPluginsTask>> taskList = ScanPluginsTask.supplyAsync();
        for (CompletableFuture<ScanPluginsTask> f : taskList) {
            // when done, stuff the results directly into the model
            f.thenAccept(task -> {
                Logging.info("... got {0} plugins from {1}",
                    task.getPluginInformations().size(), task.getUri().toString());
                model.addUpstreamPlugins(task.getPluginInformations());
                GuiHelper.runInEDT(model::fireTableDataChanged);
            });
        }
        var t = ScanPluginsTask.create(Preferences.main().getPluginsDirectory().toURI());
        var f = CompletableFuture.supplyAsync(t);
        f.thenAccept(task -> {
            Logging.info("... got {0} plugins from {1}",
                task.getPluginInformations().size(), task.getUri().toString());
            model.addInstalledPlugins(task.getPluginInformations());
            GuiHelper.runInEDT(model::fireTableDataChanged);
        });

        Logging.info("Started {0} threads to scan plugin repositories", taskList.size() + 1);
    }

    /**
     * Replies the list of plugins which have been added by the user to
     * the set of activated plugins.
     *
     * @return the list of newly activated plugins
     */
    public static List<PluginInformation> getActivatedPlugins(Collection<PluginInformation> oldList, Collection<PluginInformation> newList) {
        List<PluginInformation> activatedPlugins = new ArrayList<>(newList);
        activatedPlugins.removeAll(oldList);
        return activatedPlugins;
    }

    /**
     * Replies the list of plugins which have been removed by the user from
     * the set of activated plugins.
     *
     * @return the list of newly deactivated plugins
     */
    @SuppressWarnings("java:S2234")
    public static List<PluginInformation> getDeactivatedPlugins(Collection<PluginInformation> oldList, Collection<PluginInformation> newList) {
        return getActivatedPlugins(newList, oldList);
    }

    /**
     * Returns a summary of the downloaded plugins
     *
     * @param taskList The plugin download tasks
     * @param restartRequired if true adds a message about restarting JOSM
     * @return the download summary string to be shown. Contains summary of success/failed plugins.
     */
    public static String buildDownloadSummary(Collection<JarDownloadTask> taskList, boolean restartRequired) {
        List<JarDownloadTask> downloaded = JarDownloadTask.successfulTasks(taskList);
        List<JarDownloadTask> failed = JarDownloadTask.failedTasks(taskList);

        StringBuilder sb = new StringBuilder();
        if (!downloaded.isEmpty()) {
            sb.append(trn(
                    "The following plugin has been downloaded <strong>successfully</strong>:",
                    "The following {0} plugins have been downloaded <strong>successfully</strong>:",
                    downloaded.size(),
                    downloaded.size()
                    ));
            sb.append("<ul>");
            for (JarDownloadTask task: downloaded) {
                sb.append("<li>")
                    .append(task.getPluginInformation().getName())
                    .append(" (").append(task.getPluginInformation().getVersion())
                    .append(")</li>");
            }
            sb.append("</ul>");
        }
        if (!failed.isEmpty()) {
            sb.append(trn(
                    "Downloading the following plugin has <strong>failed</strong>:",
                    "Downloading the following {0} plugins has <strong>failed</strong>:",
                    failed.size(),
                    failed.size()
                    ));
            sb.append("<ul>");
            for (JarDownloadTask task: failed) {
                sb.append("<li>").append(task.getPluginInformation().getName());
                if (task.getException() != null) {
                    // Same i18n string in ExceptionUtil.explainBadRequest()
                    sb.append(tr("<br>Error message(untranslated): {0}", task.getException().getMessage()));
                }
                sb.append("</li>");
            }
            sb.append("</ul>");
        }
        if (restartRequired) {
            sb.append(tr("Please restart JOSM to activate the downloaded plugins."));
        }
        return sb.toString();
    }

    /**
     * Notifies user about result of a finished plugin download task.
     * @param parent The parent component
     * @param taskList The plugin download tasks
     * @param restartRequired true if a restart is required
     * @since 6797
     */
    public static void notifyDownloadResults(final Component parent, Collection<JarDownloadTask> taskList, boolean restartRequired) {
        List<JarDownloadTask> failed = JarDownloadTask.failedTasks(taskList);

        HelpAwareOptionPane.showOptionDialog(
            parent,
            "<html>" + buildDownloadSummary(taskList, restartRequired) + "</html>",
            tr("Update plugins"),
            !failed.isEmpty() ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE,
                    HelpUtil.ht("/Preferences/Plugins")
        );
    }

    @SuppressWarnings("java:S1192") // we need the string "plugins" as context for the translators
    private JPanel buildSearchFieldPanel() {
        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.add(GBC.glue(0, 0));

        ButtonGroup bg = new ButtonGroup();
        JPanel radios = new JPanel();
        addRadioButton(bg, radios, new JRadioButton(trc("plugins", "All"), true), PluginInstallation.ALL);
        ExpertToggleAction.addVisibilitySwitcher(
            addRadioButton(bg, radios, new JRadioButton(trc("plugins", "Loaded")), PluginInstallation.LOADED));
        // addRadioButton(bg, radios, new JRadioButton(trc("plugins", "Configured")), PluginInstallation.CONFIGURED);
        addRadioButton(bg, radios, new JRadioButton(trc("plugins", "Installed")), PluginInstallation.INSTALLED);
        // addRadioButton(bg, radios, new JRadioButton(trc("plugins", "Available")), PluginInstallation.AVAILABLE);
        addRadioButton(bg, radios, new JRadioButton(trc("plugins", "With Updates")), PluginInstallation.UPDATEABLE);
        pnl.add(radios, GBC.eol().fill(HORIZONTAL));

        pnl.add(new FilterField().filter(expr ->
            rowFilter.setFilter(expr)
        ), GBC.eol().insets(0, 0, 0, 5).fill(HORIZONTAL));
        return pnl;
    }

    private JRadioButton addRadioButton(ButtonGroup bg, JPanel pnl, JRadioButton rb, PluginInstallation value) {
        bg.add(rb);
        pnl.add(rb, GBC.std());
        rb.addActionListener(e -> rowFilter.setStatus(value));
        return rb;
    }

    private static Component addButton(JPanel pnl, JButton button, String buttonName) {
        button.setName(buttonName);
        pnl.add(button, GBC.std().fill(HORIZONTAL));
        return button;
    }

    private void setColumnWidths() {
        int width = (int) Math.ceil(new JCheckBox().getPreferredSize().getWidth() + 2 * SMALLGAP);
        TableColumn col = table.getColumnModel().getColumn(0);
        col.setMinWidth(width);
        col.setMaxWidth(width);

        width = ImageProvider.ImageSizes.LARGEICON.getWidth() + 2 * SMALLGAP;
        col = table.getColumnModel().getColumn(1);
        col.setMinWidth(width);
        col.setMaxWidth(width);

        TableHelper.setPreferredColumnWidth(table, 2);

        TableHelper.setRowHeight(table, ImageProvider.ImageSizes.LARGEICON);
    }

    private JPanel buildPluginListPanel() {
        GBC gbc = GBC.eop().fill(HORIZONTAL);

        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setBorder(BorderFactory.createEmptyBorder(SMALLGAP, SMALLGAP, SMALLGAP, SMALLGAP));
        pnl.add(buildSearchFieldPanel(), gbc);

        table = new ExpertTable();

        // Problem: listeners are called last to first. We want to be called *after* the
        // rowSorter does its job, so we must register on the model before the table.
        // (The rowSorter does not register its own listener but uses the table's
        // listener.)
        model.addTableModelListener(e -> setColumnWidths());
        table.setModel(model);
        ExpertToggleAction.addExpertModeChangeListener(table, true);

        rowSorter = new TableRowSorter<>(model);
        rowFilter = new PluginRowFilter();
        rowFilter.setStatus(PluginInstallation.ALL);
        rowSorter.setSortable(0, false);
        rowSorter.setSortable(1, false);
        rowSorter.toggleSortOrder(2); // sort on plugin name
        rowSorter.setSortable(3, false);
        rowSorter.setSortable(4, false);
        rowSorter.setSortable(5, false);
        rowSorter.setSortable(6, false);
        rowSorter.setRowFilter(rowFilter);
        table.setRowSorter(rowSorter);

        table.getSelectionModel().addListSelectionListener(event -> updateInfoPanelText());

        pnl.addComponentListener(this.new ComponentListener());

        infoPanel = new HtmlPanel();
        infoPanel.enableClickableHyperlinks();
        infoScrollPane = new JScrollPane(infoPanel);

        splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
            new JScrollPane(table),
            infoScrollPane
        );
        splitPane.setDividerLocation(-1);
        pnl.add(splitPane, GBC.eop().fill(BOTH));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.LINE_AXIS));
        // assign some component names to these as we go to aid testing
        addButton(buttons, new JButton(new UpdateAction()), "updatePluginsButton");
        ExpertToggleAction.addVisibilitySwitcher(addButton(buttons, new JButton(new SelectByListAction()), "loadFromListButton"));
        ExpertToggleAction.addVisibilitySwitcher(addButton(buttons, new JButton(new CopyToClipboardAction()), "copyToClipboardButton"));
        ExpertToggleAction.addVisibilitySwitcher(addButton(buttons, new JButton(new PurgeAction()), "purgeButton"));
        ExpertToggleAction.addVisibilitySwitcher(addButton(buttons, new JButton(new ConfigureSitesAction()), "configureSitesButton"));

        pnl.add(buttons, GBC.std().fill(HORIZONTAL));
        return pnl;
    }

    /**
     * Updates the info panel when the user selects a different row.
     */
    private void updateInfoPanelText() {
        int row = table.getSelectedRow();
        if (row > -1) {
            int scrollPaneWidth = infoScrollPane.getWidth();
            int scrollBarWidth = UIManager.getInt("ScrollBar.width");
            Insets insets = infoScrollPane.getInsets();
            Insets insets2 = infoPanel.getInsets();
            int width = scrollPaneWidth - insets.left - insets.right - insets2.left - insets2.right
                - scrollBarWidth - 2 * SMALLGAP;
            // the only way to set the width of the panel when you don't know the height
            infoPanel.setText(
                String.format("<html><body><div style=\"width: %d\">", width)
                + model.getTableEntryAt(table.convertRowIndexToModel(row)).getDescriptionAsHtml()
                + "</div></body></html>");
        } else {
            infoPanel.setText(null);
        }
    }

    class ComponentListener extends ComponentAdapter {
        @Override
        public void componentResized(ComponentEvent e) {
            // reformat the panel text
            updateInfoPanelText();
            splitPane.setDividerLocation(splitPane.getSize().height * 2 / 3);
        }
    }

    @Override
    public void addGui(final PreferenceTabbedPane gui) {
        JTabbedPane pane = getTabPane();
        pnlPluginUpdatePolicy = new PluginUpdatePolicyPanel();
        pane.addTab(tr("Plugins"), buildPluginListPanel());
        pane.addTab(tr("Plugin update policy"), pnlPluginUpdatePolicy);
        super.addGui(gui);
        pluginPreferencesActivated = true;
    }

    /**
     * Returns the model
     * @return the model
     */
    public PluginPreferenceModel getModel() {
        return model;
    }

    JarDownloadTask withMonitors(JarDownloadTask task) {
        task.withProgressMonitor(
            new ProgMonNotification(
                new Notification(tr("Downloading plugin: {0}", task.getPluginInformation().getName()))
                    .setIcon(downloadIcon)
                    .withProgressBar()
            ).setVisible(true)
        );
        return task;
    }

    @Override
    public boolean ok(StringBuilder sb) {
        if (!pluginPreferencesActivated)
            return false;

        // Create a task for downloading plugins if the user has activated, yet not downloaded, new plugins
        final Collection<PluginInformation> toDownload = getModel().getPluginsToDownload();
        Logging.info("Starting tasks for download of plugins: {0}",
            String.join(", ", PluginPreferenceModel.getNames(toDownload)));
        final List<CompletableFuture<JarDownloadTask>> futureList = toDownload.stream()
            .map(JarDownloadTask::new)
            .map(this::withMonitors)
            .map(CompletableFuture::supplyAsync)
            .toList();
        working = !futureList.isEmpty();

        // Try to unload and load the plugins after the downloads complete
        JarDownloadTask.allOf(futureList).thenRun(() ->
            GuiHelper.runInEDT(() -> {
                model.clearInstalledPlugins();
                model.addInstalledPlugins(scanLocalPlugins());
                model.fireTableDataChanged();
                tryUnloadPlugins(getModel().getPluginsToDeactivate());
                tryLoadPlugins(getModel().getPluginsToActivate());
                working = false;
            })
        );

        // save the user's new choices
        List<String> selectedPluginNames = getModel().getSelectedPluginNames();
        Collections.sort(selectedPluginNames);
        Config.getPref().putList("plugins", selectedPluginNames);
        pnlPluginUpdatePolicy.rememberInPreferences();

        Config.getPref().putInt("pluginmanager.version", Version.getInstance().getVersion());
        // Config.getPref().put("pluginmanager.lastupdate", Long.toString(System.currentTimeMillis()));

        return false; // we handle restart reporting ourselves, and we don't know yet anyway
    }

    /**
     * Tries to load the given plugins without restarting JOSM
     * <p>
     * This function runs in the EDT after all downloads have completed and the
     * respective jars have been installed. It tries to load the plugins without
     * restarting JOSM.
     *
     * @param activatedPlugins list of plugins that were activated
     */
    void tryLoadPlugins(List<PluginInformation> activatedPlugins) {
        PluginHandler.pluginListNotLoaded.clear();
        boolean needRestart = !PluginHandler.loadPlugins(
            getTabPane(), activatedPlugins, null, true).isEmpty();

        // report restart
        if (needRestart)
            new Notification().warning(tr("Please restart JOSM to activate the plugins."))
                .setDuration(Notification.TIME_VERY_LONG).setVisible(true);
    }

    /**
     * Tries to unload the given plugins without restarting JOSM
     * <p>
     * If any of the plugins cannot be unloaded, true will be returned.
     *
     * @param deactivatedPlugins the given plugins
     */
    void tryUnloadPlugins(List<PluginInformation> deactivatedPlugins) {
        boolean needRestart = false;

        for (PluginInformation pi : deactivatedPlugins) {
            if (PluginHandler.removePlugin(pi.getName())) {
                needRestart = true;
                new Notification().warning(tr("Unloading ''{0}'' needs restart.", pi.getName())).setVisible(true);
            } else {
                new Notification().success(tr("Plugin ''{0}'' unloaded.", pi.getName())).setVisible(true);
            }
        }

        // report restart
        if (needRestart)
            new Notification().warning(tr("Please restart JOSM to remove the plugins."))
                .setDuration(Notification.TIME_VERY_LONG).setVisible(true);
    }

    /**
     * Tries to update the plugins without restarting
     * <p>
     * This function tries to unload and reload the given plugins at runtime.  If any
     * given plugin cannot be processed in this way, then true will be returned.
     *
     * @param updatedPlugins list of plugins to update
     */
    void tryUpdatePlugins(List<PluginInformation> updatedPlugins) {
        boolean needRestart = false;

        // first unload all plugins that can load at runtime
        for (PluginInformation pi : updatedPlugins) {
            boolean restart = false;
            if (pi.canLoadAtRuntime()) {
                if (PluginHandler.removePlugin(pi.getName())) {
                    restart = true;
                } else {
                    PluginHandler.pluginListNotLoaded.clear();
                    restart |= !PluginHandler.loadPlugins(getTabPane(), updatedPlugins, null, true).isEmpty();
                }
            } else {
                restart = true;
            }
            if (restart) {
                new Notification().warning(tr("Update of plugin ''{0}'' needs restart.", pi.getName())).setVisible(true);
                needRestart = true;
            } else {
                new Notification().info(tr("Plugin ''{0}'' updated.", pi.getName())).setVisible(true);
            }
        }

        // report restart
        if (needRestart)
            new Notification().warning(tr("Please restart JOSM to complete the update."))
                .setDuration(Notification.TIME_VERY_LONG).setVisible(true);
    }

    /**
     * Scans the plugin jar files in the configured plugins directory.
     */
    public Collection<PluginInformation> scanLocalPlugins() {
        try {
            File pluginDir = Preferences.main().getPluginsDirectory();
            Collection<PluginInformation> l = PluginHandler.getPluginJarsInDirectory(pluginDir);
            Logging.debug("PluginPreference.scanLocalPlugins -> addLocalPlugins: {0} plugins", l.size());
            return l;
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    /**
     * The action of updating the plugins
     */
    class UpdateAction extends AbstractAction {
        UpdateAction() {
            putValue(NAME, tr("Update plugins"));
            putValue(SHORT_DESCRIPTION, tr("Update the selected plugins"));
            new ImageProvider("dialogs", "refresh").getResource().attachImageIcon(this);
        }

        void alertNothingToUpdate() {
            HelpAwareOptionPane.showOptionDialog(
                table,
                tr("All installed plugins are up to date. JOSM does not have to download newer versions."),
                tr("Plugins up to date"),
                JOptionPane.INFORMATION_MESSAGE,
                null // FIXME: provide help context
            );
        }

        /**
         * Updates the plugins
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                List<PluginInformation> toUpdate = model.getPluginsToUpdate();
                if (toUpdate.isEmpty()) {
                    Config.getPref().putInt("pluginmanager.version", Version.getInstance().getVersion());
                    alertNothingToUpdate();
                    return;
                }

                // Download all plugins in parallel
                final List<CompletableFuture<JarDownloadTask>> futureList = toUpdate.stream()
                    .map(JarDownloadTask::new)
                    .map(PluginPreference.this::withMonitors)
                    .map(CompletableFuture::supplyAsync)
                    .toList();

                working = !futureList.isEmpty();

                // Wait for all tasks to complete, then update table
                JarDownloadTask.allOf(futureList).thenRun(() ->
                    GuiHelper.runInEDT(() -> {
                        // get the list of installed plugins because
                        // only local plugins know their classes
                        Set<String> names = new HashSet<>(getNames(toUpdate));
                        List<PluginInformation> toLoad = PluginHandler.getInstalledPlugins().stream()
                            .filter(pi -> names.contains(pi.getName())).toList();

                        tryUpdatePlugins(toLoad);
                        model.clearInstalledPlugins();
                        model.addInstalledPlugins(scanLocalPlugins());
                        model.fireTableDataChanged();
                        Config.getPref().putInt("pluginmanager.version", Version.getInstance().getVersion());
                        Config.getPref().put("pluginmanager.lastupdate", Long.toString(System.currentTimeMillis()));
                        working = false;
                    })
                );
            } catch (IllegalArgumentException ex) {
                HelpAwareOptionPane.showOptionDialog(
                    table,
                    ex.getMessage(),
                    tr("Error"),
                    JOptionPane.ERROR_MESSAGE,
                    null // FIXME: provide help context
                );
            }
        }
    }

    /**
     * Opens a dialog to configure the plugin download sites
     */
    class ConfigureSitesAction extends AbstractAction {
        ConfigureSitesAction() {
            putValue(NAME, tr("Configure sites..."));
            putValue(SHORT_DESCRIPTION, tr("Configure the list of sites where plugins are downloaded from"));
            new ImageProvider("preference").getResource().attachImageIcon(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            configureSites();
        }

        private void configureSites() {
            ButtonSpec[] options = {
                    new ButtonSpec(
                            tr("OK"),
                            new ImageProvider("ok"),
                            tr("Accept the new plugin sites and close the dialog"),
                            null /* no special help topic */
                            ),
                            new ButtonSpec(
                                    tr("Cancel"),
                                    new ImageProvider("cancel"),
                                    tr("Close the dialog"),
                                    null /* no special help topic */
                                    )
            };
            PluginRepositoriesConfigurationDialog dialog = new PluginRepositoriesConfigurationDialog();

            int answer = HelpAwareOptionPane.showOptionDialog(
                    table,
                    dialog,
                    tr("Configure Plugin Sites"),
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0],
                    null /* no help topic */
                    );
            if (answer != 0 /* OK */)
                return;
            Preferences.main().setPluginSites(dialog.getUpdateSites());
        }
    }

    /**
     * Selects plugins using a text file compatible to the JOSM bug report.
     *
     * @author Michael Zangl
     */
    class SelectByListAction extends AbstractAction {
        SelectByListAction() {
            putValue(NAME, tr("Load from list..."));
            putValue(SHORT_DESCRIPTION, tr("Load plugins from a list of plugins"));
            new ImageProvider("misc/statusreport").getResource().attachImageIcon(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            JTextArea textField = new JTextArea(10, 0);
            JCheckBox deleteNotInList = new JCheckBox(tr("Disable all other plugins"));

            JLabel helpLabel = new JLabel("<html>" + String.join("<br/>",
                    tr("Enter a list of plugins you want to download."),
                    tr("You should add one plugin id per line, version information is ignored."),
                    tr("You can copy+paste the list of a status report here.")) + "</html>");

            if (JOptionPane.OK_OPTION == JOptionPane.showConfirmDialog(GuiHelper.getFrameForComponent(getTabPane()),
                    new Object[] {helpLabel, new JScrollPane(textField), deleteNotInList},
                    tr("Load plugins from list"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)) {
                activatePlugins(textField, deleteNotInList.isSelected());
            }
        }

        private void activatePlugins(JTextArea textField, boolean deleteNotInList) {
            String[] lines = textField.getText().split("\n", -1);
            List<String> toActivate = new ArrayList<>();
            List<String> notFound = new ArrayList<>();
            // This pattern matches the default list format JOSM uses for bug reports.
            // It removes a list item mark at the beginning of the line: +, -, *
            // It removes the version number after the plugin, like: 123, (123), (v5.7alpha3), (1b3), (v1-SNAPSHOT-1)...
            Pattern regex = Pattern.compile("^[-+*\\s]*|\\s[\\d\\s]*(\\([^()\\[\\]]*\\))?[\\d\\s]*$");
            for (String line : lines) {
                String name = regex.matcher(line).replaceAll("");
                if (name.isEmpty()) {
                    continue;
                }
                boolean found = model.getUpstreamPlugins().stream().anyMatch(pi -> name.equals(pi.getName()));
                if (!found) {
                    notFound.add(name);
                } else {
                    toActivate.add(name);
                }
            }

            if (notFound.isEmpty() || confirmIgnoreNotFound(notFound)) {
                activatePlugins(toActivate, deleteNotInList);
            }
        }

        private void activatePlugins(List<String> toActivate, boolean deleteNotInList) {
            if (deleteNotInList) {
                for (String name : model.getSelectedPluginNames()) {
                    if (!toActivate.contains(name)) {
                        model.select(name, false);
                    }
                }
            }
            for (String name : toActivate) {
                model.select(name, true);
            }
            model.fireTableDataChanged();
        }

        private boolean confirmIgnoreNotFound(List<String> notFound) {
            String list = "<ul><li>" + String.join("</li><li>", notFound) + "</li></ul>";
            String message = "<html>" + tr("The following plugins were not found. Continue anyway?") + list + "</html>";
            return JOptionPane.showConfirmDialog(GuiHelper.getFrameForComponent(getTabPane()),
                    message) == JOptionPane.OK_OPTION;
        }
    }

    /**
     * Copies the names of the selected plugins to the clipboard
     */
    class CopyToClipboardAction extends AbstractAction {
        CopyToClipboardAction() {
            putValue(NAME, tr("Copy"));
            putValue(SHORT_DESCRIPTION, tr("Copies a list of selected plugins to the clipboard"));
            new ImageProvider("copy").getResource().attachImageIcon(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ClipboardUtils.copyString(String.join("\n", model.getSelectedPluginNames()));

            new Notification("Plugin list copied to clipboard!")
                .setIcon(ImageProvider.get("copy", ImageProvider.ImageSizes.XLARGEICON))
                // .setManager(manager)
                .setVisible(true);
        }
    }

    /**
     * Purges all currently unused plugin files.
     */
    class PurgeAction extends AbstractAction {
        PurgeAction() {
            putValue(NAME, tr("Purge"));
            putValue(SHORT_DESCRIPTION, tr("Removes all installed plugins that are not selected."));
            new ImageProvider("dialogs/delete").getResource().attachImageIcon(this);
        }

        private List<Path> walkDir(Path dir) {
            try (Stream<Path> walk = Files.walk(dir)) {
                return walk.sorted(Comparator.reverseOrder()).toList();
            } catch (IOException e) {
                return Collections.emptyList();
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            List<String> configured = PluginHandler.getConfiguredPluginNames();
            List<PluginInformation> toPurge = scanLocalPlugins().stream()
                .filter(pi -> !configured.contains(pi.getName())).toList();

            if (!toPurge.isEmpty()) {
                String message;
                for (var pi : toPurge) {
                    try {
                        Path path = Path.of(pi.getUri());
                        Files.delete(path); // delete jar
                        // eventually delete a subdir of the same name as plugin
                        List<Path> paths = walkDir(Path.of(path.getParent().toString(), pi.getName()));
                        for (Path pth : paths) {
                            Files.delete(pth);
                        }
                        message = tr("Purged plugin {0}", pi.getName());
                        new Notification().info(message).setVisible(true);
                        Logging.info(message);
                    } catch (IOException ex) {
                        message = tr("Could not purge file: {0}", ex.getMessage());
                        new Notification().warning(message).setVisible(true);
                        Logging.warn(message);
                    }
                }
                model.clearInstalledPlugins();
                model.addInstalledPlugins(scanLocalPlugins());
                model.fireTableDataChanged();
            }
        }
    }

    @Override
    public String getHelpContext() {
        return HelpUtil.ht("/Preferences/Plugins");
    }
}
