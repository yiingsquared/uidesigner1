import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.function.Consumer;

public class PromptsPanel extends JPanel {
    private final UIDesigner app;
    private final JTextArea values = area(), source = area();
    private final JLabel status = UiStyle.hint("");
    private final Timer refreshTimer;
    private final ArrayList<Consumer<JavaExporter.Result>> waiting = new ArrayList<>();
    private JavaExporter.Result result;
    private SwingWorker<JavaExporter.Result, Void> worker;
    private long revision, exported = -1;
    private boolean active;
    private String currentPage = "";
    public PromptsPanel(UIDesigner app) {
        super(new BorderLayout()); this.app = app; values.setLineWrap(true); values.setWrapStyleWord(true);
        refreshTimer = new Timer(450, e -> refreshNow()); refreshTimer.setRepeats(false);
        UiStyle.SidePanel body = new UiStyle.SidePanel();
        JPanel geometry = UiStyle.section("GUI values"); geometry.add(scroll(values, 290));
        JPanel buttons = new JPanel(new GridLayout(1, 2, 7, 0)); action(buttons, "Copy values", () -> copy(false)); action(buttons, "Refresh", () -> { revision++; refreshNow(); }); geometry.add(Box.createVerticalStrut(8)); geometry.add(buttons); body.add(geometry);
        JPanel visual = UiStyle.section("GUI appearance"); JComponent thumbnail = preview(); thumbnail.setPreferredSize(new Dimension(240, 220)); visual.add(thumbnail); visual.add(Box.createVerticalStrut(8)); action(visual, "View GUI", () -> new PreviewWindow(app, app.project, app.current).setVisible(true)); body.add(visual);
        JPanel code = UiStyle.section("Full version Java code"); code.add(UiStyle.hint("GeneratedGUI.java")); code.add(scroll(source, 300));
        JPanel actions = new JPanel(new GridLayout(1, 2, 7, 0)); action(actions, "Copy code", () -> copy(true)); action(actions, "Open code", this::showCode); code.add(Box.createVerticalStrut(8)); code.add(actions); code.add(status); body.add(code);
        add(UiStyle.scroll(body));
    }
    private static JTextArea area() { JTextArea text = new JTextArea(); text.setEditable(false); text.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11)); text.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8)); return text; }
    private static JScrollPane scroll(JTextArea text, int height) { JScrollPane pane = new JScrollPane(text); pane.setBorder(new UiStyle.RoundBorder(14)); pane.setPreferredSize(new Dimension(230, height)); pane.setMaximumSize(new Dimension(Integer.MAX_VALUE, height)); return pane; }
    private void action(JPanel panel, String label, Runnable run) { JButton button = UiStyle.button(label, false); button.setFont(UiStyle.FONT.deriveFont(11f)); button.addActionListener(e -> run.run()); panel.add(button); }
    public void setActive(boolean value) { active = value; if (value) { pageChanged(); refreshNow(); } else refreshTimer.stop(); }
    public void pageChanged() { if (app.current != null && !currentPage.equals(app.current.id)) { currentPage = app.current.id; designChanged(); } }
    public void designChanged() { revision++; if (active) { status.setText("Updating..."); refreshTimer.restart(); repaint(); } }
    public void refreshNow() {
        refreshTimer.stop();
        if (worker != null || app.current == null) return;
        if (result != null && exported == revision) { deliver(); return; }
        DesignProject snapshot = app.project.snapshot(); String page = app.current.id; long requested = revision;
        status.setText("Generating...");
        worker = new SwingWorker<>() {
            protected JavaExporter.Result doInBackground() { return JavaExporter.export(snapshot, page); }
            protected void done() {
                worker = null;
                try {
                    JavaExporter.Result ready = get();
                    if (requested != revision) { if (active || !waiting.isEmpty()) refreshNow(); return; }
                    result = ready; exported = requested;
                    values.setText(limit(ready.values(), 100_000)); values.setCaretPosition(0);
                    source.setText(limit(ready.code(), 60_000)); source.setCaretPosition(0);
                    status.setText(ready.code().length() > 60_000 ? "Preview shortened; Copy code copies all." : "Ready"); deliver();
                } catch (Exception error) {
                    result = null; values.setText(""); source.setText(""); waiting.clear();
                    status.setText("Could not generate code."); JOptionPane.showMessageDialog(app, error.getCause() == null ? error.getMessage() : error.getCause().getMessage());
                }
            }
        }; worker.execute();
    }
    private String limit(String text, int max) { return text.length() <= max ? text : text.substring(0, max); }
    private void deliver() { var actions = new ArrayList<>(waiting); waiting.clear(); for (var action : actions) action.accept(result); }
    private void withResult(Consumer<JavaExporter.Result> action) { waiting.add(action); refreshNow(); }
    private void copy(boolean code) { withResult(ready -> { try { Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code ? ready.code() : ready.values()), null); status.setText("Copied"); } catch (IllegalStateException error) { status.setText("Clipboard busy. Try again."); } }); }
    private void showCode() {
        withResult(ready -> {
            JDialog dialog = new JDialog(app, "GeneratedGUI.java", false); JTextArea complete = area(); complete.setText(limit(ready.code(), 200_000)); complete.setCaretPosition(0); dialog.add(new JScrollPane(complete));
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT)); action(buttons, "Copy code", () -> copy(true)); action(buttons, "Close", dialog::dispose); dialog.add(buttons, BorderLayout.SOUTH);
            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize(); dialog.setSize(Math.min(1000, screen.width - 80), Math.min(800, screen.height - 80)); dialog.setLocationRelativeTo(app); dialog.setVisible(true);
        });
    }
    private JComponent preview() {
        return new JPanel() {
            { setBackground(UiStyle.BACK); }
            protected void paintComponent(Graphics graphics) {
                super.paintComponent(graphics); if (app.current == null) return;
                double scale = Math.max(.001, Math.min((getWidth() - 24.0) / app.current.width, (getHeight() - 24.0) / app.current.height));
                Graphics2D g = (Graphics2D) graphics.create(); g.translate((getWidth() - app.current.width * scale) / 2, (getHeight() - app.current.height * scale) / 2); g.scale(scale, scale);
                if (app.current.items.isEmpty()) { g.setColor(app.current.background < 0 ? app.project.surface() : new Color(app.current.background)); g.fillRoundRect(0, 0, app.current.width, app.current.height, 28, 28); }
                else DesignCanvas.paintPage(g, app.project, app.current);
                g.dispose();
            }
        };
    }
}
