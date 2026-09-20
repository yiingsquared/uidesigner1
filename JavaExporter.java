import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class JavaExporter {
    public record Result(String values, String code) { }
    private static final class Assets {
        final LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        String reference(String data) {
            return data.isEmpty() ? "\"\"" : entries.computeIfAbsent(data, key -> "ASSET_" + entries.size());
        }
        String source() {
            StringBuilder out = new StringBuilder();
            for (var entry : entries.entrySet()) {
                out.append("    private static final String ").append(entry.getValue()).append(" = ").append(entry.getValue()).append("();\n");
                out.append("    private static String ").append(entry.getValue()).append("() {\n        return String.join(\"\", new String[]{\n");
                for (int i = 0; i < entry.getKey().length(); i += 12000)
                    out.append("            ").append(quote(entry.getKey().substring(i, Math.min(i + 12000, entry.getKey().length())))).append(",\n");
                out.append("        });\n    }\n");
            }
            return out.toString();
        }
    }
    public static Result export(DesignProject original, String selectedPageId) {
        DesignProject project = original.snapshot(); Assets assets = new Assets();
        StringBuilder values = new StringBuilder(), methods = new StringBuilder(), create = new StringBuilder();
        StringBuilder fields =
                new StringBuilder("    protected GuiRuntime.AppView view;\n");

        StringBuilder bindings = new StringBuilder();

        HashMap<String, Integer> counts = new HashMap<>();
        Graphics2D metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB).createGraphics(); GuiRuntime.smooth(metrics);
        String fontAsset = assets.reference(fontData(project.font));

        Font actualFont = IconStore.font(project.font, Font.PLAIN, 14);
        create.append(
                "    private GuiRuntime.AppView createView() {\n"
                        + "        java.util.List<GuiRuntime.Page> pages = new java.util.ArrayList<>();\n"
        );

        try {
            for (int pi = 0; pi < project.pages.size(); pi++) {
                DesignPage page = project.pages.get(pi); Color background = page.background < 0 ? project.surface() : new Color(page.background);
                values.append("PAGE ").append(pi + 1).append(": ").append(page.name).append("\n  x: 0  y: 0\n  width: ").append(page.width).append("  height: ").append(page.height).append("\n  background: ").append(hex(background)).append("\n\n");
                fields.append("    protected GuiRuntime.Page page")
                        .append(pi + 1)
                        .append(";\n");
                create.append("        pages.add(page").append(pi).append("());\n");
                StringBuilder pageMethod = new StringBuilder("    private GuiRuntime.Page page" + pi + "() {\n        GuiRuntime.Page page = new GuiRuntime.Page(" + quote(page.id) + ", " + quote(page.name) + ", " + page.width + ", " + page.height + ", " + colour(background) + ");\n");
                for (int ii = 0; ii < page.items.size(); ii++) {
                    DesignItem item = page.items.get(ii); GuiRuntime.Part part = PartRenderer.runtime(item, project); String method = "part" + pi + "_" + ii;
                    String kind = item.type.replace(" ", "");
                    kind = Character.toLowerCase(kind.charAt(0)) + kind.substring(1);

                    String variable = kind + counts.merge(kind, 1, Integer::sum);

                    fields.append("    protected GuiRuntime.Part ")
                            .append(variable)
                            .append("Data;\n");

                    fields.append("    protected GuiRuntime.PartView ")
                            .append(variable)
                            .append(";\n");

                    bindings.append("        ")
                            .append(variable)
                            .append(" = view.getPart(")
                            .append(variable)
                            .append("Data.id);\n");
                    pageMethod.append("        page.parts.add(").append(method).append("());\n");
                    values.append("  ").append(ii + 1).append(". ").append(item.type).append(" - ").append(item.name).append("\n    x: ").append(item.x).append("  y: ").append(item.y).append("\n    width: ").append(item.width).append("  height: ").append(item.height).append("\n");
                    if (item.hasSkin()) values.append("    PNG: ").append(item.skinName).append("\n");
                    else {
                        if (!Set.of("Text", "Divider", "Slider", "Switch", "Checkbox", "Radio Button").contains(item.type)) values.append("    background: ").append(hex(part.background)).append("\n");
                        values.append("    foreground: ").append(hex(part.foreground)).append("\n");
                    }
                    for (GuiRuntime.TextRun row : GuiRuntime.textRuns(metrics, part)) {
                        if (row.text().isEmpty()) continue;
                        values.append("    Text: ").append(quote(row.text())).append("\n      x: ").append(item.x + row.x()).append("  y: ").append(item.y + row.baseline() - row.ascent()).append("\n      width: ").append(row.width()).append("  height: ").append(row.height()).append("\n      font size: ").append(row.font().getSize()).append("  baselineY: ").append(item.y + row.baseline()).append("\n");
                        if (!item.hasSkin()) values.append("      colour: ").append(hex(row.colour())).append("\n");
                    }
                    if (!part.target.isEmpty()) values.append("    Tap action: ").append(pageName(project, part.target)).append("\n");
                    for (int i = 0; i < part.targets.length; i++) if (!part.targets[i].isEmpty()) values.append("    Text ").append(i + 1).append(" tap: ").append(pageName(project, part.targets[i])).append("\n");
                    values.append("\n");
                    methods.append("    private GuiRuntime.Part ").append(method).append("() {\n        GuiRuntime.Part part = new GuiRuntime.Part();\n");
                    field(methods, "id", quote(part.id)); field(methods, "type", quote(part.type)); field(methods, "text", quote(part.text)); field(methods, "detail", quote(part.detail));
                    field(methods, "x", part.x); field(methods, "y", part.y); field(methods, "width", part.width); field(methods, "height", part.height); field(methods, "radius", part.radius);
                    field(methods, "value", part.value); field(methods, "checked", part.checked); field(methods, "visible", part.visible); field(methods, "bold", part.bold);
                    field(methods, "target", quote(part.target)); field(methods, "options", strings(part.options)); field(methods, "targets", strings(part.targets));
                    field(methods, "skin", assets.reference(part.skin)); field(methods, "icon", assets.reference(part.icon));
                    field(methods, "background", colour(part.background)); field(methods, "foreground", colour(part.foreground)); field(methods, "accent", colour(part.accent)); field(methods, "soft", colour(part.soft)); field(methods, "line", colour(part.line));
                    field(methods, "font", "PROJECT_FONT.deriveFont(" + part.font.getStyle() + ", " + part.font.getSize() + "f)");
                    methods.append("        ")
                            .append(variable)
                            .append("Data = part;\n");
                    methods.append("        return part;\n    }\n\n");
                }
                pageMethod.append("        page")
                        .append(pi + 1)
                        .append(" = page;\n");

                pageMethod.append("        return page;\n    }\n\n");
                methods.append(pageMethod);
            }
        } finally { metrics.dispose(); }
        create.append("        return new GuiRuntime.AppView(pages, ").append(quote(selectedPageId)).append(");\n    }\n\n");
        String runtime = runtimeSource(); int bodyStart = runtime.indexOf("public class GuiRuntime {");
        if (bodyStart < 0) throw new IllegalStateException("GUI runtime source is unavailable.");
        String runtimeBody = runtime.substring(bodyStart).replaceFirst("public class GuiRuntime", "public static class GuiRuntime");
        String windowCode = """
            public GeneratedGUI() {
                super(%s);
                setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

                view = createView();
                setContentPane(view);

        %s
                configureActions();

                pack();

                Rectangle screen = GraphicsEnvironment
                        .getLocalGraphicsEnvironment()
                        .getMaximumWindowBounds();

                setSize(
                        Math.min(getWidth(), screen.width - 40),
                        Math.min(getHeight(), screen.height - 40)
                );

                setMinimumSize(new Dimension(180, 180));
                setLocationRelativeTo(null);
            }

            protected void configureActions() {
            }

            public static void main(String[] args) {
                SwingUtilities.invokeLater(
                        () -> new GeneratedGUI().setVisible(true)
                );
            }

        """.formatted(quote(project.name), bindings.toString());

        String code = runtime.substring(0, bodyStart)
                + "\npublic class GeneratedGUI extends JFrame {\n"
                + fields + "\n" + windowCode
                + assets.source()
                + "    private static final Font PROJECT_FONT = GuiRuntime.makeFont("
                + quote(actualFont.getName())
                + ", Font.PLAIN, 14, "
                + fontAsset
                + ");\n\n"
                + create + methods + "\n" + runtimeBody + "\n}\n";
        return new Result(values.toString(), code);
    }
    private static String pageName(DesignProject project, String id) { DesignPage page = project.findPage(id); return page == null ? "" : page.name; }
    private static void field(StringBuilder out, String name, Object value) { out.append("        part.").append(name).append(" = ").append(value).append(";\n"); }
    private static String strings(String[] values) { return "new String[]{" + String.join(", ", Arrays.stream(values).map(JavaExporter::quote).toList()) + "}"; }
    private static String fontData(String font) {
        if (font.equals("System")) return "";
        String name = font.equals("Roboto Flex") ? "RobotoFlex.ttf" : font.equals("Roboto Serif") ? "RobotoSerif.ttf" : "Roboto.ttf";
        try (InputStream stream = IconStore.class.getResourceAsStream("/fonts/" + name)) {
            if (stream == null) return ""; byte[] bytes = stream.readNBytes(10_000_001);
            if (bytes.length > 10_000_000) return "";
            Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(bytes));
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception error) { return ""; }
    }
    private static String runtimeSource() {
        try {
            try (InputStream stream = JavaExporter.class.getResourceAsStream("/runtime/GuiRuntime.txt")) {
                if (stream != null) return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            }
            Path source = Path.of("src", "GuiRuntime.java");
            if (Files.isRegularFile(source)) return Files.readString(source);
            throw new IOException("Missing runtime/GuiRuntime.txt; rebuild the project.");
        } catch (IOException error) { throw new IllegalStateException(error.getMessage(), error); }
    }
    static String hex(Color c) { return String.format(Locale.ROOT, "#%06X", c.getRGB() & 0xFFFFFF); }
    static String colour(Color c) { return String.format(Locale.ROOT, "new Color(0x%08X, true)", c.getRGB()); }
    static String quote(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) switch (c) {
            case '\\' -> out.append("\\\\"); case '"' -> out.append("\\\""); case '\n' -> out.append("\\n"); case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
            default -> { if (c < 32 || c == 127) out.append(String.format(Locale.ROOT, "\\%03o", (int) c)); else out.append(c); }
        }
        return out.append('"').toString();
    }
}
