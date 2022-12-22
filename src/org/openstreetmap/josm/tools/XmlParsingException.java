// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools;

import static org.openstreetmap.josm.tools.I18n.tr;

import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.LocatorImpl;

/**
 * An exception thrown during XML parsing, with known line and column.
 * @since 6906
 */
public class XmlParsingException extends SAXException {
    private LocatorImpl locator = new LocatorImpl();

    /**
     * Constructs a new {@code XmlParsingException}.
     * @param e The cause
     */
    public XmlParsingException(Exception e) {
        super(e);
    }

    /**
     * Constructs a new {@code XmlParsingException}.
     * @param message The error message
     * @param e The cause
     */
    public XmlParsingException(String message, Exception e) {
        super(message, e);
    }

    /**
     * Constructs a new {@code XmlParsingException}.
     * @param message The error message
     */
    public XmlParsingException(String message) {
        super(message);
    }

    /**
     * Sets the location (line/column) where the exception occurred.
     * @param locator object giving the location (line/column) where the exception occurred
     * @return {@code this}
     */
    public XmlParsingException rememberLocation(Locator locator) {
        if (locator != null) {
            this.locator = new LocatorImpl(locator);
        }
        return this;
    }

    @Override
    public String getMessage() {
        String msg = super.getMessage();
        if (getLineNumber() == 0 && getColumnNumber() == 0)
            return msg;
        if (msg == null) {
            msg = getClass().getName();
        }
        return msg + ' ' + tr("(in {0} at line {1}, column {2})",
            locator.getSystemId(), getLineNumber(), getColumnNumber());
    }

    /**
     * Returns the column number where the exception occurred.
     * @return the column number where the exception occurred
     */
    public int getColumnNumber() {
        return locator.getColumnNumber();
    }

    /**
     * Returns the line number where the exception occurred.
     * @return the line number where the exception occurred
     */
    public int getLineNumber() {
        return locator.getLineNumber();
    }
}
