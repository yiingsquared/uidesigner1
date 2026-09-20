import java.util.UUID;

//components data - independent; -1 means inherit the theme color or shape
public class DesignItem {
    String id = UUID.randomUUID().toString();
    String type, text, name;
    String detail = "", icon = "", target = "", group = "", variant = "Filled";
    String skin = "", skinName = "";
    java.util.ArrayList<String> options = new java.util.ArrayList<>();
    java.util.ArrayList<String> optionTargets = new java.util.ArrayList<>();
    int x = 24, y = 24, width, height, fontSize = 14, radius = -1;
    int background = -1, foreground = -1, value = 40;
    boolean checked, visible = true, locked;

    public DesignItem(String type) {
        PartCatalog.Part part = PartCatalog.find(type);
        this.type = type; name = type; text = type; icon = part.icon(); width = part.width(); height = part.height();
        switch (type) {
            case "Button": text = "Continue"; break;
            case "Icon Button": text = "Edit"; break;
            case "FAB": text = "Add"; break;
            case "Split Button": text = "Create"; value = 0; break;
            case "Chip": text = "Design"; break;
            case "Top App Bar": text = "My application"; break;
            case "Navigation Bar": case "Navigation Rail": case "Tabs": text = "Home, Tasks, Profile"; value = 0; break;
            case "Toolbar": text = "Edit, Share, Save"; value = 0; break;
            case "Carousel": value = 0; break;
            case "Search Bar": text = "Search"; break;
            case "Card": text = "Card title"; detail = "Add a short description."; break;
            case "List Item": text = "List item"; detail = "Supporting text"; break;
            case "Dialog": text = "Save changes?"; detail = "Your changes will be saved locally."; break;
            case "Snackbar": text = "Changes saved"; detail = "Dismiss"; break;
            case "Text Field": text = "Enter some text"; break;
            case "Dropdown": text = "Option one, Option two, Option three"; value = 0; break;
            case "Switch": text = "Notifications"; break;
            case "Checkbox": text = "Complete a task"; break;
            case "Radio Button": text = "Choose this option"; break;
            case "Date Picker": text = "MM/DD/YYYY"; break;
            case "Time Picker": text = "Choose a time"; break;
            case "Text": text = "Your heading"; fontSize = 26; break;
            case "Image": text = "Image placeholder"; break;
            case "Camera": text = "Camera placeholder"; break;
            case "Map": text = "Map placeholder"; break;
            case "Divider": text = ""; break;
        }
        if (type.equals("Navigation Rail") || type.equals("Toolbar") || type.equals("Dropdown")) {
            for (String label : text.split(",", -1)) options.add(label.trim());
        } else if (type.equals("Split Button")) options.addAll(java.util.List.of("Option one", "Option two", "Option three"));
        else if (type.equals("Dialog")) options.addAll(java.util.List.of("Cancel", "Confirm"));
        else if (type.equals("Snackbar")) options.add("Dismiss");
        while (optionTargets.size() < options.size()) optionTargets.add("");
    }
    public DesignItem duplicate() {
        DesignItem copy = snapshot(); copy.id = UUID.randomUUID().toString(); return copy;
    }
    public DesignItem snapshot() {
        DesignItem copy = new DesignItem(type);
        copy.id = id; copy.text = text; copy.name = name; copy.detail = detail; copy.icon = icon;
        copy.target = target; copy.group = group; copy.variant = variant;
        copy.skin = skin; copy.skinName = skinName;
        copy.options = new java.util.ArrayList<>(options); copy.optionTargets = new java.util.ArrayList<>(optionTargets);
        copy.x = x; copy.y = y; copy.width = width; copy.height = height;
        copy.fontSize = fontSize; copy.radius = radius; copy.background = background; copy.foreground = foreground;
        copy.value = value; copy.checked = checked; copy.visible = visible; copy.locked = locked;
        return copy;
    }
    public void keepInside(DesignPage page) {
        width = Math.max(24, Math.min(page.width, width)); height = Math.max(8, Math.min(page.height, height));
        x = Math.max(0, Math.min(page.width - width, x)); y = Math.max(0, Math.min(page.height - height, y));
    }
    public boolean contains(int px, int py) { return visible && px >= x && py >= y && px < x + width && py < y + height; }
    public boolean isAction() { return PartCatalog.find(type).category().equals("Actions") || type.equals("List Item") || type.equals("Card"); }
    public boolean supportsSkin() {
        String category = PartCatalog.find(type).category();

        return category.equals("Actions")
                || category.equals("Navigation")
                || category.equals("Containment")
                || java.util.Set.of(
                "Text Field",
                "Dropdown",
                "Date Picker",
                "Time Picker",
                "Image",
                "Camera",
                "Map"
        ).contains(type);
    }
    public boolean supportsCornerRadius() {
        return supportsSkin() && !hasSkin();
    }
    //public boolean supportsSkin() { return PartCatalog.find(type).category().equals("Actions") || PartCatalog.find(type).category().equals("Containment") || java.util.Set.of("Text Field", "Dropdown", "Date Picker", "Time Picker", "Image", "Camera", "Map").contains(type); }
    public boolean hasSkin() { return !skin.isEmpty(); }
    public boolean hasTapTrigger() { return PartCatalog.find(type).category().equals("Actions") && !type.equals("Split Button"); }
    public boolean hasOptionTriggers() {
        return java.util.Set.of(
                "Navigation Rail",
                "Toolbar",
                "Dialog",
                "Snackbar",
                "Split Button"
        ).contains(type);
    }
    //public boolean hasOptionTriggers() { return java.util.Set.of("Navigation Rail", "Toolbar", "Dialog", "Snackbar").contains(type); }
    @Override public String toString() { return name; }
}
