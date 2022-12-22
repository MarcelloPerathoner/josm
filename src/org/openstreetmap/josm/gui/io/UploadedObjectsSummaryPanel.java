// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.io;

import static org.openstreetmap.josm.tools.I18n.trn;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.openstreetmap.josm.actions.AutoScaleAction;
import org.openstreetmap.josm.data.APIDataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.PrimitiveRenderer;

/**
 * This panel displays a summary of the objects to upload. It is displayed in the upper part of the {@link UploadDialog}.
 * @since 2599
 */
public class UploadedObjectsSummaryPanel extends JPanel {
    /**
     * Zooms to the primitive on double-click
     */
    private static MouseAdapter mouseListener = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent evt) {
            if (evt.getButton() == MouseEvent.BUTTON1 && evt.getClickCount() == 2) {
                @SuppressWarnings("unchecked")
                JList<OsmPrimitive> list = (JList<OsmPrimitive>) evt.getSource();
                int index = list.locationToIndex(evt.getPoint());
                AutoScaleAction.zoomTo(Collections.singleton(list.getModel().getElementAt(index)));
            }
        }
    };

    /**
     * A JLabel and a JList
     */
    private static class ListPanel extends JPanel {
        /**
         * Constructor
         *
         * @param primitives the list of primitives
         * @param singular the singular form of the label
         * @param plural the plural form of the label
         */
        ListPanel(List<OsmPrimitive> primitives, String singular, String plural) {
            DefaultListModel<OsmPrimitive> model = new DefaultListModel<>();
            JList<OsmPrimitive> jList = new JList<>(model);
            primitives.forEach(model::addElement);
            jList.setCellRenderer(new PrimitiveRenderer());
            jList.addMouseListener(mouseListener);
            JScrollPane scrollPane = new JScrollPane(jList);
            JLabel label = new JLabel(trn(singular, plural, model.size(), model.size()));
            label.setLabelFor(jList);
            this.setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));
            this.add(label);
            this.add(scrollPane);
        }
    }

    /**
     * Constructs a new {@code UploadedObjectsSummaryPanel}.
     */
    public UploadedObjectsSummaryPanel() {
        super(new GridBagLayout());
    }

    /**
     * Builds the panel
     *
     * @param toUpload the primitives to upload
     */
    public void build(APIDataSet toUpload) {
        GridBagConstraints gcList = new GridBagConstraints();
        gcList.fill = GridBagConstraints.BOTH;
        gcList.weightx = 1.0;
        gcList.weighty = 1.0;
        gcList.anchor = GridBagConstraints.CENTER;

        removeAll();
        List<OsmPrimitive> list = toUpload.getPrimitivesToAdd();
        if (!list.isEmpty()) {
            gcList.gridy++;
            add(new ListPanel(list, "{0} object to add:", "{0} objects to add:"), gcList);
        }
        list = toUpload.getPrimitivesToUpdate();
        if (!list.isEmpty()) {
            gcList.gridy++;
            add(new ListPanel(list, "{0} object to modify:", "{0} objects to modify:"), gcList);
        }
        list = toUpload.getPrimitivesToDelete();
        if (!list.isEmpty()) {
            gcList.gridy++;
            add(new ListPanel(list, "{0} object to delete:", "{0} objects to delete:"), gcList);
        }
        revalidate();
        repaint();
    }
}
