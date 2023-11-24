// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint;

import org.junit.jupiter.api.Test;
import org.openstreetmap.josm.tools.Pair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit tests of {@link DividedScale} class.
 */
class DividedScaleTest {

    /**
     * Test {@link DividedScale#get}
     */
    @Test
    void testGetEmpty() {
        DividedScale<String> scale = new DividedScale<>();
        assertThrows(IllegalArgumentException.class, () -> scale.get(0.));
        assertNull(scale.get(0.01));
        assertNull(scale.get(1.));
        assertNull(scale.get(4.));
        assertNull(scale.get(6.));
        assertNull(scale.get(8.));
        assertNull(scale.get(100.));
    }

    /**
     * Test {@link DividedScale#put} and {@link DividedScale#get}
     */
    @Test
    void testGetFoo() {
        DividedScale<String> scale = new DividedScale<String>()
                .put("foo", new Range(4., 8.));
        assertNull(scale.get(1.));
        assertNull(scale.get(4.));
        assertEquals("foo", scale.get(4.01));
        assertEquals("foo", scale.get(6.));
        assertEquals("foo", scale.get(8.));
        assertNull(scale.get(8.01));
        assertNull(scale.get(100.));
        assertEquals(scale, new DividedScale<>(scale));
    }

    /**
     * Test {@link DividedScale#put} and {@link DividedScale#getWithRange}
     */
    @Test
    void testGetWithRangeFoo() {
        DividedScale<String> scale = new DividedScale<String>()
                .put("foo", new Range(4., 8.));
        Pair<String, Range> pair = scale.getWithRange(6.);
        assertEquals("foo", pair.a);
        assertEquals(new Range(4., 8.), pair.b);
    }

    /**
     * Test {@link DividedScale#put} and {@link DividedScale#get}
     */
    @Test
    void testGetFooBar() {
        DividedScale<String> scale = new DividedScale<String>()
                .put("foo", new Range(4., 8.))
                .put("bar", new Range(2., 3.));
        assertNull(scale.get(2.));
        assertEquals("bar", scale.get(2.01));
        assertEquals("bar", scale.get(3.));
        assertNull(scale.get(3.01));
        assertNull(scale.get(4.));
        assertEquals("foo", scale.get(4.01));
        assertEquals("foo", scale.get(8.));
        assertNull(scale.get(8.01));
    }

    /**
     * Test {@link DividedScale#put} and {@link DividedScale#get}
     */
    @Test
    void testGetFooBarBaz() {
        DividedScale<String> scale = new DividedScale<String>()
                .put("foo", new Range(4., 8.))
                .put("bar", new Range(2., 3.))
                .put("baz", new Range(3., 4.));
        assertNull(scale.get(2.));
        assertEquals("bar", scale.get(2.01));
        assertEquals("bar", scale.get(3.));
        assertEquals("baz", scale.get(3.01));
        assertEquals("baz", scale.get(4.));
        assertEquals("foo", scale.get(4.01));
        assertEquals("foo", scale.get(8.));
        assertNull(scale.get(8.01));
    }

    /**
     * Test {@link DividedScale#put} and {@link DividedScale#get}
     */
    @Test
    void testSplitLower() {
        DividedScale<String> scale = new DividedScale<String>()
                .put("foo",  new Range(2., 4.))
                .put("bar",  new Range(6., 8.))
                .put("baz",  new Range(4., 5.))
                .put("quux", new Range(8., 9.));
        assertNull(scale.get(1.5));
        assertEquals("foo",  scale.get(3.));
        assertEquals("bar",  scale.get(7.));
        assertEquals("baz",  scale.get(4.5));
        assertEquals("quux", scale.get(8.5));
        assertNull(scale.get(9.5));
    }

    /**
     * Test {@link DividedScale#put} and {@link DividedScale#get}
     */
    @Test
    void testSplitUpper() {
        DividedScale<String> scale = new DividedScale<String>()
                .put("foo",  new Range(2., 4.))
                .put("bar",  new Range(6., 8.))
                .put("baz",  new Range(5., 6.))
                .put("quux", new Range(0., 2.));
        assertEquals("quux", scale.get(1.));
        assertEquals("foo",  scale.get(3.));
        assertEquals("bar",  scale.get(7.));
        assertEquals("baz",  scale.get(5.5));
        assertNull(scale.get(9.));
    }

    /**
     * Test {@link DividedScale#put} and {@link DividedScale#get}
     */
    @Test
    void testSplitMiddle() {
        DividedScale<String> scale = new DividedScale<String>()
                .put("foo",  new Range(2., 4.))
                .put("bar",  new Range(6., 8.))
                .put("baz",  new Range(4.5, 5.5))
                .put("quux", new Range(0.5, 1.5))
                .put("quux", new Range(8.5, 9.5));
        assertNull(scale.get(0.1));
        assertEquals("quux", scale.get(1.));
        assertEquals("foo", scale.get(3.));
        assertNull(scale.get(4.2));
        assertEquals("baz", scale.get(5.));
        assertNull(scale.get(5.7));
        assertEquals("bar", scale.get(7.));
        assertEquals("quux", scale.get(9.));
    }

    /**
     * Test {@link DividedScale#put} and {@link DividedScale#get}
     */
    @Test
    void testSplitExact() {
        DividedScale<String> scale = new DividedScale<String>()
                .put("foo", new Range(2., 4.))
                .put("bar", new Range(6., 8.))
                .put("baz", new Range(4., 6.));
        assertNull(scale.get(1.));
        assertEquals("foo", scale.get(3.));
        assertEquals("baz", scale.get(5.));
        assertEquals("bar", scale.get(7.));
        assertNull(scale.get(9.));
    }

    /**
     * Test {@link DividedScale#put}
     */
    @Test
    void testPutSingleSubrange1() {
        DividedScale<String> scale = new DividedScale<String>()
                .put("foo", new Range(4., 8.));
        Exception ex = assertThrows(DividedScale.RangeViolatedError.class, () -> scale.put("bar", new Range(4., 9.)));
        assertEquals("the new range must be within a single subrange", ex.getMessage());
    }

    /**
     * Test {@link DividedScale#put}
     */
    @Test
    void testPutSingleSubrangeNoData() {
        DividedScale<String> scale = new DividedScale<String>()
                .put("foo", new Range(4., 8.));
        Exception ex = assertThrows(DividedScale.RangeViolatedError.class, () -> scale.put("bar", new Range(4., 5.)));
        assertEquals("the new range must be within a subrange that has no data", ex.getMessage());
    }

    /**
     * Test {@link DividedScale#put}
     */
    @Test
    void testPutSingleSubrange2() {
        DividedScale<String> scale = new DividedScale<String>()
                .put("foo", new Range(4., 8.));
        Exception ex = assertThrows(DividedScale.RangeViolatedError.class, () -> scale.put("bar", new Range(2., 5.)));
        assertEquals("the new range must be within a subrange that has no data", ex.getMessage());
        // assertEquals("the new range must be within a single subrange", ex.getMessage());
    }
}
