import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;

//mouse/keyboard actions; edit location/size
public class DesignCanvas extends JPanel implements Scrollable {
    DesignProject project;
    DesignPage page;
    final ArrayList<DesignItem> selection = new ArrayList<>();
    Runnable selectionChanged = () -> { }, geometryChanged = () -> { }, designChanged = () -> { }, zoomChanged = () -> { };
    double zoom;
    private double scale = 1;
    private int originX, originY;
    private double panX, panY;
    private boolean panMode, panning;
    private Point panStart;
    private double panStartX, panStartY;
    private int reportedPercent = -1;
    private Point start;
    private final ArrayList<Rectangle> originals = new ArrayList<>();
    private boolean resizing, moved, dragging;
    public DesignCanvas() {
        setFocusable(true); setOpaque(true); setBackground(UiStyle.BACK);
        MouseAdapter mouse = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e) || page == null) return;
                requestFocusInWindow();
                metrics();

                if (panMode) {
                    panning = true;
                    dragging = false;
                    moved = false;

                    panStart = e.getPoint();
                    panStartX = panX;
                    panStartY = panY;

                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                    return;
                }

                start = toPage(e.getPoint());
                //requestFocusInWindow(); metrics(); start = toPage(e.getPoint());
                DesignItem selected = selected(); resizing = selected != null && !selected.locked && selection.size() == 1 && onHandle(start);
                if (!resizing) {
                    DesignItem hit = page.findAt(start.x, start.y);
                    if (e.isControlDown() || e.isShiftDown()) {
                        if (hit != null) { if (selection.contains(hit)) selection.remove(hit); else selection.add(hit); }
                    } else if (hit == null) selection.clear();
                    else if (!selection.contains(hit)) select(hit, true);
                }
                originals.clear(); for (DesignItem item : selection) originals.add(new Rectangle(item.x, item.y, item.width, item.height));
                dragging = !selection.isEmpty() && selection.stream().noneMatch(item -> item.locked); moved = false;
                selectionChanged.run(); repaint();
            }
            public void mouseDragged(MouseEvent e) {
                if (panning) {
                    panX = panStartX + e.getX() - panStart.x;
                    panY = panStartY + e.getY() - panStart.y;

                    repaint();
                    return;
                }
                if (!dragging || selection.isEmpty()) return;
                Point point = toPage(e.getPoint()); int dx = point.x - start.x, dy = point.y - start.y;
                if (resizing) {
                    DesignItem item = selected(); Rectangle old = originals.get(0);
                    item.width = Math.max(24, Math.min(page.width - item.x, old.width + dx));
                    item.height = Math.max(8, Math.min(page.height - item.y, old.height + dy));
                } else {
                    for (Rectangle old : originals) { dx = Math.max(-old.x, Math.min(page.width - old.x - old.width, dx)); dy = Math.max(-old.y, Math.min(page.height - old.y - old.height, dy)); }
                    for (int i = 0; i < selection.size(); i++) { DesignItem item = selection.get(i); item.x = originals.get(i).x + dx; item.y = originals.get(i).y + dy; }
                }
                moved = true; geometryChanged.run(); repaint();
            }
            public void mouseReleased(MouseEvent e) {
                if (panning) {
                    panning = false;
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    return;
                }

                dragging = false;

                if (moved) designChanged.run();

                moved = false;
            }
            //public void mouseReleased(MouseEvent e) { dragging = false; if (moved) designChanged.run(); moved = false; }
            public void mouseMoved(MouseEvent e) {
                if (panMode) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    return;
                }
                if (page == null) return; metrics(); Point point = toPage(e.getPoint());
                setCursor(Cursor.getPredefinedCursor(onHandle(point) ? Cursor.SE_RESIZE_CURSOR : page.findAt(point.x, point.y) != null ? Cursor.MOVE_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        addMouseListener(mouse); addMouseMotionListener(mouse);
        bind("LEFT", () -> nudge(-1, 0)); bind("RIGHT", () -> nudge(1, 0)); bind("UP", () -> nudge(0, -1)); bind("DOWN", () -> nudge(0, 1));
        bind("shift LEFT", () -> nudge(-10, 0)); bind("shift RIGHT", () -> nudge(10, 0)); bind("shift UP", () -> nudge(0, -10)); bind("shift DOWN", () -> nudge(0, 10));
        addComponentListener(new ComponentAdapter() { public void componentResized(ComponentEvent e) { metrics(); zoomChanged.run(); } });
    }
    public DesignItem selected() { return selection.isEmpty() ? null : selection.get(selection.size() - 1); }
    public void select(DesignItem item, boolean includeGroup) {
        selection.clear(); if (item == null) return;
        if (includeGroup && !item.group.isEmpty()) for (DesignItem part : page.items) { if (part.group.equals(item.group) && part.visible && !part.locked) selection.add(part); }
        if (!selection.contains(item)) selection.add(item);
    }
    public void showPage(DesignProject project, DesignPage page) {
        this.project = project;
        this.page = page;

        selection.clear();
        dragging = false;
        panning = false;

        panX = 0;
        panY = 0;

        revalidate();
        repaint();
        selectionChanged.run();
    }

    public void setPanMode(boolean enabled) {
        panMode = enabled;
        panning = false;
        dragging = false;
        moved = false;

        setCursor(Cursor.getPredefinedCursor(
                enabled ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR
        ));

        requestFocusInWindow();
    }

    public boolean isPanMode() {
        return panMode;
    }

    public void panBy(double dx, double dy) {
        panX += dx;
        panY += dy;
        repaint();
    }
    public void bind(String key, Runnable action) {
        getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key);
        getActionMap().put(key, new AbstractAction() { public void actionPerformed(ActionEvent e) { action.run(); } });
    }
    public void nudge(int dx, int dy) {
        if (selection.isEmpty() || selection.stream().anyMatch(item -> item.locked)) return;
        for (DesignItem item : selection) { dx = Math.max(-item.x, Math.min(page.width - item.width - item.x, dx)); dy = Math.max(-item.y, Math.min(page.height - item.height - item.y, dy)); }
        for (DesignItem item : selection) { item.x += dx; item.y += dy; }
        selectionChanged.run(); designChanged.run(); repaint();
    }
    public int percent() { metrics(); return (int) Math.round(scale * 100); }
    public void setZoom(double requested) {
        metrics();
        double previousScale = scale;

        zoom = requested == 0
                ? 0
                : Math.max(.20, Math.min(2.5, requested));

        metrics();

        if (requested == 0) {
            panX = 0;
            panY = 0;
        } else {
            double ratio = scale / previousScale;
            panX *= ratio;
            panY *= ratio;
        }

        metrics();
        repaint();
        zoomChanged.run();
    }
    private void metrics() {
        if (page == null) return;
        Dimension extent = getParent() instanceof JViewport ? ((JViewport) getParent()).getExtentSize() : getSize();
        scale = zoom == 0 ? Math.max(.10, Math.min(1, Math.min((extent.width - 130.0) / page.width, (extent.height - 170.0) / page.height))) : zoom;
        originX = (int) Math.round(
                (getWidth() - page.width * scale) / 2 + panX
        );

        originY = (int) Math.round(
                (getHeight() - page.height * scale) / 2 + panY
        );
        int percent = (int) Math.round(scale * 100);
        if (percent != reportedPercent) { reportedPercent = percent; zoomChanged.run(); }
    }
    private Point toPage(Point point) { return new Point((int) Math.round((point.x - originX) / scale), (int) Math.round((point.y - originY) / scale)); }
    private boolean onHandle(Point point) { DesignItem item = selected(); return item != null && !item.locked && selection.size() == 1 && Math.abs(point.x - item.x - item.width) < 9 / scale && Math.abs(point.y - item.y - item.height) < 9 / scale; }
    public void paintComponent(Graphics graphics) {
        super.paintComponent(graphics); Graphics2D g = (Graphics2D) graphics.create(); UiStyle.smooth(g);
        g.setColor(new Color(0xD9E2EF)); for (int x = 12; x < getWidth(); x += 22) for (int y = 12; y < getHeight(); y += 22) g.fillRect(x, y, 1, 1);
        if (page == null) { g.dispose(); return; } metrics();
        g.translate(originX, originY); g.scale(scale, scale);
        g.setColor(new Color(0, 30, 80, 16)); g.fillRoundRect(5, 8, page.width, page.height, 30, 30);
        paintPage(g, project, page);
        g.setColor(new Color(0xCFD9E8)); g.setStroke(new BasicStroke((float) (1 / scale))); g.drawRoundRect(0, 0, page.width, page.height, 28, 28);
        for (DesignItem item : selection) {
            g.setColor(UiStyle.BLUE); g.setStroke(new BasicStroke((float) (1.8 / scale))); g.drawRect(item.x, item.y, item.width, item.height);
        }
        DesignItem item = selected();
        if (item != null && !item.locked && selection.size() == 1) {
            int size = Math.max(5, (int) (8 / scale)); g.setColor(Color.WHITE); g.fillRoundRect(item.x + item.width - size / 2, item.y + item.height - size / 2, size, size, 2, 2);
            g.setColor(UiStyle.BLUE); g.drawRoundRect(item.x + item.width - size / 2, item.y + item.height - size / 2, size, size, 2, 2);
        }
        g.dispose();
    }
    public static void paintPage(Graphics2D original, DesignProject project, DesignPage page) {
        Graphics2D g = (Graphics2D) original.create(); UiStyle.smooth(g);
        g.clip(new RoundRectangle2D.Double(0, 0, page.width, page.height, 28, 28));
        g.setColor(page.background >= 0 ? new Color(page.background) : project.surface()); g.fillRect(0, 0, page.width, page.height);
        for (DesignItem item : page.items) if (item.visible) {
            Graphics2D part = (Graphics2D) g.create(item.x, item.y, item.width, item.height); PartRenderer.paint(part, item, project); part.dispose();
        }
        if (page.items.isEmpty()) { g.setColor(UiStyle.MUTED); g.setFont(UiStyle.FONT.deriveFont(18f)); PartRenderer.center(g, "Click a part to add it", 0, page.height / 2 - 20, page.width, 40); }
        g.dispose();
    }
    public BufferedImage exportImage() { BufferedImage image = new BufferedImage(page.width, page.height, BufferedImage.TYPE_INT_ARGB); Graphics2D g = image.createGraphics(); paintPage(g, project, page); g.dispose(); return image; }
    public Dimension getPreferredSize() {
        return new Dimension(450, 500);
    }
    public Dimension getPreferredScrollableViewportSize() { return new Dimension(600, 700); }
    public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 28; }
    public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return o == SwingConstants.HORIZONTAL ? r.width - 50 : r.height - 50; }
    public boolean getScrollableTracksViewportWidth() {
        return true;
    }
    public boolean getScrollableTracksViewportHeight() {
        return true;
    }
}
