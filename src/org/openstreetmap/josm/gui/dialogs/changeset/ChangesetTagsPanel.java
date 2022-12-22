// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs.changeset;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.BorderLayout;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

import org.openstreetmap.josm.data.osm.Changeset;

/**
 * This panel displays the tags of the currently selected changeset in the {@link ChangesetCacheManager}
 *
 */
public class ChangesetTagsPanel extends JPanel implements PropertyChangeListener {

    /**
     * Class that allows fast creation of read-only table model with String columns
     */
    public static class ReadOnlyTableModel extends DefaultTableModel {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }
    }

    private ReadOnlyTableModel model;

    protected void build() {
        setLayout(new BorderLayout());
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        model = new ReadOnlyTableModel();
        model.setColumnIdentifiers(new String[]{tr("Key"), tr("Value")});
        JTable tblTags = new JTable(model);
        tblTags.setAutoCreateRowSorter(true);
        tblTags.getTableHeader().setReorderingAllowed(false);
        add(new JScrollPane(tblTags), BorderLayout.CENTER);
    }

    /**
     * Constructs a new {@code ChangesetTagsPanel}.
     */
    public ChangesetTagsPanel() {
        build();
    }

    /* ---------------------------------------------------------------------------- */
    /* interface PropertyChangeListener                                             */
    /* ---------------------------------------------------------------------------- */
    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (!evt.getPropertyName().equals(ChangesetCacheManagerModel.CHANGESET_IN_DETAIL_VIEW_PROP))
            return;
        model.setRowCount(0);
        Changeset cs = (Changeset) evt.getNewValue();
        if (cs != null) {
            for (Map.Entry<String, String> tag : cs.getKeys().entrySet()) {
                model.addRow(new String[] {tag.getKey(), tag.getValue()});
            }
        }
    }
}
