// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.tagging.presets;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import javax.swing.JOptionPane;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.ValidatorHandler;

import org.openstreetmap.josm.data.preferences.sources.PresetPrefHelper;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.io.NetworkManager;
import org.openstreetmap.josm.io.UTFInputStreamReader;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.I18n;
import org.openstreetmap.josm.tools.JosmRuntimeException;
import org.openstreetmap.josm.tools.LanguageInfo;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.Stopwatch;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.XmlParsingException;
import org.openstreetmap.josm.tools.XmlUtils;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXParseException;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * The tagging presets XML file parser.
 * <p>
 * Parses an XML file and builds an in-memory tree of template classes. The template classes are
 * then used to create preset dialogs and menus.
 *
 * @since xxx
 */
public final class TaggingPresetReader {

    /**
     * The accepted MIME types sent in the HTTP Accept header.
     * @since 6867
     */
    public static final String PRESET_MIME_TYPES =
            "application/xml, text/xml, text/plain; q=0.8, application/zip, application/octet-stream; q=0.5";

    /**
     * The XML namespace for the tagging presets
     * @since 16640
     */
    public static final String NAMESPACE = Config.getUrls().getXMLBase() + "/tagging-preset-1.0";

    /**
     * The internal resource URL of the XML schema file to be used with {@link CachedFile}
     * @since 16640
     */
    public static final String SCHEMA_SOURCE = "resource://data/tagging-preset.xsd";

    private static File zipIcons;
    private static boolean loadIcons = true;

    private static class Parser extends DefaultHandler {
        private Root root;
        private Stack<Item> stack = new Stack<>();
        private StringBuilder characters;
        private Locator locator;
        private final String lang = LanguageInfo.getLanguageCodeXML();

        public Root getRoot() {
            return root;
        }

        @Override
        public void setDocumentLocator(Locator locator) {
            this.locator = locator;
        }

        private Map<String, String> getAttributes(Attributes a) {
            Map<String, String> attributes = new HashMap<>();
            for (int i = 0; i < a.getLength(); ++i) {
                String name = a.getLocalName(i);
                if (name.startsWith(lang)) {
                    name = "locale_" + name.substring(lang.length());
                }
                attributes.put(a.getLocalName(i), a.getValue(i));
            }
            return attributes;
        }

        @Override
        public void startElement(String ns, String lname, String qname, Attributes a) throws SAXException {
            Item item = null;
            Map<String, String> attributes = getAttributes(a);

            try {
                item = ItemFactory.build(lname, attributes);
                if (item instanceof Root) {
                    Root root = (Root) item;
                    if (this.root == null) {
                        root.url = locator.getSystemId();
                        this.root = root;
                    }
                }
                if (stack.size() > 0) {
                    // add this item to the parent
                    // do not put this into the constructor of Item or Chunks will not work
                    stack.peek().addItem(item);
                }
            } catch (IllegalArgumentException e) {
                throwException(e);
            }
            stack.push(item);
        }

        @Override
        public void endElement(String ns, String lname, String qname) throws SAXException {
            try {
                Item item = stack.peek();
                if (characters != null)
                    item.setContent(characters.toString().trim());
                item.endElement();
            } catch (IllegalArgumentException e) {
                throwException(e);
            }
            stack.pop();
            characters = null;
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (characters == null)
                characters = new StringBuilder(64); // lazily get a builder
            characters.append(ch, start, length);
        }

        /**
         * Rethrows an exception and adds location information
         * @param e the exception without location information
         * @throws XmlParsingException the exception with location information
         */
        private void throwException(Exception e) throws XmlParsingException {
            throw new XmlParsingException(e).rememberLocation(locator);
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throwException(e);
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throwException(e);
        }
    }

    private static void start(final Reader in, final ContentHandler contentHandler, String url) throws SAXException, IOException {
        try {
            XMLReader reader = XmlUtils.newSafeSAXParser().getXMLReader();
            reader.setContentHandler(contentHandler);
            try {
                // better performance on big files like defaultpresets.xml
                reader.setProperty("http://apache.org/xml/properties/input-buffer-size", 8 * 1024);
                // enable xinclude
                reader.setFeature("http://apache.org/xml/features/xinclude", true);
                // do not set xml:base, it doesn't validate
                reader.setFeature("http://apache.org/xml/features/xinclude/fixup-base-uris", false);
                // Do not load external DTDs (fix #8191)
                reader.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            } catch (SAXException e) {
                // Exception very unlikely to happen, so no need to translate this
                Logging.log(Logging.LEVEL_ERROR, "Cannot set property or feature on SAX reader:", e);
            }
            InputSource is = new InputSource(in);
            is.setSystemId(url);
            reader.parse(is);
        } catch (ParserConfigurationException e) {
            throw new JosmRuntimeException(e);
        }
    }

    /**
     * This filter adds the default namespace
     * {@code http://josm.openstreetmap.de/tagging-preset-1.0} to all elements that have none.
     */
    private static class AddNamespaceFilter extends XMLFilterImpl {
        private final String namespace;

        AddNamespaceFilter(String namespace) {
            this.namespace = namespace;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
            if ("".equals(uri)) {
                super.startElement(namespace, localName, qName, atts);
            } else {
                super.startElement(uri, localName, qName, atts);
            }
        }
    }

    /**
     * Add validation filters to a parser
     *
     * @param parser the parser without validation
     * @param namespace default namespace
     * @param schemaUrl URL of XSD schema
     * @return the new parser with validation
     * @throws SAXException if any XML or I/O error occurs
     */
    public static ContentHandler buildParserWithValidation(Parser parser, String namespace, String schemaUrl) throws SAXException {
        SchemaFactory factory = XmlUtils.newXmlSchemaFactory();
        try (CachedFile cf = new CachedFile(schemaUrl);
            InputStream mis = cf.getInputStream()) {
            Schema schema = factory.newSchema(new StreamSource(mis));
            ValidatorHandler validator = schema.newValidatorHandler();
            validator.setContentHandler(parser);
            validator.setErrorHandler(parser);

            AddNamespaceFilter filter = new AddNamespaceFilter(namespace);
            filter.setContentHandler(validator);
            return filter;
        } catch (IOException e) {
            throw new SAXException(tr("Failed to load XML schema."), e);
        }
    }

    /**
     * Reads all tagging presets from an XML literal.
     *
     * @param xml the xml literal
     * @param validate if {@code true}, XML validation will be performed
     * @return the root element of the resource
     * @throws SAXException if any XML error occurs
     */
    public static Root readLiteral(String xml, boolean validate) throws SAXException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        Parser parser = new Parser();
        ContentHandler handler = parser;
        if (validate) {
            handler = buildParserWithValidation((Parser) handler, NAMESPACE, SCHEMA_SOURCE);
        }
        try {
            start(new StringReader(xml), handler, "XML literal");
        } catch (IOException e) {
            Logging.error("can't happen"); // reading a literal
        }

        Logging.debug(stopwatch.toString("Reading presets from XML literal"));
        return parser.getRoot();
    }

    /**
     * Reads all tagging presets from the given XML resource.
     *
     * @param url a given filename, URL or internal resource
     * @param validate if {@code true}, XML validation will be performed
     * @return the root element of the resource
     * @throws SAXException if any XML error occurs
     * @throws IOException if any I/O error occurs
     */
    public static Root read(String url, boolean validate) throws SAXException, IOException {
        Logging.debug("Reading presets from {0}", url);
        Stopwatch stopwatch = Stopwatch.createStarted();
        Parser parser = new Parser();

        try (
            CachedFile cf = new CachedFile(url);
            // zip may be null, but Java 7 allows it: https://blogs.oracle.com/darcy/entry/project_coin_null_try_with
            InputStream zip = cf.findZipEntryInputStream("xml", "preset")
        ) {
            cf.setHttpAccept(PRESET_MIME_TYPES);
            if (zip != null) {
                zipIcons = cf.getFile();
                I18n.addTexts(zipIcons);
            }
            try (InputStreamReader r = UTFInputStreamReader.create(zip == null ? cf.getInputStream() : zip)) {
                ContentHandler handler = parser;
                if (validate) {
                    handler = buildParserWithValidation((Parser) handler, NAMESPACE, SCHEMA_SOURCE);
                }
                start(new BufferedReader(r), handler, url);
            }
        }

        Root patchRoot = readPatchFile(url, validate);
        if (patchRoot != null)
            parser.getRoot().items.addAll(patchRoot.items);

        Logging.debug(stopwatch.toString("Reading presets"));
        return parser.getRoot();
    }

    /**
     * Try to read a .local preset patch file.
     * <p>
     * A preset patch file has the same structure as the {@code defaultpresets.xml} file. All items
     * in the root of the preset patch file will be appended to the root of the respective presets
     * file. Chunks in the preset patch file will replace chunks with the same {@code id} in the
     * presets file. The patch file must be placed in the {@code josmdir://} and have the same
     * filename and extension with an added extension of {@code .local} eg.
     * {@code <josmdir>/defaultpresets.xml.local}
     *
     * @param url a given filename, URL or internal resource
     * @param validate if {@code true}, XML validation will be performed
     * @return the root element of the resource
     * @throws SAXException if any XML error occurs
     * @throws IOException if any I/O error occurs
     */

    static Root readPatchFile(String url, boolean validate) throws SAXException, IOException {
        try {
            URI uri = new URI(url);
            String fileName = new File(uri.getPath()).getName();
            url = "josmdir://" + fileName + ".local";

            Parser parser = new Parser();
            try (CachedFile cf = new CachedFile(url)) {
                Logging.debug("Reading local preset patches from {0}", cf.getFile().toPath());
                try (InputStreamReader r = UTFInputStreamReader.create(cf.getInputStream())) {
                    ContentHandler handler = parser;
                    if (validate) {
                        handler = buildParserWithValidation(parser, NAMESPACE, SCHEMA_SOURCE);
                    }
                    start(new BufferedReader(r), handler, url);
                }
            }
            return parser.getRoot();
        } catch (URISyntaxException e) {
            Logging.error("readPatchFile: cannot parse url {0}", url);
            return null;
        } catch (IOException e) {
            return null; // there is no local patch file, do nothing
        }
    }

    /**
     * Reads all tagging presets from the given XML resources. Convenience function.
     *
     * @param sources Collection of tagging presets sources.
     * @param validate if {@code true}, presets will be validated against XML schema
     * @return the root elements of the XML resources
     */
    public static Collection<Root> readAll(Collection<String> sources, boolean validate) {
        return readAll(sources, validate, true);
    }

    /**
     * Reads all tagging presets from the given XML resources.
     *
     * @param sources Collection of tagging presets sources.
     * @param validate if {@code true}, presets will be validated against XML schema
     * @param displayErrMsg if {@code true}, a blocking error message is displayed in case of I/O exception.
     * @return the root elements of the XML resources
     */
    public static Collection<Root> readAll(Collection<String> sources, boolean validate, boolean displayErrMsg) {
        Collection<Root> result = new ArrayList<>();
        for (String source : sources) {
            try {
                result.add(read(source, validate));
            } catch (IOException e) {
                Logging.log(Logging.LEVEL_ERROR, e);
                Logging.error(source);
                if (source.startsWith("http")) {
                    NetworkManager.addNetworkError(source, e);
                }
                if (displayErrMsg) {
                    JOptionPane.showMessageDialog(
                            MainApplication.getMainFrame(),
                            tr("Could not read tagging preset source: {0}", source),
                            tr("Error"),
                            JOptionPane.ERROR_MESSAGE
                            );
                }
            } catch (SAXException | IllegalArgumentException e) {
                Logging.error(e);
                Logging.error(source);
                if (displayErrMsg) {
                    JOptionPane.showMessageDialog(
                            MainApplication.getMainFrame(),
                            "<html>" + tr("Error parsing {0}: ", source) + "<br><br><table width=600>" +
                                    Utils.escapeReservedCharactersHTML(e.getMessage()) + "</table></html>",
                            tr("Error"),
                            JOptionPane.ERROR_MESSAGE
                            );
                }
            }
        }
        return result;
    }

    /**
     * Reads all tagging presets from sources stored in preferences.
     * @param validate if {@code true}, presets will be validated against XML schema
     * @param displayErrMsg if {@code true}, a blocking error message is displayed in case of I/O exception.
     * @return Collection of all presets successfully read
     */
    public static Collection<Root> readFromPreferences(boolean validate, boolean displayErrMsg) {
        return readAll(getPresetSources(), validate, displayErrMsg);
    }

    /**
     * Returns the set of preset source URLs.
     * @return The set of preset source URLs.
     */
    public static Set<String> getPresetSources() {
        return new PresetPrefHelper().getActiveUrls();
    }

    /**
     * Returns the zip file where the icons are located
     * @return the zip file where the icons are located
     */
    public static File getZipIcons() {
        return zipIcons;
    }

    /**
     * Determines if icon images should be loaded.
     * @return {@code true} if icon images should be loaded
     */
    public static boolean isLoadIcons() {
        return loadIcons;
    }

    /**
     * Sets whether icon images should be loaded.
     * @param loadIcons {@code true} if icon images should be loaded
     */
    public static void setLoadIcons(boolean loadIcons) {
        TaggingPresetReader.loadIcons = loadIcons;
    }

    // fix checkstyle HideUtilityClassConstructor
    private TaggingPresetReader() {}
}
