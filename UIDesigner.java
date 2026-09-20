import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.JTextComponent;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

/* entry, navigation and editing; main only runs the swing window */
public class UIDesigner extends JFrame {
    DesignProject project;
    DesignPage current;
    final DesignCanvas canvas = new DesignCanvas();
    private final JPanel left = new JPanel(new BorderLayout());
    private final HashMap<String, JButton> railButtons = new HashMap<>();
    private final DefaultListModel<DesignItem> layerModel = new DefaultListModel<>();
    private final JList<DesignItem> layerList = new JList<>(layerModel);
    private final InspectorPanel inspector;
    private final PromptsPanel prompts;
    private final JLabel status = UiStyle.hint("Saved locally");
    private final JButton zoomLabel = UiStyle.button("100%", false), phone = UiStyle.iconButton("Phone", "phone.png"), desktop = UiStyle.iconButton("Desktop", "desktop.png");
    private final JButton undoButton = UiStyle.iconButton("Undo", "undo.png"), redoButton = UiStyle.iconButton("Redo", "redo.png");
    private final Path autosave = Path.of(System.getProperty("uidesigner.data", "data-blue"), "autosave.uid");
    private final Timer saveTimer;
    private final java.util.concurrent.ExecutorService saves = java.util.concurrent.Executors.newSingleThreadExecutor(run -> { Thread thread = new Thread(run, "uidesigner-save"); thread.setDaemon(true); return thread; });
    private final ArrayDeque<DesignProject> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    private DesignProject committed;
    private final ArrayList<DesignItem> clipboard = new ArrayList<>();
    private int pasteCount;
    private String activePanel = "Parts";
    private boolean refreshing;
    private final HashSet<String> collapsed = new HashSet<>();

    public static void main(String[] args) { SwingUtilities.invokeLater(() -> { UiStyle.install(); new UIDesigner().setVisible(true); }); }
    public UIDesigner() {
        super("uidesigner - Blue Edition"); project = DesignProject.sample();
        String warning = null;
        if (Files.exists(autosave)) try { project = ProjectIO.load(autosave); } catch (IOException e) { warning = "Local draft could not be opened; sample shown."; }
        current = project.pages.get(0); committed = project.snapshot();
        saveTimer = new Timer(600, e -> { commitHistory(); queueSave(); }); saveTimer.setRepeats(false);
        inspector = new InspectorPanel(this);
        prompts = new PromptsPanel(this);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE); setMinimumSize(new Dimension(1120, 700)); setLayout(new BorderLayout());
        JPanel navigation = new JPanel(new BorderLayout()); navigation.add(buildRail(), BorderLayout.WEST);
        left.setPreferredSize(new Dimension(254, 0)); left.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UiStyle.LINE)); navigation.add(left);
        add(navigation, BorderLayout.WEST); add(buildWorkspace(), BorderLayout.CENTER);
        add(buildRightPanel(), BorderLayout.EAST);
        canvas.selectionChanged = () -> { inspector.showSelection(); syncLayersSelection(); prompts.pageChanged(); };
        canvas.geometryChanged = inspector::showGeometry;
        canvas.designChanged = this::changed; canvas.zoomChanged = this::updateZoom;
        canvas.bind("DELETE", this::deleteSelection); canvas.bind("BACK_SPACE", this::deleteSelection); canvas.bind("control C", this::copy); canvas.bind("control V", this::paste); canvas.bind("control D", this::duplicate);
        canvas.bind("control Z", this::undo); canvas.bind("control shift Z", this::redo); canvas.bind("control Y", this::redo);
        canvas.bind("ESCAPE", () -> { canvas.selection.clear(); canvas.selectionChanged.run(); canvas.repaint(); });
        canvas.bind("control G", this::group); canvas.bind("control shift G", this::ungroup);
        canvas.bind("EQUALS", () -> zoom(1)); canvas.bind("ADD", () -> zoom(1)); canvas.bind("MINUS", () -> zoom(-1)); canvas.bind("SUBTRACT", () -> zoom(-1)); canvas.bind("0", () -> canvas.setZoom(0));
        bindEditor("DELETE", this::deleteSelection); bindEditor("BACK_SPACE", this::deleteSelection); bindEditor("control C", this::copy); bindEditor("control V", this::paste); bindEditor("control D", this::duplicate);
        bindEditor("control Y", this::redo); bindEditor("control Z", this::undo); bindEditor("control shift Z", this::redo);
        setupLayers(); refreshAll(); updateHistoryButtons();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize(); setSize(Math.min(1520, screen.width - 60), Math.min(1020, screen.height - 60)); setLocationRelativeTo(null);
        if (warning != null) status.setText(warning);
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) { saveTimer.stop(); if (saveLocal() || confirm("Saving failed. Close without saving?")) { saves.shutdown(); dispose(); } }
        });
    }

    // Properties / Codes
    private JPanel buildRightPanel() {
        JPanel right = new JPanel(new BorderLayout()); right.setPreferredSize(new Dimension(320, 0));
        right.setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, UiStyle.LINE));
        CardLayout cards = new CardLayout(); JPanel content = new JPanel(cards);
        content.add(UiStyle.scroll(inspector), "Properties"); content.add(prompts, "Codes");
        JPanel tabs = new JPanel(new GridLayout(1, 2, 8, 0));
        tabs.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, UiStyle.LINE), BorderFactory.createEmptyBorder(18, 16, 18, 16)));
        JButton propertiesTab = rightTab("Properties"), promptsTab = rightTab("Codes"); propertiesTab.setSelected(true);
        propertiesTab.addActionListener(e -> { propertiesTab.setSelected(true); promptsTab.setSelected(false); cards.show(content, "Properties"); prompts.setActive(false); tabs.repaint(); });
        promptsTab.addActionListener(e -> { propertiesTab.setSelected(false); promptsTab.setSelected(true); cards.show(content, "Codes"); prompts.setActive(true); tabs.repaint(); });
        tabs.add(propertiesTab); tabs.add(promptsTab); right.add(tabs, BorderLayout.NORTH); right.add(content); return right;
    }
    private JButton rightTab(String text) {
        JButton button = new JButton(text) {
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); UiStyle.smooth(g);
                g.setColor(isSelected() || getModel().isRollover() || hasFocus() ? UiStyle.SOFT : Color.WHITE);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14); g.dispose(); super.paintComponent(graphics);
            }
        };
        button.setFont(UiStyle.FONT.deriveFont(Font.BOLD)); button.setForeground(Color.BLACK); button.setBackground(Color.WHITE);
        button.setOpaque(false); button.setContentAreaFilled(false); button.setBorderPainted(false); button.setFocusPainted(false); button.setRolloverEnabled(true);
        button.setBorder(BorderFactory.createEmptyBorder(12, 5, 12, 5)); return button;
    }

    //logo of components, changed "ui" into png logo
    private JLabel createBrandLogo() {
        ImageIcon layers = IconStore.load("logo-layers.png", 24);

        JLabel brand = new JLabel(new Icon() {
            public int getIconWidth() {
                return 48;
            }

            public int getIconHeight() {
                return 48;
            }

            public void paintIcon(Component c, Graphics graphics, int x, int y) {
                Graphics2D g = (Graphics2D) graphics.create();
                g.translate(x, y);
                g.scale(0.8, 0.8);

                g.setRenderingHint(
                        RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON
                );

                java.awt.geom.Path2D.Double badge =
                        new java.awt.geom.Path2D.Double();

                for (int i = 0; i <= 120; i++) {
                    double angle = -Math.PI / 2 + i * Math.PI * 2 / 120;
                    double radius = 27 + 2 * Math.cos(10 * angle);

                    double px = 30 + Math.cos(angle) * radius;
                    double py = 30 + Math.sin(angle) * radius;

                    if (i == 0) {
                        badge.moveTo(px, py);
                    } else {
                        badge.lineTo(px, py);
                    }
                }

                badge.closePath();

                g.setColor(new Color(0x0B57D0));
                g.fill(badge);

                if (layers != null) {
                    layers.paintIcon(
                            c,
                            g,
                            (60 - layers.getIconWidth()) / 2,
                            (60 - layers.getIconHeight()) / 2
                    );
                }

                g.dispose();
            }
        });

        brand.setHorizontalAlignment(SwingConstants.CENTER);
        brand.setPreferredSize(new Dimension(66, 66));
        brand.setMaximumSize(new Dimension(66, 66));
        brand.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        brand.setToolTipText("uidesigner");

        return brand;
    }

    //directory and workplace
    private JPanel buildRail() {
        JPanel rail = new JPanel(new BorderLayout()); rail.setBackground(UiStyle.BACK); rail.setPreferredSize(new Dimension(78, 0));
        JPanel stack = UiStyle.vertical(); stack.setBackground(UiStyle.BACK); stack.setBorder(BorderFactory.createEmptyBorder(16, 6, 8, 6));
        stack.add(createBrandLogo());
        for (String name : new String[]{"Parts", "Layers", "Colours", "Shape", "Font", "Project"}) {
            JButton button = UiStyle.button(name, false); button.setFont(UiStyle.FONT.deriveFont(11f));
            button.setIcon(IconStore.load(name.toLowerCase() + ".png", 24)); button.setVerticalTextPosition(SwingConstants.BOTTOM); button.setHorizontalTextPosition(SwingConstants.CENTER);
            button.setPreferredSize(new Dimension(66, 62)); button.setMaximumSize(new Dimension(66, 62)); button.setToolTipText(name);
            button.addActionListener(e -> showPanel(name)); railButtons.put(name, button); stack.add(button); stack.add(Box.createVerticalStrut(8));
        }
        rail.add(stack, BorderLayout.NORTH); JButton help = UiStyle.iconButton("Help", "help.png"); help.setFont(UiStyle.FONT.deriveFont(11f)); help.addActionListener(e -> help());
        JPanel bottom = new JPanel(new BorderLayout()); bottom.setOpaque(false); bottom.setBorder(BorderFactory.createEmptyBorder(12, 7, 16, 7)); bottom.add(help); rail.add(bottom, BorderLayout.SOUTH); return rail;
    }
    private JLayeredPane buildWorkspace() {
        JScrollPane scroll = new JScrollPane(canvas); scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.getVerticalScrollBar().setUnitIncrement(28); scroll.getHorizontalScrollBar().setUnitIncrement(28);
        JPanel devices = floating(); devices.add(phone); devices.add(desktop); phone.addActionListener(e -> device(false)); desktop.addActionListener(e -> device(true));
        JPanel tools = floating(); JButton preview = UiStyle.iconButton("Preview", "preview.png"); preview.setBackground(UiStyle.BLUE); preview.setForeground(Color.WHITE); preview.addActionListener(e -> { commitPending(); new PreviewWindow(this, project, current).setVisible(true); });
        undoButton.addActionListener(e -> undo()); redoButton.addActionListener(e -> redo()); tools.add(preview); tools.add(undoButton); tools.add(redoButton);
        JPanel zoom = floating();

        JButton dragPage = UiStyle.button("Drag page", false);

        dragPage.addActionListener(e -> {
            canvas.setPanMode(!canvas.isPanMode());

            dragPage.setBackground(
                    canvas.isPanMode() ? UiStyle.SOFT : Color.WHITE
            );

            dragPage.setForeground(
                    canvas.isPanMode() ? UiStyle.BLUE : UiStyle.INK
            );
        });

        zoom.add(dragPage);

        JButton minus = UiStyle.button("-", false);
        JButton plus = UiStyle.button("+", false);
        //JPanel zoom = floating(); JButton minus = UiStyle.button("-", false), plus = UiStyle.button("+", false);
        minus.setToolTipText("Zoom out"); plus.setToolTipText("Zoom in"); zoomLabel.setToolTipText("Fit page to workspace");
        minus.addActionListener(e -> zoom(-1)); plus.addActionListener(e -> zoom(1)); zoomLabel.addActionListener(e -> canvas.setZoom(0)); zoom.add(minus); zoom.add(zoomLabel); zoom.add(plus);
        JLayeredPane workspace = new JLayeredPane() {
            public void doLayout() {
                scroll.setBounds(0, 0, getWidth(), getHeight()); Dimension d = devices.getPreferredSize(), t = tools.getPreferredSize(), z = zoom.getPreferredSize();
                devices.setBounds(getWidth() - d.width - 20, 18, d.width, d.height);
                tools.setBounds(16, getHeight() - t.height - 18, t.width, t.height); zoom.setBounds(getWidth() - z.width - 16, getHeight() - z.height - 18, z.width, z.height);
            }
        };
        workspace.add(scroll, JLayeredPane.DEFAULT_LAYER); workspace.add(devices, JLayeredPane.PALETTE_LAYER); workspace.add(tools, JLayeredPane.PALETTE_LAYER); workspace.add(zoom, JLayeredPane.PALETTE_LAYER);
        scroll.setWheelScrollingEnabled(false);
        scroll.addMouseWheelListener(e -> {
            if (e.isControlDown()) canvas.setZoom(canvas.percent() / 100.0 * (e.getWheelRotation() < 0 ? 1.1 : 1 / 1.1));
            else {
                double movement = -e.getPreciseWheelRotation() * 66;

                canvas.panBy(
                        e.isShiftDown() ? movement : 0,
                        e.isShiftDown() ? 0 : movement
                );
            }
            //else { JScrollBar bar = e.isShiftDown() ? scroll.getHorizontalScrollBar() : scroll.getVerticalScrollBar(); bar.setValue(bar.getValue() + e.getUnitsToScroll() * 22); }
            e.consume();
        });
        return workspace;
    }
    private JPanel floating() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 5)) {
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); UiStyle.smooth(g); g.setColor(Color.WHITE);
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 22, 22); g.dispose(); super.paintComponent(graphics);
            }
        }; panel.setBorder(new UiStyle.RoundBorder(22)); panel.setOpaque(false); return panel;
    }
    private void zoom(int direction) { canvas.setZoom(canvas.percent() / 100.0 + direction * .10); }
    private void updateZoom() { zoomLabel.setText(canvas.percent() + "%"); }
    private void device(boolean desktopMode) { current.setDesktop(desktopMode); canvas.setZoom(0); inspector.showSelection(); updateDevice(); changed(); }
    private void updateDevice() { phone.setBackground(current.width < 700 ? UiStyle.SOFT : Color.WHITE); desktop.setBackground(current.width >= 700 ? UiStyle.SOFT : Color.WHITE); }
    private void showPanel(String name) {
        activePanel = name; left.removeAll();
        for (var entry : railButtons.entrySet()) { entry.getValue().setBackground(entry.getKey().equals(name) ? UiStyle.SOFT : UiStyle.BACK); entry.getValue().setForeground(entry.getKey().equals(name) ? UiStyle.BLUE : UiStyle.MUTED); }
        JPanel heading = UiStyle.section("uidesigner"); JLabel title = UiStyle.label(name, 22, true); heading.add(title); left.add(heading, BorderLayout.NORTH);
        JPanel panel = switch (name) { case "Layers" -> layersPanel(); case "Colours" -> coloursPanel(); case "Shape" -> shapePanel(); case "Font" -> fontPanel(); case "Project" -> projectPanel(); default -> partsPanel(); };
        left.add(UiStyle.scroll(panel)); left.revalidate(); left.repaint();
    }
    private JPanel partsPanel() {
        UiStyle.SidePanel body = new UiStyle.SidePanel(); JPanel searchSection = UiStyle.section("Find a part"); JTextField search = UiStyle.textField(); search.putClientProperty("placeholder", "Search components");
        searchSection.add(search); searchSection.add(Box.createVerticalStrut(8)); searchSection.add(UiStyle.hint("Click to add")); body.add(searchSection);
        JPanel catalog = UiStyle.vertical(); body.add(catalog);
        Runnable populate = () -> {
            catalog.removeAll();
            for (String category : PartCatalog.CATEGORIES) {
                var parts = PartCatalog.search(category, search.getText()); if (parts.isEmpty()) continue;
                JPanel section = UiStyle.section(""); section.removeAll(); JButton title = UiStyle.button(category + " (" + parts.size() + ")", false);
                title.setFont(UiStyle.FONT.deriveFont(Font.BOLD, 13f)); title.setForeground(Color.BLACK);
                title.setHorizontalAlignment(SwingConstants.LEFT); title.setIcon(IconStore.load(category.toLowerCase() + ".png", 18)); title.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
                JPanel grid = new JPanel(new GridLayout(0, 2, 8, 8)); grid.setVisible(!collapsed.contains(category) || !search.getText().isBlank());
                title.addActionListener(e -> { if (grid.isVisible()) collapsed.add(category); else collapsed.remove(category); grid.setVisible(!grid.isVisible()); catalog.revalidate(); });
                section.add(title); section.add(Box.createVerticalStrut(10));
                for (PartCatalog.Part part : parts) {
                    JButton button = UiStyle.button(part.name(), false); button.setBackground(UiStyle.BACK); button.setFont(UiStyle.FONT.deriveFont(11f));
                    button.setIcon(IconStore.load(part.icon(), 28)); button.setVerticalTextPosition(SwingConstants.BOTTOM); button.setHorizontalTextPosition(SwingConstants.CENTER); button.setIconTextGap(10);
                    button.setPreferredSize(new Dimension(96, 82)); button.setToolTipText("Add " + part.name() + " - " + part.icon()); button.addActionListener(e -> addPart(part.name())); grid.add(button);
                }
                if (parts.size() % 2 != 0) grid.add(new JLabel()); section.add(grid); catalog.add(section);
            }
            if (catalog.getComponentCount() == 0) { JPanel empty = UiStyle.section("No matching parts"); empty.add(UiStyle.hint("Try a different search.")); catalog.add(empty); }
            catalog.revalidate(); catalog.repaint();
        };
        listen(search, populate); populate.run(); return body;
    }

    //layers and pages
    private JPanel layersPanel() {
        UiStyle.SidePanel body = new UiStyle.SidePanel();
        JPanel layers = UiStyle.section("Layers"); layers.add(UiStyle.hint("Top row is the frontmost part.")); layers.add(Box.createVerticalStrut(6)); layers.add(UiStyle.hint("Drag a row to change the order.")); layers.add(Box.createVerticalStrut(10));
        JScrollPane scroll = new JScrollPane(layerList); scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.setPreferredSize(new Dimension(200, 350)); layers.add(scroll);
        JPanel actions = new JPanel(new GridLayout(0, 2, 6, 7)); addAction(actions, "Show / hide", this::toggleVisible); addAction(actions, "Lock / unlock", this::toggleLocked);
        addAction(actions, "Forward", () -> reorder(1)); addAction(actions, "Backward", () -> reorder(-1)); addAction(actions, "Group", this::group); addAction(actions, "Ungroup", this::ungroup); addAction(actions, "Rename", this::renameLayer); addAction(actions, "Delete", this::deleteSelection);
        layers.add(Box.createVerticalStrut(12)); layers.add(actions); layers.add(Box.createVerticalStrut(12)); layers.add(UiStyle.hint("Ctrl + click rows to select several.")); body.add(layers); refreshLayers(); return body;
    }
    private JPanel pagesPanel() {
        JPanel pages = UiStyle.section("Pages"); JComboBox<DesignPage> picker = UiStyle.combo(project.pages.toArray(new DesignPage[0])); picker.setSelectedItem(current);
        picker.addActionListener(e -> { current = (DesignPage) picker.getSelectedItem(); canvas.showPage(project, current); canvas.setZoom(0); refreshLayers(); updateDevice(); inspector.invalidateFields(); inspector.showSelection(); prompts.pageChanged(); }); pages.add(picker);
        JPanel pageActions = new JPanel(new GridLayout(1, 2, 6, 0)); addAction(pageActions, "Add page", this::addPage); addAction(pageActions, "Delete page", this::deletePage); pages.add(Box.createVerticalStrut(10)); pages.add(pageActions); addAction(pages, "Copy current page", this::copyPage); return pages;
    }
    private void setupLayers() {
        layerList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION); layerList.setFixedCellHeight(55);
        layerList.setCellRenderer((list, item, index, selected, focus) -> {
            JPanel panel = new JPanel(new BorderLayout(8, 2)); panel.setBackground(selected ? UiStyle.SOFT : Color.WHITE); panel.setBorder(BorderFactory.createEmptyBorder(7, 9, 7, 9));
            JLabel icon = new JLabel(IconStore.load(PartCatalog.find(item.type).icon(), 22)); panel.add(icon, BorderLayout.WEST);
            JPanel labels = new JPanel(new GridLayout(2, 1)); labels.setOpaque(false); JLabel title = UiStyle.label(item.name.isBlank() ? item.type : item.name, 12, true); title.setForeground(item.visible ? UiStyle.INK : UiStyle.MUTED); labels.add(title);
            labels.add(UiStyle.hint(item.type + (item.locked ? " · Locked" : "") + (!item.visible ? " · Hidden" : "") + (!item.group.isEmpty() ? " · Group" : ""))); panel.add(labels); return panel;
        });
        layerList.addListSelectionListener(e -> {
            if (refreshing || e.getValueIsAdjusting()) return;
            canvas.selection.clear(); canvas.selection.addAll(layerList.getSelectedValuesList()); inspector.showSelection(); canvas.repaint();
        });
        layerList.setDragEnabled(true); layerList.setDropMode(DropMode.INSERT);
        layerList.setTransferHandler(new TransferHandler() {
            protected Transferable createTransferable(JComponent c) { DesignItem item = layerList.getSelectedValue(); return item == null ? null : new StringSelection(item.id); }
            public int getSourceActions(JComponent c) { return MOVE; }
            public boolean canImport(TransferSupport support) { return support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor) && layerList.getSelectedIndices().length == 1; }
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    String id = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor); DesignItem found = null;
                    for (DesignItem item : current.items) if (item.id.equals(id)) found = item;
                    if (found == null || found.locked) return false;
                    int insert = ((JList.DropLocation) support.getDropLocation()).getIndex(); ArrayList<DesignItem> reversed = new ArrayList<>(current.items); Collections.reverse(reversed);
                    int old = reversed.indexOf(found); reversed.remove(found); if (old < insert) insert--; reversed.add(Math.max(0, Math.min(insert, reversed.size())), found);
                    Collections.reverse(reversed); current.items = reversed; canvas.select(found, false); refreshLayers(); changed(); return true;
                } catch (Exception error) { return false; }
            }
        });
    }
    private void refreshLayers() {
        refreshing = true; layerModel.clear(); for (int i = current.items.size() - 1; i >= 0; i--) layerModel.addElement(current.items.get(i)); refreshing = false; syncLayersSelection();
    }
    private void syncLayersSelection() {
        refreshing = true; layerList.clearSelection(); for (int i = 0; i < layerModel.size(); i++) if (canvas.selection.contains(layerModel.get(i))) layerList.addSelectionInterval(i, i); refreshing = false;
    }
    private void toggleVisible() { for (DesignItem item : canvas.selection) item.visible = !item.visible; changed(); }
    private void toggleLocked() { for (DesignItem item : canvas.selection) item.locked = !item.locked; inspector.showSelection(); changed(); }
    private void renameLayer() { DesignItem item = canvas.selected(); if (item == null || item.locked) return; String name = JOptionPane.showInputDialog(this, "Layer name", item.name); if (name != null && name.length() <= 80) { item.name = name; inspector.showSelection(); changed(); } }
    void group() {
        if (canvas.selection.size() < 2 || canvas.selection.stream().anyMatch(item -> item.locked)) { status.setText("Select at least two unlocked parts to group."); return; }
        String id = UUID.randomUUID().toString(); for (DesignItem item : canvas.selection) item.group = id; changed();
    }
    void ungroup() {
        HashSet<String> groups = new HashSet<>(); for (DesignItem item : canvas.selection) if (!item.group.isEmpty()) groups.add(item.group);
        for (DesignItem item : current.items) if (groups.contains(item.group) && !item.locked) item.group = ""; changed();
    }

    //colour, shape, fonts
    private JPanel coloursPanel() {
        UiStyle.SidePanel body = new UiStyle.SidePanel(); JPanel mode = UiStyle.section("Appearance");
        JPanel choices = new JPanel(new GridLayout(1, 2, 7, 0)); JButton light = UiStyle.button("Light", !project.dark), dark = UiStyle.button("Dark", project.dark);
        light.addActionListener(e -> { project.dark = false; changed(); showPanel("Colours"); }); dark.addActionListener(e -> { project.dark = true; changed(); showPanel("Colours"); }); choices.add(light); choices.add(dark); mode.add(choices);
        body.add(mode);
        JPanel palettes = UiStyle.section("Palettes"); String[] names = {"Blue", "Purple", "Green", "Coral", "Amber", "Teal", "Mono"}; int[] colors = {0x0B57D0, 0x6750A4, 0x2E6A45, 0x984061, 0x8B5000, 0x00696E, 0x555B66};
        JPanel swatches = new JPanel(new GridLayout(0, 2, 8, 8));
        for (int i = 0; i < names.length; i++) { final int colour = colors[i]; JButton swatch = UiStyle.button(names[i], false); swatch.setBackground(new Color(colour)); swatch.setForeground(Color.WHITE); swatch.addActionListener(e -> { project.seed = colour; project.accentOverride = -1; changed(); showPanel("Colours"); }); swatches.add(swatch); } palettes.add(swatches); body.add(palettes);
        JPanel seed = UiStyle.section("Theme colour"); JTextField hex = UiStyle.textField(); hex.setText(String.format("#%06X", project.seed)); seed.add(hex); seed.add(Box.createVerticalStrut(8)); JPanel apply = new JPanel(new GridLayout(2, 1, 7, 8));
        addAction(apply, "Apply colour code", () -> { try { if (!hex.getText().matches("#[0-9a-fA-F]{6}")) throw new IllegalArgumentException(); project.seed = Integer.parseInt(hex.getText().substring(1), 16); project.accentOverride = -1; changed(); } catch (IllegalArgumentException e) { message("Use six hex digits, for example #0B57D0."); } });
        addAction(apply, "Pick colour", () -> { Color color = chooseColor("Theme colour", new Color(project.seed)); if (color != null) { project.seed = color.getRGB() & 0xFFFFFF; project.accentOverride = -1; hex.setText(String.format("#%06X", project.seed)); changed(); } }); seed.add(apply); body.add(seed);
        JPanel fine = UiStyle.section("Parts colour");
        for (String role : new String[]{"Background", "Text"}) { JButton button = UiStyle.button(role, false); button.addActionListener(e -> { Color selected = chooseColor(role, role.equals("Background") ? project.surface() : project.ink()); if (selected != null) { int rgb = selected.getRGB() & 0xFFFFFF; if (role.equals("Background")) project.surfaceOverride = rgb; else project.textOverride = rgb; changed(); } }); fine.add(button); fine.add(Box.createVerticalStrut(8)); }
        JButton reset = UiStyle.button("Reset to theme colour", false); reset.addActionListener(e -> { project.accentOverride = project.surfaceOverride = project.textOverride = -1; changed(); }); fine.add(reset); body.add(fine); return body;
    }
    private JPanel shapePanel() {
        UiStyle.SidePanel body = new UiStyle.SidePanel(); JPanel section = UiStyle.section("Corner roundness");
        for (String shape : new String[]{"Square", "Rounded", "Full"}) { JButton button = UiStyle.button(shape, project.shape.equals(shape)); button.setIcon(IconStore.load("shape-" + shape.toLowerCase() + ".png", 24)); button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60)); button.addActionListener(e -> { project.shape = shape; inspector.showSelection(); changed(); showPanel("Shape"); }); section.add(button); section.add(Box.createVerticalStrut(10)); }
        section.add(UiStyle.hint("Applies to parts using project shape.")); section.add(Box.createVerticalStrut(6)); section.add(UiStyle.hint("Custom corner radii stay unchanged.")); section.add(Box.createVerticalStrut(16));
        JButton reset = UiStyle.button("Reset all custom corners", false); reset.addActionListener(e -> { if (confirm("Use the project shape for every part?")) { for (DesignPage page : project.pages) for (DesignItem item : page.items) item.radius = -1; inspector.showSelection(); changed(); } }); section.add(reset); body.add(section); return body;
    }
    private JPanel fontPanel() {
        UiStyle.SidePanel body = new UiStyle.SidePanel(); JPanel type = UiStyle.section("Typeface");
        for (String font : new String[]{"System", "Roboto", "Roboto Flex", "Roboto Serif"}) { JButton button = UiStyle.button("Aa  " + font, project.font.equals(font)); button.setFont(IconStore.font(font, Font.PLAIN, 17)); button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60)); button.addActionListener(e -> { project.font = font; changed(); showPanel("Font"); }); type.add(button); type.add(Box.createVerticalStrut(10)); }
        JCheckBox emphasized = new JCheckBox("Bolded", project.emphasized); emphasized.setOpaque(false); emphasized.addActionListener(e -> { project.emphasized = emphasized.isSelected(); changed(); }); type.add(emphasized); type.add(Box.createVerticalStrut(12)); type.add(UiStyle.hint("Applies to headings and labels.")); body.add(type);
        return body;
    }
    private JPanel projectPanel() {
        UiStyle.SidePanel body = new UiStyle.SidePanel(); body.add(pagesPanel()); JPanel info = UiStyle.vertical(); info.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18)); JTextField name = UiStyle.textField(); name.setText(project.name); limit(name, 80); listen(name, () -> { project.name = name.getText(); changed(); }); info.add(UiStyle.field("Project name", name)); info.add(Box.createVerticalStrut(10)); info.add(status); body.add(info);
        JPanel files = UiStyle.section("Files"); for (String nameText : new String[]{"Open project", "Save as", "Export page PNG"}) { JButton button = UiStyle.button(nameText, nameText.equals("Save as")); button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42)); button.addActionListener(e -> { if (nameText.equals("Open project")) open(); else if (nameText.equals("Save as")) saveAs(); else exportPng(); }); files.add(button); files.add(Box.createVerticalStrut(10)); } body.add(files);
        JPanel samples = UiStyle.section("Create a new project"); addAction(samples, "Load study planner template", () -> { if (confirm("Replace the current project? Save a copy first if needed.")) { project = DesignProject.sample(); current = project.pages.get(0); refreshAll(); changed(); } });
        samples.add(Box.createVerticalStrut(10)); addAction(samples, "New blank project", () -> { if (confirm("Create a blank project? Save a copy first if needed.")) { project = new DesignProject(); project.name = "Untitled project"; current = new DesignPage("Page 1"); project.pages.add(current); refreshAll(); changed(); } }); body.add(samples);
        JPanel resources = UiStyle.section("Resources"); addAction(resources, "Reload PNGs and fonts", () -> { IconStore.clear(); canvas.repaint(); showPanel("Project"); message("Resources reloaded. In IntelliJ, rebuild first after adding files. Restart to refresh rail icons."); }); resources.add(Box.createVerticalStrut(12)); resources.add(UiStyle.hint("Missing PNGs use readable labels.")); body.add(resources); return body;
    }

    //editing action
    void addPart(String type) {
        if (current.items.size() >= 100) { message("Maximum 100 parts per page."); return; }
        DesignItem item = new DesignItem(type); item.x = 24 + current.items.size() % 4 * 12; item.y = 100 + current.items.size() % 7 * 34; item.keepInside(current);
        current.items.add(item); canvas.select(item, false); inspector.showSelection(); canvas.requestFocusInWindow(); refreshLayers(); changed();
    }
    void copy() { clipboard.clear(); for (DesignItem item : canvas.selection) clipboard.add(item.snapshot()); pasteCount = 0; }
    void paste() {
        if (clipboard.isEmpty()) return; if (current.items.size() + clipboard.size() > 100) { message("Maximum 100 parts per page."); return; }
        pasteCount++; canvas.selection.clear(); HashMap<String, String> groups = new HashMap<>();
        int dx = 16 * pasteCount, dy = 16 * pasteCount;
        for (DesignItem source : clipboard) { dx = Math.min(dx, current.width - Math.min(source.width, current.width) - source.x); dy = Math.min(dy, current.height - Math.min(source.height, current.height) - source.y); }
        for (DesignItem source : clipboard) {
            DesignItem item = source.duplicate(); item.x += dx; item.y += dy; item.locked = false;
            if (!item.group.isEmpty()) item.group = groups.computeIfAbsent(item.group, k -> UUID.randomUUID().toString());
            if (!item.target.equals("BACK") && project.findPage(item.target) == null) item.target = "";
            item.keepInside(current); current.items.add(item); canvas.selection.add(item);
        }
        inspector.showSelection(); refreshLayers(); canvas.requestFocusInWindow(); changed();
    }
    void duplicate() { ArrayList<DesignItem> old = new ArrayList<>(clipboard); int count = pasteCount; copy(); paste(); clipboard.clear(); clipboard.addAll(old); pasteCount = count; }
    void deleteSelection() { if (canvas.selection.isEmpty()) return; current.items.removeIf(item -> canvas.selection.contains(item) && !item.locked); canvas.selection.removeIf(item -> !current.items.contains(item)); inspector.showSelection(); refreshLayers(); changed(); }
    void reorder(int direction) {
        DesignItem item = canvas.selected(); if (item == null || item.locked) return; int i = current.items.indexOf(item), next = i + direction;
        if (next >= 0 && next < current.items.size()) { Collections.swap(current.items, i, next); refreshLayers(); changed(); }
    }
    void align(int cell) {
        var selected = canvas.selection; if (selected.isEmpty() || selected.stream().anyMatch(item -> item.locked)) return;
        Rectangle bounds = null; for (DesignItem item : selected) bounds = bounds == null ? new Rectangle(item.x, item.y, item.width, item.height) : bounds.union(new Rectangle(item.x, item.y, item.width, item.height));
        int marginX = Math.min(24, Math.max(0, (current.width - bounds.width) / 2)), marginY = Math.min(24, Math.max(0, (current.height - bounds.height) / 2));
        int x = cell % 3 == 0 ? marginX : cell % 3 == 1 ? (current.width - bounds.width) / 2 : current.width - bounds.width - marginX;
        int y = cell / 3 == 0 ? marginY : cell / 3 == 1 ? (current.height - bounds.height) / 2 : current.height - bounds.height - marginY;
        for (DesignItem item : selected) { item.x += x - bounds.x; item.y += y - bounds.y; item.keepInside(current); } inspector.showSelection(); changed();
    }

    private void addPage() {
        if (project.pages.size() >= 12) {
            message("Maximum 12 pages.");
            return;
        }

        current = new DesignPage(nextPageName());
        project.pages.add(current);

        refreshAll();
        changed();
    }

    private String nextPageName() {
        java.math.BigInteger next = java.math.BigInteger.ONE;

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                "Page\\s+(\\d+)",
                java.util.regex.Pattern.CASE_INSENSITIVE
        );

        for (int i = project.pages.size() - 1; i >= 0; i--) {
            String name = project.pages.get(i).name.trim();
            java.util.regex.Matcher match = pattern.matcher(name);

            if (match.matches()) {
                next = new java.math.BigInteger(match.group(1))
                        .add(java.math.BigInteger.ONE);
                break;
            }
        }

        java.util.HashSet<String> used = new java.util.HashSet<>();

        for (DesignPage page : project.pages) {
            used.add(
                    page.name.trim().toLowerCase(java.util.Locale.ROOT)
            );
        }

        while (used.contains("page " + next)) {
            next = next.add(java.math.BigInteger.ONE);
        }

        return "Page " + next;
    }

    private void deletePage() { if (project.pages.size() <= 1) { message("Keep at least one page."); return; } if (confirm("Delete this page and its parts? You can undo this.")) { project.removePage(current); current = project.pages.get(0); refreshAll(); changed(); } }
    private void refreshAll() { inspector.invalidateFields(); prompts.designChanged(); canvas.showPage(project, current); canvas.setZoom(0); refreshLayers(); showPanel(activePanel); updateDevice(); inspector.showSelection(); }
    void changed() { canvas.repaint(); layerList.repaint(); status.setText("Saving locally..."); saveTimer.restart(); updateHistoryButtons(); prompts.designChanged(); }
    private void commitHistory() {
        undo.push(committed); while (undo.size() > 60) undo.removeLast(); committed = project.snapshot(); redo.clear(); updateHistoryButtons();
    }
    private void commitPending() { if (saveTimer.isRunning()) { saveTimer.stop(); commitHistory(); queueSave(); } }
    private void updateHistoryButtons() { undoButton.setEnabled(!undo.isEmpty() || saveTimer.isRunning()); redoButton.setEnabled(!redo.isEmpty() && !saveTimer.isRunning()); }
    void undo() {
        commitPending(); if (undo.isEmpty()) return; redo.push(project.snapshot()); String pageId = current.id; project = undo.pop(); committed = project.snapshot(); current = project.findPage(pageId); if (current == null) current = project.pages.get(0); refreshAll(); updateHistoryButtons(); queueSave();
    }
    void redo() {
        commitPending(); if (redo.isEmpty()) return; undo.push(project.snapshot()); String pageId = current.id; project = redo.pop(); committed = project.snapshot(); current = project.findPage(pageId); if (current == null) current = project.pages.get(0); refreshAll(); updateHistoryButtons(); queueSave();
    }

    //file/project, and tools
    private void queueSave() {
        DesignProject snapshot = project.snapshot();
        saves.submit(() -> { try { ProjectIO.save(snapshot, autosave); SwingUtilities.invokeLater(() -> status.setText("Saved locally")); }
        catch (IOException error) { SwingUtilities.invokeLater(() -> status.setText("Save failed - use Save as")); } });
    }
    private boolean saveLocal() {
        DesignProject snapshot = project.snapshot();
        try { saves.submit(() -> { ProjectIO.save(snapshot, autosave); return true; }).get(); status.setText("Saved locally"); return true; }
        catch (Exception error) { status.setText("Save failed - use Save as"); return false; }
    }
    private void copyPage() {
        if (project.pages.size() >= 12) { message("Maximum 12 pages."); return; }
        DesignPage copy = current.duplicate(); project.pages.add(project.pages.indexOf(current) + 1, copy); current = copy; refreshAll(); changed();
    }
    private JFileChooser chooser(String extension) { JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new FileNameExtensionFilter(extension.equals("uid") ? "uidesigner project (*.uid)" : "PNG image (*.png)", extension)); return chooser; }
    private Path destination(String extension) {
        JFileChooser chooser = chooser(extension); chooser.setSelectedFile(new java.io.File(extension.equals("uid") ? "my-design.uid" : "my-page.png"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return null;
        Path file = chooser.getSelectedFile().toPath(); if (!file.toString().toLowerCase().endsWith("." + extension)) file = Path.of(file + "." + extension);
        if (Files.exists(file) && !confirm("Replace the existing file?")) return null; return file;
    }
    private void saveAs() { Path file = destination("uid"); if (file == null) return; try { ProjectIO.save(project, file); status.setText("Saved: " + file.getFileName()); } catch (IOException e) { message("Save failed: " + e.getMessage()); } }
    private void open() {
        JFileChooser chooser = chooser("uid"); if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        try { DesignProject loaded = ProjectIO.load(chooser.getSelectedFile().toPath()); if (confirm("Open this project and replace the current draft?")) { project = loaded; current = project.pages.get(0); refreshAll(); changed(); } }
        catch (IOException e) { message("Cannot open project: " + e.getMessage()); }
    }
    private void exportPng() { Path file = destination("png"); if (file != null) try { ImageIO.write(canvas.exportImage(), "png", file.toFile()); status.setText("PNG exported"); } catch (IOException e) { message("Export failed: " + e.getMessage()); } }

    Color chooseColor(String title, Color initial) {
        JColorChooser chooser = new JColorChooser(initial);
        JTabbedPane tabs = new JTabbedPane();

        for (javax.swing.colorchooser.AbstractColorChooserPanel panel
                : chooser.getChooserPanels()) {
            String name = panel.getDisplayName();
            if (name.toUpperCase(java.util.Locale.ROOT).contains("HSL")
                    || name.toUpperCase(java.util.Locale.ROOT).contains("CMYK")) {
                continue;
            }
            tabs.addTab(name, panel);
        }

        JDialog dialog = new JDialog(this, title, true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel content = new JPanel(new BorderLayout(12, 12));
        content.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        content.add(tabs, BorderLayout.CENTER);

        final Color[] selected = {null};
        JButton ok = UiStyle.button("OK", true);
        JButton cancel = UiStyle.button("Cancel", false);
        ok.addActionListener(e -> {
            selected[0] = chooser.getColor();
            dialog.dispose();
        });
        cancel.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(ok);
        buttons.add(cancel);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.getRootPane().setDefaultButton(ok);
        dialog.getRootPane().registerKeyboardAction(
                e -> dialog.dispose(),
                KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
        return selected[0];
    }
    private boolean confirm(String text) { return JOptionPane.showConfirmDialog(this, text, "uidesigner", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION; }
    private void message(String text) { JOptionPane.showMessageDialog(this, text); }
    private void bindEditor(String key, Runnable run) {
        AbstractAction action = new AbstractAction() {
            public void actionPerformed(ActionEvent e) {
                Component focus = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
                if (!(focus instanceof JTextComponent)) run.run();
            }
        };
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key), key);
        getRootPane().getActionMap().put(key, action);
        layerList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key), key); layerList.getActionMap().put(key, action);
    }
    private void addAction(JPanel panel, String title, Runnable action) { JButton b = UiStyle.button(title, false); b.setFont(UiStyle.FONT.deriveFont(11f)); b.addActionListener(e -> action.run()); panel.add(b); }
    void listen(JTextComponent field, Runnable action) { field.getDocument().addDocumentListener(new DocumentListener() { public void insertUpdate(DocumentEvent e) { action.run(); } public void removeUpdate(DocumentEvent e) { action.run(); } public void changedUpdate(DocumentEvent e) { action.run(); } }); }
    void limit(JTextComponent field, int max) {
        ((AbstractDocument) field.getDocument()).setDocumentFilter(new DocumentFilter() {
            public void insertString(FilterBypass fb, int offset, String string, AttributeSet attrs) throws BadLocationException { replace(fb, offset, 0, string, attrs); }
            public void replace(FilterBypass fb, int offset, int length, String value, AttributeSet attrs) throws BadLocationException {
                String text = value == null ? "" : value; int available = max - fb.getDocument().getLength() + length;
                super.replace(fb, offset, length, text.substring(0, Math.max(0, Math.min(available, text.length()))), attrs);
            }
        });
    }
    private void help() {
        message("Click a part to add it. Drag to move; drag the corner to resize.\n"
                + "Properties: width/height sliders, presets, colours and alignment.\n"
                + "Layers: drag rows, hide/lock, rename and group. Project: manage or copy pages.\n"
                + "Ctrl + click: select several parts. Ctrl + G: group; Ctrl + Shift + G: ungroup.\n"
                + "Delete / Backspace: delete selected components.\n"
                + "Ctrl+C: copy; Ctrl+V: paste; Ctrl+Z: undo; Ctrl+Y: redo.\n"
                + "Ctrl+Shift+Z also redoes. In text fields, normal text editing is preserved.\n"
                + "Codes: GUI coordinates, colours, appearance and standalone Java code.\n"
                + "Zoom: - / + or Ctrl + wheel. Click the percentage to fit.\n"
                + "Drag page: toggle the button beside zoom, then drag the workspace.\n"
                + "Wheel: move vertically. Shift + wheel: move horizontally.\n"
                + "Project: open, save, export PNG. Drafts auto-save to data-blue/autosave.uid.\n"
                + "PNG icons: src/icons/ or src/. Missing files use text labels.\n"
                + "Camera, map and image are placeholders. No network service is used.");
    }
}
