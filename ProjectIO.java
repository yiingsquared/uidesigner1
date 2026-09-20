import java.io.*;
import java.nio.file.*;
import java.util.HashSet;
import java.util.Properties;

//Java properties; saves png and triggers
public class ProjectIO {
    public static void save(DesignProject project, Path file) throws IOException {
        Properties p = new Properties();
        put(p, "version", 3); put(p, "name", project.name); put(p, "font", project.font); put(p, "shape", project.shape);
        java.util.LinkedHashMap<String, Integer> assets = new java.util.LinkedHashMap<>();
        for (DesignPage page : project.pages) for (DesignItem item : page.items) if (item.hasSkin()) assets.computeIfAbsent(item.skin, data -> assets.size());
        long assetSize = assets.keySet().stream().mapToLong(String::length).sum();
        if (assetSize > 24_000_000) throw new IOException("PNG assets exceed 24 MB. Use smaller images.");
        put(p, "assets", assets.size()); for (var entry : assets.entrySet()) put(p, "asset." + entry.getValue(), entry.getKey());
        put(p, "seed", project.seed); put(p, "contrast", project.contrast); put(p, "dark", project.dark); put(p, "emphasized", project.emphasized);
        put(p, "surface", project.surfaceOverride); put(p, "ink", project.textOverride); put(p, "accent", project.accentOverride); put(p, "pages", project.pages.size());
        for (int pageIndex = 0; pageIndex < project.pages.size(); pageIndex++) {
            DesignPage page = project.pages.get(pageIndex); String k = "page." + pageIndex + ".";
            put(p, k + "id", page.id); put(p, k + "name", page.name); put(p, k + "width", page.width); put(p, k + "height", page.height);
            put(p, k + "background", page.background); put(p, k + "items", page.items.size());
            for (int i = 0; i < page.items.size(); i++) {
                DesignItem item = page.items.get(i); String a = k + "item." + i + ".";
                put(p, a + "id", item.id); put(p, a + "type", item.type); put(p, a + "name", item.name);
                put(p, a + "text", item.text); put(p, a + "detail", item.detail); put(p, a + "icon", item.icon); put(p, a + "target", item.target);
                put(p, a + "group", item.group); put(p, a + "variant", item.variant);
                put(p, a + "skin", item.hasSkin() ? assets.get(item.skin) : -1); put(p, a + "skinName", item.skinName);
                put(p, a + "options", item.options.size());
                for (int option = 0; option < item.options.size(); option++) {
                    put(p, a + "option." + option, item.options.get(option));
                    put(p, a + "trigger." + option, option < item.optionTargets.size() ? item.optionTargets.get(option) : "");
                }
                put(p, a + "x", item.x); put(p, a + "y", item.y); put(p, a + "width", item.width); put(p, a + "height", item.height);
                put(p, a + "fontSize", item.fontSize); put(p, a + "radius", item.radius); put(p, a + "background", item.background); put(p, a + "color", item.foreground);
                put(p, a + "value", item.value); put(p, a + "checked", item.checked); put(p, a + "visible", item.visible); put(p, a + "locked", item.locked);
            }
        }
        Path target = file.toAbsolutePath(); Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "uidesigner-blue-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp)) { p.store(writer, "uidesigner project v3"); }
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
    private static void put(Properties p, String k, Object v) { p.setProperty(k, String.valueOf(v)); }
    public static DesignProject load(Path file) throws IOException {
        if (Files.size(file) > 32_000_000) throw new IOException("Project exceeds 32 MB.");
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) { p.load(reader); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid project text.", e); }
        boolean legacy = "1".equals(p.getProperty("version"));
        if (!legacy && !"2".equals(p.getProperty("version")) && !"3".equals(p.getProperty("version"))) throw new IOException("This is not a supported uidesigner project.");
        java.util.ArrayList<String> assets = new java.util.ArrayList<>();
        for (int i = 0, count = num(p, "assets", 0, 0, 1200); i < count; i++) {
            String data = text(p, "asset." + i, "", 2_700_000);
            try { PngAssets.validate(java.util.Base64.getDecoder().decode(data)); }
            catch (IllegalArgumentException e) { throw new IOException("Invalid PNG data.", e); }
            assets.add(data);
        }
        DesignProject project = new DesignProject(); project.name = text(p, "name", "Untitled", 80);
        project.font = text(p, "font", "System", 40); project.shape = text(p, "shape", "Rounded", 20);
        project.seed = num(p, "seed", 0x0B57D0, 0, 0xFFFFFF); project.contrast = num(p, "contrast", 0, 0, 2);
        project.surfaceOverride = num(p, "surface", -1, -1, 0xFFFFFF); project.textOverride = num(p, "ink", -1, -1, 0xFFFFFF); project.accentOverride = num(p, "accent", -1, -1, 0xFFFFFF);
        project.dark = bool(p, "dark", false); project.emphasized = bool(p, "emphasized", true);
        HashSet<String> ids = new HashSet<>();
        int pages = num(p, "pages", -1, 1, 12);
        for (int pageIndex = 0; pageIndex < pages; pageIndex++) {
            String k = "page." + pageIndex + "."; DesignPage page = new DesignPage(text(p, k + "name", "Page", 80));
            page.id = id(p, k + "id", ids); page.width = num(p, k + "width", 412, 200, 1600); page.height = num(p, k + "height", 892, 200, 1600);
            page.background = num(p, k + "background", -1, -1, 0xFFFFFF); int items = num(p, k + "items", -1, 0, 100);
            for (int i = 0; i < items; i++) {
                String a = k + "item." + i + "."; String type = text(p, a + "type", "", 40);
                if (legacy && type.equals("Text input")) type = "Text Field";
                if (legacy && type.equals("Navigation")) type = "Navigation Bar";
                DesignItem item;
                try { item = new DesignItem(type); } catch (IllegalArgumentException e) { throw new IOException(e.getMessage()); }
                item.id = id(p, a + "id", ids); item.name = text(p, a + "name", type, 80); item.text = text(p, a + "text", "", 1000);
                item.detail = text(p, a + "detail", "", 1000); item.icon = text(p, a + "icon", item.icon, 100);
                if (!item.icon.isEmpty() && !item.icon.matches("[a-zA-Z0-9_-]+\\.png")) throw new IOException("Invalid PNG filename.");
                item.target = text(p, a + "target", "", 100); item.group = text(p, a + "group", "", 100); item.variant = text(p, a + "variant", "Filled", 20);
                int skin = num(p, a + "skin", -1, -1, assets.size() - 1); item.skin = skin < 0 ? "" : assets.get(skin); item.skinName = text(p, a + "skinName", "", 260);
                if (p.containsKey(a + "options")) {
                    int count = num(p, a + "options", 0, 0, 20); item.options.clear(); item.optionTargets.clear();
                    for (int option = 0; option < count; option++) { item.options.add(text(p, a + "option." + option, "", 200)); item.optionTargets.add(text(p, a + "trigger." + option, "", 100)); }
                } else if (type.equals("Navigation Rail") || type.equals("Toolbar") || type.equals("Dropdown") || type.equals("Navigation Bar") || type.equals("Tabs")) {
                    item.options = new java.util.ArrayList<>(); item.optionTargets.clear();
                    for (String label : item.text.split(",", -1)) { if (item.options.size() == 20) break; item.options.add(label.trim()); item.optionTargets.add(""); }
                }
                item.x = num(p, a + "x", 24, 0, 1600); item.y = num(p, a + "y", 24, 0, 1600);
                item.width = num(p, a + "width", item.width, 24, 1600); item.height = num(p, a + "height", item.height, 8, 1600);
                item.fontSize = num(p, a + "fontSize", 14, 8, 72); item.radius = num(p, a + "radius", -1, -1, 800);
                item.background = num(p, a + "background", -1, -1, legacy ? 255 : 0xFFFFFF); item.foreground = num(p, a + "color", -1, -1, legacy ? 255 : 0xFFFFFF);
                if (legacy) { if (item.background >= 0) item.background = item.background * 0x010101; if (item.foreground >= 0) item.foreground = item.foreground * 0x010101; }
                item.value = num(p, a + "value", 40, 0, 100); item.checked = bool(p, a + "checked", false);
                item.visible = bool(p, a + "visible", true); item.locked = bool(p, a + "locked", false); item.keepInside(page); page.items.add(item);
            }
            project.pages.add(page);
        }
        for (DesignPage page : project.pages) for (DesignItem item : page.items) {
            if (project.findPage(item.target) == null) item.target = "";
            item.optionTargets.replaceAll(target -> project.findPage(target) == null ? "" : target);
        }
        return project;
    }
    private static String id(Properties p, String k, HashSet<String> ids) throws IOException {
        String id = text(p, k, "", 100); if (id.isBlank() || !ids.add(id)) throw new IOException("Missing or repeated id."); return id;
    }
    private static String text(Properties p, String k, String fallback, int max) throws IOException {
        String value = p.getProperty(k, fallback); if (value.length() > max) throw new IOException("Text too long: " + k); return value;
    }
    private static int num(Properties p, String k, int fallback, int min, int max) throws IOException {
        try { int n = Integer.parseInt(p.getProperty(k, "" + fallback)); if (n < min || n > max) throw new NumberFormatException(); return n; }
        catch (NumberFormatException e) { throw new IOException("Invalid number: " + k); }
    }
    private static boolean bool(Properties p, String k, boolean fallback) throws IOException {
        String s = p.getProperty(k, "" + fallback); if (!s.equals("true") && !s.equals("false")) throw new IOException("Invalid switch: " + k); return Boolean.parseBoolean(s);
    }
}
