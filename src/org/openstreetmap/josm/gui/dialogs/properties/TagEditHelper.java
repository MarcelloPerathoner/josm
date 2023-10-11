// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs.properties;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;
import static org.openstreetmap.josm.gui.tagging.presets.InteractiveItem.DIFFERENT_I18N;

import java.awt.Component;
import java.awt.ComponentOrientation;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.search.SearchAction;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.Tagged;
import org.openstreetmap.josm.data.osm.search.SearchCompiler;
import org.openstreetmap.josm.data.osm.search.SearchParseError;
import org.openstreetmap.josm.data.osm.search.SearchSetting;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.EnumProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.preferences.ListProperty;
import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.data.tagging.ac.AutoCompletionItem;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.IExtendedDialog;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.tagging.TagTableModel;
import org.openstreetmap.josm.gui.tagging.TagTableModel.ValueType;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompComboBox;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompletionManager;
import org.openstreetmap.josm.gui.tagging.ac.TagTableUtils;
import org.openstreetmap.josm.gui.tagging.presets.Usage;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.util.WindowGeometry;
import org.openstreetmap.josm.gui.widgets.OrientationAction;
import org.openstreetmap.josm.gui.widgets.PopupMenuLauncher;
import org.openstreetmap.josm.io.XmlWriter;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.OsmPrimitiveImageProvider;
import org.openstreetmap.josm.tools.PlatformManager;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

/**
 * Class that helps PropertiesDialog add and edit tag values.
 * @since 5633
 */
public class TagEditHelper {
    static final Comparator<AutoCompletionItem> DEFAULT_AC_ITEM_COMPARATOR =
            (o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getValue(), o2.getValue());

    /** Default number of recent tags */
    public static final int DEFAULT_LRU_TAGS_NUMBER = 5;
    /** Maximum number of recent tags */
    public static final int MAX_LRU_TAGS_NUMBER = 30;
    /** Use English language for tag by default */
    public static final BooleanProperty PROPERTY_FIX_TAG_LOCALE = new BooleanProperty("properties.fix-tag-combobox-locale", false);
    /** Whether recent tags must be remembered */
    public static final BooleanProperty PROPERTY_REMEMBER_TAGS = new BooleanProperty("properties.remember-recently-added-tags", true);
    /** Number of recent tags */
    public static final IntegerProperty PROPERTY_RECENT_TAGS_NUMBER = new IntegerProperty("properties.recently-added-tags",
            DEFAULT_LRU_TAGS_NUMBER);
    /** The preference storage of recent tags */
    public static final ListProperty PROPERTY_RECENT_TAGS = new ListProperty("properties.recent-tags",
            Collections.<String>emptyList());
    /** The preference list of tags which should not be remembered, since r9940 */
    public static final StringProperty PROPERTY_TAGS_TO_IGNORE = new StringProperty("properties.recent-tags.ignore",
            new SearchSetting().writeToString());

    /**
     * What to do with recent tags where keys already exist
     */
    private enum RecentExisting {
        ENABLE,
        DISABLE,
        HIDE
    }

    /**
     * Preference setting for popup menu item "Recent tags with existing key"
     */
    public static final EnumProperty<RecentExisting> PROPERTY_RECENT_EXISTING = new EnumProperty<>(
        "properties.recently-added-tags-existing-key", RecentExisting.class, RecentExisting.DISABLE);

    /**
     * What to do after applying tag
     */
    private enum RefreshRecent {
        NO,
        STATUS,
        REFRESH
    }

    /**
     * Preference setting for popup menu item "Refresh recent tags list after applying tag"
     */
    public static final EnumProperty<RefreshRecent> PROPERTY_REFRESH_RECENT = new EnumProperty<>(
        "properties.refresh-recently-added-tags", RefreshRecent.class, RefreshRecent.STATUS);

    final RecentTagCollection recentTags = new RecentTagCollection(MAX_LRU_TAGS_NUMBER);
    SearchSetting tagsToIgnore;

    /**
     * Copy of recently added tags in sorted from newest to oldest order.
     *
     * We store the maximum number of recent tags to allow dynamic change of number of tags shown in the preferences.
     * Used to cache initial status.
     */
    private List<Tag> tags;

    static {
        // init user input based on recent tags
        final RecentTagCollection recentTags = new RecentTagCollection(MAX_LRU_TAGS_NUMBER);
        recentTags.loadFromPreference(PROPERTY_RECENT_TAGS);
        recentTags.toList().forEach(tag -> AutoCompletionManager.rememberUserInput(tag.getKey(), tag.getValue(), false));
    }

    /**
     * Open the add selection dialog and add a new key/value to the table (and
     * to the dataset, of course).
     * @param tagTableModel the tag table model
     */
    public void addTag(TagTableModel tagTableModel) {
        // clone if you don't want the additions to show up immediately in the properties window
        // tagTableModel = tagTableModel.clone();
        if (tagTableModel.getHandler().get().isEmpty())
            return; // nothing selected

        tagTableModel.getHandler().begin();
        final AddTagsDialog addDialog = new AddTagsDialog(tagTableModel);
        addDialog.showDialog();
        addDialog.destroyActions();

        if (addDialog.getValue() == 1) { // OK
            addDialog.performTagAdding();
            tagTableModel.getHandler().commit(tr("Add Tags"));
        } else {
            tagTableModel.getHandler().abort();
            tagTableModel.initFromHandler(); // if not cloned
        }
    }

    /**
     * Edit the value in the tags table row.
     * @param tagTableModel the tag table model
     * @param key the key to edit
     * @param focusOnKey Determines if the initial focus should be set on key instead of value
     * @since 5653
    */
    public void editTag(TagTableModel tagTableModel, String key, boolean focusOnKey) {
        if (tagTableModel.getHandler().get().isEmpty())
            return;

        final EditTagDialog editDialog = new EditTagDialog(tagTableModel, key, focusOnKey);
        editDialog.showDialog();
        if (editDialog.getValue() == 1)
            editDialog.performTagEdit();
    }

    /**
     * Extracted interface of {@link EditTagDialog}.
     */
    protected interface IEditTagDialog extends IExtendedDialog {
        /**
         * Edit tags of multiple selected objects according to selected ComboBox values
         * If value == "", tag will be deleted
         * Confirmations may be needed.
         */
        void performTagEdit();
    }

    /**
     * Load recently used tags from preferences if needed.
     */
    public void loadTagsIfNeeded() {
        loadTagsToIgnore();
        if (PROPERTY_REMEMBER_TAGS.get() && recentTags.isEmpty()) {
            recentTags.loadFromPreference(PROPERTY_RECENT_TAGS);
        }
    }

    void loadTagsToIgnore() {
        final SearchSetting searchSetting = Utils.firstNonNull(
                SearchSetting.readFromString(PROPERTY_TAGS_TO_IGNORE.get()), new SearchSetting());
        if (!Objects.equals(tagsToIgnore, searchSetting)) {
            try {
                tagsToIgnore = searchSetting;
                recentTags.setTagsToIgnore(tagsToIgnore);
            } catch (SearchParseError parseError) {
                warnAboutParseError(parseError);
                tagsToIgnore = new SearchSetting();
                recentTags.setTagsToIgnore(SearchCompiler.Never.INSTANCE);
            }
        }
    }

    private static void warnAboutParseError(SearchParseError parseError) {
        Logging.warn(parseError);
        JOptionPane.showMessageDialog(
                MainApplication.getMainFrame(),
                parseError.getMessage(),
                tr("Error"),
                JOptionPane.ERROR_MESSAGE
        );
    }

    /**
     * Store recently used tags in preferences if needed.
     */
    public void saveTagsIfNeeded() {
        if (PROPERTY_REMEMBER_TAGS.get() && !recentTags.isEmpty()) {
            recentTags.saveToPreference(PROPERTY_RECENT_TAGS);
        }
    }

    /**
     * Update cache of recent tags used for displaying tags.
     */
    private void cacheRecentTags() {
        tags = recentTags.toList();
        Collections.reverse(tags);
    }

    /**
     * Returns the edited item with whitespaces removed
     * @param cb the combobox
     * @return the edited item with whitespaces removed
     * @since 18173
     */
    public static String getEditItem(AutoCompComboBox<AutoCompletionItem> cb) {
        return Utils.removeWhiteSpaces(cb.getEditorItemAsString());
    }

    /**
     * Returns the selected item or the edited item as string
     * @param cb the combobox
     * @return the selected item or the edited item as string
     * @since 18173
     */
    public static String getSelectedOrEditItem(AutoCompComboBox<AutoCompletionItem> cb) {
        final Object selectedItem = cb.getSelectedItem();
        if (selectedItem != null)
            return selectedItem.toString();
        return getEditItem(cb);
    }

    /**
     * Warns user about a key being overwritten.
     * @param action The action done by the user. Must state what key is changed
     * @param togglePref  The preference to save the checkbox state to
     * @return {@code true} if the user accepts to overwrite key, {@code false} otherwise
     */
    public static boolean warnOverwriteKey(String action, String togglePref) {
        return new ExtendedDialog(
                MainApplication.getMainFrame(),
                tr("Overwrite tag"),
                tr("Overwrite"), tr("Cancel"))
            .setButtonIcons("ok", "cancel")
            .setContent(action)
            .setCancelButton(2)
            .toggleEnable(togglePref)
            .showDialog().getValue() == 1;
    }

    protected class EditTagDialog extends AbstractTagsDialog implements IEditTagDialog {
        private final String key;
        private final ValueType valueType;

        protected EditTagDialog(TagTableModel tagTableModel, String key, boolean initialFocusOnKey) {
            super(MainApplication.getMainFrame(), tagTableModel,
                trn("Change value?", "Change values?", tagTableModel.getValueType(key).values().size()),
                tr("OK"), tr("Cancel"));

            this.key = key;
            this.initialFocusOnKey = initialFocusOnKey;
            this.valueType = tagTableModel.getValueType(key);
            int count = tagTableModel.getHandler().get().size();

            setButtonIcons("ok", "cancel");
            setCancelButton(2);
            configureContextsensitiveHelp("/Dialog/EditValue", true /* show help button */);

            JPanel mainPanel = new JPanel(new GridBagLayout()) {
                /**
                 * This hack allows the comboboxes to have their own orientation.
                 * <p>
                 * The problem is that
                 * {@link org.openstreetmap.josm.gui.ExtendedDialog#showDialog ExtendedDialog} calls
                 * {@code applyComponentOrientation} very late in the dialog construction process
                 * thus overwriting the orientation the components have chosen for themselves.
                 * <p>
                 * This stops the propagation of {@code applyComponentOrientation}, thus all
                 * components may (and have to) set their own orientation.
                 */
                @Override
                public void applyComponentOrientation(ComponentOrientation o) {
                    setComponentOrientation(o);
                }
            };

            mainPanel.add(new JLabel(trn("This will change {0} object.",
                "This will change up to {0} objects.", count, count)), GBC.eol());
            mainPanel.add(new JLabel(tr("An empty value deletes the tag.", key)), GBC.eop());

            keyEditor = tagTableUtils.getKeyEditor(null);
            valueEditor = tagTableUtils.getValueEditor(null);

            keyEditor.setFixedLocale(PROPERTY_FIX_TAG_LOCALE.get());
            keyEditor.setSelectedItemText(key);
            valueEditor.setSelectedItemText(valueType.toString());
            tagTableUtils.setKeySupplier(keyEditor::getText);

            mainPanel.add(new JLabel(tr("Key")), GBC.std());
            mainPanel.add(keyEditor, GBC.eol().fill(GBC.HORIZONTAL).insets(10, 0, 0, 10));
            mainPanel.add(new JLabel(tr("Value")), GBC.std());
            mainPanel.add(valueEditor, GBC.eop().fill(GBC.HORIZONTAL).insets(10, 0, 0, 10));

            mainPanel.applyComponentOrientation(OrientationAction.getDefaultComponentOrientation());
            keyEditor.applyComponentOrientation(ComponentOrientation.LEFT_TO_RIGHT);
            valueEditor.applyComponentOrientation(OrientationAction.getNamelikeOrientation(keyEditor.getText()));

            setContent(mainPanel, false);

            addEventListeners();
        }

        @Override
        public void performTagEdit() {
            String newKey = keyEditor.getText();
            String value = valueEditor.getText();
            tagTableModel.getHandler().update(key, newKey, value);

            if (!value.isEmpty() && !DIFFERENT_I18N.equals(value)) {
                recentTags.add(new Tag(newKey, value));
                AutoCompletionManager.rememberUserInput(newKey, value, false);
            }
        }
    }

    protected abstract class AbstractTagsDialog extends ExtendedDialog implements FocusListener {
        protected AutoCompComboBox<AutoCompletionItem> keyEditor;
        protected AutoCompComboBox<AutoCompletionItem> valueEditor;
        protected boolean initialFocusOnKey = true;
        /**
         * The 'values' model is currently holding values for this key. Used for lazy-loading of values.
         */
        protected String currentValuesModelKey = "";
        TagTableModel tagTableModel;
        protected TagTableUtils tagTableUtils;

        AbstractTagsDialog(Component parent, TagTableModel tagTableModel, String title, String... buttonTexts) {
            super(parent, title, buttonTexts);
            this.tagTableModel = tagTableModel;
            this.tagTableUtils = new TagTableUtils(tagTableModel, null);
            this.tagTableUtils.setTypes(tagTableModel.getHandler());
            addMouseListener(new PopupMenuLauncher(popupMenu));
            setRememberWindowGeometry(getClass().getName() + ".geometry",
                WindowGeometry.centerInWindow(MainApplication.getMainFrame(), new Dimension(100, 100)));
        }

        @Override
        public void setSize(int width, int height) {
            super.setSize(width, height);
            // This is a bit too small on the GTK L&F (and maybe others).  HiDPI issue?
            setMinimumSize(getPreferredSize());
        }

        protected void addEventListeners() {
            // OK on Enter in values
            valueEditor.getEditor().addActionListener(e -> buttonAction(0, null));
            // update values orientation according to key
            keyEditor.getEditorComponent().addFocusListener(this);
            addComponentListener(new ComponentAdapter() {
                @Override
                public void componentShown(ComponentEvent e) {
                    updateOkButtonIconEnabled();
                    // set the initial focus to either combobox
                    (initialFocusOnKey ? keyEditor : valueEditor).requestFocusInWindow();
                }
            });
        }

        @Override
        public void focusGained(FocusEvent e) {
        }

        @Override
        public void focusLost(FocusEvent e) {
            // update the values combobox orientation if the key changed
            valueEditor.applyComponentOrientation(OrientationAction.getNamelikeOrientation(keyEditor.getText()));
        }

        protected void updateOkButtonIconEnabled() {
            if (buttons.isEmpty()) {
                return;
            }
            buttons.get(0).setIcon(findIcon(getSelectedOrEditItem(keyEditor), getSelectedOrEditItem(valueEditor))
                    .orElse(ImageProvider.get("ok", ImageProvider.ImageSizes.LARGEICON)));
            buttons.get(0).setEnabled(!tagTableModel.getHandler().isReadOnly());
        }

        protected Optional<ImageIcon> findIcon(String key, String value) {
            OsmPrimitiveType type = OsmPrimitiveType.NODE;

            Collection<? extends Tagged> sel = tagTableModel.getHandler().get();
            if (!sel.isEmpty()) {
                Tagged first = sel.iterator().next();
                if (first instanceof OsmPrimitive) {
                    type = ((OsmPrimitive) first).getType();
                }
            }
            return OsmPrimitiveImageProvider.getResource(key, value, type)
                    .map(resource -> resource.getPaddedIcon(ImageProvider.ImageSizes.LARGEICON.getImageDimension()));
        }

        protected JPopupMenu popupMenu = new JPopupMenu() {
            private final JCheckBoxMenuItem fixTagLanguageCb = new JCheckBoxMenuItem(
                new AbstractAction(tr("Use English language for tag by default")) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    boolean use = ((JCheckBoxMenuItem) e.getSource()).getState();
                    PROPERTY_FIX_TAG_LOCALE.put(use);
                    keyEditor.setFixedLocale(use);
                }
            });
            {
                add(fixTagLanguageCb);
                fixTagLanguageCb.setState(PROPERTY_FIX_TAG_LOCALE.get());
            }
        };
    }

    protected class AddTagsDialog extends AbstractTagsDialog {
        private final List<JosmAction> recentTagsActions = new ArrayList<>();
        private final JPanel mainPanel;
        private final JPanel recentTagsPanel;

        protected AddTagsDialog(TagTableModel tagTableModel) {
            super(MainApplication.getMainFrame(), tagTableModel, tr("Add tag"), tr("OK"), tr("Cancel"));
            setButtonIcons("ok", "cancel");
            setCancelButton(2);
            configureContextsensitiveHelp("/Dialog/AddValue", true /* show help button */);

            mainPanel = new JPanel(new GridBagLayout()) {
                /**
                 * This hack allows the comboboxes to have their own orientation.
                 * <p>
                 * The problem is that
                 * {@link org.openstreetmap.josm.gui.ExtendedDialog#showDialog ExtendedDialog} calls
                 * {@code applyComponentOrientation} very late in the dialog construction process
                 * thus overwriting the orientation the components have chosen for themselves.
                 * <p>
                 * This stops the propagation of {@code applyComponentOrientation}, thus all
                 * components may (and have to) set their own orientation.
                 */
                @Override
                public void applyComponentOrientation(ComponentOrientation o) {
                    setComponentOrientation(o);
                }
            };

            keyEditor = tagTableUtils.getKeyEditor(null);
            valueEditor = tagTableUtils.getValueEditor(null);
            tagTableUtils.setKeySupplier(keyEditor::getText);
            keyEditor.setFixedLocale(PROPERTY_FIX_TAG_LOCALE.get());
            int count = tagTableModel.getHandler().get().size();

            mainPanel.add(new JLabel(trn("This will change up to {0} object.",
                "This will change up to {0} objects.", count, count)), GBC.eop());
            mainPanel.add(new JLabel(tr("Please select a key")), GBC.eol());
            mainPanel.add(keyEditor, GBC.eop().fill(GBC.HORIZONTAL));
            mainPanel.add(new JLabel(tr("Choose a value")), GBC.eol());
            mainPanel.add(valueEditor, GBC.eop().fill(GBC.HORIZONTAL));

            recentTagsPanel = new JPanel(new GridBagLayout());
            mainPanel.add(recentTagsPanel, GBC.eop().anchor(GBC.LINE_START));

            cacheRecentTags();
            buildRecentTagsPanel();

            // pre-fill first recent tag for which the key is not already present
            tags.stream()
                    .filter(tag -> !tagTableModel.keySet().contains(tag.getKey()))
                    .findFirst()
                    .ifPresent(tag -> {
                        keyEditor.setSelectedItemText(tag.getKey());
                        valueEditor.setSelectedItemText(tag.getValue());
                    });

            keyEditor.addActionListener(ignore -> updateOkButtonIconEnabled());
            valueEditor.addActionListener(ignore -> updateOkButtonIconEnabled());

            // Add tag on Shift-Enter
            mainPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK), "addAndContinue");
                mainPanel.getActionMap().put("addAndContinue", new AbstractAction() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        performTagAdding();
                        refreshRecentTags();
                        keyEditor.requestFocus();
                    }
                });

            mainPanel.applyComponentOrientation(OrientationAction.getDefaultComponentOrientation());

            setContent(mainPanel, false);

            addEventListeners();

            popupMenu.add(new AbstractAction(tr("Set number of recently added tags")) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    selectNumberOfTags();
                    rebuildRecentTagsPanel();
                }
            });

            popupMenu.add(buildMenuRecentExisting());
            popupMenu.add(buildMenuRefreshRecent());

            JCheckBoxMenuItem rememberLastTags = new JCheckBoxMenuItem(
                new AbstractAction(tr("Remember last used tags after a restart")) {
                @Override
                public void actionPerformed(ActionEvent e) {
                    boolean state = ((JCheckBoxMenuItem) e.getSource()).getState();
                    PROPERTY_REMEMBER_TAGS.put(state);
                    if (state)
                        saveTagsIfNeeded();
                }
            });
            rememberLastTags.setState(PROPERTY_REMEMBER_TAGS.get());
            popupMenu.add(rememberLastTags);
        }

        private JMenu buildMenuRecentExisting() {
            JMenu menu = new JMenu(tr("Recent tags with existing key"));
            TreeMap<RecentExisting, String> radios = new TreeMap<>();
            radios.put(RecentExisting.ENABLE, tr("Enable"));
            radios.put(RecentExisting.DISABLE, tr("Disable"));
            radios.put(RecentExisting.HIDE, tr("Hide"));
            ButtonGroup buttonGroup = new ButtonGroup();
            for (final Map.Entry<RecentExisting, String> entry : radios.entrySet()) {
                JRadioButtonMenuItem radio = new JRadioButtonMenuItem(new AbstractAction(entry.getValue()) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        PROPERTY_RECENT_EXISTING.put(entry.getKey());
                        rebuildRecentTagsPanel();
                    }
                });
                buttonGroup.add(radio);
                radio.setSelected(PROPERTY_RECENT_EXISTING.get() == entry.getKey());
                menu.add(radio);
            }
            return menu;
        }

        private JMenu buildMenuRefreshRecent() {
            JMenu menu = new JMenu(tr("Refresh recent tags list after applying tag"));
            TreeMap<RefreshRecent, String> radios = new TreeMap<>();
            radios.put(RefreshRecent.NO, tr("No refresh"));
            radios.put(RefreshRecent.STATUS, tr("Refresh tag status only (enabled / disabled)"));
            radios.put(RefreshRecent.REFRESH, tr("Refresh tag status and list of recently added tags"));
            ButtonGroup buttonGroup = new ButtonGroup();
            for (final Map.Entry<RefreshRecent, String> entry : radios.entrySet()) {
                JRadioButtonMenuItem radio = new JRadioButtonMenuItem(new AbstractAction(entry.getValue()) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        PROPERTY_REFRESH_RECENT.put(entry.getKey());
                    }
                });
                buttonGroup.add(radio);
                radio.setSelected(PROPERTY_REFRESH_RECENT.get() == entry.getKey());
                menu.add(radio);
            }
            return menu;
        }

        /*
         * This adds the help label *below* the buttons.
         */
        @Override
        public void setContentPane(Container contentPane) {
            final int commandDownMask = PlatformManager.getPlatform().getMenuShortcutKeyMaskEx();
            List<String> lines = new ArrayList<>();
            Shortcut.findShortcut(KeyEvent.VK_1, commandDownMask).ifPresent(sc ->
                    lines.add(sc.getKeyText() + ' ' + tr("to apply first suggestion"))
            );
            lines.add(Shortcut.getKeyText(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK)) + ' '
                    +tr("to add without closing the dialog"));
            Shortcut.findShortcut(KeyEvent.VK_1, commandDownMask | KeyEvent.SHIFT_DOWN_MASK).ifPresent(sc ->
                    lines.add(sc.getKeyText() + ' ' + tr("to add first suggestion without closing the dialog"))
            );
            final JLabel helpLabel = new JLabel("<html>" + String.join("<br>", lines) + "</html>");
            helpLabel.setFont(helpLabel.getFont().deriveFont(Font.PLAIN));
            contentPane.add(helpLabel, GBC.eol().fill(GridBagConstraints.HORIZONTAL).insets(5, 5, 5, 5));
            super.setContentPane(contentPane);
        }

        protected void selectNumberOfTags() {
            String s = String.format("%d", PROPERTY_RECENT_TAGS_NUMBER.get());
            while (true) {
                s = JOptionPane.showInputDialog(this, tr("Please enter the number of recently added tags to display"), s);
                if (Utils.isEmpty(s)) {
                    return;
                }
                try {
                    int v = Integer.parseInt(s);
                    if (v >= 0 && v <= MAX_LRU_TAGS_NUMBER) {
                        PROPERTY_RECENT_TAGS_NUMBER.put(v);
                        return;
                    }
                } catch (NumberFormatException ex) {
                    Logging.warn(ex);
                }
                JOptionPane.showMessageDialog(this, tr("Please enter integer number between 0 and {0}", MAX_LRU_TAGS_NUMBER));
            }
        }

        protected void rebuildRecentTagsPanel() {
            recentTagsPanel.removeAll();
            buildRecentTagsPanel();
            setMinimumSize(getPreferredSize());
            // invalidate();
            // revalidate();
            // repaint();
        }

        /**
         * Determines if the given tag key is already used (by all selected primitives, not just some of them)
         * @param key the key to check
         * @return {@code true} if the key is used by all selected primitives (key not unset for at least one primitive)
         */
        boolean containsDataKey(String key) {
            Usage usage = Usage.determineTextUsage(tagTableModel.getHandler().get(), key);
            return usage.hasUniqueValue();
        }

        protected void buildRecentTagsPanel() {
            final int tagsToShow = Math.min(PROPERTY_RECENT_TAGS_NUMBER.get(), MAX_LRU_TAGS_NUMBER);
            if (!(tagsToShow > 0 && !recentTags.isEmpty()))
                return;
            recentTagsPanel.add(new JLabel(tr("Recently added tags")), GBC.eol());

            int count = 0;
            destroyActions();
            for (int i = 0; i < tags.size() && count < tagsToShow; i++) {
                final Tag t = tags.get(i);
                boolean keyExists = containsDataKey(t.getKey());
                if (keyExists && PROPERTY_RECENT_EXISTING.get() == RecentExisting.HIDE)
                    continue;
                count++;
                // Create action for reusing the tag, with keyboard shortcut
                /* POSSIBLE SHORTCUTS: 1,2,3,4,5,6,7,8,9,0=10 */
                final Shortcut sc = count > 10 ? null : Shortcut.registerShortcut("properties:recent:" + count,
                        tr("Choose recent tag {0}", count), KeyEvent.VK_0 + (count % 10), Shortcut.CTRL);
                final JosmAction action = new JosmAction(
                        tr("Choose recent tag {0}", count), null, tr("Use this tag again"), sc, false) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        keyEditor.setSelectedItemText(t.getKey());
                        // fix #7951, #8298 - update list of values before setting value (?)
                        // updateValueModel(autocomplete, DEFAULT_AC_ITEM_COMPARATOR);
                        valueEditor.setSelectedItemText(t.getValue());
                        valueEditor.requestFocus();
                    }
                };
                /* POSSIBLE SHORTCUTS: 1,2,3,4,5,6,7,8,9,0=10 */
                final Shortcut scShift = count > 10 ? null : Shortcut.registerShortcut("properties:recent:apply:" + count,
                         tr("Apply recent tag {0}", count), KeyEvent.VK_0 + (count % 10), Shortcut.CTRL_SHIFT);
                final JosmAction actionShift = new JosmAction(
                        tr("Apply recent tag {0}", count), null, tr("Use this tag again"), scShift, false) {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        action.actionPerformed(null);
                        performTagAdding();
                        refreshRecentTags();
                        keyEditor.requestFocus();
                    }
                };
                recentTagsActions.add(action);
                recentTagsActions.add(actionShift);
                if (keyExists && PROPERTY_RECENT_EXISTING.get() == RecentExisting.DISABLE) {
                    action.setEnabled(false);
                }
                ImageIcon icon = findIcon(t.getKey(), t.getValue())
                        // If still nothing display an empty icon

                        .orElseGet(() -> new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)));
                GridBagConstraints gbc = new GridBagConstraints();
                gbc.ipadx = 5;
                recentTagsPanel.add(new JLabel(action.isEnabled() ? icon : GuiHelper.getDisabledIcon(icon)), gbc);
                // Create tag label
                final String color = action.isEnabled() ? "" : "; color:gray";
                final JLabel tagLabel = new JLabel("<html>"
                        + "<style>td{" + color + "}</style>"
                        + "<table><tr>"
                        + "<td>" + count + ".</td>"
                        + "<td style='border:1px solid gray'>" + XmlWriter.encode(t.toString(), true) + '<' +
                        "/td></tr></table></html>");
                tagLabel.setFont(tagLabel.getFont().deriveFont(Font.PLAIN));
                if (action.isEnabled() && sc != null && scShift != null) {
                    // Register action
                    recentTagsPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(sc.getKeyStroke(), "choose"+count);
                    recentTagsPanel.getActionMap().put("choose"+count, action);
                    recentTagsPanel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(scShift.getKeyStroke(), "apply"+count);
                    recentTagsPanel.getActionMap().put("apply"+count, actionShift);
                }
                if (action.isEnabled()) {
                    // Make the tag label clickable and set tooltip to the action description (this displays also the keyboard shortcut)
                    tagLabel.setToolTipText((String) action.getValue(Action.SHORT_DESCRIPTION));
                    tagLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    tagLabel.addMouseListener(new MouseAdapter() {
                        @Override
                        public void mouseClicked(MouseEvent e) {
                            action.actionPerformed(null);
                            if (SwingUtilities.isRightMouseButton(e)) {
                                Component component = e.getComponent();
                                if (component.isShowing()) {
                                    new TagPopupMenu(t).show(component, e.getX(), e.getY());
                                }
                            } else if (e.isShiftDown()) {
                                // add tags on Shift-Click
                                performTagAdding();
                                refreshRecentTags();
                                keyEditor.requestFocus();
                            } else if (e.getClickCount() > 1) {
                                // add tags and close window on double-click
                                buttonAction(0, null); // emulate OK click and close the dialog
                            }
                        }
                    });
                } else {
                    // Disable tag label
                    tagLabel.setEnabled(false);
                    // Explain in the tooltip why
                    tagLabel.setToolTipText(tr("The key ''{0}'' is already used", t.getKey()));
                }
                // Finally add label to the resulting panel
                recentTagsPanel.add(tagLabel, GBC.eol());
            }
            // Clear label if no tags were added
            if (count == 0) {
                recentTagsPanel.removeAll();
            }
        }

        class TagPopupMenu extends JPopupMenu {

            TagPopupMenu(Tag t) {
                add(new IgnoreTagAction(tr("Ignore key ''{0}''", t.getKey()), new Tag(t.getKey(), "")));
                add(new IgnoreTagAction(tr("Ignore tag ''{0}''", t), t));
                add(new EditIgnoreTagsAction());
            }
        }

        class IgnoreTagAction extends AbstractAction {
            final transient Tag tag;

            IgnoreTagAction(String name, Tag tag) {
                super(name);
                this.tag = tag;
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    if (tagsToIgnore != null) {
                        recentTags.ignoreTag(tag, tagsToIgnore);
                        PROPERTY_TAGS_TO_IGNORE.put(tagsToIgnore.writeToString());
                    }
                } catch (SearchParseError parseError) {
                    throw new IllegalStateException(parseError);
                }
            }
        }

        class EditIgnoreTagsAction extends AbstractAction {

            EditIgnoreTagsAction() {
                super(tr("Edit ignore list"));
            }

            @Override
            public void actionPerformed(ActionEvent e) {
                final SearchSetting newTagsToIngore = SearchAction.showSearchDialog(tagsToIgnore);
                if (newTagsToIngore == null) {
                    return;
                }
                try {
                    tagsToIgnore = newTagsToIngore;
                    recentTags.setTagsToIgnore(tagsToIgnore);
                    PROPERTY_TAGS_TO_IGNORE.put(tagsToIgnore.writeToString());
                } catch (SearchParseError parseError) {
                    warnAboutParseError(parseError);
                }
            }
        }

        /**
         * Destroy the recentTagsActions.
         */
        public void destroyActions() {
            for (JosmAction action : recentTagsActions) {
                action.destroy();
            }
            recentTagsActions.clear();
        }

        /**
         * Read tags from comboboxes and add it to all selected objects
         */
        public final void performTagAdding() {
            String key = keyEditor.getText();
            String value = valueEditor.getText();
            if (key.isEmpty() || value.isEmpty())
                return;
            if (tagTableModel.keySet().contains(key)) {
                // trying to add duplicate key
                String val = tagTableModel.get(key).toString();
                if (!val.equals(value)) {
                    String valueHtmlString = Utils.joinAsHtmlUnorderedList(Arrays.asList("<strike>" + val + "</strike>", value));
                    if (!warnOverwriteKey("<html>"
                            + tr("You changed the value of ''{0}'': {1}", key, valueHtmlString)
                            + tr("Overwrite?"), "overwriteAddKey"))
                        return;
                }
            }
            tagTableModel.put(key, value);
            tagTableModel.getHandler().update(key, key, value);
            recentTags.add(new Tag(key, value));
            AutoCompletionManager.rememberUserInput(key, value, false);
            clearEntries();
        }

        protected void clearEntries() {
            keyEditor.getEditor().setItem("");
            valueEditor.getEditor().setItem("");
        }

        private void refreshRecentTags() {
            switch (PROPERTY_REFRESH_RECENT.get()) {
                case REFRESH:
                    cacheRecentTags();
                    rebuildRecentTagsPanel();
                    break;
                case STATUS:
                    rebuildRecentTagsPanel();
                    break;
                default: // Do nothing
            }
        }
    }
}
