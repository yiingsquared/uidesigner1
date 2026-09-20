import javax.swing.ImageIcon;
import java.awt.Font;
import java.awt.Image;
import java.net.URL;
import java.util.HashMap;


//place icon png under src, if missing then go back to null
public class IconStore {
    private static final HashMap<String, ImageIcon> icons = new HashMap<>();
    private static final HashMap<String, Font> fonts = new HashMap<>();
    public static synchronized ImageIcon load(String file, int size) {
        if (file == null || !file.matches("[a-zA-Z0-9_-]+\\.png")) return null;
        String key = file + ":" + size;
        if (icons.containsKey(key)) return icons.get(key);
        URL resource = IconStore.class.getResource("/icons/" + file);
        if (resource == null) resource = IconStore.class.getResource("/" + file);
        ImageIcon result = null;
        if (resource != null) {
            ImageIcon original = new ImageIcon(resource);
            if (original.getIconWidth() > 0) {
                double scale = Math.min(size / (double) original.getIconWidth(), size / (double) original.getIconHeight());
                result = new ImageIcon(original.getImage().getScaledInstance(Math.max(1, (int) (original.getIconWidth() * scale)), Math.max(1, (int) (original.getIconHeight() * scale)), Image.SCALE_SMOOTH));
            }
        }
        icons.put(key, result); return result;
    }
    public static synchronized Font font(String name, int style, int size) {
        if (!fonts.containsKey(name)) {
            Font result = new Font(name.equals("Roboto Serif") ? "Serif" : "SansSerif", Font.PLAIN, 14);
            String file = name.equals("Roboto Flex") ? "RobotoFlex.ttf" : name.equals("Roboto Serif") ? "RobotoSerif.ttf" : "Roboto.ttf";
            if (!name.equals("System")) {
                URL resource = IconStore.class.getResource("/fonts/" + file);
                if (resource == null) {
                    System.err.println("Missing font: /fonts/" + file);
                } else {
                    try (var stream = resource.openStream()) {
                        result = Font.createFont(Font.TRUETYPE_FONT, stream);

                        System.out.println(
                                "Loaded font: " + name + " -> " + result.getFontName()
                        );
                    } catch (Exception error) {
                        System.err.println(
                                "Cannot load " + file + ": " + error.getMessage()
                        );
                    }
                }
            }
            fonts.put(name, result);
        }
        return fonts.get(name).deriveFont(style, (float) size);
    }
    public static void clear() { synchronized (IconStore.class) { icons.clear(); fonts.clear(); } PngAssets.clear(); }
}
