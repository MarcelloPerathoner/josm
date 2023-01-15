// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.display;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import javax.swing.JCheckBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JSeparator;
import javax.swing.JSpinner;
import javax.swing.ListCellRenderer;
import javax.swing.LookAndFeel;
import javax.swing.SpinnerNumberModel;
import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import org.openstreetmap.josm.actions.ExpertToggleAction;
import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapMover;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.dialogs.properties.PropertiesDialog;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.preferences.SubPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.TabPreferenceSetting;
import org.openstreetmap.josm.gui.util.WindowGeometry;
import org.openstreetmap.josm.gui.widgets.FileChooserManager;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.gui.widgets.VerticallyScrollablePanel;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.PlatformManager;
import org.openstreetmap.josm.tools.date.DateUtils;

/**
 * Look-and-feel preferences.
 */
public class LafPreference implements SubPreferenceSetting {

    /**
     * Look-and-feel property.
     * @since 11713
     */
    public static final StringProperty LAF = new StringProperty("laf", PlatformManager.getPlatform().getDefaultStyle());

    static final class LafListCellRenderer implements ListCellRenderer<LookAndFeelInfo> {
        private final ListCellRenderer<Object> def = new JosmComboBox<>().getRenderer();

        @Override
        public Component getListCellRendererComponent(JList<? extends LookAndFeelInfo> list, LookAndFeelInfo value,
                int index, boolean isSelected, boolean cellHasFocus) {
            return def.getListCellRendererComponent(list, value.getName(), index, isSelected, cellHasFocus);
        }
    }

    /**
     * Factory used to create a new {@code LafPreference}.
     */
    public static class Factory implements PreferenceSettingFactory {
        @Override
        public PreferenceSetting createPreferenceSetting() {
            return new LafPreference();
        }
    }

    /**
     * ComboBox with all look and feels.
     */
    private JosmComboBox<LookAndFeelInfo> lafCombo;
    private final JCheckBox showSplashScreen = new JCheckBox(tr("Show splash screen at startup"));
    private final JCheckBox showUser = new JCheckBox(tr("Show user name in title"));
    private final JCheckBox showID = new JCheckBox(tr("Show object ID in selection lists"));
    private final JCheckBox showVersion = new JCheckBox(tr("Show object version in selection lists"));
    private final JCheckBox showCoor = new JCheckBox(tr("Show node coordinates in selection lists"));
    private final JCheckBox showLocalizedName = new JCheckBox(tr("Show localized name in selection lists"));
    private final JCheckBox modeless = new JCheckBox(tr("Modeless working (Potlatch style)"));
    private final JCheckBox previewPropsOnHover = new JCheckBox(tr("Preview object properties on mouse hover"));
    private final JCheckBox previewPrioritizeSelection = new JCheckBox(tr("Prefer showing information for selected objects"));
    private final JCheckBox dynamicButtons = new JCheckBox(tr("Dynamic buttons in side menus"));
    private final JCheckBox isoDates = new JCheckBox(tr("Display ISO dates"));
    private final JCheckBox dialogGeometry = new JCheckBox(tr("Remember dialog geometries"));
    private final JCheckBox nativeFileChoosers = new JCheckBox(tr("Use native file choosers (nicer, but do not support file filters)"));
    private final JCheckBox zoomReverseWheel = new JCheckBox(tr("Reverse zoom with mouse wheel"));
    private final JCheckBox zoomIntermediateSteps = new JCheckBox(tr("Intermediate steps between native resolutions"));
    private JSpinner spinZoomRatio;

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        lafCombo = new JosmComboBox<>(UIManager.getInstalledLookAndFeels());

        // let's try to load additional LookAndFeels and put them into the list
        if (PlatformManager.isPlatformOsx()) {
            try {
                Class<?> cquaqua = Class.forName("ch.randelshofer.quaqua.QuaquaLookAndFeel");
                Object oquaqua = cquaqua.getConstructor((Class[]) null).newInstance((Object[]) null);
                // no exception? Then Go!
                lafCombo.addItem(
                        new UIManager.LookAndFeelInfo(((LookAndFeel) oquaqua).getName(), "ch.randelshofer.quaqua.QuaquaLookAndFeel")
                );
            } catch (ReflectiveOperationException ex) {
                // just debug, Quaqua may not even be installed...
                Logging.debug(ex);
            }
        }

        String laf = LAF.get();
        for (int i = 0; i < lafCombo.getItemCount(); ++i) {
            if (lafCombo.getItemAt(i).getClassName().equals(laf)) {
                lafCombo.setSelectedIndex(i);
                break;
            }
        }

        lafCombo.setRenderer(new LafListCellRenderer());

        VerticallyScrollablePanel panel = new VerticallyScrollablePanel(new GridBagLayout());
        GBC std = GBC.std();
        GBC eol = GBC.eol();

        // First the most important setting "Look and Feel" that changes the most
        panel.add(new JLabel(tr("Look and Feel")), std);
        panel.add(DefaultTabPreferenceSetting.hGlue(), std);
        panel.add(lafCombo, eol);
        panel.add(DefaultTabPreferenceSetting.vSkip(), eol);

        panel.add(new JSeparator(), GBC.eop().fill(GridBagConstraints.HORIZONTAL));

        // Show splash screen on startup
        showSplashScreen.setToolTipText(tr("Show splash screen at startup"));
        showSplashScreen.setSelected(Config.getPref().getBoolean("draw.splashscreen", true));

        // Show user name in title
        showUser.setToolTipText(tr("Show user name in title"));
        showUser.setSelected(Config.getPref().getBoolean("draw.show-user", false));

        // Show ID in selection
        showID.setToolTipText(tr("Show object ID in selection lists"));
        showID.setSelected(Config.getPref().getBoolean("osm-primitives.showid", false));

        // Show version in selection
        showVersion.setToolTipText(tr("Show object version in selection lists"));
        showVersion.setSelected(Config.getPref().getBoolean("osm-primitives.showversion", false));

        // Show Coordinates in selection
        showCoor.setToolTipText(tr("Show node coordinates in selection lists"));
        showCoor.setSelected(Config.getPref().getBoolean("osm-primitives.showcoor", false));

        // Show localized names
        showLocalizedName.setToolTipText(tr("Show localized name in selection lists, if available"));
        showLocalizedName.setSelected(Config.getPref().getBoolean("osm-primitives.localize-name", true));

        // Modeless working
        modeless.setToolTipText(tr("Do not require to switch modes (potlatch style workflow)"));
        modeless.setSelected(MapFrame.MODELESS.get());

        panel.add(showSplashScreen, eol);
        panel.add(showUser, eol);
        panel.add(showID, eol);
        panel.add(showVersion, eol);
        panel.add(showCoor, eol);
        panel.add(showLocalizedName, eol);
        panel.add(modeless, eol);

        ExpertToggleAction.addVisibilitySwitcher(showLocalizedName);
        ExpertToggleAction.addVisibilitySwitcher(modeless);

        previewPropsOnHover.setToolTipText(
                tr("Show tags and relation memberships of objects in the properties dialog when hovering over them with the mouse pointer"));
        previewPropsOnHover.setSelected(PropertiesDialog.PROP_PREVIEW_ON_HOVER.get());
        panel.add(previewPropsOnHover, eol);
        previewPropsOnHover.addActionListener(l -> previewPrioritizeSelection.setEnabled(previewPropsOnHover.isSelected()));

        Insets indent = DefaultTabPreferenceSetting.getIndent();
        previewPrioritizeSelection.setToolTipText(
            tr("Always show information for selected objects when something is selected instead of the hovered object"));
        previewPrioritizeSelection.setSelected(PropertiesDialog.PROP_PREVIEW_ON_HOVER_PRIORITIZE_SELECTION.get());
        panel.add(previewPrioritizeSelection, GBC.eol().insets(indent));

        dynamicButtons.setToolTipText(tr("Display buttons in right side menus only when mouse is inside the element"));
        dynamicButtons.setSelected(ToggleDialog.PROP_DYNAMIC_BUTTONS.get());
        panel.add(dynamicButtons, eol);

        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        isoDates.setToolTipText(tr("Format dates according to {0}. Today''s date will be displayed as {1} instead of {2}",
                tr("ISO 8601"),
                today.toString(),
                DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT).format(today)));
        isoDates.setSelected(DateUtils.PROP_ISO_DATES.get());
        panel.add(isoDates, eol);

        dialogGeometry.setSelected(WindowGeometry.GUI_GEOMETRY_ENABLED.get());
        panel.add(dialogGeometry, eol);

        nativeFileChoosers.setToolTipText(
                tr("Use file choosers that behave more like native ones. They look nicer but do not support some features like file filters"));
        nativeFileChoosers.setSelected(FileChooserManager.PROP_USE_NATIVE_FILE_DIALOG.get());
        panel.add(nativeFileChoosers, eol);

        zoomReverseWheel.setToolTipText(
                tr("Check if you feel opposite direction more convenient"));
        zoomReverseWheel.setSelected(MapMover.PROP_ZOOM_REVERSE_WHEEL.get());
        panel.add(zoomReverseWheel, eol);

        zoomIntermediateSteps.setToolTipText(
                tr("Divide intervals between native resolution levels to smaller steps if they are much larger than zoom ratio"));
        zoomIntermediateSteps.setSelected(NavigatableComponent.PROP_ZOOM_INTERMEDIATE_STEPS.get());
        ExpertToggleAction.addVisibilitySwitcher(zoomIntermediateSteps);
        panel.add(zoomIntermediateSteps, eol);

        double logZoomLevel = Math.log(2) / Math.log(NavigatableComponent.PROP_ZOOM_RATIO.get());
        logZoomLevel = Math.max(1, logZoomLevel);
        logZoomLevel = Math.min(5, logZoomLevel);
        JLabel labelZoomRatio = new JLabel(tr("Zoom steps to get double scale"));
        spinZoomRatio = new JSpinner(new SpinnerNumberModel(logZoomLevel, 1, 5, 1));
        Component spinZoomRatioEditor = spinZoomRatio.getEditor();
        JFormattedTextField jftf = ((JSpinner.DefaultEditor) spinZoomRatioEditor).getTextField();
        jftf.setColumns(2);
        String zoomRatioToolTipText = tr("Higher value means more steps needed, therefore zoom steps will be smaller");
        spinZoomRatio.setToolTipText(zoomRatioToolTipText);
        labelZoomRatio.setToolTipText(zoomRatioToolTipText);
        labelZoomRatio.setLabelFor(spinZoomRatio);
        panel.add(labelZoomRatio, std);
        panel.add(DefaultTabPreferenceSetting.hGlue(), std);
        panel.add(spinZoomRatio, eol);

        getTabPreferenceSetting(gui).addSubTab(
            this, tr("Look and Feel"), DefaultTabPreferenceSetting.decorateScrollable(panel));
    }

    @Override
    public boolean ok() {
        boolean mod = false;
        Config.getPref().putBoolean("draw.splashscreen", showSplashScreen.isSelected());
        Config.getPref().putBoolean("draw.show-user", showUser.isSelected());
        Config.getPref().putBoolean("osm-primitives.showid", showID.isSelected());
        Config.getPref().putBoolean("osm-primitives.showversion", showVersion.isSelected());
        Config.getPref().putBoolean("osm-primitives.showcoor", showCoor.isSelected());
        Config.getPref().putBoolean("osm-primitives.localize-name", showLocalizedName.isSelected());
        MapFrame.MODELESS.put(modeless.isSelected());
        PropertiesDialog.PROP_PREVIEW_ON_HOVER.put(previewPropsOnHover.isSelected());
        PropertiesDialog.PROP_PREVIEW_ON_HOVER_PRIORITIZE_SELECTION.put(previewPrioritizeSelection.isSelected());
        Config.getPref().putBoolean(ToggleDialog.PROP_DYNAMIC_BUTTONS.getKey(), dynamicButtons.isSelected());
        Config.getPref().putBoolean(DateUtils.PROP_ISO_DATES.getKey(), isoDates.isSelected());
        WindowGeometry.GUI_GEOMETRY_ENABLED.put(dialogGeometry.isSelected());
        Config.getPref().putBoolean(FileChooserManager.PROP_USE_NATIVE_FILE_DIALOG.getKey(), nativeFileChoosers.isSelected());
        MapMover.PROP_ZOOM_REVERSE_WHEEL.put(zoomReverseWheel.isSelected());
        NavigatableComponent.PROP_ZOOM_INTERMEDIATE_STEPS.put(zoomIntermediateSteps.isSelected());
        NavigatableComponent.PROP_ZOOM_RATIO.put(Math.pow(2, 1/(double) spinZoomRatio.getModel().getValue()));
        mod |= LAF.put(((LookAndFeelInfo) lafCombo.getSelectedItem()).getClassName());
        return mod;
    }

    @Override
    public boolean isExpert() {
        return false;
    }

    @Override
    public TabPreferenceSetting getTabPreferenceSetting(final PreferenceTabbedPane gui) {
        return gui.getDisplayPreference();
    }

}
