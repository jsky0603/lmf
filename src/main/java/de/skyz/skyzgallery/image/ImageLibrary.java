package de.skyz.skyzgallery.image;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Locale;

/** Only administrators can place files in the server-side image folder. */
public final class ImageLibrary {

    private static Path imageDirectory;

    private ImageLibrary() {
    }

    public static void initialize(Path gameDirectory) throws IOException {
        imageDirectory = gameDirectory.resolve("skyzgallery").resolve("images").toAbsolutePath().normalize();
        Files.createDirectories(imageDirectory);
    }

    public static Path directory() {
        if (imageDirectory == null) {
            throw new IllegalStateException("SkyZGallery ist noch nicht initialisiert.");
        }
        return imageDirectory;
    }

    public static ImageFile load(String requestedName) throws IOException {
        String file = cleanFileName(requestedName);
        Path path = directory().resolve(file).normalize();
        if (!path.startsWith(directory()) || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Datei nicht gefunden: skyzgallery/images/" + file);
        }
        long size = Files.size(path);
        if (size < 1 || size > ImageSafety.MAX_FILE_BYTES) {
            throw new IOException("Das Bild darf maximal 2 MiB groß sein.");
        }
        byte[] bytes = new byte[(int) size];
        try (InputStream stream = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
            int offset = 0;
            while (offset < bytes.length) {
                int count = stream.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    throw new IOException("Die Bilddatei wurde während des Lesens verändert.");
                }
                offset += count;
            }
            if (stream.read() != -1) {
                throw new IOException("Die Bilddatei wurde zu groß oder während des Lesens verändert.");
            }
        }
        ImageSafety.checkImage(bytes);
        return new ImageFile(file, ImageSafety.sha256(bytes), bytes);
    }

    public static ImageFile loadMatching(String fileName, String expectedHash) throws IOException {
        ImageFile image = load(fileName);
        if (!image.hash.equals(expectedHash)) {
            throw new IOException("Die Bilddatei wurde verändert. Bitte /createimage erneut ausführen.");
        }
        return image;
    }

    private static String cleanFileName(String requestedName) throws IOException {
        String name = requestedName == null ? "" : requestedName.trim();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!(lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg"))
                || name.length() > 110 || name.length() < 5 || name.contains("/")
                || name.contains("\\") || name.equals(".") || name.equals("..")
                || name.indexOf('\0') >= 0 || name.startsWith(".")) {
            throw new IOException("Bitte einen PNG- oder JPG-Dateinamen ohne Verzeichnispfad angeben.");
        }
        return name;
    }

    public static final class ImageFile {
        public final String fileName;
        public final String hash;
        public final byte[] bytes;

        private ImageFile(String fileName, String hash, byte[] bytes) {
            this.fileName = fileName;
            this.hash = hash;
            this.bytes = bytes;
        }
    }
}
