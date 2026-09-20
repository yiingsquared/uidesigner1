import javax.swing.*;
import javax.swing.plaf.basic.BasicTextFieldUI;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;

public class GuiRuntime {
    public static class Part {
        public String id = "", type = "Button", text = "", detail = "", target = "", skin = "", icon = "";
        public String[] options = {}, targets = {};
        public int x, y, width = 168, height = 48, radius = 16, value;
        public boolean checked, visible = true, bold = true;
        public Color background = Color.WHITE, foreground = Color.BLACK, accent = new Color(0x0B57D0), soft = new Color(0xE8F0FE), line = new Color(0xDFE5EE);
        public Font font = new Font("SansSerif", Font.PLAIN, 14);
    }
    public static class Page {
        public final String id, name;
        public final int width, height;
        public final Color background;
        public final List<Part> parts = new ArrayList<>();
        public Page(String id, String name, int width, int height, Color background) {
            this.id = id; this.name = name; this.width = width; this.height = height; this.background = background;
        }
        public String toString() { return name; }
    }
    public record TextRun(String text, int x, int baseline, Font font, Color colour, int width, int height, int ascent) { }
    private static final LinkedHashMap<String, BufferedImage> IMAGES = new LinkedHashMap<>(16, .75f, true);
    private static long imageBytes;
    public static synchronized BufferedImage image(String data) {
        if (data == null || data.isEmpty()) return null;
        BufferedImage found = IMAGES.get(data);
        if (found != null) return found;
        try {
            found = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(data)));
            if (found == null) return null;
            long bytes = 4L * found.getWidth() * found.getHeight();
            while (!IMAGES.isEmpty() && imageBytes + bytes > 48L * 1024 * 1024) {
                var first = IMAGES.entrySet().iterator(); var old = first.next();
                imageBytes -= 4L * old.getValue().getWidth() * old.getValue().getHeight(); first.remove();
            }
            IMAGES.put(data, found); imageBytes += bytes; return found;
        } catch (IOException | IllegalArgumentException error) { return null; }
    }
    public static Font makeFont(String name, int style, int size, String data) {
        if (!data.isEmpty()) try {
            return Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(Base64.getDecoder().decode(data))).deriveFont(style, (float) size);
        } catch (Exception ignored) { }
        return new Font(name, style, size);
    }
    public static void smooth(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    public static String shorten(FontMetrics fm, String text, int width) {
        if (width <= 0) return "";
        String value = text.replace('\n', ' ');
        if (fm.stringWidth(value) <= width) return value;
        int end = value.length();
        while (end > 0 && fm.stringWidth(value.substring(0, end) + "...") > width) end--;
        return end == 0 ? "" : value.substring(0, end) + "...";
    }
    private static void centre(List<TextRun> rows, Graphics2D g, String text, int x, int y, int w, int h, Font font, Color colour) {
        FontMetrics fm = g.getFontMetrics(font); String label = shorten(fm, text, w);
        rows.add(new TextRun(label, x + Math.max(0, (w - fm.stringWidth(label)) / 2), y + (h - fm.getHeight()) / 2 + fm.getAscent(), font, colour, fm.stringWidth(label), fm.getHeight(), fm.getAscent()));
    }
    private static void left(List<TextRun> rows, Graphics2D g, String text, int x, int middle, int w, Font font, Color colour) {
        FontMetrics fm = g.getFontMetrics(font); String label = shorten(fm, text, w);
        rows.add(new TextRun(label, x, middle + (fm.getAscent() - fm.getDescent()) / 2, font, colour, fm.stringWidth(label), fm.getHeight(), fm.getAscent()));
    }
    private static void wrap(List<TextRun> rows, Graphics2D g, String text, int x, int y, int w, int h, Font font, Color colour) {
        FontMetrics fm = g.getFontMetrics(font); int baseline = y + fm.getAscent(); String line = "";
        for (String word : text.replace("\n", " \n ").split(" ")) {
            if (word.equals("\n") || fm.stringWidth(line + word) > w) {
                if (baseline > y + h) return;
                String label = shorten(fm, line.strip(), w);
                rows.add(new TextRun(label, x, baseline, font, colour, fm.stringWidth(label), fm.getHeight(), fm.getAscent()));
                baseline += fm.getHeight() + 3; line = ""; if (word.equals("\n")) continue;
            }
            line += word + " ";
        }
        if (baseline <= y + h) {
            String label = shorten(fm, line.strip(), w);
            rows.add(new TextRun(label, x, baseline, font, colour, fm.stringWidth(label), fm.getHeight(), fm.getAscent()));
        }
    }
    public static List<TextRun> textRuns(Graphics2D g, Part p) {
        List<TextRun> rows = new ArrayList<>(); int w = p.width, h = p.height;
        Font font = p.font, bold = font.deriveFont(p.bold ? Font.BOLD : Font.PLAIN);
        Color fg = p.foreground;
        switch (p.type) {
            case "Text" -> wrap(rows, g, p.text, 0, 0, w, h, p.font.getSize() >= 18 ? bold : font, fg);
            case "Button", "Chip", "Split Button" -> {
                int inset = p.skin.isEmpty() && !p.icon.isEmpty() ? 32 : 0;
                centre(rows, g, p.text, 8 + inset, 0, w - inset - (p.type.equals("Split Button") ? 48 : 16), h, bold, fg);
            }
            case "FAB", "Icon Button" -> { if (!p.skin.isEmpty() || p.icon.isEmpty()) centre(rows, g, p.text, 4, 0, w - 8, h, font, fg); }
            case "Card", "List Item", "Dialog" -> {
                int inset = p.type.equals("List Item") && p.skin.isEmpty() && !p.icon.isEmpty() ? 54 : 20;
                wrap(rows, g, p.text, inset, 16, w - inset - 20, Math.min(50, h - 20), bold, fg);
                int top = p.type.equals("List Item") ? 38 : 52;
                wrap(rows, g, p.detail, inset, top, w - inset - 20, h - top - (p.type.equals("Dialog") ? 60 : 10), font, fg);
                if (p.type.equals("Dialog")) for (int i = 0; i < Math.min(2, p.options.length); i++) centre(rows, g, p.options[i], 16 + i * (w - 32) / 2, h - 52, (w - 32) / 2, 40, font, fg);
            }
            case "Top App Bar" -> centre(rows, g, p.text, 18, 0, w - 36, h, font.deriveFont(Font.BOLD, Math.max(16, font.getSize())), fg);
            case "Navigation Rail", "Toolbar", "Navigation Bar", "Tabs" -> {
                int count = p.options.length;
                for (int i = 0; i < count; i++) {
                    Rectangle r = optionBounds(p, i);
                    centre(rows, g, p.options[i], r.x + 5, r.y, r.width - 10, r.height, font, p.skin.isEmpty() && i == p.value ? p.accent : fg);
                }
            }
            case "Dropdown" -> centre(rows, g, p.options.length == 0 ? "" : p.options[Math.floorMod(p.value, p.options.length)], 16, 0, w - 44, h, font, fg);
            case "Search Bar", "Text Field", "Date Picker", "Time Picker" -> centre(rows, g, p.text, 16, 0, w - 32, h, font, fg);
            case "Checkbox", "Radio Button" -> left(rows, g, p.text, 34, h / 2, w - 40, font, fg);
            case "Switch" -> left(rows, g, p.text, 62, h / 2, w - 68, font, fg);
            case "Snackbar" -> {
                left(rows, g, p.text, 16, h / 2, w - 112, font, fg);
                centre(rows, g, p.options.length == 0 ? "Dismiss" : p.options[0], w - 96, 0, 88, h, font, fg);
            }
            case "Image", "Camera", "Map" -> centre(rows, g, p.text, 12, h / 2 + 8, w - 24, 28, font, fg);
            case "Carousel" -> {
                for (int i = 0; i < 3; i++) { int cw = Math.max(24, (w - 32) / 3); centre(rows, g, "Image " + (i + 1), 8 + i * (cw + 8), 0, cw, h, font, fg); }
            }
        }
        return rows;
    }
    public static Rectangle optionBounds(Part p, int i) {
        int count = Math.max(1, p.options.length); boolean rail = p.type.equals("Navigation Rail");
        int w = rail ? p.width - 12 : (p.width - 12) / count, h = rail ? (p.height - 12) / count : p.height - 12;
        return new Rectangle(rail ? 6 : 6 + i * w, rail ? 6 + i * h : 6, w, h);
    }
    private static void icon(Graphics2D g, Part p, int x, int y, int size) {
        BufferedImage image = image(p.icon); if (image != null) g.drawImage(image, x, y, size, size, null);
    }
    private static void arrow(Graphics2D g, int x, int y) { g.drawLine(x - 4, y - 2, x, y + 2); g.drawLine(x, y + 2, x + 4, y - 2); }
    public static void paintPart(Graphics2D original, Part p, boolean showText) {
        Graphics2D g = (Graphics2D) original.create(); smooth(g); g.clipRect(0, 0, p.width, p.height);
        int w = p.width, h = p.height, arc = p.radius * 2; boolean skin = !p.skin.isEmpty();
        if (skin) { BufferedImage image = image(p.skin); if (image != null) g.drawImage(image, 0, 0, w, h, null); }
        else {
            boolean noBox = List.of("Text", "Divider", "Checkbox", "Radio Button", "Switch", "Slider", "Loading", "Progress").contains(p.type);
            if (!noBox) { g.setColor(p.background); g.fillRoundRect(0, 0, w, h, arc, arc); g.setColor(p.line); g.drawRoundRect(0, 0, w - 1, h - 1, arc, arc); }
            switch (p.type) {
                case "Button", "Chip", "Split Button" -> icon(g, p, 14, (h - 20) / 2, 20);
                case "FAB", "Icon Button" -> icon(g, p, (w - 24) / 2, (h - 24) / 2, 24);
                case "List Item" -> icon(g, p, 16, (h - 24) / 2, 24);
                case "Image", "Camera", "Map" -> { g.setColor(p.soft); g.fillRoundRect(1, 1, w - 2, h - 2, arc, arc); icon(g, p, (w - 32) / 2, Math.max(8, h / 2 - 32), 32); }
            }
        }
        g.setColor(p.foreground);
        switch (p.type) {
            case "Split Button" -> { if (!skin) g.drawLine(w - 42, 10, w - 42, h - 10); arrow(g, w - 23, h / 2); }
            case "Dropdown" -> arrow(g, w - 17, h / 2);
            case "Navigation Rail", "Toolbar", "Navigation Bar", "Tabs" -> {
                if (!skin && p.options.length > 0) { Rectangle r = optionBounds(p, Math.floorMod(p.value, p.options.length)); g.setColor(p.soft); g.fillRoundRect(r.x + 2, r.y + 2, Math.max(1, r.width - 4), Math.max(1, r.height - 4), Math.min(22, arc), Math.min(22, arc)); }
            }
            case "Checkbox", "Radio Button" -> {
                int y = (h - 20) / 2; g.setColor(p.checked ? p.accent : p.line);
                if (p.type.equals("Radio Button")) { g.drawOval(2, y, 20, 20); if (p.checked) g.fillOval(7, y + 5, 11, 11); }
                else if (p.checked) { g.fillRoundRect(2, y, 20, 20, 6, 6); g.setColor(Color.WHITE); g.setStroke(new BasicStroke(2)); g.drawLine(6, y + 10, 10, y + 14); g.drawLine(10, y + 14, 18, y + 6); }
                else g.drawRoundRect(2, y, 20, 20, 6, 6);
            }
            case "Switch" -> { g.setColor(p.checked ? p.accent : p.line); g.fillRoundRect(0, h / 2 - 14, 50, 28, 28, 28); g.setColor(Color.WHITE); g.fillOval(p.checked ? 26 : 4, h / 2 - 10, 20, 20); }
            case "Slider", "Progress" -> { int extent = (int) ((w - 16) * p.value / 100.0); g.setColor(p.soft); g.fillRoundRect(8, h / 2 - 3, w - 16, 6, 6, 6); g.setColor(p.accent); g.fillRoundRect(8, h / 2 - 3, extent, 6, 6, 6); if (p.type.equals("Slider")) g.fillRoundRect(4 + extent, h / 2 - 12, 8, 24, 8, 8); }
            case "Loading" -> { g.setStroke(new BasicStroke(4)); g.setColor(p.soft); g.drawOval(7, 7, w - 14, h - 14); g.setColor(p.accent); g.drawArc(7, 7, w - 14, h - 14, 90, -250); }
            case "Divider" -> { g.setColor(p.foreground); g.drawLine(0, h / 2, w, h / 2); }
            case "Carousel" -> { for (int i = 0; i < 3; i++) { int cw = Math.max(24, (w - 32) / 3); g.setColor(i == Math.floorMod(p.value, 3) ? p.soft : p.background); g.fillRoundRect(8 + i * (cw + 8), 8, cw, h - 16, 20, 20); } }
        }
        if (showText) for (TextRun row : textRuns(g, p)) { g.setFont(row.font); g.setColor(row.colour); g.drawString(row.text, row.x, row.baseline); }
        g.dispose();
    }
    public static class AppView extends JPanel {
        public final List<Page> pages;
        public Page current;
        private final java.util.Map<String, JPanel> boards = new LinkedHashMap<>();
        private JPanel activeBoard;
        public Consumer<Page> pageChanged = page -> { };
        public AppView(List<Page> pages, String first) {
            super(null); this.pages = pages; setBackground(new Color(0xF3F6FB));
            for (Page page : pages) {
                JPanel panel = new JPanel(null) {
                    public boolean isOptimizedDrawingEnabled() { return false; }
                    protected void paintComponent(Graphics graphics) {
                        Graphics2D g = (Graphics2D) graphics.create(); smooth(g); g.setColor(page.background);
                        int arc = Math.max(1, (int) Math.round(28.0 * getWidth() / page.width));
                        g.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc); g.dispose();
                    }
                    protected void paintChildren(Graphics graphics) {
                        Graphics2D g = (Graphics2D) graphics.create(); int arc = Math.max(1, (int) Math.round(28.0 * getWidth() / page.width));
                        g.clip(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), arc, arc)); super.paintChildren(g); g.dispose();
                    }
                };
                panel.setOpaque(false); panel.setBackground(page.background);
                for (Part part : page.parts) { PartView child = new PartView(this, part); panel.add(child); panel.setComponentZOrder(child, 0); }
                boards.put(page.id, panel);
            }
            if (!pages.isEmpty()) showPage(first);
        }
        public void showPage(String id) {
            Page page = pages.stream().filter(p -> p.id.equals(id)).findFirst().orElse(pages.isEmpty() ? null : pages.get(0));
            if (page == null) return;
            if (activeBoard != null) remove(activeBoard);
            current = page; activeBoard = boards.get(page.id); add(activeBoard); setPreferredSize(new Dimension(page.width, page.height));
            revalidate(); doLayout(); repaint(); pageChanged.accept(page);
        }
        public void go(String id) { if (pages.stream().anyMatch(page -> page.id.equals(id))) showPage(id); }
        public JPanel currentBoard() { return activeBoard; }
        public PartView getPart(String id) {
            for (JPanel board : boards.values()) {
                for (Component component : board.getComponents()) {
                    if (component instanceof PartView view
                            && view.part.id.equals(id)) {
                        return view;
                    }
                }
            }
            return null;
        }
        public void doLayout() {
            if (current == null || activeBoard == null) return;
            double scale = Math.max(.001, Math.min(getWidth() / (double) current.width, getHeight() / (double) current.height));
            int w = Math.max(1, (int) Math.round(current.width * scale)), h = Math.max(1, (int) Math.round(current.height * scale));
            activeBoard.setBounds((getWidth() - w) / 2, (getHeight() - h) / 2, w, h);
            for (Component child : activeBoard.getComponents()) if (child instanceof PartView view) {
                Part p = view.part;
                view.setBounds((int) Math.round(p.x * scale), (int) Math.round(p.y * scale), Math.max(1, (int) Math.round(p.width * scale)), Math.max(1, (int) Math.round(p.height * scale)));
                view.doLayout();
            }
        }
    }
    public static class PartView extends JComponent {
        public final Part part;
        public final AppView app;
        public JTextField input;
        public PartView(AppView app, Part part) {
            this.app = app; this.part = part; setLayout(null); setVisible(part.visible); setName(part.id);
            getAccessibleContextSafe();
            setFocusable(true); setCursor(Cursor.getPredefinedCursor(interactive(part.type) ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            if (part.type.equals("Text Field") || part.type.equals("Search Bar")) {
                input = new JTextField(); input.setUI(new BasicTextFieldUI()); input.setOpaque(false); input.setBorder(BorderFactory.createEmptyBorder()); input.setHorizontalAlignment(JTextField.CENTER);
                input.setForeground(part.foreground); input.setCaretColor(part.foreground); input.setFont(part.font); input.setToolTipText(part.text);
                input.getAccessibleContext().setAccessibleName(part.text); add(input);
                input.addFocusListener(new FocusAdapter() { public void focusGained(FocusEvent e) { repaint(); } public void focusLost(FocusEvent e) { repaint(); } });
                input.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                    public void insertUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                    public void removeUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                    public void changedUpdate(javax.swing.event.DocumentEvent e) { repaint(); }
                });
            }

            MouseAdapter mouse = new MouseAdapter() {
                private boolean armed;
                private Page pressedPage;

                @Override
                public void mousePressed(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e) || !isEnabled()) {
                        return;
                    }

                    armed = true;
                    pressedPage = app.current;

                    if (input != null) {
                        input.requestFocusInWindow();
                    } else {
                        requestFocusInWindow();
                    }

                    if (part.type.equals("Slider")) {
                        slide(e.getX());
                    }
                }

                @Override
                public void mouseDragged(MouseEvent e) {
                    if (armed && part.type.equals("Slider")) {
                        slide(e.getX());
                    }
                }

                @Override
                public void mouseReleased(MouseEvent e) {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;

                    boolean activateNow = armed
                            && isEnabled()
                            && part.visible
                            && app.current == pressedPage
                            && contains(e.getPoint());

                    armed = false;

                    if (activateNow && !part.type.equals("Slider")) {
                        activate(
                                logicalX(e.getX()),
                                logicalY(e.getY())
                        );
                    }
                }
            }; //fix preview state need to click button twice to function bug

            addMouseListener(mouse); addMouseMotionListener(mouse);
            bind("SPACE", this::keyboardActivate); bind("ENTER", this::keyboardActivate);
            bind("alt DOWN", this::popup); bind("DOWN", () -> adjust(1)); bind("RIGHT", () -> adjust(1)); bind("UP", () -> adjust(-1)); bind("LEFT", () -> adjust(-1));
        }
        private void getAccessibleContextSafe() { setToolTipText(part.type); }
        private void keyboardActivate() {
            if (part.type.equals("Navigation Rail") || part.type.equals("Toolbar")) {
                Rectangle r = optionBounds(part, Math.floorMod(part.value, Math.max(1, part.options.length))); activate(r.x + r.width / 2, r.y + r.height / 2);
            } else if (part.type.equals("Split Button")) popup();
            else activate(part.width / 2, part.height / 2);
        }
        private static boolean interactive(String type) { return List.of("Button", "FAB", "Split Button", "Icon Button", "Chip", "Dropdown", "Switch", "Checkbox", "Radio Button", "Slider", "Date Picker", "Time Picker", "Dialog", "Snackbar", "Navigation Rail", "Toolbar", "Carousel", "Text Field", "Search Bar").contains(type); }
        private void bind(String key, Runnable run) { getInputMap().put(KeyStroke.getKeyStroke(key), key); getActionMap().put(key, new AbstractAction() { public void actionPerformed(ActionEvent e) { run.run(); } }); }
        private int logicalX(int x) { return (int) Math.round(x * part.width / (double) Math.max(1, getWidth())); }
        private int logicalY(int y) { return (int) Math.round(y * part.height / (double) Math.max(1, getHeight())); }
        public void doLayout() {
            if (input != null) { double s = getWidth() / (double) part.width; int inset = Math.max(1, (int) Math.round(16 * s)); input.setBounds(inset, 0, Math.max(1, getWidth() - 2 * inset), getHeight()); input.setFont(part.font.deriveFont((float) Math.max(1, part.font.getSize2D() * Math.min(s, getHeight() / (double) part.height)))); }
        }
        @Override
        protected void paintComponent(Graphics original) {
            Graphics2D g = (Graphics2D) original.create();

            g.scale(
                    getWidth() / (double) part.width,
                    getHeight() / (double) part.height
            );

            paintPart(
                    g,
                    part,
                    input == null
                            || (!input.hasFocus() && input.getText().isEmpty())
            );

            g.dispose();
        }
        private void slide(int x) { part.value = Math.max(0, Math.min(100, (logicalX(x) - 8) * 100 / Math.max(1, part.width - 16))); repaint(); }
        private void adjust(int delta) {
            if (part.type.equals("Slider")) part.value = Math.max(0, Math.min(100, part.value + delta));
            else if (part.type.equals("Navigation Rail") || part.type.equals("Toolbar")) part.value = Math.floorMod(part.value + delta, Math.max(1, part.options.length));
            repaint();
        }
        public void activate(int x, int y) {
            switch (part.type) {
                case "Button", "FAB", "Icon Button", "Chip" -> app.go(part.target);
                case "Split Button" -> { if (x >= part.width - 42) popup(); }
                case "Dropdown" -> popup();
                case "Checkbox", "Switch" -> part.checked = !part.checked;
                case "Radio Button" -> { for (Part p : app.current.parts) if (p.type.equals("Radio Button")) p.checked = false; part.checked = true; app.repaint(); }
                case "Date Picker" -> chooseDate();
                case "Time Picker" -> chooseTime();
                case "Carousel" -> part.value = Math.floorMod(part.value + 1, 3);
                case "Navigation Rail", "Toolbar" -> {
                    for (int i = 0; i < part.options.length; i++) if (optionBounds(part, i).contains(x, y)) { part.value = i; target(i, false); break; }
                }
                case "Dialog" -> { if (y >= part.height - 52 && y <= part.height - 12 && x >= 16 && x < part.width - 16) target(x < part.width / 2 ? 0 : 1, true); }
                case "Snackbar" -> { if (x >= part.width - 96) target(0, true); }
            }
            repaint();
        }
        private void target(int index, boolean dismiss) {
            String id = index < part.targets.length ? part.targets[index] : "";
            if (!id.isEmpty()) app.go(id); else if (dismiss) { part.visible = false; setVisible(false); }
        }
        public JPopupMenu createOptionsMenu() {
            JPopupMenu menu = new JPopupMenu();

            for (int i = 0; i < part.options.length; i++) {
                final int index = i;
                JMenuItem option = new JMenuItem(part.options[i]);

                option.addActionListener(e -> {
                    part.value = index;

                    if (part.type.equals("Split Button")) {
                        part.text = part.options[index];
                        target(index, false);
                    }

                    repaint();
                });

                menu.add(option);
            }

            return menu;
        }
        private void popup() { if ((part.type.equals("Split Button") || part.type.equals("Dropdown")) && isShowing()) createOptionsMenu().show(this, 0, getHeight()); }
        private void chooseDate() {
            LocalDate initial = LocalDate.now(); try { initial = LocalDate.parse(part.text, DateTimeFormatter.ofPattern("MM/dd/uuuu")); } catch (Exception ignored) { }
            JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), "Month / Day / Year", Dialog.ModalityType.APPLICATION_MODAL);
            JComboBox<String> month = new JComboBox<>(new String[]{"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"}); month.setSelectedIndex(initial.getMonthValue() - 1);
            JSpinner year = new JSpinner(new SpinnerNumberModel(initial.getYear(), 1900, 2100, 1)); year.setEditor(new JSpinner.NumberEditor(year, "0000"));
            JPanel days = new JPanel(new GridLayout(0, 7, 4, 4)); JPanel body = new JPanel(new BorderLayout(10, 10)); body.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
            JPanel top = new JPanel(); top.add(new JLabel("Month")); top.add(month); top.add(new JLabel("Year")); top.add(year); body.add(top, BorderLayout.NORTH); body.add(days);
            Runnable fill = () -> {
                days.removeAll(); YearMonth calendar = YearMonth.of((Integer) year.getValue(), month.getSelectedIndex() + 1);
                for (String name : new String[]{"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"}) days.add(new JLabel(name, SwingConstants.CENTER));
                for (int gap = 1; gap < calendar.atDay(1).getDayOfWeek().getValue(); gap++) days.add(new JLabel());
                for (int d = 1; d <= calendar.lengthOfMonth(); d++) { LocalDate date = calendar.atDay(d); JButton button = new JButton(String.valueOf(d)); button.addActionListener(e -> { setDate(date); dialog.dispose(); }); days.add(button); }
                days.revalidate(); days.repaint(); dialog.pack();
            };
            month.addActionListener(e -> fill.run()); year.addChangeListener(e -> fill.run()); JButton cancel = new JButton("Cancel"); cancel.addActionListener(e -> dialog.dispose()); JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT)); bottom.add(cancel); body.add(bottom, BorderLayout.SOUTH);
            dialog.add(body); dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE); fill.run(); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
        }
        public void setDate(LocalDate date) { part.text = date.format(DateTimeFormatter.ofPattern("MM/dd/uuuu")); repaint(); }
        public void setTime(int hour, int minute) { part.text = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern("HH:mm")); repaint(); }
        private void chooseTime() {
            String[] hours = new String[24], minutes = new String[60]; for (int i = 0; i < 24; i++) hours[i] = String.format("%02d", i); for (int i = 0; i < 60; i++) minutes[i] = String.format("%02d", i);
            JComboBox<String> hour = new JComboBox<>(hours), minute = new JComboBox<>(minutes); JPanel panel = new JPanel(new GridLayout(2, 2, 8, 8)); panel.add(new JLabel("Hour")); panel.add(new JLabel("Minute")); panel.add(hour); panel.add(minute);
            try { LocalTime value = LocalTime.parse(part.text); hour.setSelectedIndex(value.getHour()); minute.setSelectedIndex(value.getMinute()); } catch (Exception ignored) { }
            if (JOptionPane.showConfirmDialog(this, panel, "Choose a time", JOptionPane.OK_CANCEL_OPTION) == JOptionPane.OK_OPTION) setTime(hour.getSelectedIndex(), minute.getSelectedIndex());
        }
    }
}
