// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.conflict.tags;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmDataManager;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.testutils.annotations.BasicPreferences;

import org.junit.jupiter.api.Test;

/**
 * Unit tests of {@link RelationMemberConflictResolver} class.
 */
@BasicPreferences
class RelationMemberConflictResolverTest {
    /**
     * Unit test for {@link RelationMemberConflictResolver#RelationMemberConflictResolver}.
     */
    @Test
    void testRelationMemberConflictResolver() {
        DataSet ds = new DataSet();
        OsmDataManager.getInstance().setActiveDataSet(ds);
        MainApplication.getLayerManager().addLayer(new OsmDataLayer(ds, "Test Layer", null));
        assertNotNull(new RelationMemberConflictResolver(null));
    }
}
