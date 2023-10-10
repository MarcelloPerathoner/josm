// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging;

import static org.openstreetmap.josm.tools.I18n.trn;
import static org.openstreetmap.josm.gui.tagging.presets.InteractiveItem.DIFFERENT_I18N;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.command.SequenceCommand;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Tagged;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.tagging.TagTableModel.ValueType;
import org.openstreetmap.josm.data.UndoRedoHandler;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Utils;

/**
 * Collection of data handlers.
 */
public class DataHandlers {

    /**
     * This bidirectional interface connects a tag editor like {@link TagTable} or
     * {@link org.openstreetmap.josm.gui.tagging.presets.TaggingPresetDialog TaggingPresetDialog} to
     * a backing store. The backing store can be a JOSM {@code DataSet} or any other key/value
     * store.
     */
    public interface TaggedHandler {
        /**
         * Returns the collection of {@link Tagged} the editor should edit.
         * <p>
         * This is called by the tag editor to obtain the initial values and by the {@link #update}
         * method to store the edited values.  The returned collection should not change between
         * invocations.
         * <p>
         * The return type is unfortunate but makes it easier to implement handlers that return
         * collections of IPrimitive or OsmPrimitive instead.
         *
         * @return A collection of {@link Tagged}.
         */
        Collection<? extends Tagged> get();

        /**
         * Update the elements in the collection.
         * <p>
         * This method should write the tag to every one of the elements returned by {@code get()}.
         *
         * @param oldKey the key to update
         * @param newKey the new key if this op is a key edit, else the same as oldKey
         * @param value the value to set (null or empty removes the key)
         */
        void update(String oldKey, String newKey, String value);

        /**
         * Returns whether the underlying data is editable.
         * <p>
         * Use case: disabling of the OK button in dialogs.
         *
         * @return Returns true if the data is not editable.
         */
        default boolean isReadOnly() {
            return false;
        }

        /**
         * Begins a transaction.
         * <p>
         * During a transaction all updates are queued until either {@code commit()} or
         * {@code abort()} are called.  For our purposes a transaction is any series of edits that
         * should show up in the undo menu as single entry.  Transaction support in data handlers is
         * optional.
         */
        default void begin() {}

        /**
         * Commits the current transaction executing all queued updates.
         *
         * @param title The title of the transaction in the undo menu.
         */
        default void commit(String title) {}

        /**
         * Aborts the current transaction discarding all queued updates.
         */
        default void abort() {}
    }

    /**
     * A TaggedHandler that operates on a live DataSet.
     */
    public static class DataSetHandler implements TaggedHandler {
        protected List<Command> commands = new ArrayList<>();
        protected boolean inTransaction = false;
        /** The DataSet. Never access this directly, always through getDataSet(). */
        private DataSet dataSet;

        /**
         * Constructor
         * @param dataSet the dataset
         */
        public DataSetHandler(DataSet dataSet) {
            setDataSet(dataSet);
        }

        /**
         * Constructs a handler that operates on a live {@link DataSet}
         * @param dataSet the dataset to edit
         * @return this
         */
        public DataSetHandler setDataSet(DataSet dataSet) {
            this.dataSet = dataSet;
            return this;
        }

        /**
         * Returns the DataSet
         * @return the DataSet
         */
        public DataSet getDataSet() {
            return dataSet;
        }

        @Override
        public Collection<OsmPrimitive> get() {
            return getDataSet().getSelected();
        }

        @Override
        public boolean isReadOnly() {
            return getDataSet().isLocked();
        }

        @Override
        public void update(String oldKey, String key, String value) {
            int count = get().size();
            Logging.info("Update: ({0}) {1} => {2} on {3} objects", oldKey, key, value, count);
            String undoText = trn("Change properties of up to {0} object", "Change properties of up to {0} objects", count, count);

            final String newKey = (key == null) ? "" : Normalizer.normalize(key, Normalizer.Form.NFC);
            value = (value == null) ? "" : Normalizer.normalize(value, Normalizer.Form.NFC);

            List<Command> cmds = new ArrayList<>();
            if (newKey.isEmpty() || value.isEmpty() || ValueType.UNSET.equals(value)) {
                // delete the key
                Map<String, String> map = new HashMap<>();
                map.put(oldKey, null);
                cmds.add(new ChangePropertyCommand(getDataSet(), get(), map));
            } else if (!newKey.equals(oldKey)) {
                // update the key
                if (DIFFERENT_I18N.equals(value)) {
                    get().stream()
                        .filter(primitive -> primitive.hasKey(oldKey))
                        // group by current value
                        .collect(Collectors.groupingBy(primitive -> primitive.get(oldKey)))
                        .forEach((oldValue, primitives) -> {
                            Map<String, String> map = new HashMap<>();
                            map.put(oldKey, null);
                            map.put(newKey, oldValue);
                            cmds.add(new ChangePropertyCommand(getDataSet(), primitives, map));
                        });
                } else {
                    Map<String, String> map = new HashMap<>();
                    map.put(oldKey, null);
                    map.put(newKey, value);
                    cmds.add(new ChangePropertyCommand(getDataSet(), get(), map));
                }
            } else {
                // update the value
                if (DIFFERENT_I18N.equals(value))
                    return;
                Map<String, String> map = new HashMap<>();
                map.put(oldKey, value);
                cmds.add(new ChangePropertyCommand(getDataSet(), get(), map));
            }
            if (inTransaction) {
                commands.addAll(cmds);
            } else {
                UndoRedoHandler.getInstance().add(SequenceCommand.wrapIfNeeded(undoText, cmds)); // execute immediately
            }
        }

        @Override
        public void begin() {
            Logging.info("Begin");
            commands.clear();
            inTransaction = true;
        }

        @Override
        public void abort() {
            Logging.info("Abort");
            commands.clear();
            inTransaction = false;
        }

        /**
         * A commit that locks the dataset for atomic updates.
         */
        @Override
        public void commit(String title) {
            Logging.info("Commit: {0} on {1} objects", title, get().size());
            if (inTransaction && commands.size() > 0) {
                if (commands.size() == 1 && Utils.isEmpty(title)) {
                    UndoRedoHandler.getInstance().add(commands.get(0));
                } else {
                    // wrap if more than one command or title
                    getDataSet().beginUpdate();
                    try {
                        UndoRedoHandler.getInstance().add(
                            new SequenceCommand(getDataSet(), title, commands, false));
                    } finally {
                        getDataSet().endUpdate();
                    }
                }
            }
            commands.clear();
            inTransaction = false;
        }
    }

    /**
     * A Read-Only handler that operates on a collection of primitives.
     * <p>
     * Mainly for testing purposes.
     */
    public static class ReadOnlyHandler extends DataSetHandler {
        Collection<OsmPrimitive> selection;

        /**
         * Constructor
         * @param selection the selection in the DataSet
         */
        public ReadOnlyHandler(Collection<OsmPrimitive> selection) {
            super(new DataSet());
            this.selection = selection;
            selection.forEach(p -> getDataSet().addPrimitive(p));
            getDataSet().addSelected(selection);
        }

        @Override
        public void update(String oldKey, String key, String value) {
            // do nothing
        }

        @Override
        public boolean isReadOnly() {
            return true;
        }
    }

    /**
     * A handler that creates a new DataSet and clones the given selection into it.
     * <p>
     * Use this to apply temporary edits, eg. for the validator.
     * <p>
     * Caveat emptor: Update semantics are tricky: If you clone an existing DataSet and
     * apply some changes and then pass it to another preset dialog, that dialog will
     * assume your changes are original values. It will not include those changes in its
     * own changed tags when the user hits "Apply". This may be what you want, but then
     * it may be not, because some of the values visible in the dialog do not get
     * applied.
     */
    public static class CloneDataSetHandler extends DataSetHandler {
        protected final DataSet clonedDataSet = new DataSet();
        protected final DataSetHandler parentHandler;
        final Map<OsmPrimitive, OsmPrimitive> clonedMap = new HashMap<>();

        /**
         * Constructor
         * <p>
         * Clones all selected primitives of the parent handler into a new dataset.
         *
         * @param the parent DataSetHandler
         */
        public CloneDataSetHandler(DataSetHandler parentHandler) {
            super(null);
            this.parentHandler = parentHandler;

            Collection<OsmPrimitive> parentSelection = parentHandler.get();
            parentSelection.forEach(p -> clonePrimitive(p));
            parentSelection.forEach(p -> clonedDataSet.addSelected(clonedMap.get(p)));

            clonedDataSet.setName("Cloned by CloneDataSetHandler");
            Logging.info("Cloned by CloneDataSetHandler {0} objects {1} selected",
                clonedDataSet.getPrimitives(p -> true).size(), clonedDataSet.getSelected().size());
        }

        /**
         * Add a primitive to the cloned dataset if not already there.
         */
        private void add(OsmPrimitive p, OsmPrimitive newP) {
            if (!clonedMap.containsKey(p)) {
                clonedDataSet.addPrimitive(newP);
                clonedMap.put(p, newP);
            }
        }

        /**
         * Clone a primitive with all dependent primitives into the cloned dataset.
         * @param p the primitive to clone
         */
        private void clonePrimitive(OsmPrimitive p) {
            if (p instanceof Node) {
                Node newNode = new Node((Node) p);
                add(p, newNode);
                return;
            }
            if (p instanceof Way) {
                Way w = (Way) p;
                w.getNodes().forEach(n -> clonePrimitive(n));

                Way newWay = new Way(w, false, false);
                newWay.setNodes(w.getNodes().stream()
                        .map(n -> (Node) clonedMap.get(n))
                        .collect(Collectors.toList()));
                add(w, newWay);
                return;
            }
            if (p instanceof Relation) {
                Relation r = (Relation) p;
                r.getMembers().forEach(m -> clonePrimitive(m.getMember()));

                Relation newRelation = new Relation(r, false, false);
                newRelation.setMembers(r.getMembers().stream()
                        .map(m -> new RelationMember(m.getRole(), clonedMap.get(m.getMember())))
                        .collect(Collectors.toList()));
                add(r, newRelation);
                return;
            }
            assert false : "Can't happen";
        }

        @Override
        public void update(String oldKey, String newKey, String value) {
            // update the parent handler, not the cloned DataSet
            parentHandler.update(oldKey, newKey, value);
        }

        @Override
        public void begin() {
            parentHandler.begin();
        }

        @Override
        public void commit(String title) {
            parentHandler.commit(title);
        }

        @Override
        public void abort() {
            parentHandler.abort();
        }

        @Override
        public boolean isReadOnly() {
            return parentHandler.isReadOnly();
        }

        @Override
        public DataSet getDataSet() {
            return clonedDataSet;
        }

        @Override
        public Collection<OsmPrimitive> get() {
            return getDataSet().getSelected();
        }
    }
}
