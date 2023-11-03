// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
// import org.openstreetmap.josm.testutils.annotations.BasicPreferences;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.openstreetmap.josm.gui.mappaint.Environment;

/**
 * Unit tests of {@link FunctionSplitTrafficSign}.
 * <p>
 * See: https://wiki.openstreetmap.org/wiki/Key:traffic_sign#Tagging
 */
// @BasicPreferences

class FunctionSplitTrafficSignTest {

    private static Map<String, Object> m(String country, String id, Boolean add, String ... texts) {
        Map<String, Object> map = new HashMap<>();
        map.put("country", country);
        map.put("id", id);
        map.put("text", Arrays.asList(texts));
        map.put("additional", add);
        return map;
    }

    private static Stream<Arguments> argumentsProvider() {
        return Stream.of(
            Arguments.of("GB:956",
                Arrays.asList(
                    m("GB", "956", false)
                )
            ),
            Arguments.of("GB:616,954",
                Arrays.asList(
                    m("GB", "616", false),
                    m("GB", "954", true)
                )
            ),
            Arguments.of("GB:523.1[-10]",
                Arrays.asList(
                    m("GB", "523.1", false, "-10")
                )
            ),
            Arguments.of("DE:260,1020-30;265[3.8]",
                Arrays.asList(
                    m("DE", "260",     false),
                    m("DE", "1020-30", true),
                    m("DE", "265",     false, "3.8")
                )
            ),
            Arguments.of("US:CA:SW-59",
                Arrays.asList(
                    m("US:CA", "SW-59", false)
                )
            ),
            Arguments.of("NL:H01d[Merum][Maerem][Roermond];A0150",
                Arrays.asList(
                    m("NL", "H01d",  false, "Merum", "Maerem", "Roermond"),
                    m("NL", "A0150", false)
                )
            ),
            Arguments.of("BE:F4a[a:b][7;5][7,5];NL:A0150", // separators in brackets, multiple countries
                Arrays.asList(
                    m("BE", "F4a",   false, "a:b", "7;5", "7,5"),
                    m("NL", "A0150", false)
                )
            ),
            Arguments.of("", Arrays.asList()),
            Arguments.of(null, Arrays.asList())
        );
    }

    @ParameterizedTest
    @MethodSource("argumentsProvider")
    void test(String trafficSign, List<String> expected) {
        FunctionSplitTrafficSign f = new FunctionSplitTrafficSign();
        f.setArgs(Arrays.asList(
            LiteralExpression.create(trafficSign),
            LiteralExpression.create("DE")
        ));

        assertEquals(expected, f.evaluate(new Environment(), List.class, null));
    }
}
