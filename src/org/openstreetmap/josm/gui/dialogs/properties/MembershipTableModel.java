// License: GPL. For details, see LICENSE file.

package org.openstreetmap.josm.gui.dialogs.properties;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.table.DefaultTableModel;

import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.gui.dialogs.properties.PropertiesDialog.MemberInfo;

/**
 * Table model for the membership table.
 */
public class MembershipTableModel extends DefaultTableModel {
    String[] COLUMN_NAMES = new String[]{"<Relation>", "<Info>", tr("Member Of"), tr("Role"), tr("Position")};

    @Override
    public Object getValueAt(int row, int column) {
        Relation relation = (Relation) super.getValueAt(row, 0);
        MemberInfo info = (MemberInfo) super.getValueAt(row, 1);
        switch(column) {
            case 0:
                return relation;
            case 1:
                return info;
            case 2:
                return relation; // for PrimitiveRenderer
                // return.getDisplayName(DefaultNameFormatter.getInstance());
            case 3:
                return info.getRoleString();
            case 4:
                return info.getPositionString();
        }
        return "";
    }

    @Override
    public boolean isCellEditable(int row, int column) {
        return false;
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int column) {
        switch(column) {
            case 0:
                return Relation.class;
            case 1:
                return MemberInfo.class;
        }
        return String.class;
    }
}
