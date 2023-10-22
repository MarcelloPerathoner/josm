// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint;

import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;
import org.openstreetmap.josm.gui.mappaint.mapcss.AllMapCSSTests;

/**
 * All mappaint tests.
 */
@Suite
@SelectClasses({
    MapCSSWithExtendedTextDirectivesTest.class,
    AllMapCSSTests.class
})
class AllMappaintTests {

}
