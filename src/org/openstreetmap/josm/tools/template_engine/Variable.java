// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.template_engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.openstreetmap.josm.tools.LanguageInfo;

/**
 * {@link TemplateEntry} that inserts the value of a variable.
 * <p>
 * Variables starting with "special:" form a separate namespace and
 * provide actions other than simple key-value lookup.
 * <p>
 * A variable with no mapping for a given data provider will be considered not "valid"
 * (see {@link TemplateEntry#isValid(TemplateEngineDataProvider)}).
 */
public class Variable implements TemplateEntry {

    private static final String SPECIAL_VARIABLE_PREFIX = "special:";
    private static final String SPECIAL_LOCAL_PREFIX = "local:";
    private static final String SPECIAL_MULTI_LANG_PREFIX = "mergelang:";
    private static final String SPECIAL_VALUE_EVERYTHING = "everything";

    private final String variableName;
    private final boolean special;

    /**
     * Constructs a new {@code Variable}.
     * @param variableName the variable name (i.e. the key in the data provider key-value mapping);
     * will be considered "special" if the variable name starts with {@link #SPECIAL_VARIABLE_PREFIX}
     */
    public Variable(String variableName) {
        if (variableName.toLowerCase(Locale.ENGLISH).startsWith(SPECIAL_VARIABLE_PREFIX)) {
            this.variableName = variableName.substring(SPECIAL_VARIABLE_PREFIX.length());
            // special:special:key means that real key named special:key is needed, not special variable
            this.special = !this.variableName.toLowerCase(Locale.ENGLISH).startsWith(SPECIAL_VARIABLE_PREFIX);
        } else {
            this.variableName = variableName;
            this.special = false;
        }
    }

    private List<String> varAsList(TemplateEngineDataProvider dataProvider, String name) {
        String val = (String) dataProvider.getTemplateValue(name, false);
        if (val == null || val.isEmpty())
            return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(val.split(";")));
    }

    /**
     * Merges multiple language tags
     * <p>
     * This function merges multiple language tags accounting for multiple values in each tag.
     * <p>
     * Paramter string format: {@code nameTemplate,delimiter,lang[,lang[...]]}  In the nameTemplate
     * part {@code '%'} is substituted by {@code lang}.
     * <p>
     * Example:
     * <p>
     * {@code special:mergelang:destination:lang:%:forward, - ,de,it} will merge
     * {@code destination:lang:de:forward} and {@code destination:lang:it:forward} using a delimiter
     * of {@code ' - '}.
     * <p>
     * If {@code destination:lang:de:forward=Bozen;Brixen} and
     * {@code destination:lang:it:forward=Bolzano;Bressanone} the returned value will be:
     * {@code Bozen - Bolzano;Brixen - Bressanone}.
     *
     * @param parameters the parameter string
     * @param dataProvider the data provider
     * @return the merged string
     */
    private String mergeLang(String parameters, TemplateEngineDataProvider dataProvider) {
        String paramDelimiter = ",";
        List<String> params = new ArrayList<>(Arrays.asList(parameters.split(paramDelimiter)));
        String nameTemplate = params.remove(0);
        String delimiter = params.remove(0);

        // list of languages, tokens
        List<List<String>> values = new ArrayList<>();
        int nLangs = params.size();
        int nTokens = 0;
        for (String lang : params) {
            List<String> langAsList = varAsList(dataProvider, nameTemplate.replace("%", lang));
            values.add(langAsList);
            nTokens = Math.max(nTokens, langAsList.size());
        }
        List<String> pieces = new ArrayList<>();
        for (int i = 0; i < nTokens; ++i) {
            if (i > 0)
                pieces.add(";");  // String.join doesn't handle a trailing empty piece correctly
            // eliminates duplicates and keeps insertion order
            Set<String> seen = new LinkedHashSet<>();
            for (int j = 0; j < nLangs; ++j) {
                try {
                    String v = values.get(j).get(i).trim();
                    if (!v.isEmpty())
                        seen.add(values.get(j).get(i).trim());
                } catch (IndexOutOfBoundsException e) {
                    // not all langs need to be filled
                    continue; // make checkstyle happy
                }
            }
            pieces.add(String.join(delimiter, seen));
        }
        return String.join("", pieces);
    }

    private String getValue(TemplateEngineDataProvider dataProvider) {
        if (special) {
            String result = "";
            if (variableName.startsWith(SPECIAL_LOCAL_PREFIX)) {
                // in a 'de_DE' locale 'special:local:description' will yield the value of
                // 'description:de_DE' if existent else of 'description:de' else of 'description'
                String tmpName = variableName.substring(SPECIAL_LOCAL_PREFIX.length());
                for (String lang : LanguageInfo.getLanguageCodes(null)) {
                    result = (String) dataProvider.getTemplateValue(tmpName + ':' + lang, false);
                    if (result != null)
                        return result;
                }
                return (String) dataProvider.getTemplateValue(tmpName, false);
            }
            if (variableName.startsWith(SPECIAL_MULTI_LANG_PREFIX)) {
                return mergeLang(variableName.substring(SPECIAL_MULTI_LANG_PREFIX.length()), dataProvider);
            }
            if (SPECIAL_VALUE_EVERYTHING.equals(variableName)) {
                ArrayList<String> keys = new ArrayList<>(dataProvider.getTemplateKeys());
                Collections.sort(keys);
                return keys.stream().map(key -> key + "=" + dataProvider.getTemplateValue(key, false))
                        .collect(Collectors.joining(", "));
            }
        }
        return (String) dataProvider.getTemplateValue(variableName, special);
    }

    @Override
    public void appendText(StringBuilder result, TemplateEngineDataProvider dataProvider) {
        String value = getValue(dataProvider);
        if (value != null) {
            result.append(value);
        }
    }

    @Override
    public boolean isValid(TemplateEngineDataProvider dataProvider) {
        String value = getValue(dataProvider);
        return value != null;
    }

    @Override
    public String toString() {
        return '{' + (special ? SPECIAL_VARIABLE_PREFIX : "") + variableName + '}';
    }

    /**
     * Check if this variable is special.
     *
     * @return true if this variable is special
     */
    public boolean isSpecial() {
        return special;
    }

    @Override
    public int hashCode() {
        return Objects.hash(special, variableName);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        Variable other = (Variable) obj;
        if (special != other.special)
            return false;
        if (variableName == null) {
            if (other.variableName != null)
                return false;
        } else if (!variableName.equals(other.variableName))
            return false;
        return true;
    }
}
