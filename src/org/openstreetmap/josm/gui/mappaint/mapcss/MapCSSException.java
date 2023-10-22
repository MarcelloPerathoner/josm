// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.Token;

/**
 * MapCSS parsing error, with line/column information in error message.
 */
public class MapCSSException extends RuntimeException {

    /** source identifier at which the parse error occurred */
    protected String source = "";
    /** line number at which the parse error occurred */
    protected Integer line;
    /** column number at which the parse error occurred */
    protected Integer column;

    /**
     * Constructs a new {@code MapCSSException} with an explicit error message.
     * @param specialMessage error message
     */
    public MapCSSException(String specialMessage) {
        super(specialMessage);
    }

    /**
     * Constructs a new {@code MapCSSException} with a cause.
     * @param cause the root cause
     * @since 11562
     */
    public MapCSSException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new {@code MapCSSException} with an error message and a root cause.
     * @param message error message
     * @param cause the root cause
     * @since xxx
     */
    public MapCSSException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Sets the source identifier at which the parse error occurred.
     * @param source the source id at which the parse error occurred
     */
    public void setSource(MapCSSStyleSource source, Token token) {
        this.source = source != null ? source.url : null;
        this.line = token != null ? token.beginLine : null;
        this.column = token != null ? token.beginColumn : null;
    }

    /**
     * Sets the source identifier at which the parse error occurred.
     * @param source the source id at which the parse error occurred
     */
    public void setSource(String source) {
        this.source = source;
    }

    /**
     * Sets the column number at which the parse error occurred.
     * @param column the column number at which the parse error occurred
     */
    public void setColumn(int column) {
        this.column = column;
    }

    /**
     * Sets the line number at which the parse error occurred.
     * @param line the line number at which the parse error occurred
     */
    public void setLine(int line) {
        this.line = line;
    }

    @Override
    public String getMessage() {
        if (line == null || column == null)
            return super.getMessage();
        return String.format("%s:%s:%s Error: %s", source, line, column, super.getMessage());
    }
}
