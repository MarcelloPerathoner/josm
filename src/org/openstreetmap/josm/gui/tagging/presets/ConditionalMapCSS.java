// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.StringReader;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.mapcss.Selector;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.MapCSSParser;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.ParseException;
import org.openstreetmap.josm.gui.tagging.DataHandlers.TaggedHandler;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.gui.tagging.DataHandlers.DataSetHandler;

/**
 * An element that displays its children conditionally on a MapCSS expression.
 */
abstract class ConditionalMapCSS extends Conditional {
    /** The list of MapCSS selectors to match. */
    final List<Selector> selectors;
    /** Whether "any" or "all" selected primitives must match. */
    final String matchMode;
    /** The parsed map_css for debugging */
    final String mapCss;

    /**
     * Constructor.
     * @param attributes the XML attributes
     * @throws IllegalArgumentException on illegal attributes
     */
    ConditionalMapCSS(Map<String, String> attributes) throws IllegalArgumentException {
        super(attributes);
        matchMode = attributes.getOrDefault("match_mode", "any");
        mapCss = attributes.get("map_css");
        try {
            selectors = new MapCSSParser(new StringReader(mapCss)).selectors_for_search();
        } catch (ParseException | IllegalArgumentException e) {
            throw new IllegalArgumentException(tr("Failed to parse MapCSS selector; {0}", mapCss), e);
        }
    }

    @Override
    boolean matches(TaggedHandler handler) {
        boolean result = false;
        if (handler instanceof DataSetHandler) {
            Collection<OsmPrimitive> selection = ((DataSetHandler) handler).get();
            if (matchMode.equals("any")) {
                result = selection.stream().anyMatch(osm -> selectors.stream()
                    .anyMatch(selector -> selector.matches(new Environment(osm))));
            }
            if (matchMode.equals("all")) {
                result = selection.stream().allMatch(osm -> selectors.stream()
                    .anyMatch(selector -> selector.matches(new Environment(osm))));
            }
        }
        Logging.info("{0} map_css: {1}", result ? "Matched" : "Rejected", mapCss);
        return result;
    }

    @Override
    public String toString() {
        return "ConditionalMapCSS";
    }
}
