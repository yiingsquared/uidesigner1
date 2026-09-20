import java.awt.Color;
import java.util.ArrayList;

//project theme, pages, and other information; panels use blue+grey+white as default style
public class DesignProject {
    String name = "Study Planner", font = "System", shape = "Rounded";
    int seed = 0x0B57D0, contrast, surfaceOverride = -1, textOverride = -1, accentOverride = -1;
    boolean dark, emphasized = true;
    ArrayList<DesignPage> pages = new ArrayList<>();
    public DesignPage findPage(String id) { for (DesignPage page : pages) if (page.id.equals(id)) return page; return null; }
    public DesignProject snapshot() {
        DesignProject copy = new DesignProject(); copy.name = name; copy.font = font; copy.shape = shape; copy.seed = seed;
        copy.contrast = contrast; copy.surfaceOverride = surfaceOverride; copy.textOverride = textOverride; copy.accentOverride = accentOverride;
        copy.dark = dark; copy.emphasized = emphasized; for (DesignPage page : pages) copy.pages.add(page.snapshot()); return copy;
    }
    public void removePage(DesignPage page) {
        if (pages.size() <= 1) return; pages.remove(page);
        for (DesignPage remaining : pages) for (DesignItem item : remaining.items) {
            if (item.target.equals(page.id)) item.target = "";
            item.optionTargets.replaceAll(target -> target.equals(page.id) ? "" : target);
        }
    }
    public Color accent() {
        if (accentOverride >= 0) return new Color(accentOverride);
        Color color = new Color(seed);
        return dark ? UiStyle.mix(color, Color.WHITE, .55) : UiStyle.mix(color, Color.BLACK, contrast * .09);
    }
    public Color surface() { return surfaceOverride >= 0 ? new Color(surfaceOverride) : dark ? new Color(0x171C25) : new Color(0xFAFBFF); }
    public Color ink() { return textOverride >= 0 ? new Color(textOverride) : dark ? new Color(0xE7EDF8) : new Color(0x202B3D); }
    public Color soft() { return UiStyle.mix(accent(), surface(), dark ? .83 : .90); }
    public int corner(DesignItem item) { return item.radius >= 0 ? item.radius : shape.equals("Square") ? 0 : shape.equals("Full") ? Math.min(item.width, item.height) / 2 : 16; }
    public static DesignProject sample() {
        DesignProject project = new DesignProject(); DesignPage home = new DesignPage("My planner"), session = new DesignPage("Study session");
        project.pages.add(home); project.pages.add(session);
        add(home, "Top App Bar", 20, 20, 372, 56, "My workspace", "");
        add(home, "Text", 28, 112, 356, 44, "Study Planner", "").fontSize = 30;
        add(home, "Text", 28, 170, 356, 28, "Your tasks for today", "").fontSize = 15;
        add(home, "Card", 24, 232, 364, 182, "Next study session", "Computer science · 30 minutes");
        add(home, "Button", 44, 340, 184, 48, "Start session", "").target = session.id;
        add(home, "Text", 28, 458, 350, 38, "Today's tasks", "").fontSize = 22;
        add(home, "Checkbox", 28, 516, 356, 48, "Review Java classes", "").checked = true;
        add(home, "Checkbox", 28, 572, 356, 48, "Finish the design sketch", "");
        add(home, "Text Field", 24, 658, 364, 56, "Add a reminder", "");
        DesignItem toolbar = add(home, "Toolbar", 20, 794, 372, 72, "Planner, Notes, Profile", "");
        toolbar.options = new java.util.ArrayList<>(java.util.List.of("Planner", "Notes", "Profile"));
        toolbar.optionTargets = new java.util.ArrayList<>(java.util.List.of(home.id, session.id, home.id));
        add(session, "Top App Bar", 20, 20, 372, 56, "Study session", "");
        add(session, "Text", 28, 130, 356, 48, "Computer science", "");
        add(session, "Card", 24, 218, 364, 154, "Practice Java", "Classes, methods and Swing events.");
        add(session, "Divider", 28, 412, 356, 12, "", "");
        add(session, "Checkbox", 28, 482, 356, 48, "Session complete", "");
        add(session, "Text Field", 24, 562, 364, 56, "What did you learn?", "");
        add(session, "Button", 24, 668, 364, 48, "Back to planner", "").target = home.id;
        return project;
    }
    private static DesignItem add(DesignPage page, String type, int x, int y, int width, int height, String text, String detail) {
        DesignItem item = new DesignItem(type); item.x = x; item.y = y; item.width = width; item.height = height;
        item.text = text; item.detail = detail; page.items.add(item); return item;
    }
}
