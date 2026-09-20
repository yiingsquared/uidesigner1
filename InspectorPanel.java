import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;

//right property panel: width/height slider, preview, align, etc.
public class InspectorPanel extends UiStyle.SidePanel {
    private final UIDesigner app;
    private final JLabel title = UiStyle.label("Page", 19, true), selectionInfo = UiStyle.hint("Click a part to edit it.");
    private final JPanel pagePanel = UiStyle.vertical(), itemPanel = UiStyle.vertical(), details = UiStyle.vertical();
    private final JTextField pageName = UiStyle.textField(), name = UiStyle.textField();
    private final JTextArea text = new JTextArea(2, 12), detail = new JTextArea(2, 12);
    private final JCheckBox checked = new JCheckBox("On / checked by default");
    private final JSlider width = UiStyle.slider(24, 412, 168), height = UiStyle.slider(8, 892, 48);
    private final JSlider radius = UiStyle.slider(0, 100, 16), fontSize = UiStyle.slider(8, 72, 14), value = UiStyle.slider(0, 100, 40);
    private final JLabel widthValue = UiStyle.label("", 12, true), heightValue = UiStyle.label("", 12, true), radiusValue = UiStyle.label("", 12, true), fontValue = UiStyle.label("", 12, true), progressValue = UiStyle.label("", 12, true);
    private final JPanel valuePanel = UiStyle.vertical(), shapeControls = UiStyle.vertical();
    private boolean updating;
    private final JPanel optionFields = UiStyle.vertical(), triggerFields = UiStyle.vertical(), skinPanel = UiStyle.section("PNG appearance");
    private final JPanel textField = UiStyle.field("Text", UiStyle.textArea(text));
    private final JLabel skinName = UiStyle.hint("No PNG uploaded");
    private final JButton fill = UiStyle.button("Fill", false);
    private String fieldsFor = "";

    public InspectorPanel(UIDesigner app) {
        this.app = app;
        JPanel heading = UiStyle.vertical(); heading.setBorder(BorderFactory.createEmptyBorder(18, 18, 10, 18)); heading.add(title); heading.add(Box.createVerticalStrut(8)); heading.add(selectionInfo); add(heading);
        JPanel page = UiStyle.section("Current page"); page.add(UiStyle.field("Page name", pageName)); page.add(Box.createVerticalStrut(12));
        page.add(Box.createVerticalStrut(20)); page.add(UiStyle.hint("Click to add")); page.add(Box.createVerticalStrut(8)); page.add(UiStyle.hint("Ctrl + click: select several parts."));
        pagePanel.add(page); add(pagePanel);

        JPanel content = UiStyle.section("Content");
        content.add(textField); content.add(Box.createVerticalStrut(12));
        details.add(UiStyle.field("Supporting text", UiStyle.textArea(detail))); content.add(details); content.add(Box.createVerticalStrut(12));
        content.add(optionFields); itemPanel.add(content);
        JButton upload = UiStyle.button("Upload PNG", false), remove = UiStyle.button("Remove PNG", false);
        upload.addActionListener(e -> uploadPng());
        remove.addActionListener(e -> { DesignItem item = editable(); if (item != null) { item.skin = ""; item.skinName = ""; showSelection(); app.changed(); } });
        skinPanel.add(skinName); skinPanel.add(Box.createVerticalStrut(8)); skinPanel.add(upload); skinPanel.add(Box.createVerticalStrut(8)); skinPanel.add(remove); itemPanel.add(skinPanel);
        JPanel triggers = UiStyle.section("Trigger"); triggers.add(triggerFields); itemPanel.add(triggers);

        JPanel styles = UiStyle.section("Colour");
        checked.setOpaque(false); styles.add(checked); JPanel colourButtons = new JPanel(new GridLayout(1, 2, 8, 0));
        JButton ink = UiStyle.button("Text", false), reset = UiStyle.button("Reset colours to theme", false);
        fill.addActionListener(e -> customColor(true)); ink.addActionListener(e -> customColor(false));
        reset.addActionListener(e -> { DesignItem item = editable(); if (item != null) { item.background = -1; item.foreground = -1; app.changed(); } });
        colourButtons.add(fill); colourButtons.add(ink); styles.add(Box.createVerticalStrut(12)); styles.add(colourButtons); styles.add(Box.createVerticalStrut(8)); styles.add(reset); itemPanel.add(styles);

        JPanel size = UiStyle.section("Size");
        size.add(sliderRow("Width - horizontal", "width.png", width, widthValue)); size.add(Box.createVerticalStrut(7));
        JPanel widths = new JPanel(new GridLayout(1, 3, 5, 0));
        for (String option : new String[]{"Compact", "Half", "Full"}) { JButton b = UiStyle.button(option, false); b.setFont(UiStyle.FONT.deriveFont(11f)); b.addActionListener(e -> {
            DesignItem item = editable(); if (item == null) return;
            item.width = option.equals("Compact") ? PartCatalog.find(item.type).width() : option.equals("Half") ? (app.current.width - 64) / 2 : app.current.width - 48;
            item.keepInside(app.current); showSelection(); app.changed();
        }); widths.add(b); }
        size.add(widths); size.add(Box.createVerticalStrut(18)); size.add(sliderRow("Height - vertical", "height.png", height, heightValue)); size.add(Box.createVerticalStrut(7));
        JPanel heights = new JPanel(new GridLayout(1, 4, 5, 0)); for (int pixels : new int[]{32, 48, 64, 96}) { JButton b = UiStyle.button("" + pixels, false); b.addActionListener(e -> height.setValue(pixels)); heights.add(b); } size.add(heights);
        size.add(Box.createVerticalStrut(16)); size.add(sliderRow("Font size", "font.png", fontSize, fontValue));
        size.add(Box.createVerticalStrut(10)); size.add(UiStyle.hint("Or drag the bottom-right corner.")); itemPanel.add(size, 1);

        JPanel align = UiStyle.section("Align on page"); JPanel grid = new JPanel(new GridLayout(3, 3, 6, 6));
        String[] labels = {"Top left", "Top", "Top right", "Left", "Centre", "Right", "Bottom left", "Bottom", "Bottom right"};
        for (int i = 0; i < 9; i++) {
            final int cell = i; JButton b = UiStyle.iconButton(labels[i], "align-" + i + ".png"); b.setFont(UiStyle.FONT.deriveFont(10f)); b.setPreferredSize(new Dimension(70, 38)); b.addActionListener(e -> app.align(cell)); grid.add(b);
        }
        align.add(grid); itemPanel.add(align);

        JPanel appearance = UiStyle.section("Shape & type"); appearance.add(sliderRow("Corner radius", "shape.png", radius, radiusValue));
        JButton inherit = UiStyle.button("Use project shape", false);

        inherit.addActionListener(e -> {
            DesignItem item = editable();

            if (item != null) {
                item.radius = -1;
                showSelection();
                app.changed();
            }
        });

        JButton applyAll = UiStyle.button("Apply to all shapes", false);

        inherit.setFont(UiStyle.FONT.deriveFont(10f));
        applyAll.setFont(UiStyle.FONT.deriveFont(10f));

        applyAll.addActionListener(e -> {
            DesignItem source = editable();

            if (source == null || !source.supportsCornerRadius()) return;

            int chosenRadius = app.project.corner(source);

            for (DesignPage designPage : app.project.pages) {
                for (DesignItem item : designPage.items) {
                    if (!item.locked && item.supportsCornerRadius()) {
                        item.radius = chosenRadius;
                    }
                }
            }

            showSelection();
            app.changed();
        });

        JPanel shapeButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        shapeButtons.setOpaque(false);
        shapeButtons.add(inherit);
        shapeButtons.add(applyAll);

        appearance.add(shapeButtons);
        //JButton inherit = UiStyle.button("Use project shape", false); inherit.addActionListener(e -> { DesignItem item = editable(); if (item != null) { item.radius = -1; showSelection(); app.changed(); } }); appearance.add(inherit);
        shapeControls.add(appearance); itemPanel.add(shapeControls);
        JPanel state = UiStyle.section("Default value"); state.add(sliderRow("Value", "slider.png", value, progressValue)); valuePanel.add(state); itemPanel.add(valuePanel);
        JPanel arrange = UiStyle.section("Arrange"); JPanel actions = new JPanel(new GridLayout(2, 2, 7, 7));
        addAction(actions, "Duplicate", app::duplicate); addAction(actions, "Delete", app::deleteSelection); addAction(actions, "Bring forward", () -> app.reorder(1)); addAction(actions, "Send backward", () -> app.reorder(-1)); arrange.add(actions); itemPanel.add(arrange); add(itemPanel);

        app.listen(pageName, () -> { if (!updating) { app.current.name = pageName.getText(); app.changed(); } });
        app.listen(name, () -> { DesignItem item = editable(); if (item != null) { item.name = name.getText(); app.changed(); } });
        app.listen(text, () -> { DesignItem item = editable(); if (item != null) { item.text = text.getText(); app.changed(); } });
        app.listen(detail, () -> { DesignItem item = editable(); if (item != null) { item.detail = detail.getText(); app.changed(); } });
        app.limit(pageName, 80); app.limit(name, 80); app.limit(text, 1000); app.limit(detail, 1000);
        width.addChangeListener(e -> updateNumber(width, "width")); height.addChangeListener(e -> updateNumber(height, "height"));
        radius.addChangeListener(e -> updateNumber(radius, "radius")); fontSize.addChangeListener(e -> updateNumber(fontSize, "fontSize")); value.addChangeListener(e -> updateNumber(value, "value"));
        checked.addActionListener(e -> { DesignItem item = editable(); if (item != null) { item.checked = checked.isSelected(); app.changed(); } });
    }
    private void addAction(JPanel row, String name, Runnable run) { JButton b = UiStyle.button(name, false); b.setFont(UiStyle.FONT.deriveFont(11f)); b.addActionListener(e -> run.run()); row.add(b); }
    private JPanel sliderRow(String label, String png, JSlider slider, JLabel number) {
        JPanel panel = UiStyle.vertical(); JPanel heading = new JPanel(new BorderLayout(6, 0)); JLabel name = UiStyle.label(label, 12, false); name.setIcon(IconStore.load(png, 16)); heading.add(name); heading.add(number, BorderLayout.EAST);
        slider.getAccessibleContext().setAccessibleName(label); panel.add(heading); panel.add(slider); return panel;
    }
    private DesignItem editable() { DesignItem item = app.canvas.selected(); return updating || item == null || item.locked ? null : item; }
    private void updateNumber(JSlider slider, String field) {
        DesignItem item = editable(); if (item == null) return;
        switch (field) {
            case "width": item.width = slider.getValue(); break;
            case "height": item.height = slider.getValue(); break;
            case "radius": item.radius = slider.getValue(); break;
            case "fontSize": item.fontSize = slider.getValue(); break;
            case "value": item.value = slider.getValue(); break;
        }
        item.keepInside(app.current); updateNumbers(item); app.canvas.repaint();
        if (!slider.getValueIsAdjusting()) app.changed();
    }
    private void customColor(boolean fill) {
        DesignItem item = editable(); if (item == null) return;
        Color color = app.chooseColor(fill ? "Component fill" : "Component text", fill ? PartRenderer.background(item, app.project) : PartRenderer.foreground(item, app.project));
        if (color != null) { if (fill) item.background = color.getRGB() & 0xFFFFFF; else item.foreground = color.getRGB() & 0xFFFFFF; app.changed(); }
    }
    private void updateNumbers(DesignItem item) {
        widthValue.setText(item.width + " px"); heightValue.setText(item.height + " px"); radiusValue.setText(item.radius < 0 ? "Theme" : item.radius + " px"); fontValue.setText(item.fontSize + " px"); progressValue.setText(item.value + "%");
    }
    public void showSelection() {
        if (app.current == null) return; updating = true; DesignItem item = app.canvas.selected();
        pagePanel.setVisible(item == null); itemPanel.setVisible(item != null); setText(pageName, app.current.name);
        title.setText(item == null ? "Page" : item.type);
        selectionInfo.setText(item == null ? "Click a part to edit it." : item.locked ? "Locked - unlock in Layers." : app.canvas.selection.size() > 1 ? app.canvas.selection.size() + " selected; editing last selected." : "");
        selectionInfo.setVisible(!selectionInfo.getText().isEmpty());
        if (item != null) {
            setText(name, item.name); setText(text, item.text); setText(detail, item.detail);
            width.setMaximum(app.current.width); height.setMaximum(app.current.height); width.setValue(item.width); height.setValue(item.height);
            radius.setMaximum(
                    Math.max(
                            Math.min(item.width, item.height) / 2,
                            app.project.corner(item)
                    )
            );
            radius.setValue(Math.min(radius.getMaximum(), app.project.corner(item))); fontSize.setValue(item.fontSize); value.setValue(item.value);
            checked.setSelected(item.checked); checked.setVisible(item.type.equals("Checkbox") || item.type.equals("Switch") || item.type.equals("Radio Button"));
            details.setVisible(item.type.equals("Card") || item.type.equals("List Item") || item.type.equals("Dialog") || item.type.equals("Snackbar"));
            shapeControls.setVisible(item.supportsCornerRadius()); valuePanel.setVisible(item.type.equals("Slider") || item.type.equals("Progress") || item.type.equals("Loading"));
            if (!fieldsFor.equals(item.id)) { buildFields(item); fieldsFor = item.id; }
            skinPanel.setVisible(item.supportsSkin()); skinName.setText(item.hasSkin() ? item.skinName : "No PNG uploaded");
            enabled(itemPanel, !item.locked); fill.setEnabled(!item.locked && !item.hasSkin()); updateNumbers(item);
        }
        if (item == null) fieldsFor = "";
        updating = false; revalidate(); repaint();
    }
    private void enabled(Container container, boolean enabled) { for (Component component : container.getComponents()) { component.setEnabled(enabled); if (component instanceof Container child) enabled(child, enabled); } }
    private void setText(javax.swing.text.JTextComponent field, String value) { if (!field.getText().equals(value)) field.setText(value); }
    public void invalidateFields() { fieldsFor = ""; }
    public void showGeometry() {
        DesignItem item = app.canvas.selected();

        if (item == null) return;

        updating = true;

        try {
            width.setValue(item.width);
            height.setValue(item.height);

            radius.setMaximum(
                    Math.max(
                            Math.min(item.width, item.height) / 2,
                            app.project.corner(item)
                    )
            );

            radius.setValue(app.project.corner(item));
            updateNumbers(item);

        } finally {
            updating = false;
        }
    }
    private JComboBox<Object> trigger(String selected, java.util.function.Consumer<String> changed) {
        java.util.ArrayList<Object> choices = new java.util.ArrayList<>(); choices.add("No trigger"); choices.addAll(app.project.pages);
        JComboBox<Object> box = UiStyle.combo(choices.toArray());
        DesignPage page = app.project.findPage(selected); box.setSelectedItem(page == null ? "No trigger" : page);
        box.addActionListener(e -> { if (!updating) { Object choice = box.getSelectedItem(); changed.accept(choice instanceof DesignPage p ? p.id : ""); app.changed(); } });
        return box;
    }
    private void buildFields(DesignItem item) {
        optionFields.removeAll(); triggerFields.removeAll();
        boolean separate = item.type.equals("Navigation Rail") || item.type.equals("Toolbar") || item.type.equals("Dropdown");
        textField.setVisible(!separate);
        boolean options = separate || item.type.equals("Split Button") || item.type.equals("Dialog") || item.type.equals("Snackbar");
        optionFields.setVisible(options);
        while (item.optionTargets.size() < item.options.size()) item.optionTargets.add("");
        for (int i = 0; options && i < item.options.size(); i++) {
            final int index = i; JTextField label = UiStyle.textField(); label.setText(item.options.get(i)); app.limit(label, 200);
            String heading = item.type.equals("Dropdown") || item.type.equals("Split Button") ? "Option " : "Text ";
            optionFields.add(UiStyle.field(heading + (i + 1), label));
            app.listen(label, () -> { if (editable() == item) { item.options.set(index, label.getText()); if (separate) item.text = String.join(", ", item.options); app.changed(); } });
            if (item.hasOptionTriggers()) optionFields.add(UiStyle.field("Tap action", trigger(item.optionTargets.get(i), target -> { if (!item.locked) item.optionTargets.set(index, target); })));
            if ((item.type.equals("Dropdown") || item.type.equals("Split Button")) && item.options.size() > 1) {
                JButton remove = UiStyle.button("Remove option " + (i + 1), false);
                remove.addActionListener(e -> { if (editable() == item) { item.options.remove(index); item.optionTargets.remove(index); fieldsFor = ""; showSelection(); app.changed(); } }); optionFields.add(remove);
            }
            optionFields.add(Box.createVerticalStrut(10));
        }
        if (item.type.equals("Dropdown") || item.type.equals("Split Button")) {
            JButton add = UiStyle.button("Add option", false); add.setEnabled(item.options.size() < 20);
            add.addActionListener(e -> { if (editable() == item && item.options.size() < 20) { item.options.add("Option " + (item.options.size() + 1)); item.optionTargets.add(""); fieldsFor = ""; showSelection(); app.changed(); } }); optionFields.add(add);
        }
        if (item.hasTapTrigger()) triggerFields.add(UiStyle.field("Tap action", trigger(item.target, target -> { if (!item.locked) item.target = target; })));
        else triggerFields.add(UiStyle.hint(item.hasOptionTriggers() ? "Set a tap action for each text above." : "No trigger"));
    }
    private void uploadPng() {
        DesignItem item = editable(); if (item == null || !item.supportsSkin()) return;
        JFileChooser chooser = new JFileChooser(); chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PNG image", "png"));
        if (chooser.showOpenDialog(app) != JFileChooser.APPROVE_OPTION) return;
        java.nio.file.Path path = chooser.getSelectedFile().toPath(); skinName.setText("Loading PNG...");
        new SwingWorker<String, Void>() {
            protected String doInBackground() throws Exception { return PngAssets.read(path); }
            protected void done() {
                try {
                    String data = get();
                    if (app.project.pages.stream().anyMatch(page -> page.items.contains(item)) && !item.locked) {
                        item.skin = data; item.skinName = path.getFileName().toString(); showSelection(); app.changed();
                    }
                } catch (Exception error) { showSelection(); JOptionPane.showMessageDialog(app, "PNG could not be loaded: " + (error.getCause() == null ? error.getMessage() : error.getCause().getMessage())); }
            }
        }.execute();
    }

}
