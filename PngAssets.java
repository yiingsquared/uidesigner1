import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.Base64;
import java.util.HashMap;

public class PngAssets {
    private static final HashMap<String, String> icons = new HashMap<>();
    public static String read(Path path) throws IOException {
        if (Files.size(path) > 2_000_000) throw new IOException("Choose a PNG smaller than 2 MB.");
        byte[] bytes = Files.readAllBytes(path); validate(bytes); return Base64.getEncoder().encodeToString(bytes);
    }
    public static void validate(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new IOException("The file is not a valid PNG.");
            ImageReader reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase("png")) throw new IOException("Only PNG images are supported.");
                reader.setInput(input); int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width > 4096 || height > 4096 || (long) width * height > 4_000_000) throw new IOException("Use a PNG up to 4096 px per side and 4 million pixels.");
                if (reader.read(0) == null) throw new IOException("Cannot decode PNG.");
            } finally { reader.dispose(); }
        }
    }
    public static synchronized String icon(String name) {
        if (icons.containsKey(name)) return icons.get(name);
        String data = ""; javax.swing.ImageIcon source = IconStore.load(name, 32);
        if (source != null) try {
            BufferedImage image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB); Graphics2D g = image.createGraphics(); g.drawImage(source.getImage(), 0, 0, 32, 32, null); g.dispose();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); ImageIO.write(image, "png", bytes); data = Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException ignored) { }
        icons.put(name, data); return data;
    }
    public static synchronized void clear() { icons.clear(); }
}
