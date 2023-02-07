// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.swing.Action;
import javax.swing.JLayeredPane;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openstreetmap.josm.actions.CreateMultipolygonAction;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Tagged;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.search.SearchCompiler;
import org.openstreetmap.josm.data.osm.search.SearchCompiler.Match;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.Notification;
import org.openstreetmap.josm.gui.dialogs.relation.RelationEditor;
import org.openstreetmap.josm.gui.dialogs.relation.sort.RelationSorter;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.gui.tagging.DataHandlers.CloneDataSetHandler;
import org.openstreetmap.josm.gui.tagging.DataHandlers.DataSetHandler;
import org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler;
import org.openstreetmap.josm.gui.tagging.ac.AutoCompletionManager;
import org.openstreetmap.josm.gui.widgets.JosmMenuItem;
import org.openstreetmap.josm.tools.ListenerList;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.template_engine.TemplateEngineDataProvider;
import org.openstreetmap.josm.tools.template_engine.TemplateEntry;

/**
 * A template class to build preset dialogs.
 * <p>
 * This class is an immutable template class mainly used to build Swing dialogs. It also
 * creates menu and toolbar entries.
 * <p>
 * This class is immutable and uses the companion class {@link TaggingPresetDialog} to store
 * instance data.
 *
 * @since 294
 */
public class TaggingPreset extends TaggingPresetBase implements Predicate<IPrimitive> {
    /** Show the preset name and icon in the dialog if true */
    boolean showPresetName;
    /** The OSM primitive types this preset can be applied to. */
    private final Set<TaggingPresetType> types;
    /**
     * The name_template custom name formatter. See:
     * <a href="https://josm.openstreetmap.de/wiki/TaggingPresets#Attributes">JOSM wiki</a>
     */
    private final TemplateEntry nameTemplate;
    /** The name_template_filter */
    private final Match nameTemplateFilter;
    /** The match_expression */
    private final Match matchExpression;
    /** Minimum width in em */
    final int minWidth;
    /** A cache of all items relevant to the matching algorithm. */
    private final List<KeyedItem> matchItemsCache = new ArrayList<>();
    /**
     * A store to persist information from one invocation of this preset's dialog to the next.
     * <p>
     * Example: The autoincrement value of the street address preset.
     */
    static Map<String, Object> properties = new HashMap<>();

    /**
     * Create an empty tagging preset. This will not have any items and
     * will be an empty string as text. createPanel will return null.
     * Use this as default item for "do not select anything".
     *
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on invalid attributes
     */
    TaggingPreset(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);

        showPresetName = TaggingPresetUtils.parseBoolean(attributes.getOrDefault("preset_name_label", "false"));
        types = TaggingPresetType.getOrDefault(attributes.get("type"), EnumSet.allOf(TaggingPresetType.class));
        nameTemplate = TaggingPresetUtils.parseTemplate(attributes.get("name_template"));
        nameTemplateFilter = TaggingPresetUtils.parseSearchExpression(attributes.get("name_template_filter"));
        matchExpression = TaggingPresetUtils.parseSearchExpression(attributes.get("match_expression"));
        minWidth = Integer.parseInt(attributes.getOrDefault("min_width", "0"));
    }

    /**
     * Create a {@code TaggingPreset} from an XML element's attributes.
     * @param attributes the XML attributes
     * @return the {@code TaggingPreset}
     * @throws IllegalArgumentException on invalid attributes
     */
    static TaggingPreset fromXML(Map<String, String> attributes) throws IllegalArgumentException {
        return new TaggingPreset(attributes);
    }

    @Override
    void fixup(Map<String, Chunk> chunks, Item parent) {
        super.fixup(chunks, parent);
        action = new TaggingPresetAction();
        iconFuture = TaggingPresetUtils.loadIconAsync(getIconName(), action);
        for (KeyedItem ki : getAllItems(KeyedItem.class)) {
            if (!"none".equals(ki.getMatchType()))
                matchItemsCache.add(ki);
        }
    }

    /**
     * The data handler we pass to any preset dialog opened from here.
     * <p>
     * This handler clones the selected primitives into a new dataset and then changes
     * their tags according to the current values in the dialog.
     * <p>
     * Writes go through to the next handler and at the same time update the dialog
     * elements.
     */
    static class PresetLinkHandler extends CloneDataSetHandler {
        final TaggingPreset.Instance presetInstance;

        PresetLinkHandler(DataSetHandler parentHandler, TaggingPreset.Instance presetInstance) {
            super(parentHandler); // clones
            this.presetInstance = presetInstance;

            // change tags
            getDataSet().beginUpdate();
            Map<String, String> changedTags = presetInstance.getChangedTags();
            // why not putAll? see #22580
            get().forEach(p -> changedTags.forEach((k, v) -> {
                if (Utils.isEmpty(v)) {
                    p.remove(k);
                } else {
                    p.put(k, v);
                }
            }));
            getDataSet().endUpdate();
        }

        @Override
        public void update(String oldKey, String newKey, String value) {
            Logging.info("Update through PresetLinkHandler");
            Item.Instance instance = presetInstance.getInstance(newKey);
            if (instance != null)
                instance.setValue(value);
            // FIXME: if we open a child dialog and the user changes a value in the
            // child and saves, and then the user resets the same value in the parent
            // dialog, the dialog won't save the now "original" value
            super.update(oldKey, newKey, value);
        }
    }

    /**
     * A debounced ChangeListener.
     * <p>
     * Once triggered, this listener fires after the trigger has been inactive for a
     * given amount of time.  Can be used to reduce the frequency of costly operations,
     * eg. for running the validator only after the user paused typing.
     */
    static class DebouncedChangeListener implements ChangeListener, ActionListener {
        ChangeListener listener;
        final Timer timer;
        Object source;

        DebouncedChangeListener(ChangeListener listener, int delay) {
            this.listener = listener;
            timer = new Timer(delay, this);
            timer.setRepeats(false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            listener.stateChanged(new ChangeEvent(source));
        }

        @Override
        public void stateChanged(ChangeEvent e) {
            if (source != null && source != e.getSource()) {
                listener.stateChanged(new ChangeEvent(source));
            }
            this.source = e.getSource();
            timer.restart();
        }
    }

    /**
     * This instance represents a preset dialog.
     * <p>
     * All data is interfaced through the {@link TaggedHandler handler}.  By writing
     * custom handlers you can interface the dialog to virtually any key/value store.
     * The {@link DataSetHandler} can be used to interface to JOSM
     * {@link DataSet datasets}.
     */
    public class Instance extends Composite.Instance implements TemplateEngineDataProvider {
        /** The preset template that created this instance. */
        private final TaggingPreset preset;
        /** The current TaggedHandler */
        private final TaggedHandler handler;
        /** The AutoCompletionManager to use or null */
        private final AutoCompletionManager autoCompletionManager;
        /** The map from items to their Instance. */
        private final Map<Item, Item.Instance> instances = new HashMap<>();
        /** Data change listeners */
        private final ListenerList<ChangeListener> listeners = ListenerList.create();
        /**
         * True if all selected primitives matched this preset at the moment the dialog was openend.
         * <p>
         * This usually means that the preset dialog was opened from the Tags / Memberships panel as
         * opposed to being opened from the menu or toolbar or the preset search dialog.
         * <p>
         * If this flag is set, and {@code use_last_value_as_default} is also set on the field in
         * {@code defaultpresets.xml}, the last value the user entered in the field will be chosen as
         * default.
         */
        private final boolean presetInitiallyMatches;
        /** whether to fire data changed events or not */
        private boolean fireDataChange;
        /** The swing dialog */
        TaggingPresetDialog dialog;

        Instance(TaggingPreset item, TaggedHandler handler, AutoCompletionManager autoCompletionManager) {
            super(item, null);
            this.preset = item;
            this.handler = handler;
            this.autoCompletionManager = autoCompletionManager;
            this.presetInitiallyMatches = handler != null
                && !handler.get().isEmpty()
                && handler instanceof DataSetHandler
                && ((DataSetHandler) handler).get().stream().allMatch(preset);
        }

        @Override
        void addChangedTag(Map<String, String> changedTags) {
            // the dialog itself adds no commands
        }

        @Override
        public void addCurrentTag(Map<String, String> currentTags) {
            // the dialog itself holds no values
        }

        /**
         * Returns the preset
         * @return the preset
         */
        public TaggingPreset getPreset() {
            return preset;
        }

        @Override
        public Item getItem() {
            return getPreset();
        }

        @Override
        public TaggingPreset.Instance getPresetInstance() {
            return this;
        }

        public TaggingPresetDialog getDialog() {
            return dialog;
        }

        /**
         * Registers the instance of the preset item.
         * <p>
         * All presets have to register an instance if the item is editable.
         *
         * @param item the preset item
         * @param instance the instance
         */
        @Override
        public void putInstance(Item item, Item.Instance instance) {
            instances.put(item, instance);
        }

        /**
         * Returns the handler
         * @return the handler
         */
        public TaggedHandler getHandler() {
            return handler;
        }

        /**
         * Returns the autocompletion manager
         * @return the autocompletion mannager
         */
        public AutoCompletionManager getAutoCompletionManager() {
            return autoCompletionManager;
        }

        /**
         * Return the selected primitives.
         * @return the selected primitives
         */
        public Collection<? extends Tagged> getSelected() {
            return handler != null ? handler.get() : Collections.emptyList();
        }

        /**
         * Return the instance for the item
         * @param item the item
         * @return the instance
         */
        Item.Instance getInstance(Item item) {
            return instances.get(item);
        }

        /**
         * Returns the item instance with the key
         * @param key the key of the item
         * @return the item instance or null
         */
        Item.Instance getInstance(String key) {
            if (key == null)
                return null;
            for (Item.Instance instance : instances.values()) {
                if (key.equals(instance.getKey()))
                    return instance;
            }
            return null;
        }

        /**
         * Returns true if all selected primitives matched this preset (before opening the dialog).
         * @return true if the preset initially matched
         */
        public boolean isPresetInitiallyMatches() {
            return presetInitiallyMatches;
        }

        /**
         * Recalculates all calculated fields.
         */
        public void recalculateAll() {
            instances.forEach((item, instance) -> instance.recalculate());
        }

        /**
         * Gets all tags as currently edited.
         * @return The map of tags.
         */
        public Map<String, String> getCurrentTags() {
            Map<String, String> result = new HashMap<>();
            instances.forEach((item, instance) -> instance.addCurrentTag(result));
            return result;
        }

        /**
         * Gets the edited tags only.
         * @return The map of tags.
         */
        public Map<String, String> getChangedTags() {
            Map<String, String> result = new HashMap<>();
            instances.forEach((item, instance) -> instance.addChangedTag(result));
            return result;
        }

        /**
         * Fills in the tags.
         * @param tags a map of tags
         */
        public void fillIn(Map<String, String> tags) {
            tags.forEach((key, value) -> {
                Item.Instance itemInstance = getInstance(key);
                if (itemInstance != null)
                    itemInstance.setValue(value);
            });
            fireChangeEvent(this);
        }

        @Override
        public void highlight(Color color, String tooltip) {
            // override this
        }

        /**
         * Highlights the ui item used for editing the OSM key.
         * <p>
         * Draws a colored border around the item. Use case: to signal input errors.
         *
         * @param key the key of the item to highlight
         * @param color the color to use or null to reset
         * @param message the message to display
         */
        public void highlight(String key, Color color, String message) {
            if (color == null) {
                JLayeredPane pane = getDialog().getLayeredPane();
                for (Component c : pane.getComponentsInLayer(JLayeredPane.PALETTE_LAYER)) {
                    pane.remove(c);
                }
                pane.repaint();
            }
            for (Item.Instance instance : instances.values()) {
                Item item = instance.getItem();
                if (item instanceof InteractiveItem &&
                        (key == null || ((InteractiveItem) item).getKey().equals(key))) {
                    instance.highlight(color, message);
                }
            }
        }

        /**
         * Returns the preset property value
         *
         * @param key the property key
         * @param defaultValue the default value
         * @return the preset property value
         */
        Object getPresetProperty(String key, Object defaultValue) {
            return properties.getOrDefault(preset.getRawName() + "." + key, defaultValue);
        }

        /**
         * Returns the preset property value
         *
         * @param key the property key
         * @param value the new value
         * @return the preset property value
         */
        Object putPresetProperty(String key, Object value) {
            return properties.put(preset.getRawName() + "." + key, value);
        }

        /**
         * Returns whether firing of events is enabled
         *
         * @return true if firing of events is enabled
         */
        boolean isFireDataChange() {
            return fireDataChange;
        }

        /**
         * Enables or disables the firing of events
         *
         * @param enabled fires if true
         * @return the old state of enabled
         */
        boolean setFireDataChanged(boolean enabled) {
            boolean oldEnabled = this.fireDataChange;
            this.fireDataChange = enabled;
            return oldEnabled;
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
        public void fireChangeEvent(Item.Instance source) {
            if (fireDataChange)
                listeners.fireEvent(e -> e.stateChanged(new ChangeEvent(source)));
        }

        /*
         * Interface TemplateEngineDataProvider
         */
        @Override
        public Collection<String> getTemplateKeys() {
            return getCurrentTags().keySet();
        }

        @Override
        public Object getTemplateValue(String key, boolean special) {
            String value = getCurrentTags().get(key);
            return Utils.isEmpty(value) || InteractiveItem.DIFFERENT_I18N.equals(value) ? null : value;
        }

        @Override
        public boolean evaluateCondition(SearchCompiler.Match condition) {
            return condition.match(Tagged.ofMap(getCurrentTags()));
        }
    }

    JPanel buildPresetPanel(Instance instance) {
        JPanel panel = new JPanel(new GridBagLayout());
        addToPanel(panel, instance);
        return panel;
    }

    @Override
    void addToMenu(JMenu parentMenu) {
        if (findMenu(parentMenu) == null) {
            JMenuItem menuItem = new JosmMenuItem(getAction(), getIconSize());
            menuItem.setText(getLocaleName());
            parentMenu.add(menuItem);
            // don't recurse into children
        }
    }

    /**
     * Returns the primitive types this preset applies to
     * @return the set of types
     */
    public Set<TaggingPresetType> getTypes() {
        return Collections.unmodifiableSet(types);
    }

    /**
     * Returns the name template
     * @return the name template
     */
    public TemplateEntry getNameTemplate() {
        return nameTemplate;
    }

    /**
     * Returns the name template filter
     * @return the name template filter
     */
    public Match getNameTemplateFilter() {
        return nameTemplateFilter;
    }

    /**
     * Shows the preset dialog and applies edits.
     * <p>
     * This function constructs and shows a dialog and applies any changes to the
     * handler {@code handler}.
     *
     * @param handler the tagging preset handler
     * @param autoCompletionManager the autocompletion manager (or null)
     * @return one of {@code TaggingPresetDialog.DIALOG_ANSWER_*}
     */
    public int showDialog(TaggedHandler handler, AutoCompletionManager autoCompletionManager) {
        TaggingPresetDialog dialog = prepareDialog(handler, autoCompletionManager);
        if (dialog != null)
            dialog.setVisible(true);
        return processAnswer(dialog);
    }

    /**
     * Prepares the dialog for showing.
     * @param handler a handler or null
     * @param autoCompletionManager an ac manager or null
     * @return the dialog ready for showing or null if it cannot be shown
     */
    public TaggingPresetDialog prepareDialog(TaggedHandler handler, AutoCompletionManager autoCompletionManager) {
        if (!isShowable())
            return null;

        Instance instance = new Instance(this, handler, autoCompletionManager);
        Collection<? extends Tagged> selected = instance.getSelected();

        // sanity checks
        boolean showNewRelation = types.contains(TaggingPresetType.RELATION);
        if (handler != null && selected.isEmpty() && !showNewRelation) {
            new Notification(
                tr("The preset <i>{0}</i> cannot be applied since nothing has been selected!", getLocaleName()))
                .setIcon(JOptionPane.WARNING_MESSAGE)
                .show();
            return null;
        }
        if (handler instanceof DataSetHandler) {
            Collection<OsmPrimitive> filtered = ((DataSetHandler) handler).get();
            filtered = filtered.stream()
                .filter(TaggingPreset.this::typeMatches).collect(Collectors.toList());
            if (filtered.isEmpty() && !showNewRelation) {
                new Notification(
                    tr("The preset <i>{0}</i> cannot be applied since the selection is unsuitable!", getLocaleName()))
                    .setIcon(JOptionPane.WARNING_MESSAGE)
                    .show();
                return null;
            }
        }

        return new TaggingPresetDialog(instance);
    }

    /**
     * Process the user input
     *
     * @param dialog the dialog
     * @return the user's answer
     */
    public int processAnswer(TaggingPresetDialog dialog) {
        if (dialog == null)
            return TaggingPresetDialog.DIALOG_ANSWER_CANCEL;
        int answer = dialog.answer;
        if (answer == TaggingPresetDialog.DIALOG_ANSWER_CANCEL)
            return answer;

        Instance instance = dialog.presetInstance;
        TaggedHandler handler = instance.getHandler();
        Collection<? extends Tagged> selected = instance.getSelected();

        if (answer == TaggingPresetDialog.DIALOG_ANSWER_APPLY && !instance.getSelected().isEmpty()) {
            // commit the changed tags
            handler.begin();
            instance.getChangedTags().forEach((k, v) -> handler.update(k, k, v));
            handler.commit(tr("Apply preset {0}", getLocaleName()));
        } else if (answer == TaggingPresetDialog.DIALOG_ANSWER_NEW_RELATION) {
            // create the new relation
            Relation calculated = null;
            Map<String, String> currentTags = instance.getCurrentTags();
            String type = currentTags.get("type");
            if (("boundary".equals(type) || "multipolygon".equals(type)) &&
                    handler instanceof DataSetHandler) {
                Collection<OsmPrimitive> primitives = ((DataSetHandler) handler).get();
                Collection<Way> ways = Utils.filteredCollection(primitives, Way.class);
                Pair<Relation, Relation> res = CreateMultipolygonAction.createMultipolygonRelation(ways, true);
                if (res != null) {
                    calculated = res.b;
                }
            }
            final Relation r = calculated != null ? calculated : new Relation();
            final Collection<RelationMember> selectedMembers = new LinkedHashSet<>(r.getMembers());
            currentTags.forEach((k, v) -> {
                 if (!Utils.isEmpty(v)) r.put(k, v);
            });
            for (Tagged t : selected) {
                if (t instanceof OsmPrimitive) {
                    OsmPrimitive primitive = (OsmPrimitive) t;
                    if (r == calculated && t instanceof Way)
                        continue;
                    RelationMember rm = new RelationMember(suggestRoleForOsmPrimitive(primitive), primitive);
                    r.addMember(rm);
                    selectedMembers.add(rm);
                }
            }
            if (r.isMultipolygon() && r != calculated) {
                r.setMembers(RelationSorter.sortMembersByConnectivity(r.getMembers()));
            }
            // open a relation editor for the new relation
            SwingUtilities.invokeLater(() -> RelationEditor.getEditor(
                    MainApplication.getLayerManager().getEditLayer(), r, selectedMembers).setVisible(true));
        }
        if (!selected.isEmpty() && handler instanceof DataSetHandler) {
            DataSet ds = ((DataSetHandler) handler).getDataSet();
            ds.setSelected(((DataSetHandler) handler).get()); // force update
        }
        return answer;
    }

    /**
     * Determines whether a meaningful dialog can be shown for this preset, i.e. at
     * least one tag is editable.
     *
     * @return {@code true} if a dialog can be shown for this preset
     */
    public boolean isShowable() {
        return !getAllItems(Item::isInteractive, true).isEmpty();
    }

    /**
     * Suggests a relation role for this primitive
     * <p>
     * If this preset supports relations, this function suggests a role for the given
     * primitive when added as a member of the relation. If no suitable role can be
     * found the default role {@code ""} is returned.
     *
     * @param osm The primitive
     * @return the suggested role or ""
     */
    public String suggestRoleForOsmPrimitive(IPrimitive osm) {
        if (osm == null)
            return "";
        return getAllRoles().stream()
                .filter(role -> role.getMemberExpression() != null && role.getMemberExpression().match(osm))
                .filter(role -> role.appliesTo(TaggingPresetType.forPrimitive(osm)))
                .findFirst()
                .map(Role::getKey)
                .orElse("");
    }

    @Override
    public String toString() {
        return "TaggingPreset " + types.toString() + " " + getRawName();
    }

    /**
     * Determines whether this preset matches the OSM primitive type.
     * @param primitive The OSM primitive for which type must match
     * @return <code>true</code> if type matches.
     * @since 15640
     */
    public final boolean typeMatches(IPrimitive primitive) {
        return typeMatches(EnumSet.of(TaggingPresetType.forPrimitive(primitive)));
    }

    /**
     * Returns {@code true} if this preset matches all the given types.
     *
     * @param types the types (or null to match any type)
     * @return <code>true</code> if the preset matches all types
     */
    public boolean typeMatches(Collection<TaggingPresetType> types) {
        return types == null || this.types.containsAll(types);
    }

    /**
     * Determines whether this preset matches the given primitive, i.e., whether the
     * {@link #typeMatches(Collection) type matches} and the
     * {@link KeyedItem#matches(Map) tags match}.
     *
     * @param p the primitive
     * @return {@code true} if this preset matches the primitive
     * @since 13623 (signature)
     */
    @Override
    public boolean test(IPrimitive p) {
        return matches(EnumSet.of(TaggingPresetType.forPrimitive(p)), p.getKeys(), false);
    }

    /**
     * Returns {@code true} if this preset matches the parameters.
     * <p>
     * To match the preset must match all {@code types}, plus there must be at least one
     * positive match and no negative matches from the {@code tags}.
     *
     * @param types the preset types (or null to match any preset type)
     * @param tags the tags to perform matching on
     * @param onlyShowable whether the preset must be {@link #isShowable() showable}
     * @return {@code true} if this preset matches the parameters.
     *
     * @see #typeMatches(Collection)
     * @see KeyedItem#matches(Map)
     * @see #isShowable()
     */
    public boolean matches(Collection<TaggingPresetType> types, Map<String, String> tags, boolean onlyShowable) {
        if ((onlyShowable && !isShowable()) || !typeMatches(types)) {
            return false;
        } else if (matchExpression != null && !matchExpression.match(Tagged.ofMap(tags))) {
            return false;
        } else {
            int positiveMatches = 0;
            for (KeyedItem item : matchItemsCache) {
                Boolean m = item.matches(tags);
                if (Boolean.FALSE == m)
                    return false;
                if (Boolean.TRUE == m) // NOSONAR wrong, it can also be null
                     positiveMatches++;
            }
            return positiveMatches > 0;
        }
    }

    /**
     * Determines which keys match the parameter.
     * <p>
     * Use this to get more info after having a obtained a match. Used by the TagChecker.
     *
     * @param tags the tags to perform matching on, see {@link KeyedItem#matches(Map)}
     * @param onlyRequiredKeys returns only required keys if set
     * @return the key or keys that matched
     */
    public Set<String> matchingKeys(Map<String, String> tags, boolean onlyRequiredKeys) {
        Set<String> keys = new HashSet<>();
        for (KeyedItem item : matchItemsCache) {
            Boolean m = item.matches(tags);
            if (Boolean.TRUE == m && (!onlyRequiredKeys || item.isKeyRequired()))
                keys.add(item.getKey());
        }
        return keys;
    }

    /**
     * An action that opens the preset dialog.
     */
    public class TaggingPresetAction extends TaggingPresetBase.TaggingPresetBaseAction {
        TaggingPresetAction() {
            super();
            putValue(Action.NAME, getName());
            putValue(ToolbarPreferences.TOOLBAR_KEY, "tagging_" + getRawName());
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            DataSet dataSet = OsmDataManager.getInstance().getEditDataSet();
            if (dataSet != null) {
                // invokeLater fixes the following problem: if the preset dialog was
                // opened by pressing a hotkey, the hotkey sometimes bleeds through and
                // appears in the focused editor control
                SwingUtilities.invokeLater(() -> showDialog(
                    new DataSetHandler(dataSet),
                    AutoCompletionManager.of(dataSet)
                ));
            }
        }
    }
}
