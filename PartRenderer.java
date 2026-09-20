import java.awt.*;
import java.util.ArrayList;

public class PartRenderer {
    public static Color background(DesignItem item, DesignProject project) {
        if (item.background >= 0) return new Color(item.background);
        if (PartCatalog.find(item.type).category().equals("Actions")) return project.accent();
        if (project.surfaceOverride >= 0) return project.surface();
        return project.dark ? new Color(0x242D3A) : Color.WHITE;
    }
    public static Color foreground(DesignItem item, DesignProject project) {
        if (item.foreground >= 0) return new Color(item.foreground);
        if (project.textOverride >= 0 || item.hasSkin()) return project.ink();
        if (PartCatalog.find(item.type).category().equals("Actions")) return UiStyle.readable(background(item, project));
        return project.ink();
    }
    public static GuiRuntime.Part runtime(DesignItem item, DesignProject project) {
        GuiRuntime.Part part = new GuiRuntime.Part(); part.id = item.id; part.type = item.type; part.text = item.text; part.detail = item.detail;
        part.x = item.x; part.y = item.y; part.width = item.width; part.height = item.height; part.radius = project.corner(item);
        part.value = item.value; part.checked = item.checked; part.visible = item.visible; part.bold = project.emphasized;
        part.skin = item.skin; part.icon = item.hasSkin() ? "" : PngAssets.icon(item.icon);
        part.target = item.hasTapTrigger() ? item.target : "";
        part.options = item.options.toArray(new String[0]); part.targets = item.hasOptionTriggers() ? item.optionTargets.toArray(new String[0]) : new String[0];
        part.background = background(item, project); part.foreground = foreground(item, project); part.accent = project.accent(); part.soft = project.soft();
        part.line = UiStyle.mix(project.ink(), project.surface(), .82); part.font = IconStore.font(project.font, Font.PLAIN, item.fontSize);
        return part;
    }
    public static ArrayList<GuiRuntime.Page> runtime(DesignProject project) {
        ArrayList<GuiRuntime.Page> pages = new ArrayList<>();
        for (DesignPage source : project.pages) {
            GuiRuntime.Page page = new GuiRuntime.Page(source.id, source.name, source.width, source.height, source.background < 0 ? project.surface() : new Color(source.background));
            for (DesignItem item : source.items) page.parts.add(runtime(item, project)); pages.add(page);
        }
        return pages;
    }
    public static void paint(Graphics2D g, DesignItem item, DesignProject project) { GuiRuntime.paintPart(g, runtime(item, project), true); }
    public static String shorten(Graphics2D g, String text, int width) { return GuiRuntime.shorten(g.getFontMetrics(), text, width); }
    public static void center(Graphics2D g, String text, int x, int y, int width, int height) {
        String label = shorten(g, text, width); FontMetrics fm = g.getFontMetrics(); g.drawString(label, x + Math.max(0, (width - fm.stringWidth(label)) / 2), y + (height - fm.getHeight()) / 2 + fm.getAscent());
    }
}
