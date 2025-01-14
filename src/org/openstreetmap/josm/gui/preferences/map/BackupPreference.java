// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.map;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JSeparator;

import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.layer.AutosaveTask;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.widgets.HtmlPanel;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.tools.GBC;

/**
 * Preference settings for data layer autosave.
 */
public class BackupPreference extends DefaultTabPreferenceSetting {

    /**
     * Factory used to create a new {@code BackupPreference}.
     */
    public static class Factory implements PreferenceSettingFactory {
        @Override
        public BackupPreference createPreferenceSetting() {
            return new BackupPreference();
        }
    }

    private static final BooleanProperty PROP_KEEP_BACKUP = new BooleanProperty("save.keepbackup", false);
    private JCheckBox notification;
    private JCheckBox keepBackup;
    private JCheckBox autosave;
    private final JosmTextField autosaveInterval = new JosmTextField(8);
    private final JosmTextField backupPerLayer = new JosmTextField(8);

    BackupPreference() {
        super(/* ICON(preferences/) */ "backup", tr("File backup"), tr("Configure whether to create backup files"));
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        VerticallyScrollablePanel panel = new VerticallyScrollablePanel(new GridBagLayout());

        GBC eop = GBC.eop().fill(GridBagConstraints.HORIZONTAL);
        Insets indent = getIndentForText();
        Insets rightMargin = new Insets(0, 0, 0, 10);
        Insets indentRm = addInsets(indent, rightMargin);

        autosave = new JCheckBox(tr("Auto save enabled"));
        autosave.setSelected(AutosaveTask.PROP_AUTOSAVE_ENABLED.get());
        panel.add(autosave, GBC.eol());

        final JLabel autosaveIntervalLabel = new JLabel(tr("Auto save interval (seconds)"));
        autosaveIntervalLabel.setLabelFor(autosaveInterval);
        panel.add(autosaveIntervalLabel, GBC.std().insets(indentRm));
        autosaveInterval.setText(Integer.toString(AutosaveTask.PROP_INTERVAL.get()));
        autosaveInterval.setToolTipText(tr("Default value: {0}", AutosaveTask.PROP_INTERVAL.getDefaultValue()));
        autosaveInterval.setMinimumSize(autosaveInterval.getPreferredSize());
        panel.add(autosaveInterval, GBC.eol());

        final JLabel backupPerLayerLabel = new JLabel(tr("Auto saved files per layer"));
        backupPerLayerLabel.setLabelFor(backupPerLayer);
        panel.add(backupPerLayerLabel, GBC.std().insets(indentRm));
        backupPerLayer.setText(Integer.toString(AutosaveTask.PROP_FILES_PER_LAYER.get()));
        backupPerLayer.setToolTipText(tr("Default value: {0}", AutosaveTask.PROP_FILES_PER_LAYER.getDefaultValue()));
        backupPerLayer.setMinimumSize(backupPerLayer.getPreferredSize());
        panel.add(backupPerLayer, GBC.eol());

        panel.add(new HtmlPanel(
            tr("<i>(Autosave stores the changed data layers in periodic intervals. " +
                "The backups are saved in JOSM''s preference folder. " +
                "In case of a crash, JOSM tries to recover the unsaved changes " +
                "on next start.)</i>")),
            GBC.eop().insets(indent).fill(GridBagConstraints.HORIZONTAL));

        panel.add(new JSeparator(), eop);

        keepBackup = new JCheckBox(tr("Keep backup files when saving data layers"));
        keepBackup.setSelected(PROP_KEEP_BACKUP.get());
        keepBackup.setToolTipText(tr("When saving, keep backup files ending with a ~"));
        panel.add(keepBackup, GBC.eop());

        panel.add(new HtmlPanel(
            tr("<i>(JOSM can keep a backup file when saving data layers. "+
                "It appends ''~'' to the file name and saves it in the same folder.)</i>")),
            GBC.eop().insets(indent).fill(GridBagConstraints.HORIZONTAL));

        panel.add(new JSeparator(), eop);

        notification = new JCheckBox(tr("Notification at each save"));
        notification.setSelected(AutosaveTask.PROP_NOTIFICATION.get());
        notification.setToolTipText(tr("When saving, display a small notification"));
        panel.add(notification, GBC.eop());

        ActionListener autosaveEnabled = e -> {
            boolean enabled = autosave.isSelected();
            autosaveIntervalLabel.setEnabled(enabled);
            autosaveInterval.setEnabled(enabled);
            backupPerLayerLabel.setEnabled(enabled);
            backupPerLayer.setEnabled(enabled);
        };
        autosave.addActionListener(autosaveEnabled);
        autosaveEnabled.actionPerformed(null);

        gui.createPreferenceTab(this).add(decorateScrollable(panel), GBC.eol().fill());
    }

    @Override
    public boolean ok() {
        boolean restartRequired = false;
        PROP_KEEP_BACKUP.put(keepBackup.isSelected());

        restartRequired |= AutosaveTask.PROP_AUTOSAVE_ENABLED.put(autosave.isSelected());
        restartRequired |= AutosaveTask.PROP_INTERVAL.parseAndPut(autosaveInterval.getText());
        AutosaveTask.PROP_FILES_PER_LAYER.parseAndPut(backupPerLayer.getText());
        AutosaveTask.PROP_NOTIFICATION.put(notification.isSelected());
        return restartRequired;
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    @Override
    public String getHelpContext() {
        return HelpUtil.ht("/Preferences/BackupPreference");
    }
}
