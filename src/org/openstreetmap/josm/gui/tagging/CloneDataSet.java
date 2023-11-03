// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Tagged;
import org.openstreetmap.josm.data.osm.Way;

/**
 * A helper class to clone a selection into a dataset.
 */
public class CloneDataSet {
    final DataSet clonedDataSet;
    final Map<Tagged, OsmPrimitive> clonedMap = new HashMap<>();

    public CloneDataSet(DataSet into, Collection<? extends Tagged> selection) {
        clonedDataSet = into;
        selection.forEach(this::clonePrimitive);
        selection.forEach(p -> clonedDataSet.addSelected(clonedMap.get(p)));
    }

    public DataSet get() {
        return clonedDataSet;
    }

    /**
     * Add a primitive to the cloned dataset if not already there.
     */
    private void add(Tagged p, OsmPrimitive newP) {
        clonedMap.computeIfAbsent(p, dummy -> {
            clonedDataSet.addPrimitive(newP);
            return newP;
        });
    }

    /**
     * Clone a primitive with all dependent primitives into the cloned dataset.
     * @param p the primitive to clone
     */
    public void clonePrimitive(Tagged p) {
        if (p instanceof Node) {
            Node newNode = new Node((Node) p);
            add(p, newNode);
            return;
        }
        if (p instanceof Way) {
            Way w = (Way) p;
            w.getNodes().forEach(this::clonePrimitive);

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
}
