import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicSliderUI;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicTextFieldUI;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.metal.DefaultMetalTheme;
import javax.swing.plaf.metal.MetalLookAndFeel;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

//editing appearance
public class UiStyle {
    static final Color BLUE = new Color(0x0B57D0), INK = new Color(0x24324B), MUTED = new Color(0x68778D);
    static final Color LINE = new Color(0xDFE5EE), SOFT = new Color(0xE8F0FE), BACK = new Color(0xF3F6FB);
    static final Font FONT = new Font("SansSerif", Font.PLAIN, 13);
    public static void install() {
        try {
            MetalLookAndFeel.setCurrentTheme(new DefaultMetalTheme() {
                protected ColorUIResource getPrimary1() { return new ColorUIResource(BLUE); }
                protected ColorUIResource getPrimary2() { return new ColorUIResource(0xA6C6F8); }
                protected ColorUIResource getPrimary3() { return new ColorUIResource(SOFT); }
                protected ColorUIResource getSecondary1() { return new ColorUIResource(MUTED); }
                protected ColorUIResource getSecondary2() { return new ColorUIResource(LINE); }
                protected ColorUIResource getSecondary3() { return new ColorUIResource(BACK); }
                public FontUIResource getControlTextFont() { return new FontUIResource(FONT); }
                public FontUIResource getUserTextFont() { return new FontUIResource(FONT); }
                public FontUIResource getSystemTextFont() { return new FontUIResource(FONT); }
                public FontUIResource getMenuTextFont() { return new FontUIResource(FONT); }
            });
            UIManager.setLookAndFeel(new MetalLookAndFeel());
            UIManager.put("Panel.background", Color.WHITE); UIManager.put("Label.foreground", INK);
            UIManager.put("TextField.selectionBackground", SOFT); UIManager.put("TextField.selectionForeground", INK);
            UIManager.put("TextArea.selectionBackground", SOFT); UIManager.put("TextArea.selectionForeground", INK);
            UIManager.put("ScrollBar.width", 9);
            UIManager.put("Button.border", new RoundBorder(14)); UIManager.put("Button.background", SOFT);
            UIManager.put("ButtonUI", RoundedButtonUI.class.getName());
            UIManager.put("TextFieldUI", RoundedTextFieldUI.class.getName());
        } catch (Exception error) { throw new IllegalStateException("Cannot initialise Swing", error); }
    }
    public static Color mix(Color a, Color b, double amount) {
        return new Color((int) (a.getRed() * (1 - amount) + b.getRed() * amount), (int) (a.getGreen() * (1 - amount) + b.getGreen() * amount), (int) (a.getBlue() * (1 - amount) + b.getBlue() * amount));
    }
    public static Color readable(Color background) { return background.getRed() * .299 + background.getGreen() * .587 + background.getBlue() * .114 > 150 ? INK : Color.WHITE; }
    public static void smooth(Graphics2D g) { g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON); }
    public static JButton button(String text, boolean primary) {
        JButton button = new JButton(text) {
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); smooth(g);
                Color fill = getBackground();
                if (getModel().isPressed() && isEnabled()) fill = mix(fill, BLUE, .15);
                else if (getModel().isRollover() && isEnabled()) fill = mix(fill, BLUE, .07);
                g.setColor(fill); g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                super.paintComponent(g); g.dispose();
            }
        };
        button.setFont(FONT); button.setOpaque(false); button.setContentAreaFilled(false); button.setFocusPainted(false);
        button.setRolloverEnabled(true); button.setBackground(primary ? BLUE : Color.WHITE); button.setForeground(primary ? Color.WHITE : INK);
        button.setBorder(new RoundBorder(16)); button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }
    public static JButton iconButton(String label, String file) {
        JButton button = button(label, false); ImageIcon icon = IconStore.load(file, 22);
        if (icon != null) { button.setText(""); button.setIcon(icon); }
        button.setToolTipText(label); button.getAccessibleContext().setAccessibleName(label); return button;
    }
    public static JTextField textField() {
        JTextField field = new JTextField() {
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); smooth(g); g.setColor(getBackground());
                g.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
                super.paintComponent(g);
                if (getText().isEmpty() && getClientProperty("placeholder") instanceof String hint) {
                    g.setFont(getFont()); g.setColor(MUTED);
                    String label = PartRenderer.shorten(g, hint, getWidth() - 24);
                    g.drawString(label, 12, (getHeight() - g.getFontMetrics().getHeight()) / 2 + g.getFontMetrics().getAscent());
                }
                g.dispose();
            }
        };
        field.setOpaque(false); field.setBackground(Color.WHITE); field.setBorder(new RoundBorder(16)); field.setFont(FONT);
        field.setPreferredSize(new Dimension(160, 38)); field.setMinimumSize(new Dimension(30, 38)); return field;
    }
    public static JScrollPane textArea(JTextArea area) {
        area.setFont(FONT); area.setLineWrap(true); area.setWrapStyleWord(true); area.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JScrollPane scroll = new JScrollPane(area) {
            public void paint(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); smooth(g);
                g.clip(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 16, 16)); super.paint(g); g.dispose();
            }
        };
        scroll.setOpaque(false); scroll.setBorder(new RoundBorder(16)); return scroll;
    }
    public static <T> JComboBox<T> combo(T[] items) {
        JComboBox<T> box = new JComboBox<>(items); box.setOpaque(false); box.setBackground(Color.WHITE); box.setFont(FONT); box.setBorder(new RoundBorder(16));
        box.setUI(new BasicComboBoxUI() {
            protected JButton createArrowButton() {
                JButton arrow = button("", false); arrow.setBorder(BorderFactory.createEmptyBorder()); arrow.setPreferredSize(new Dimension(18, 22));
                arrow.setIcon(new Icon() {
                    public int getIconWidth() { return 10; } public int getIconHeight() { return 6; }
                    public void paintIcon(Component c, Graphics g, int x, int y) { g.setColor(MUTED); g.drawLine(x, y, x + 4, y + 4); g.drawLine(x + 4, y + 4, x + 8, y); }
                }); return arrow;
            }
        });
        box.setPreferredSize(new Dimension(160, 38)); return box;
    }
    public static JSlider slider(int min, int max, int value) {
        JSlider slider = new JSlider(min, max, value); slider.setOpaque(false); slider.setFocusable(true);
        slider.setUI(new BasicSliderUI(slider) {
            protected Dimension getThumbSize() { return new Dimension(16, 24); }
            public void paintTrack(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); smooth(g); int y = trackRect.y + trackRect.height / 2 - 3;
                g.setColor(SOFT); g.fillRoundRect(trackRect.x, y, trackRect.width, 6, 6, 6);
                g.setColor(BLUE); g.fillRoundRect(trackRect.x, y, Math.max(0, thumbRect.x + 8 - trackRect.x), 6, 6, 6); g.dispose();
            }
            public void paintThumb(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create(); smooth(g); g.setColor(BLUE);
                g.fillRoundRect(thumbRect.x + 4, thumbRect.y, 8, thumbRect.height, 8, 8); g.dispose();
            }
            public void paintFocus(Graphics graphics) { if (slider.hasFocus()) { graphics.setColor(BLUE); graphics.drawRect(0, 0, slider.getWidth() - 1, slider.getHeight() - 1); } }
        });
        slider.setPreferredSize(new Dimension(200, 30)); return slider;
    }
    public static class RoundBorder extends AbstractBorder {
        private final int arc;
        public RoundBorder(int arc) { this.arc = arc; }
        public Insets getBorderInsets(Component c) { return new Insets(9, 12, 9, 12); }
        public Insets getBorderInsets(Component c, Insets insets) { insets.set(9, 12, 9, 12); return insets; }
        public void paintBorder(Component c, Graphics graphics, int x, int y, int w, int h) {
            Graphics2D g = (Graphics2D) graphics.create(); smooth(g);
            g.setColor(c.hasFocus() ? BLUE : c.getBackground().equals(BLUE) ? BLUE : LINE);
            g.setStroke(new BasicStroke(c.hasFocus() ? 2 : 1)); g.drawRoundRect(x + 1, y + 1, w - 3, h - 3, arc, arc); g.dispose();
        }
    }
    // let JOptionPane / swing in text field to use round corner
    public static class RoundedButtonUI extends BasicButtonUI {
        public static ComponentUI createUI(JComponent c) { return new RoundedButtonUI(); }
        protected void installDefaults(AbstractButton button) {
            super.installDefaults(button); button.setOpaque(false); button.setRolloverEnabled(true); button.setBorder(new RoundBorder(16));
        }
        public void paint(Graphics graphics, JComponent component) {
            Graphics2D g = (Graphics2D) graphics.create(); smooth(g); AbstractButton button = (AbstractButton) component;
            if (button.isContentAreaFilled()) { g.setColor(button.getBackground()); g.fillRoundRect(0, 0, button.getWidth(), button.getHeight(), 16, 16); }
            super.paint(g, component); g.dispose();
        }
        protected void paintButtonPressed(Graphics g, AbstractButton button) {
            if (button.isContentAreaFilled()) { g.setColor(SOFT); g.fillRoundRect(0, 0, button.getWidth(), button.getHeight(), 16, 16); }
        }
    }
    public static class RoundedTextFieldUI extends BasicTextFieldUI {
        public static ComponentUI createUI(JComponent c) { return new RoundedTextFieldUI(); }
        protected void installDefaults() { super.installDefaults(); getComponent().setOpaque(false); getComponent().setBorder(new RoundBorder(16)); }
        protected void paintSafely(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create(); smooth(g); JComponent c = getComponent();
            g.setColor(c.getBackground()); g.fillRoundRect(0, 0, c.getWidth(), c.getHeight(), 16, 16);
            super.paintSafely(g); g.dispose();
        }
    }
    public static JPanel vertical() {
        JPanel panel = new JPanel() {
            public Dimension getMaximumSize() { return new Dimension(Integer.MAX_VALUE, getPreferredSize().height); }
            protected void addImpl(Component component, Object constraints, int index) {
                if (component instanceof JComponent c) c.setAlignmentX(Component.LEFT_ALIGNMENT); super.addImpl(component, constraints, index);
            }
        }; panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setAlignmentX(Component.LEFT_ALIGNMENT); return panel;
    }
    public static JLabel label(String text, int size, boolean bold) {
        JLabel label = new JLabel(text) { public Dimension getPreferredSize() { Dimension d = super.getPreferredSize(); d.width += 6; return d; } };
        label.setFont(FONT.deriveFont(bold ? Font.BOLD : Font.PLAIN, size)); label.setForeground(INK); return label;
    }
    public static JLabel hint(String text) { JLabel label = label(text, 11, false); label.setForeground(MUTED); return label; }
    public static JPanel field(String name, JComponent component) {
        JPanel panel = vertical(); panel.add(label(name, 12, false)); panel.add(Box.createVerticalStrut(7));
        component.setMaximumSize(new Dimension(Integer.MAX_VALUE, component.getPreferredSize().height)); panel.add(component); return panel;
    }
    public static JPanel section(String name) {
        JPanel panel = vertical(); panel.setBorder(BorderFactory.createEmptyBorder(18, 18, 10, 18));
        panel.add(label(name, 14, true)); panel.add(Box.createVerticalStrut(14)); return panel;
    }
    public static class SidePanel extends JPanel implements Scrollable {
        public SidePanel() { setLayout(new BoxLayout(this, BoxLayout.Y_AXIS)); }
        public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 22; }
        public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(22, r.height - 30); }
        public boolean getScrollableTracksViewportWidth() { return true; }
        public boolean getScrollableTracksViewportHeight() { return false; }
    }
    public static JScrollPane scroll(JPanel panel) {
        JScrollPane scroll = new JScrollPane(panel); scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder()); scroll.getVerticalScrollBar().setUnitIncrement(22); return scroll;
    }
}
