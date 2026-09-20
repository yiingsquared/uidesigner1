import java.util.ArrayList;
import java.util.UUID;

public class DesignPage {
    String id = UUID.randomUUID().toString(), name;
    int width = 412, height = 892, background = -1;
    ArrayList<DesignItem> items = new ArrayList<>();
    public DesignPage(String name) { this.name = name; }
    public DesignItem findAt(int x, int y) {
        for (int i = items.size() - 1; i >= 0; i--) {
            DesignItem item = items.get(i); if (!item.locked && item.contains(x, y)) return item;
        }
        return null;
    }
    public void setDesktop(boolean desktop) {
        int oldWidth = width, oldHeight = height; width = desktop ? 1280 : 412; height = desktop ? 800 : 892;
        for (DesignItem item : items) {
            item.x = (int) Math.round(item.x * width / (double) oldWidth);
            item.y = (int) Math.round(item.y * height / (double) oldHeight); item.keepInside(this);
        }
    }
    public DesignPage snapshot() {
        DesignPage copy = new DesignPage(name); copy.id = id; copy.width = width; copy.height = height; copy.background = background;
        for (DesignItem item : items) copy.items.add(item.snapshot()); return copy;
    }
    public DesignPage duplicate() {
        DesignPage copy = snapshot(); copy.id = UUID.randomUUID().toString(); copy.name = name.substring(0, Math.min(75, name.length())) + " copy";
        java.util.HashMap<String, String> groups = new java.util.HashMap<>();
        for (DesignItem item : copy.items) {
            item.id = UUID.randomUUID().toString();
            if (!item.group.isEmpty()) item.group = groups.computeIfAbsent(item.group, key -> UUID.randomUUID().toString());
            if (item.target.equals(id)) item.target = copy.id;
            item.optionTargets.replaceAll(target -> target.equals(id) ? copy.id : target);
        }
        return copy;
    }
    @Override public String toString() { return name; }
}
