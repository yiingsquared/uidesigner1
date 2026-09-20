import java.util.ArrayList;
import java.util.List;


//list of components information: name, category, png, default values/sizes
public class PartCatalog {
    public record Part(String name, String category, String icon, int width, int height) { }
    public static final String[] CATEGORIES = {"Actions", "Navigation", "Containment", "Inputs", "Content"};
    public static final Part[] PARTS = {
            new Part("Button", "Actions", "button.png", 168, 48),
            new Part("FAB", "Actions", "fab.png", 64, 64),
            new Part("Split Button", "Actions", "split-button.png", 208, 48),
            new Part("Top App Bar", "Navigation", "app-bar.png", 364, 64),
            new Part("Navigation Rail", "Navigation", "navigation-rail.png", 88, 320),
            new Part("Toolbar", "Navigation", "toolbar.png", 280, 56),
            new Part("Search Bar", "Navigation", "search.png", 364, 56),
            new Part("Card", "Containment", "card.png", 364, 144),
            new Part("List Item", "Containment", "list-item.png", 364, 72),
            new Part("Box", "Containment", "box.png", 200, 120),
            new Part("Dialog", "Containment", "dialog.png", 328, 212),
            new Part("Snackbar", "Containment", "snackbar.png", 364, 56),
            new Part("Text Field", "Inputs", "text-field.png", 364, 56),
            new Part("Dropdown", "Inputs", "dropdown.png", 240, 56),
            new Part("Switch", "Inputs", "switch.png", 180, 48),
            new Part("Checkbox", "Inputs", "checkbox.png", 280, 44),
            new Part("Radio Button", "Inputs", "radio.png", 280, 44),
            new Part("Slider", "Inputs", "slider.png", 300, 44),
            new Part("Date Picker", "Inputs", "calendar.png", 250, 56),
            new Part("Time Picker", "Inputs", "clock.png", 200, 56),
            new Part("Text", "Content", "text.png", 364, 44),
            new Part("Image", "Content", "image.png", 364, 184),
            new Part("Carousel", "Content", "carousel.png", 364, 160),
            new Part("Camera", "Content", "camera.png", 280, 176),
            new Part("Map", "Content", "map.png", 364, 192),
            new Part("Divider", "Content", "divider.png", 364, 12)
    };
    private static final Part[] LEGACY = {
            new Part("Icon Button", "Actions", "icon-button.png", 48, 48),
            new Part("Chip", "Actions", "chip.png", 112, 36),
            new Part("Navigation Bar", "Navigation", "navigation-bar.png", 364, 72),
            new Part("Tabs", "Navigation", "tabs.png", 364, 48),
            new Part("Loading", "Progress", "loading.png", 56, 56),
            new Part("Progress", "Progress", "progress.png", 320, 16)
    };
    public static Part find(String type) {
        for (Part part : PARTS) if (part.name.equals(type)) return part;
        for (Part part : LEGACY) if (part.name.equals(type)) return part;
        throw new IllegalArgumentException("Unknown component: " + type);
    }
    public static List<Part> search(String category, String query) {
        List<Part> result = new ArrayList<>();
        for (Part part : PARTS) if (part.category.equals(category) && part.name.toLowerCase().contains(query.toLowerCase().trim())) result.add(part);
        return result;
    }
}
