/*
    Copyright 2024 Will Winder

    This file is part of Universal Gcode Sender (UGS).

    UGS is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    UGS is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with UGS.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.willwinder.ugs.nbp.radialjog;

import javax.swing.JComponent;
import javax.swing.event.MouseInputAdapter;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.Point;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;

/**
 * A bullseye jog pad where each concentric ring is a jog step distance and the
 * angle from the centre selects the direction. A stepped strip on the right
 * jogs the Z axis and four corner buttons home / return the axes to zero. Drag
 * out from the centre and release to jog; the centre hub cancels a jog.
 *
 * @author Claude
 */
public class RadialJogControl extends JComponent {

    /**
     * Ring step distances in millimetres, inner ring first.
     */
    static final double[] DISTANCES = {0.1, 1, 10, 50, 100};

    private static final int MARGIN = 12;
    private static final int Z_STRIP_WIDTH = 56;
    private static final int Z_STRIP_GAP = 12;
    private static final int Z_CENTER_GAP = 18;

    // Ring / dome.
    private static final Color DOME_LIGHT = new Color(0xEDEDED);
    private static final Color DOME_MID = new Color(0xCCCCCC);
    private static final Color DOME_EDGE = new Color(0xA2A2A2);
    private static final Color GROOVE = new Color(0x9A9A9A);
    private static final Color GROOVE_HI = new Color(0xF4F4F4);
    private static final Color SPOKE = new Color(0x707070);
    private static final Color AXIS_LINE = new Color(0xD64A3A);
    private static final Color ARROW = new Color(0x4FA0CF);
    private static final Color ARROW_EDGE = new Color(0x2E7AA8);
    private static final Color HILIGHT = new Color(0xF0B94E);
    private static final Color HILIGHT_LINE = new Color(0xC88F1E);
    private static final Color HUB = new Color(0x7E7E7E);
    private static final Color HUB_EDGE = new Color(0x5E5E5E);

    // Z strip.
    private static final Color Z_PLUS = new Color(0x8FC06B);
    private static final Color Z_MINUS = new Color(0xD89A6E);

    // Corner buttons (TL=X, TR=Y, BL=home all, BR=Z).
    private static final Color CORNER_X = new Color(0xF0D878);
    private static final Color CORNER_Y = new Color(0x8FB8DC);
    private static final Color CORNER_HOME = new Color(0xC9C9C9);
    private static final Color CORNER_Z = new Color(0x9FCB7E);

    private static final Color CHIP_BG = new Color(0xFFFFFF);
    private static final Color CHIP_TX = new Color(0x3A3A3A);
    private static final Color DISABLED = new Color(0xBDBDBD);

    private final transient RadialJogListener listener;

    private boolean pressed = false;
    private Point mouse = null;
    private Target target = Target.NONE;

    // Cached geometry.
    private int cx, cy, radius, hubR;
    private double ringStep;
    private int zx, zTop, zBottom;
    private double zCellH;
    private int cornerSize;

    private enum Kind {NONE, STOP, XY, Z, HOME}

    private static final class Target {
        static final Target NONE = new Target(Kind.NONE, null, 0, 0, (char) 0);
        final Kind kind;
        final RadialJogDirection dir;
        final int zSign;
        final double distance;
        final char homeAxis; // 0 = home all

        Target(Kind kind, RadialJogDirection dir, int zSign, double distance, char homeAxis) {
            this.kind = kind;
            this.dir = dir;
            this.zSign = zSign;
            this.distance = distance;
            this.homeAxis = homeAxis;
        }
    }

    public RadialJogControl(RadialJogListener listener) {
        this.listener = listener;
        setPreferredSize(new Dimension(340, 320));
        setMinimumSize(new Dimension(240, 240));
        setOpaque(false);
        MouseHandler handler = new MouseHandler();
        addMouseListener(handler);
        addMouseMotionListener(handler);
    }

    private void computeGeometry() {
        int w = getWidth();
        int h = getHeight();
        int areaW = w - Z_STRIP_WIDTH - Z_STRIP_GAP - MARGIN * 2;
        int areaH = h - MARGIN * 2;
        // Reserve a small outer band for the direction arrows and labels.
        int rimMargin = 12;
        radius = Math.max(44, Math.min(areaW, areaH) / 2 - rimMargin);
        cx = MARGIN + rimMargin + radius;
        if (cx + radius + rimMargin > w - Z_STRIP_WIDTH - Z_STRIP_GAP) {
            radius = Math.max(44, (w - Z_STRIP_WIDTH - Z_STRIP_GAP - MARGIN * 2) / 2 - rimMargin);
            cx = MARGIN + rimMargin + radius;
        }
        cy = MARGIN + areaH / 2;
        hubR = (int) Math.max(16, radius * 0.17);
        ringStep = (radius - hubR) / (double) DISTANCES.length;
        cornerSize = (int) Math.max(30, Math.min(radius * 0.52, 62));

        zx = w - Z_STRIP_WIDTH - MARGIN;
        zTop = MARGIN;
        zBottom = h - MARGIN;
        zCellH = ((zBottom - zTop) - Z_CENTER_GAP) / (double) (DISTANCES.length * 2);
    }

    // Corner bounds: 0=TL(X) 1=TR(Y) 2=BL(home) 3=BR(Z).
    private Rectangle cornerRect(int index) {
        int s = cornerSize;
        switch (index) {
            case 0:
                return new Rectangle(cx - radius, cy - radius, s, s);
            case 1:
                return new Rectangle(cx + radius - s, cy - radius, s, s);
            case 2:
                return new Rectangle(cx - radius, cy + radius - s, s, s);
            default:
                return new Rectangle(cx + radius - s, cy + radius - s, s, s);
        }
    }

    private Target hitTest(Point p) {
        if (p == null) {
            return Target.NONE;
        }
        // Corner home buttons (in the square corners, outside the disc).
        char[] axes = {'X', 'Y', (char) 0, 'Z'};
        double ddx0 = p.x - cx;
        double ddy0 = p.y - cy;
        if (Math.hypot(ddx0, ddy0) > radius - 2) {
            for (int i = 0; i < 4; i++) {
                if (cornerRect(i).contains(p)) {
                    return new Target(Kind.HOME, null, 0, 0, axes[i]);
                }
            }
        }

        // Z strip.
        if (p.x >= zx && p.x <= zx + Z_STRIP_WIDTH && p.y >= zTop && p.y <= zBottom) {
            double centerY = (zTop + zBottom) / 2.0;
            if (Math.abs(p.y - centerY) <= Z_CENTER_GAP / 2.0) {
                return Target.NONE;
            }
            if (p.y < centerY) {
                int idx = (int) ((centerY - Z_CENTER_GAP / 2.0 - p.y) / zCellH);
                idx = clamp(idx, 0, DISTANCES.length - 1);
                return new Target(Kind.Z, null, 1, DISTANCES[idx], (char) 0);
            } else {
                int idx = (int) ((p.y - centerY - Z_CENTER_GAP / 2.0) / zCellH);
                idx = clamp(idx, 0, DISTANCES.length - 1);
                return new Target(Kind.Z, null, -1, DISTANCES[idx], (char) 0);
            }
        }

        // Bullseye.
        double dist = Math.hypot(ddx0, ddy0);
        if (dist <= hubR) {
            return new Target(Kind.STOP, null, 0, 0, (char) 0);
        }
        if (dist > radius) {
            return Target.NONE;
        }
        int band = clamp((int) ((dist - hubR) / ringStep), 0, DISTANCES.length - 1);
        double angle = Math.toDegrees(Math.atan2(-ddy0, ddx0));
        return new Target(Kind.XY, RadialJogDirection.fromAngle(angle), 0, DISTANCES[band], (char) 0);
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        computeGeometry();
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        boolean on = isEnabled();
        paintCorners(g2, on);
        paintBullseye(g2, on);
        paintZStrip(g2, on);
        g2.dispose();
    }

    private void paintBullseye(Graphics2D g2, boolean on) {
        // Glossy dome base.
        if (on) {
            Point2D c = new Point2D.Float(cx, cy);
            Point2D focus = new Point2D.Float(cx - radius * 0.28f, cy - radius * 0.28f);
            float[] fr = {0f, 0.62f, 1f};
            Color[] cols = {DOME_LIGHT, DOME_MID, DOME_EDGE};
            g2.setPaint(new RadialGradientPaint(c, radius, focus, fr, cols,
                    MultipleGradientPaint.CycleMethod.NO_CYCLE));
        } else {
            g2.setPaint(DISABLED);
        }
        fillCircle(g2, cx, cy, radius);

        // Embossed ring grooves.
        for (int i = 1; i < DISTANCES.length; i++) {
            double r = hubR + i * ringStep;
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(on ? GROOVE : DISABLED.darker());
            drawCircle(g2, cx, cy, r);
            if (on) {
                g2.setColor(GROOVE_HI);
                drawCircle(g2, cx, cy, r - 1);
            }
        }
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(on ? DOME_EDGE.darker() : DISABLED.darker());
        drawCircle(g2, cx, cy, radius);

        // Highlighted wedge.
        if (on && target.kind == Kind.XY) {
            int band = distanceToBand(target.distance);
            double rIn = hubR + band * ringStep;
            double rOut = hubR + (band + 1) * ringStep;
            g2.setColor(HILIGHT);
            g2.fill(buildWedge(target.dir.getCenterDegrees(), rIn, rOut));
            g2.setColor(HILIGHT_LINE);
            g2.setStroke(new BasicStroke(1.5f));
            g2.draw(buildWedge(target.dir.getCenterDegrees(), rIn, rOut));
        }

        // Spokes + red diagonal axis cross.
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(on ? SPOKE : DISABLED.darker());
        g2.draw(new Line2D.Double(cx, cy - radius, cx, cy + radius));
        g2.draw(new Line2D.Double(cx - radius, cy, cx + radius, cy));
        g2.setStroke(new BasicStroke(2.5f));
        g2.setColor(on ? AXIS_LINE : DISABLED.darker());
        double d = radius * Math.cos(Math.toRadians(45));
        g2.draw(new Line2D.Double(cx - d, cy - d, cx + d, cy + d));
        g2.draw(new Line2D.Double(cx + d, cy - d, cx - d, cy + d));

        // Drag line.
        if (on && pressed && mouse != null && target.kind == Kind.XY) {
            g2.setColor(HILIGHT_LINE);
            g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[]{5f, 4f}, 0f));
            g2.draw(new Line2D.Double(cx, cy, mouse.x, mouse.y));
        }

        // Ring distance chips along a clear diagonal ray (between the top
        // spoke and the upper-left red diagonal) so they miss the arrows.
        g2.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        double chipAngle = Math.toRadians(112.5);
        for (int i = 0; i < DISTANCES.length; i++) {
            double rm = hubR + (i + 0.5) * ringStep;
            int chipX = (int) (cx + rm * Math.cos(chipAngle));
            int chipY = (int) (cy - rm * Math.sin(chipAngle));
            drawChip(g2, chipX, chipY, formatDistance(DISTANCES[i]), on);
        }

        // Cyan chevron arrows on the outer ring.
        double arrowR = radius - ringStep * 0.5;
        paintArrow(g2, cx, cy - arrowR, 0, on);
        paintArrow(g2, cx, cy + arrowR, 180, on);
        paintArrow(g2, cx - arrowR, cy, 270, on);
        paintArrow(g2, cx + arrowR, cy, 90, on);
        g2.setFont(getFont().deriveFont(Font.BOLD, 12f));
        g2.setColor(on ? ARROW_EDGE : DISABLED.darker());
        drawCenteredString(g2, "Y+", cx, cy - radius - 14);
        drawCenteredString(g2, "Y-", cx, cy + radius + 1);
        drawCenteredString(g2, "X-", cx - radius + 13, cy - 7);
        drawCenteredString(g2, "X+", cx + radius - 13, cy - 7);

        // Hub + readout.
        g2.setColor(on ? HUB : DISABLED.darker());
        fillCircle(g2, cx, cy, hubR);
        g2.setColor(on ? HUB_EDGE : DISABLED.darker().darker());
        g2.setStroke(new BasicStroke(1.5f));
        drawCircle(g2, cx, cy, hubR);
        g2.setColor(Color.WHITE);
        g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
        String readout = readoutText(on);
        if (readout != null) {
            drawCenteredString(g2, readout, cx, cy - 6);
        }
    }

    private void paintCorners(Graphics2D g2, boolean on) {
        Color[] base = {CORNER_X, CORNER_Y, CORNER_HOME, CORNER_Z};
        String[] label = {"X", "Y", "", "Z"};
        for (int i = 0; i < 4; i++) {
            Rectangle r = cornerRect(i);
            boolean hot = on && target.kind == Kind.HOME
                    && target.homeAxis == new char[]{'X', 'Y', 0, 'Z'}[i];
            Path2D shape = beveledCorner(r, i);
            g2.setColor(on ? (hot ? HILIGHT : base[i]) : DISABLED);
            g2.fill(shape);
            g2.setColor(on ? (hot ? HILIGHT_LINE : base[i].darker()) : DISABLED.darker());
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(shape);

            // Home glyph + axis letter.
            int gx = r.x + r.width / 2;
            int gy = r.y + r.height / 2 - 4;
            g2.setColor(new Color(0x3A3A3A));
            drawHouse(g2, gx, gy, 9);
            if (!label[i].isEmpty()) {
                g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
                drawCenteredString(g2, label[i], gx, gy + 8);
            }
        }
    }

    private Path2D beveledCorner(Rectangle r, int index) {
        int cut = Math.max(10, r.width / 3);
        Path2D p = new Path2D.Double();
        switch (index) {
            case 0: // TL, bevel bottom-right
                p.moveTo(r.x, r.y);
                p.lineTo(r.x + r.width, r.y);
                p.lineTo(r.x + r.width, r.y + r.height - cut);
                p.lineTo(r.x + r.width - cut, r.y + r.height);
                p.lineTo(r.x, r.y + r.height);
                break;
            case 1: // TR, bevel bottom-left
                p.moveTo(r.x, r.y);
                p.lineTo(r.x + r.width, r.y);
                p.lineTo(r.x + r.width, r.y + r.height);
                p.lineTo(r.x + cut, r.y + r.height);
                p.lineTo(r.x, r.y + r.height - cut);
                break;
            case 2: // BL, bevel top-right
                p.moveTo(r.x, r.y);
                p.lineTo(r.x + r.width - cut, r.y);
                p.lineTo(r.x + r.width, r.y + cut);
                p.lineTo(r.x + r.width, r.y + r.height);
                p.lineTo(r.x, r.y + r.height);
                break;
            default: // BR, bevel top-left
                p.moveTo(r.x + cut, r.y);
                p.lineTo(r.x + r.width, r.y);
                p.lineTo(r.x + r.width, r.y + r.height);
                p.lineTo(r.x, r.y + r.height);
                p.lineTo(r.x, r.y + cut);
                break;
        }
        p.closePath();
        return p;
    }

    private void drawHouse(Graphics2D g2, int cx, int cy, int s) {
        Path2D roof = new Path2D.Double();
        roof.moveTo(cx - s, cy);
        roof.lineTo(cx, cy - s);
        roof.lineTo(cx + s, cy);
        roof.closePath();
        g2.fill(roof);
        int bw = (int) (s * 1.1);
        g2.fillRect(cx - bw / 2, cy, bw, s);
    }

    private void paintZStrip(Graphics2D g2, boolean on) {
        double centerY = (zTop + zBottom) / 2.0;
        g2.setFont(getFont().deriveFont(Font.BOLD, 11f));
        g2.setColor(on ? new Color(0x2F5A14) : DISABLED.darker());
        drawCenteredString(g2, "Z+", zx + Z_STRIP_WIDTH / 2, zTop + 2);
        for (int i = 0; i < DISTANCES.length; i++) {
            double dist = DISTANCES[DISTANCES.length - 1 - i];
            int yTop = (int) (centerY - Z_CENTER_GAP / 2.0 - (DISTANCES.length - i) * zCellH);
            boolean hot = on && target.kind == Kind.Z && target.zSign == 1 && target.distance == dist;
            paintZCell(g2, yTop, dist, on ? Z_PLUS : DISABLED, hot, new Color(0x2F5A14));
        }
        for (int i = 0; i < DISTANCES.length; i++) {
            double dist = DISTANCES[i];
            int yTop = (int) (centerY + Z_CENTER_GAP / 2.0 + i * zCellH);
            boolean hot = on && target.kind == Kind.Z && target.zSign == -1 && target.distance == dist;
            paintZCell(g2, yTop, dist, on ? Z_MINUS : DISABLED, hot, new Color(0x7A2F14));
        }
        g2.setColor(on ? new Color(0x7A2F14) : DISABLED.darker());
        drawCenteredString(g2, "Z-", zx + Z_STRIP_WIDTH / 2, zBottom - 12);
    }

    private void paintZCell(Graphics2D g2, int yTop, double dist, Color base, boolean hot, Color text) {
        int h = (int) Math.ceil(zCellH) - 2;
        g2.setColor(hot ? HILIGHT : base);
        g2.fillRoundRect(zx, yTop, Z_STRIP_WIDTH, h, 6, 6);
        g2.setColor(hot ? HILIGHT_LINE : base.darker());
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(zx, yTop, Z_STRIP_WIDTH, h, 6, 6);
        g2.setColor(text);
        g2.setFont(getFont().deriveFont(Font.PLAIN, 12f));
        drawCenteredString(g2, formatDistance(dist), zx + Z_STRIP_WIDTH / 2, yTop + h / 2 - 6);
    }

    private void paintArrow(Graphics2D g2, double tipX, double tipY, int rotationDegrees, boolean on) {
        double size = Math.max(11, ringStep * 0.42);
        Path2D p = new Path2D.Double();
        p.moveTo(0, -size);
        p.lineTo(-size * 0.8, size * 0.5);
        p.lineTo(0, size * 0.18);
        p.lineTo(size * 0.8, size * 0.5);
        p.closePath();
        Graphics2D gg = (Graphics2D) g2.create();
        gg.translate(tipX, tipY);
        gg.rotate(Math.toRadians(rotationDegrees));
        gg.setColor(on ? ARROW : DISABLED.darker());
        gg.fill(p);
        gg.setColor(on ? ARROW_EDGE : DISABLED.darker().darker());
        gg.setStroke(new BasicStroke(1f));
        gg.draw(p);
        gg.dispose();
    }

    private String readoutText(boolean on) {
        if (!on) {
            return "--";
        }
        switch (target.kind) {
            case STOP:
                return "STOP";
            case XY:
                return axisLabel(target.dir) + " " + formatDistance(target.distance);
            case Z:
                return "Z" + (target.zSign > 0 ? "+" : "-") + " " + formatDistance(target.distance);
            case HOME:
                return target.homeAxis == 0 ? "HOME" : ("⌂" + target.homeAxis);
            default:
                return null;
        }
    }

    private static String axisLabel(RadialJogDirection dir) {
        StringBuilder sb = new StringBuilder();
        if (dir.getDx() > 0) {
            sb.append("X+");
        } else if (dir.getDx() < 0) {
            sb.append("X-");
        }
        if (dir.getDy() > 0) {
            sb.append("Y+");
        } else if (dir.getDy() < 0) {
            sb.append("Y-");
        }
        return sb.toString();
    }

    private int distanceToBand(double dist) {
        for (int i = 0; i < DISTANCES.length; i++) {
            if (DISTANCES[i] == dist) {
                return i;
            }
        }
        return 0;
    }

    private Path2D buildWedge(int centerDegrees, double rIn, double rOut) {
        double a0 = Math.toRadians(centerDegrees - 22.5);
        double a1 = Math.toRadians(centerDegrees + 22.5);
        int steps = 10;
        Path2D path = new Path2D.Double();
        for (int i = 0; i <= steps; i++) {
            double a = a0 + (a1 - a0) * i / steps;
            if (i == 0) {
                path.moveTo(cx + rOut * Math.cos(a), cy - rOut * Math.sin(a));
            } else {
                path.lineTo(cx + rOut * Math.cos(a), cy - rOut * Math.sin(a));
            }
        }
        for (int i = steps; i >= 0; i--) {
            double a = a0 + (a1 - a0) * i / steps;
            path.lineTo(cx + rIn * Math.cos(a), cy - rIn * Math.sin(a));
        }
        path.closePath();
        return path;
    }

    private void drawChip(Graphics2D g2, int centerX, int centerY, String text, boolean on) {
        int w = g2.getFontMetrics().stringWidth(text) + 10;
        int h = 16;
        g2.setColor(on ? CHIP_BG : DISABLED.brighter());
        g2.fillRoundRect(centerX - w / 2, centerY - h / 2, w, h, 5, 5);
        g2.setColor(GROOVE);
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(centerX - w / 2, centerY - h / 2, w, h, 5, 5);
        g2.setColor(on ? CHIP_TX : DISABLED.darker());
        drawCenteredString(g2, text, centerX, centerY - h / 2 + 2);
    }

    private void drawCenteredString(Graphics2D g2, String text, int centerX, int top) {
        int w = g2.getFontMetrics().stringWidth(text);
        int ascent = g2.getFontMetrics().getAscent();
        g2.drawString(text, centerX - w / 2, top + ascent);
    }

    private static void fillCircle(Graphics2D g2, int cx, int cy, double r) {
        g2.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    private static void drawCircle(Graphics2D g2, int cx, int cy, double r) {
        g2.draw(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    static String formatDistance(double dd) {
        if (dd == Math.rint(dd)) {
            return Integer.toString((int) dd);
        }
        return Double.toString(dd);
    }

    private class MouseHandler extends MouseInputAdapter {
        @Override
        public void mouseMoved(java.awt.event.MouseEvent e) {
            if (isEnabled()) {
                mouse = e.getPoint();
                updateTarget();
            }
        }

        @Override
        public void mousePressed(java.awt.event.MouseEvent e) {
            if (isEnabled()) {
                pressed = true;
                mouse = e.getPoint();
                updateTarget();
            }
        }

        @Override
        public void mouseDragged(java.awt.event.MouseEvent e) {
            if (isEnabled()) {
                mouse = e.getPoint();
                updateTarget();
            }
        }

        @Override
        public void mouseReleased(java.awt.event.MouseEvent e) {
            if (!isEnabled()) {
                return;
            }
            pressed = false;
            mouse = e.getPoint();
            Target t = hitTest(mouse);
            fire(t);
            target = t;
            repaint();
        }

        @Override
        public void mouseExited(java.awt.event.MouseEvent e) {
            if (!pressed) {
                mouse = null;
                target = Target.NONE;
                repaint();
            }
        }

        private void updateTarget() {
            target = hitTest(mouse);
            repaint();
        }
    }

    private void fire(Target t) {
        switch (t.kind) {
            case STOP:
                listener.onStop();
                break;
            case XY:
                listener.onJog(t.dir.getDx(), t.dir.getDy(), 0, t.distance);
                break;
            case Z:
                listener.onJog(0, 0, t.zSign, t.distance);
                break;
            case HOME:
                if (t.homeAxis == 0) {
                    listener.onHomeAll();
                } else {
                    listener.onReturnToZero(t.homeAxis);
                }
                break;
            default:
                break;
        }
    }
}
