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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.RowFilter;
import javax.swing.UIManager;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableRowSorter;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.gui.HelpAwareOptionPane;
import org.openstreetmap.josm.gui.HelpAwareOptionPane.ButtonSpec;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.preferences.ExtensibleTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.progress.swing.ProgMon.IProgMonDisplay;
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
import org.openstreetmap.josm.tools.Utils;

/**
 * Preference settings for plugins.
 * @since 168
 */
@SuppressWarnings("java:S1192")
public final class PluginPreference extends ExtensibleTabPreferenceSetting {
    /**
     * Plugin installation status, used to filter plugin preferences model.
     * @since 13799
     */
    public enum PluginInstallation {
        /** Plugins installed and loaded **/
        INSTALLED,
        /** Plugins not loaded **/
        AVAILABLE,
        /** All plugins **/
        ALL,
        /** Plugins with updates **/
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
                case INSTALLED : return model.getSelectedPluginNames().contains(e.getName());
                case AVAILABLE : return !model.getSelectedPluginNames().contains(e.getName());
                case UPDATEABLE : return e.isSelected() && e.hasUpdate();
                default: return true;
            }
        }

        /**
         * Replies true if either the name, the description, or the version match (case insensitive)
         * one of the words in filter. Replies true if filter is null.
         *
         * @param filter the filter expression
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
            boolean compatible = (e.local != null)  || (e.update != null && e.update.isCompatible()) ||
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

    private JTable table;
    private PluginRowFilter rowFilter;
    private TableRowSorter<PluginPreferenceModel> rowSorter;
    private JScrollPane infoScrollPane;
    private HtmlPanel infoPanel;
    private final PluginPreferenceModel model = new PluginPreferenceModel();
    private PluginUpdatePolicyPanel pnlPluginUpdatePolicy;

    /**
     * is set to true if this preference pane has been selected by the user
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
            model.addLocalPlugins(task.getPluginInformations());
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
     * Returns the download summary string to be shown.
     * @param task The plugin download task that has completed
     * @return the download summary string to be shown. Contains summary of success/failed plugins.
     */
    public static String buildDownloadSummary(Collection<JarDownloadTask> taskList) {
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
        return sb.toString();
    }

    /**
     * Notifies user about result of a finished plugin download task.
     * @param parent The parent component
     * @param task The finished plugin download task
     * @param restartRequired true if a restart is required
     * @since 6797
     */
    public static void notifyDownloadResults(final Component parent, Collection<JarDownloadTask> taskList, boolean restartRequired) {
        List<JarDownloadTask> failed = taskList.stream()
            .filter(task -> task.getStatus() != 200)
            .toList();

        final StringBuilder sb = new StringBuilder();
        sb.append("<html>")
          .append(buildDownloadSummary(taskList));
        if (restartRequired) {
            sb.append(tr("Please restart JOSM to activate the downloaded plugins."));
        }
        sb.append("</html>");
        HelpAwareOptionPane.showOptionDialog(
            parent,
            sb.toString(),
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
        addRadioButton(bg, radios, new JRadioButton(trc("plugins", "Installed")), PluginInstallation.INSTALLED);
        addRadioButton(bg, radios, new JRadioButton(trc("plugins", "Available")), PluginInstallation.AVAILABLE);
        addRadioButton(bg, radios, new JRadioButton(trc("plugins", "Updates")), PluginInstallation.UPDATEABLE);
        pnl.add(radios, GBC.eol().fill(HORIZONTAL));

        pnl.add(new FilterField().filter(expr ->
            rowFilter.setFilter(expr)
        ), GBC.eol().insets(0, 0, 0, 5).fill(HORIZONTAL));
        return pnl;
    }

    private void addRadioButton(ButtonGroup bg, JPanel pnl, JRadioButton rb, PluginInstallation value) {
        bg.add(rb);
        pnl.add(rb, GBC.std());
        rb.addActionListener(e -> rowFilter.setStatus(value));
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

        TableHelper.setRowHeight(table, ImageProvider.ImageSizes.LARGEICON);

        // if (table.getRowCount() > 0) {
        //     TableHelper.setFixedColumnWidth(table, 0, 0);
        //     TableHelper.setFixedColumnWidth(table, 1, 0);
        // }
        // TableHelper.setPreferredColumnWidth(table, 2);
        // TableHelper.setPreferredColumnWidth(table, 3);
        // TableHelper.setPreferredColumnWidth(table, 4);
    }

    private JPanel buildPluginListPanel() {
        GBC gbc = GBC.eop().fill(HORIZONTAL);

        JPanel pnl = new JPanel(new GridBagLayout());
        pnl.setBorder(BorderFactory.createEmptyBorder(SMALLGAP, SMALLGAP, SMALLGAP, SMALLGAP));
        pnl.add(buildSearchFieldPanel(), gbc);

        table = new JTable();
        // Problem: listeners are called last to first. We want to be called *after* the
        // rowSorter does its job, so we must register on the model before the table.
        // (The rowSorter does not register its own listener but uses the table's
        // listener.)
        model.addTableModelListener(e -> setColumnWidths());
        table.setModel(model);
        setColumnWidths();

        TableColumnModel cm = table.getColumnModel();
        cm.getColumn(0).setHeaderValue("");
        cm.getColumn(1).setHeaderValue("");
        cm.getColumn(2).setHeaderValue(tr("Plugin Name"));
        cm.getColumn(3).setHeaderValue(tr("Local"));
        cm.getColumn(4).setHeaderValue(tr("Compatible"));
        cm.getColumn(5).setHeaderValue(tr("Latest"));
        cm.getColumn(1).setHeaderValue("");

        rowSorter = new TableRowSorter<>(model);
        rowFilter = new PluginRowFilter();
        rowFilter.setStatus(PluginInstallation.ALL);
        rowSorter.setSortable(0, false);
        rowSorter.setSortable(1, false);
        /*
        rowSorter.setComparator(2, Comparator.<PluginInformation, String>comparing(
            pi -> pi.getName() == null ? "" : pi.getName().toLowerCase(Locale.ENGLISH))
        );
        */
        rowSorter.setSortable(3, false);
        rowSorter.setSortable(4, false);
        rowSorter.toggleSortOrder(2);
        rowSorter.setRowFilter(rowFilter);
        table.setRowSorter(rowSorter);

        // table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getSelectionModel().addListSelectionListener(event -> updateInfoPanelText());

        pnl.addComponentListener(this.new ComponentListener());

        pnl.add(new JScrollPane(table), GBC.eop().fill(BOTH).weight(1.0, 5.0));

        infoPanel = new HtmlPanel();
        infoPanel.enableClickableHyperlinks();
        infoScrollPane = new JScrollPane(infoPanel);
        pnl.add(infoScrollPane, GBC.eop().fill(BOTH).weight(1.0, 1.0));

        JPanel buttons = new JPanel();
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.LINE_AXIS));
        // assign some component names to these as we go to aid testing
        addButton(buttons, new JButton(new UpdateSelectedPluginsAction()), "updatePluginsButton");
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
        int scrollPaneWidth = infoScrollPane.getWidth();
        int scrollBarWidth = UIManager.getInt("ScrollBar.width");
        int row = table.getSelectedRow();
        Insets insets = infoScrollPane.getInsets();
        Insets insets2 = infoPanel.getInsets();
        int width = scrollPaneWidth - insets.left - insets.right - insets2.left - insets2.right - scrollBarWidth - SMALLGAP;
        if (row > -1) {
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

    @Override
    public boolean ok(StringBuilder sb) {
        if (!pluginPreferencesActivated)
            return false;

        Collection<PluginInformation> installedPlugins = PluginHandler.getConfigPlugins();

        // save the user's new choices
        pnlPluginUpdatePolicy.rememberInPreferences();
        List<String> l = getModel().getSelectedPluginNames();
        Collections.sort(l);
        Config.getPref().putList("plugins", l);

        // Create a task for downloading plugins if the user has activated, yet not downloaded, new plugins
        final Collection<PluginInformation> toDownload = getModel().getPluginsScheduledForDownload();

        if (!Utils.isEmpty(toDownload)) {
            List<JarDownloadTask> taskList = toDownload.stream()
                .map(JarDownloadTask::new)
                .map(task -> task.withProgressMonitor(new ProgMonNotification(new Notification())))
                .map(CompletableFuture::supplyAsync)
                .map(task -> task.join())
                .toList();

            PluginHandler.installDownloadedPlugins(true);
            sb.append(PluginPreference.buildDownloadSummary(taskList));
        }

        //
        // If any plugins were deactivated or updated, we have to restart.  Note: this
        // works for updates too because getDeactivatedPlugins will report the
        // deactivated old version of an updated plugin.
        //
        List<PluginInformation> deactivatedPlugins = getDeactivatedPlugins(installedPlugins, PluginHandler.getConfigPlugins());
        boolean needRestart = PluginHandler.removePlugins(deactivatedPlugins);

        //
        // Load all new plugins that can be loaded at runtime
        // (The user may want to do some more work before restarting.)
        //
        PluginHandler.pluginListNotLoaded.clear();
        List<PluginInformation> activatedPlugins = getActivatedPlugins(installedPlugins, PluginHandler.getConfigPlugins());
        Logging.debug("PluginPreference.ok: activated {0} deactivated {1} installed {2}",
            activatedPlugins.size(), deactivatedPlugins.size(), installedPlugins.size());

        needRestart |= !PluginHandler.loadPlugins(getTabPane(), activatedPlugins, null, true).isEmpty();

        Logging.debug("PluginPreference.ok: {0} needRestart={1}", sb.toString(), needRestart);

        return needRestart;
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
     * Scans all locally available plugin jar files.
     */
    public Collection<PluginInformation> scanLocallyCachedSiteManifestFiles() {
        File pluginDir = Preferences.main().getPluginsDirectory();
        Collection<PluginInformation> l = PluginHandler.scanCachedSiteManifestFiles(pluginDir);
        Logging.debug("PluginPreference.scanLocallyCachedSiteManifestFiles -> addUpstreamPlugins: {0} plugins", l.size());
        return l;
    }

    /**
     * The action for updating the list of selected plugins
     */
    class UpdateSelectedPluginsAction extends AbstractAction {
        UpdateSelectedPluginsAction() {
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
         * Update plugins
         */
        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                final List<PluginInformation> toUpdate = model.getPluginsScheduledForUpdate();
                if (toUpdate.isEmpty()) {
                    alertNothingToUpdate();
                    return;
                }
                List<JarDownloadTask> taskList = JarDownloadTask.join(JarDownloadTask.supplyAsync(toUpdate));

                boolean restartRequired = toUpdate.stream().anyMatch(Predicate.not(PluginInformation::canLoadAtRuntime));
                model.clearLocalPlugins();
                model.addLocalPlugins(scanLocalPlugins());
                model.fireTableDataChanged();
                notifyDownloadResults(table, taskList, restartRequired);
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
     * The action for configuring the plugin download sites
     *
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
            PluginConfigurationSitesPanel pnl = new PluginConfigurationSitesPanel();

            int answer = HelpAwareOptionPane.showOptionDialog(
                    table,
                    pnl,
                    tr("Configure Plugin Sites"),
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0],
                    null /* no help topic */
                    );
            if (answer != 0 /* OK */)
                return;
            Preferences.main().setPluginSites(pnl.getUpdateSites());
        }
    }

    /**
     * The action for selecting the plugins given by a text file compatible to JOSM bug report.
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
     * The action for selecting the plugins given by a text file compatible to JOSM bug report.
     * @author Michael Zangl
     */
    class CopyToClipboardAction extends AbstractAction {
        CopyToClipboardAction() {
            putValue(NAME, tr("Copy to clipboard"));
            putValue(SHORT_DESCRIPTION, tr("Copies a list of selected plugins to the clipboard"));
            new ImageProvider("copy").getResource().attachImageIcon(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            ClipboardUtils.copyString(String.join("\n", model.getSelectedPluginNames()));
            Notification note = new Notification("Plugin list copied to clipboard!");
            note.setIcon(JOptionPane.INFORMATION_MESSAGE);
            note.show();
        }
    }

    /**
     * The action for selecting the plugins given by a text file compatible to JOSM bug report.
     * @author Michael Zangl
     */
    class PurgeAction extends AbstractAction {
        PurgeAction() {
            putValue(NAME, tr("Purge old jars"));
            putValue(SHORT_DESCRIPTION, tr("Removes all not currently configured plugin files."));
            new ImageProvider("dialogs/delete").getResource().attachImageIcon(this);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            List<String> configured = PluginHandler.getConfigPluginNames();
            List<PluginInformation> notConfigured = scanLocalPlugins().stream()
                .filter(pi -> !configured.contains(pi.getName())).toList();
            for (var pi : notConfigured) {
                try {
                    Path path = Path.of(pi.getUri());
                    Files.delete(path);
                    Path dir = Path.of(path.getParent().toString(), pi.getName());
                    try (Stream<Path> walk = Files.walk(dir)) {
                        walk.sorted(Comparator.reverseOrder())
                            .map(Path::toFile)
                            .forEach(File::delete);
                    } catch (IOException ex) {
                        // this plugin has no directory
                    }
                } catch (IOException ex) {
                    JOptionPane.showMessageDialog(
                        getTabPane(),
                        tr("Could not delete file: {0}.", ex.getMessage()),
                        tr("Warning"),
                        JOptionPane.WARNING_MESSAGE
                    );
                }
            }
        }
    }

    private static class PluginConfigurationSitesPanel extends JPanel {

        private final DefaultListModel<String> model = new DefaultListModel<>();

        PluginConfigurationSitesPanel() {
            super(new GridBagLayout());
            add(new JLabel(tr("Add JOSM Plugin description URL.")), GBC.eol());
            for (String s : Preferences.main().getPluginSites()) {
                model.addElement(s);
            }
            final JList<String> list = new JList<>(model);
            add(new JScrollPane(list), GBC.std().fill());
            JPanel buttons = new JPanel(new GridBagLayout());
            buttons.add(new JButton(new AbstractAction(tr("Add")) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String s = JOptionPane.showInputDialog(
                            GuiHelper.getFrameForComponent(PluginConfigurationSitesPanel.this),
                            tr("Add JOSM Plugin description URL."),
                            tr("Enter URL"),
                            JOptionPane.QUESTION_MESSAGE
                            );
                    if (!Utils.isEmpty(s)) {
                        model.addElement(s);
                    }
                }
            }), GBC.eol().fill(HORIZONTAL));
            buttons.add(new JButton(new AbstractAction(tr("Edit")) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if (list.getSelectedValue() == null) {
                        JOptionPane.showMessageDialog(
                                GuiHelper.getFrameForComponent(PluginConfigurationSitesPanel.this),
                                tr("Please select an entry."),
                                tr("Warning"),
                                JOptionPane.WARNING_MESSAGE
                                );
                        return;
                    }
                    String s = (String) JOptionPane.showInputDialog(
                            MainApplication.getMainFrame(),
                            tr("Edit JOSM Plugin description URL."),
                            tr("JOSM Plugin description URL"),
                            JOptionPane.QUESTION_MESSAGE,
                            null,
                            null,
                            list.getSelectedValue()
                            );
                    if (!Utils.isEmpty(s)) {
                        model.setElementAt(s, list.getSelectedIndex());
                    }
                }
            }), GBC.eol().fill(HORIZONTAL));
            buttons.add(new JButton(new AbstractAction(tr("Delete")) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    if (list.getSelectedValue() == null) {
                        JOptionPane.showMessageDialog(
                                GuiHelper.getFrameForComponent(PluginConfigurationSitesPanel.this),
                                tr("Please select an entry."),
                                tr("Warning"),
                                JOptionPane.WARNING_MESSAGE
                                );
                        return;
                    }
                    model.removeElement(list.getSelectedValue());
                }
            }), GBC.eol().fill(HORIZONTAL));
            buttons.add(new JButton(new AbstractAction(tr("Move Up")) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    int index = list.getSelectedIndex();
                    if (index > 0) {
                        model.add(index - 1, model.remove(index));
                        list.setSelectedIndex(index - 1);
                    }
                }
            }), GBC.eol().fill(HORIZONTAL));
            buttons.add(new JButton(new AbstractAction(tr("Move Down")) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    int index = list.getSelectedIndex();
                    if (index < model.size() - 1) {
                        model.add(index + 1, model.remove(index));
                        list.setSelectedIndex(index + 1);
                    }
                }
            }), GBC.eol().fill(HORIZONTAL));
            buttons.add(new JButton(new AbstractAction(tr("Add Default Site")) {
                @Override
                public void actionPerformed(ActionEvent event) {
                    model.add(0, Preferences.getDefaultPluginSite());
                }
            }), GBC.eol().fill(HORIZONTAL));
            add(buttons, GBC.eol());

            add(new JLabel(tr("<html>Use:<ul>" +
                "<li>https:// to install plugins from the web" +
                "<li>file:///path/to/file to install a single jar" +
                "<li>file:///path/to/directory/ to install plugins from a local directory" +
                "<li>github://owner/repo to install plugins from a github repository")),
            GBC.eol());
        }

        protected List<String> getUpdateSites() {
            if (model.getSize() == 0)
                return Collections.emptyList();
            return IntStream.range(0, model.getSize())
                    .mapToObj(model::get)
                    .collect(Collectors.toList());
        }
    }

    @Override
    public String getHelpContext() {
        return HelpUtil.ht("/Preferences/Plugins");
    }
}
