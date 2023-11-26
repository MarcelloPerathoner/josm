// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.openstreetmap.josm.gui.mappaint.styleelement.StyleElement;
import org.openstreetmap.josm.tools.Pair;

/**
 * Splits the range of possible scale values (0 &lt; scale &lt; +Infinity) into
 * multiple subranges, for each scale range it keeps a data object of a certain
 * type T (can be null).
 *
 * Used for caching style information for different zoom levels.
 *
 * Immutable class, equals &amp; hashCode is required (the same for
 * {@link StyleElementList}, {@link StyleElement} and its subclasses).
 *
 * @param <T> the type of the data objects
 */
public class DividedScale<T> {

    /**
     * This exception type is for debugging #8997 and can later be replaced by AssertionError
     */
    public static class RangeViolatedError extends RuntimeException {
        /**
         * Constructs a new {@code RangeViolatedError}
         * @param message error message
         */
        public RangeViolatedError(String message) {
            super(message);
        }
    }

    /* list of posts */
    private final List<Double> posts;
    /* list of fences */
    private final List<T> data;

    protected DividedScale() {
        posts = new ArrayList<>();
        data = new ArrayList<>();
        data.add(null);
    }

    /**
     * Copy constructor
     */
    protected DividedScale(DividedScale<T> s) {
        posts = new ArrayList<>(s.posts);
        data = new ArrayList<>(s.data);
    }

    /**
     * Returns the index or the insertion point
     * <p>
     * The index is the point at where the value was actually found. The insertion point
     * is the index where the value would have to be inserted if not found.
     *
     * @param scale scale
     * @return the index or insertion point
     */
    private int indexOf(double scale) {
        if (scale <= 0)
            throw new IllegalArgumentException("scale must be <= 0 but is "+scale);
        int n = data.size();
        if (n == 0)
            return -1;
        int index = Collections.binarySearch(posts, scale);
        return (index >= 0) ? index : -index - 1;
    }

    /**
     * Looks up the data object for a certain scale value.
     *
     * @param scale scale
     * @return the data object at the given scale, can be null
     */
    public T get(double scale) {
        int index = indexOf(scale);
        if (index == -1)
            return null;
        return data.get(index);
    }

    /**
     * Looks up the data object for a certain scale value and additionally returns
     * the scale range where the object is valid.
     *
     * @param scale scale
     * @return pair containing data object and range
     */
    public Pair<T, Range> getWithRange(double scale) {
        int index = indexOf(scale);
        if (index == -1)
            return null;
        double lower = index < 1 ? 0d : posts.get(index - 1);
        double upper = index >= posts.size() ? Double.MAX_VALUE : posts.get(index);
        return new Pair<>(data.get(index), new Range(lower, upper));
    }

    /**
     * Add data object which is valid for the given range.
     *
     * This is only possible, if there is no data for the given range yet.
     *
     * @param o data object
     * @param r the valid range
     * @return a new, updated, <code>DividedScale</code> object
     */
    public DividedScale<T> put(T o, Range r) {
        DividedScale<T> s = new DividedScale<>(this);
        s.putImpl(o, r.getLower(), r.getUpper());
        // Logging.info(s.toString());
        return s;
    }

    /**
     * Implementation of the <code>put</code> operation.
     *
     * ASCII-art explanation:
     *
     *    data[i-1]      data[i]      data[i+1]
     * (--------------]------------]--------------]
     *             post[i]      post[i+1]
     *
     *                       (--------]
     *                     lower     upper
     * @param o data object
     * @param lower lower bound
     * @param upper upper bound
     */
    private void putImpl(T o, double lower, double upper) {
        if (get(upper) != null)
            throw new RangeViolatedError("the new range must be within a subrange that has no data");

        // Logging.info(toString());

        int lowerIndex = binarySearchWithInsert(lower);
        int upperIndex = binarySearchWithInsert(upper);
        if (lowerIndex + 1 != upperIndex)
            throw new RangeViolatedError("the new range must be within a single subrange");

        // Logging.info(toString());
        // Logging.info("lower: {0} upper: {1}", lower, upper);
        // Logging.info("lowerIndex: {0} upperIndex: {1}", lowerIndex, upperIndex);

        data.set(lowerIndex + 1, o);
    }

    /**
     * Searches the posts array for the key.
     * <p>
     * Returns the index if the exact value was found.  If the value was not found, it
     * will be inserted and the new index returned.
     */
    private int binarySearchWithInsert(Double key) {
        int low = 0;
        int high = posts.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            Double midVal = posts.get(mid);

            if (midVal < key)
                low = mid + 1;
            else if (midVal > key)
                high = mid - 1;
            else
                return mid; // key found
        }

        // key not found, insert new post
        posts.add(low, key);
        data.add(low + 1, null);
        return low;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DividedScale<?> that = (DividedScale<?>) obj;
        return Objects.equals(posts, that.posts) &&
                Objects.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return 31 * posts.hashCode() + data.hashCode();
    }

    @Override
    public String toString() {
        return "DS{" + posts + ' ' + data + '}';
    }
}
