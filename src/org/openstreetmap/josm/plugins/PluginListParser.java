// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.plugins;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A parser for the plugin list provided by a JOSM Plugin Download Site.
 *
 * See <a href="https://josm.openstreetmap.de/plugin">https://josm.openstreetmap.de/plugin</a>
 * for a sample of the document. The format is a custom format, kind of mix of CSV and RFC822 style
 * name/value-pairs.
 *
 */
public class PluginListParser {

    /**
     * Creates the plugin information object
     *
     * @param name the plugin name
     * @param url the plugin download url
     * @param manifest the plugin manifest attributes
     * @return a plugin information object
     * @throws PluginListParseException if plugin manifest cannot be parsed
     */
    public static PluginInformation createInfo(String name, URI uri, Attributes manifest) {
        return new PluginInformation(
                manifest,
                name.substring(0, name.length() - 4),
                uri
        );
    }

    /**
     * Parses a plugin information document and replies a list of plugin information objects.
     *
     * See <a href="https://josm.openstreetmap.de/plugin">https://josm.openstreetmap.de/plugin</a>
     * for a sample of the document. The format is a custom format, kind of mix of CSV and RFC822 style
     * name/value-pairs.
     *
     * @param in the input stream from which to parse
     * @param baseUri the base URI to use for resolving relative URIs or null
     * @return the list of plugin information objects
     * @throws PluginListParseException if something goes wrong while parsing
     */
    public List<PluginInformation> parse(InputStream in, URI baseUri) throws PluginListParseException {
        List<PluginInformation> ret = new LinkedList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String name = null;
            URI uri = null;
            Attributes manifest = new Attributes();
            final Pattern spaceColonSpace = Pattern.compile("\\s*:\\s*", Pattern.UNICODE_CHARACTER_CLASS);
            final Matcher matcher = spaceColonSpace.matcher("");
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                if (line.startsWith("\t")) {
                    matcher.reset(line);
                    if (matcher.find() && matcher.start() > 0 && matcher.end() < line.length()) {
                        final String key = line.substring(1, matcher.start());
                        final String value = line.substring(matcher.end());
                        manifest.put(new Attributes.Name(key), value);
                    }
                    continue;
                }
                addPluginInformation(ret, name, uri, manifest);
                String[] x = line.split(";", -1);
                if (x.length != 2)
                    throw new IOException(tr("Illegal entry in plugin list.") + " " + line);
                name = x[0];
                uri = new URI(x[1]);
                if (baseUri != null && !uri.isAbsolute()) {
                    uri = baseUri.resolve(uri);
                }
                manifest = new Attributes();
            }
            addPluginInformation(ret, name, uri, manifest);
            return ret;
        } catch (IOException | URISyntaxException e) {
            throw new PluginListParseException(e);
        }
    }

    private static void addPluginInformation(List<PluginInformation> ret, String name, URI uri, Attributes manifest) {
        if (name != null) {
            ret.add(createInfo(name, uri, manifest));
        }
    }
}
