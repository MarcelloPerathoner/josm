// License: GPL. For details, see LICENSE file.

package org.openstreetmap.josm.gui.mappaint.mapcss;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.mapcss.ExpressionFactory.CacheableExpression;
import org.openstreetmap.josm.tools.Utils;

/**
 * Splits a {@code traffic_sign} string into its components.
 * <p>
 * Splits a string in the form:
 * <code>
 *    DE:260,1020-30;265[3.8][42]
 * </code>
 * into a {@code List<Map<String, Object>>} of the form:
 * <table><caption>Result</caption>
 * <tr><th>country<th>id<th>additional<th>text
 * <tr><td>DE<td>260<td>false<td>empty list
 * <tr><td>DE<td>1020-30<td>true<td>empty list
 * <tr><td>DE<td>265<td>false<td>list of "3.8", "42"
 * </table>
 * <p>
 * For each traffic sign, the map contains the country prefix, the sign id, whether
 * the sign is an additional one, and a list of zero or more custom texts.
 * <p>
 * See: https://wiki.openstreetmap.org/wiki/Key:traffic_sign#Tagging
 *
 * @since xxx
 */
public class FunctionSplitTrafficSign extends CacheableExpression {
    static Pattern reCountryPrefix = Pattern.compile(
        "^(?:([-:A-Z]*):)?(.*)$",
        Pattern.CASE_INSENSITIVE + Pattern.UNICODE_CASE
    );

    public FunctionSplitTrafficSign() {
        super(Cacheability.IMMUTABLE, 1, 2);
    }

    @Override
    public Object evalImpl(Environment env) {
        // a simple top-down parser

        String trafficSign = argAsString(env, 0);
        if (Utils.isEmpty(trafficSign))
            return Collections.emptyList();
        String lastCountry = argAsString(env, 1);

        trafficSign += ";"; // add sentinel
        char[] input = trafficSign.toCharArray();
        List<Map<String, Object>> result = new ArrayList<>();

        String content = null;
        List<String> brackets = new ArrayList<>();

        int i = 0;
        int startOfSection = 0;
        boolean additionalSign = false;
        while (i < input.length) {
            char c = input[i];
            if (c == '[') {
                int startOfBracket = i;
                i = parseBracket(input, i);
                brackets.add(new String(input, startOfBracket + 1, i - startOfBracket - 1));
                if (content == null) // put away content on first bracket only
                    content = new String(input, startOfSection, startOfBracket - startOfSection);
            } else if (c == ';' || c == ',') {
                if (content == null)
                    content = new String(input, startOfSection, i - startOfSection);
                Matcher m = reCountryPrefix.matcher(content);
                if (m.matches()) {
                    if (m.group(1) != null)
                        lastCountry = m.group(1);
                    Map<String, Object> map = new HashMap<>();
                    map.put("country", lastCountry);
                    map.put("id", m.group(2));
                    map.put("text", brackets);
                    map.put("additional", additionalSign);
                    result.add(map);
                } else {
                    assert false : "matches always";
                }
                additionalSign = c == ',';
                content = null;
                brackets = new ArrayList<>();
                startOfSection = i + 1;
            }
            ++i;
        }
        return result;
    }

    int parseBracket(char[] input, int i) {
        while (i < input.length) {
            char c = input[i];
            if (c == ']') {
                return i;
            }
            ++i;
        }
        return i;
    }

}
