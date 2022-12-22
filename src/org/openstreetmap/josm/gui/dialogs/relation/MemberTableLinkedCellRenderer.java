// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs.relation;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.ImageIcon;
import javax.swing.JTable;

import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionType;
import org.openstreetmap.josm.gui.dialogs.relation.sort.WayConnectionType.Direction;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;

/**
 * This class renders the link column of the member table. It shows if the way segments
 * in a relation are connected or not.
 */
public class MemberTableLinkedCellRenderer extends MemberTableCellRenderer {

    /** padding around cell border  */
    private static final int padding = ImageProvider.adj(2);
    /** size of the small black "connected" rectangle */
    private static final int smallRectSize = padding;
    /** size of the big red "unconnected" rectangle */
    private static final int bigRectSize = 2 * padding;

    private static final ImageIcon ARROW_UP = ImageProvider.get("dialogs/relation", "arrowup", ImageSizes.RELATION_LINK);
    private static final ImageIcon ARROW_DOWN = ImageProvider.get("dialogs/relation", "arrowdown", ImageSizes.RELATION_LINK);
    private static final ImageIcon ROUNDABOUT_RIGHT = ImageProvider.get("dialogs/relation", "roundabout_right_tiny", ImageSizes.TABLE);
    private static final ImageIcon ROUNDABOUT_LEFT = ImageProvider.get("dialogs/relation", "roundabout_left_tiny", ImageSizes.TABLE);

    private transient WayConnectionType value = new WayConnectionType();

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus,
            int row, int column) {

        reset();
        if (value != null) {
            this.value = (WayConnectionType) value;
            setToolTipText(((WayConnectionType) value).getTooltip());
            renderBackgroundForeground(getModel(table), null, isSelected);
        }
        return this;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (value == null || !value.isValid())
            return;

        int w = this.getSize().width;
        int h = this.getSize().height;

        int ymax = h - 1;

        /** x-coordinate for basic line */
        int xoff = w / 2;

        /** x-coordinate for the "return" line in a loop (or 0 if there is no loop) */
        int xloop = 0;

        /** offset from xoff for lines in forward/backward split route (or 0 if there is no split) */
        int xfbsplit = 0;

        assert !(value.isOnewayLoopForwardPart && value.isOnewayLoopBackwardPart);

        if (value.isOnewayLoopForwardPart) {
            xfbsplit = -w / 8;
        } else if (value.isOnewayLoopBackwardPart) {
            xfbsplit = w / 8;
        }

        if (value.isLoop) {
            xloop = 3 * w / 4;
        }
        int y1 = drawCellTop(g, xloop, value.isOnewayHead ? xoff : xoff + xfbsplit);
        int y2 = drawCellBottom(g, ymax, xloop, value.isOnewayTail ? xoff : xoff + xfbsplit);

        /* vertical lines */
        if (value.onewayFollowsNext && value.onewayFollowsPrevious) {
            // foreground is set via renderBackgroundForeground
            g.setColor(getForeground());
        } else {
            g.setColor(getForeground().brighter());
        }
        if (xloop != 0) {
            // draw the closed loop line
            // draw this first so changes to y1, y2 won't affect it
            g.drawLine(xloop, y1, xloop, y2);
        }
        if (xfbsplit == 0) {
            // no split
            g.drawLine(xoff, y1, xoff, y2);
        } else {
            // in a forward/backward split
            int pad = ImageProvider.adj(6);
            // CHECKSTYLE.OFF: SingleSpaceSeparator
            int xplain = xoff + xfbsplit;
            int xdots  = xoff - xfbsplit;
            int[] xPlain = {xoff, xplain, xplain};
            int[] xDots  = {xoff, xdots,  xdots};
            int[] yHead  = {1,    pad,        y2};
            int[] yTail  = {ymax, ymax - pad, y1};
            // CHECKSTYLE.ON: SingleSpaceSeparator
            if (value.isOnewayHead) {
                // this is the start of a forward/backward split
                y1 = pad;
                setDotted(g);
                g.drawPolyline(xDots, yHead, 3);
                unsetDotted(g);
                g.drawPolyline(xPlain, yHead, 3);
            } else if (value.isOnewayTail) {
                // this is the end of a forward/backward split
                y2 = ymax - pad;
                setDotted(g);
                g.drawPolyline(xDots, yTail, 3);
                unsetDotted(g);
                g.drawPolyline(xPlain, yTail, 3);
            } else {
                // this is in the middle of a forward/backward split
                setDotted(g);
                g.drawLine(xdots, y1, xdots, y2);
                unsetDotted(g);
                g.drawLine(xplain, y1, xplain, y2);
            }
        }

        /* draw arrow and roundabout icons */
        if (value.direction == Direction.ROUNDABOUT_LEFT) {
            drawCenteredIcon(g, ROUNDABOUT_LEFT, xoff, (y1 + y2) / 2);
        } else if (value.direction == Direction.ROUNDABOUT_RIGHT) {
            drawCenteredIcon(g, ROUNDABOUT_RIGHT, xoff, (y1 + y2) / 2);
        } else {
            drawCenteredIcon(g, getArrowIcon(), xoff + xfbsplit, (y1 + y2) / 2);
        }
    }

    private void drawCenteredRect(Graphics g, int x, int y, int size) {
        g.fillRect(x - size / 2, y - size / 2, size, size);
    }

    private void drawTopCenteredRect(Graphics g, int x, int y, int size) {
        g.fillRect(x - size / 2, y, size, size);
    }

    private void drawBottomCenteredRect(Graphics g, int x, int y, int size) {
        g.fillRect(x - size / 2, y - size, size, size);
    }

    private void drawCenteredIcon(Graphics g, ImageIcon icon, int x, int y) {
        if (icon != null) {
            int iconHeight = icon.getIconHeight();
            int iconWidth = icon.getIconWidth();
            int iconX = x - iconWidth / 2;
            int iconY = y - iconHeight / 2;
            g.drawImage(icon.getImage(), iconX, iconY, null);
        }
    }

    private int drawCellTop(Graphics g, int xloop, int xoff) {
        int y1;

        if (value.linkPrev) {
            // linked, draw the small black rectangle
            y1 = 0;
            if (value.onewayFollowsPrevious) {
                g.setColor(getForeground());
            } else {
                g.setColor(getForeground().brighter());
            }
            drawCenteredRect(g, xoff, 0, smallRectSize);
        } else {
            if (value.isLoop) {
                // not linked but first in roundtrip
                g.setColor(getForeground());
                y1 = padding;
                g.drawArc(xoff, 0, xloop, padding, 0, 180);
            } else {
                // not linked, draw the big red rectangle
                g.setColor(Color.red);
                drawTopCenteredRect(g, xoff, padding, bigRectSize);
                y1 = padding + bigRectSize;
            }
        }
        return y1;
    }

    private int drawCellBottom(Graphics g, int ymax, int xloop, int xoff) {
        int y2;

        if (value.linkNext) {
            // linked, draw the small black rectangle
            if (value.onewayFollowsNext) {
                g.setColor(getForeground());
            } else {
                g.setColor(getForeground().brighter());
            }
            drawCenteredRect(g, xoff, ymax, smallRectSize);
            y2 = ymax;
        } else {
            if (xloop != 0) {
                // not linked but last in roundtrip
                g.setColor(getForeground());
                y2 = ymax - padding;
                g.drawArc(xoff, y2, xloop - xoff, padding, 180, 180);
            } else {
                // not linked, draw the big red rectangle
                g.setColor(Color.red);
                drawBottomCenteredRect(g, xoff, ymax - padding, bigRectSize);
                y2 = ymax - bigRectSize - padding;
            }
        }
        return y2;
    }

    private ImageIcon getArrowIcon() {
        if (!value.ignoreOneway) {
            switch (value.direction) {
            case FORWARD:
                return ARROW_DOWN;
            case BACKWARD:
                return ARROW_UP;
            default:
                break;
            }
        }
        return null;
    }

    private static void setDotted(Graphics g) {
        ((Graphics2D) g).setStroke(new BasicStroke(
                1f,
                BasicStroke.CAP_BUTT,
                BasicStroke.JOIN_MITER,
                5f,
                new float[] {1f, 2f},
                0f));
    }

    private static void unsetDotted(Graphics g) {
        ((Graphics2D) g).setStroke(new BasicStroke());
    }
}
