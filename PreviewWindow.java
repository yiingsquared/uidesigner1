import javax.swing.*;
import java.awt.*;

public class PreviewWindow extends JDialog {
    final GuiRuntime.AppView view;
    public PreviewWindow(JFrame owner, DesignProject source, DesignPage first) {
        super(owner, "uidesigner - Preview", true);
        view = new GuiRuntime.AppView(PartRenderer.runtime(source.snapshot()), first.id);
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        JComboBox<GuiRuntime.Page> pages = UiStyle.combo(view.pages.toArray(new GuiRuntime.Page[0])); pages.setSelectedItem(view.current);
        pages.addActionListener(e -> { GuiRuntime.Page page = (GuiRuntime.Page) pages.getSelectedItem(); if (page != null && page != view.current) view.showPage(page.id); });
        view.pageChanged = page -> pages.setSelectedItem(page);
        JButton close = UiStyle.button("Close preview", true); close.addActionListener(e -> dispose()); toolbar.add(pages); toolbar.add(close);
        add(toolbar, BorderLayout.NORTH); add(view); setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        getRootPane().registerKeyboardAction(e -> dispose(), KeyStroke.getKeyStroke("ESCAPE"), JComponent.WHEN_IN_FOCUSED_WINDOW);
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize(); setSize(Math.min(1300, screen.width - 80), Math.min(980, screen.height - 80)); setLocationRelativeTo(owner);
    }
}
