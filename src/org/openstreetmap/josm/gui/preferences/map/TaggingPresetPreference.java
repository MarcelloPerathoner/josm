// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.map;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

import org.openstreetmap.josm.data.preferences.sources.ExtendedSourceEntry;
import org.openstreetmap.josm.data.preferences.sources.PresetPrefHelper;
import org.openstreetmap.josm.data.preferences.sources.SourceEntry;
import org.openstreetmap.josm.data.preferences.sources.SourceProvider;
import org.openstreetmap.josm.data.preferences.sources.SourceType;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane.PreferencePanel;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane.ValidationListener;
import org.openstreetmap.josm.gui.preferences.SourceEditor;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetReader;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresets;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Preference settings for tagging presets.
 */
public final class TaggingPresetPreference extends DefaultTabPreferenceSetting {

    private final class TaggingPresetValidationListener implements ValidationListener {
        @Override
        public boolean validatePreferences() {
            Set<SourceEntry> newSources = new HashSet<>(getActiveSources());
            newSources.removeAll(sourceEditor.getInitialSourcesList());

            for (SourceEntry source: newSources) {
                String errorMessage = null;

                try {
                    TaggingPresetReader.read(source.url, true);
                } catch (IOException e) {
                    errorMessage = tr("Could not read tagging preset source: {0}\nDo you want to keep it?", source);
                    Logging.log(Logging.LEVEL_WARN, tr("Could not read tagging preset source: {0}", source), e);
                } catch (SAXParseException e) {
                    errorMessage = tr("<html>Tagging preset source {0} can be loaded but it contains errors. " +
                            "Do you really want to use it?<br><br><table width=600>Error is: [{1}:{2}] {3}</table></html>",
                            source, e.getLineNumber(), e.getColumnNumber(), Utils.escapeReservedCharactersHTML(e.getMessage()));
                    Logging.log(Logging.LEVEL_WARN, errorMessage, e);
                } catch (SAXException e) {
                    errorMessage = tr("<html>Tagging preset source {0} can be loaded but it contains errors. " +
                            "Do you really want to use it?<br><br><table width=600>Error is: {1}</table></html>",
                            source, Utils.escapeReservedCharactersHTML(e.getMessage()));
                    Logging.log(Logging.LEVEL_WARN, errorMessage, e);
                }

                if (errorMessage != null) {
                    Logging.error(errorMessage);
                    int result = JOptionPane.showConfirmDialog(MainApplication.getMainFrame(), new JLabel(errorMessage), tr("Error"),
                            JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.ERROR_MESSAGE);

                    switch (result) {
                    case JOptionPane.YES_OPTION:
                        continue;
                    case JOptionPane.NO_OPTION:
                        sourceEditor.getModel().removeSource(source);
                        continue;
                    default:
                        return false;
                    }
                }
            }
            return true;
        }
    }

    /**
     * Factory used to create a new {@code TaggingPresetPreference}.
     */
    public static class Factory implements PreferenceSettingFactory {
        @Override
        public PreferenceSetting createPreferenceSetting() {
            return new TaggingPresetPreference();
        }
    }

    private static final List<SourceProvider> presetSourceProviders = new ArrayList<>();
    private final ValidationListener validationListener = new TaggingPresetValidationListener();

    private SourceEditor sourceEditor;
    private JCheckBox useValidator;
    private JCheckBox sortMenu;

    private TaggingPresetPreference() {
        super("dialogs/propertiesdialog", tr("Tagging Presets"), tr("Tagging Presets"));
    }

    /**
     * Registers a new additional preset source provider.
     * @param provider The preset source provider
     * @return {@code true}, if the provider has been added, {@code false} otherwise
     */
    public static boolean registerSourceProvider(SourceProvider provider) {
        if (provider != null)
            return presetSourceProviders.add(provider);
        return false;
    }

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        useValidator = new JCheckBox(tr("Run data validator on user input"), TaggingPresets.USE_VALIDATOR.get());
        sortMenu = new JCheckBox(tr("Sort presets menu alphabetically"), TaggingPresets.SORT_VALUES.get());

        final JPanel panel = new JPanel(new GridBagLayout());

        panel.add(useValidator, GBC.std());
        panel.add(new JLabel(ImageProvider.get("dialogs/validator")), GBC.eol());

        panel.add(sortMenu, GBC.eop());

        sourceEditor = new TaggingPresetSourceEditor();
        panel.add(sourceEditor, GBC.eop().fill());

        PreferencePanel preferencePanel = gui.createPreferenceTab(this);
        preferencePanel.add(decorate(panel), GBC.eol().fill());
        sourceEditor.deferLoading(gui, preferencePanel);
        gui.addValidationListener(validationListener);
    }

    public static class TaggingPresetSourceEditor extends SourceEditor {

        public TaggingPresetSourceEditor() {
            super(SourceType.TAGGING_PRESET, Config.getUrls().getJOSMWebsite()+"/presets", presetSourceProviders, true);
        }

        @Override
        public List<SourceEntry> getInitialSourcesList() {
            return PresetPrefHelper.INSTANCE.get();
        }

        @Override
        public boolean finish() {
            return doFinish(PresetPrefHelper.INSTANCE, TaggingPresets.ICON_SOURCES.getKey());
        }

        @Override
        public Collection<ExtendedSourceEntry> getDefault() {
            return PresetPrefHelper.INSTANCE.getDefault();
        }

        @Override
        public Collection<String> getInitialIconPathsList() {
            return TaggingPresets.ICON_SOURCES.get();
        }

        @Override
        public String getStr(I18nString ident) {
            switch (ident) {
            case AVAILABLE_SOURCES:
                return tr("Available presets:");
            case ACTIVE_SOURCES:
                return tr("Active presets:");
            case NEW_SOURCE_ENTRY_TOOLTIP:
                return tr("Add a new preset by entering filename or URL");
            case NEW_SOURCE_ENTRY:
                return tr("New preset entry:");
            case REMOVE_SOURCE_TOOLTIP:
                return tr("Remove the selected presets from the list of active presets");
            case EDIT_SOURCE_TOOLTIP:
                return tr("Edit the filename or URL for the selected active preset");
            case ACTIVATE_TOOLTIP:
                return tr("Add the selected available presets to the list of active presets");
            case RELOAD_ALL_AVAILABLE:
                return marktr("Reloads the list of available presets from ''{0}''");
            case LOADING_SOURCES_FROM:
                return marktr("Loading preset sources from ''{0}''");
            case FAILED_TO_LOAD_SOURCES_FROM:
                return marktr("<html>Failed to load the list of preset sources from<br>"
                        + "''{0}''.<br>"
                        + "<br>"
                        + "Details (untranslated):<br>{1}</html>");
            case FAILED_TO_LOAD_SOURCES_FROM_HELP_TOPIC:
                return "/Preferences/Presets#FailedToLoadPresetSources";
            case ILLEGAL_FORMAT_OF_ENTRY:
                return marktr("Warning: illegal format of entry in preset list ''{0}''. Got ''{1}''");
            default: throw new AssertionError();
            }
        }
    }

    @Override
    public boolean ok() {
        Set<SourceEntry> removedSources = new HashSet<>(sourceEditor.getInitialSourcesList());
        removedSources.removeAll(getActiveSources());

        Set<SourceEntry> addedSources = new HashSet<>(getActiveSources());
        addedSources.removeAll(sourceEditor.getInitialSourcesList());

        TaggingPresets.USE_VALIDATOR.put(useValidator.isSelected());

        boolean settingsChanged = TaggingPresets.SORT_VALUES.put(sortMenu.isSelected());
        boolean sourcesChanged = !addedSources.isEmpty() || !removedSources.isEmpty();

        if (sourceEditor.finish() || sourcesChanged || settingsChanged) {
            JMenu presetsMenu = MainApplication.getMenu().presetsMenu;
            ToolbarPreferences toolbar = MainApplication.getToolbar();
            TaggingPresets taggingPresets = MainApplication.getTaggingPresets();
            removedSources.forEach(s -> taggingPresets.removeSource(s.url));
            addedSources.forEach(s -> taggingPresets.addSourceFromUrl(s.url));
            taggingPresets.reInit(presetsMenu, toolbar);
        }
        return false;
    }

    /**
     * Returns all active sources.
     */
    public List<SourceEntry> getActiveSources() {
        return sourceEditor.getModel().getSources();
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    @Override
    public String getHelpContext() {
        return HelpUtil.ht("/Preferences/TaggingPresetPreference");
    }
}
