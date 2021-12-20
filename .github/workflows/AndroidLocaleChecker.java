import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * This class validates Android locale files.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class AndroidLocaleChecker {
    /**
     * The Android resources path.
     */
    private static final Path RESOURCES_PATH = Path.of("app/src/main/res/");
    /**
     * XML file header.
     */
    private static final String HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";

    /**
     * Validate Android locale files.
     *
     * @param args The project files to validate, space-separated.
     */
    public static void main(String[] args) {
        long patchedFilesCount = Arrays.stream(args)
                .map(Path::of)
                .filter(AndroidLocaleChecker::isLocalePath)
                .filter(Predicate.not(AndroidLocaleChecker::validateLocalePath))
                .count();
        boolean patchApplied = patchedFilesCount > 0;
        if (patchApplied) {
            System.out.println(patchedFilesCount + " file(s) patched.");
        } else {
            System.out.println("No file patched.");
        }
        System.out.println("::set-output name=patch_applied::" + patchApplied);
    }

    /**
     * Check locale file path.
     *
     * @param projectFilePath The path to check.
     * @return <code>true</code> if the path is a locale file path, <code>false</code> otherwise.
     */
    private static boolean isLocalePath(Path projectFilePath) {
        try {
            Path relativePath = RESOURCES_PATH.relativize(projectFilePath);
            Path parent = relativePath.getParent();
            return parent.toString().startsWith("values") && relativePath.toString().endsWith(".xml");
        } catch (IllegalArgumentException e) {
            System.out.println("Skipping file: " + projectFilePath);
        }
        return false;
    }

    /**
     * Validate an Android locale.
     *
     * @param localePath The locale path.
     */
    private static boolean validateLocalePath(Path localePath) {
        try {
            System.out.println("Validating " + localePath + ":");
            List<String> lines = Files.readAllLines(localePath, UTF_8);
            boolean validHeader = validateHeader(lines);
            boolean validEllipsis = validateEllipsis(lines);
            if (!validHeader || !validEllipsis) {
                Files.write(localePath, lines, UTF_8);
                System.out.println("- Patched");
                return false;
            }
            return true;
        } catch (IOException e) {
            System.err.println("Failed to validate locale: " + localePath);
            e.printStackTrace();
            System.exit(1);
            return false;
        }
    }

    private static boolean validateHeader(List<String> lines) {
        if (lines.isEmpty()) {
            return true;
        }
        String header = lines.get(0);
        if (!header.startsWith("<?xml ")) {
            System.err.println("Failed to find header.");
            System.exit(1);
        }
        if (!HEADER.equals(header)) {
            System.out.println("- Fixing XML header");
            lines.set(0, HEADER);
            return false;
        }
        return true;
    }

    private static boolean validateEllipsis(List<String> lines) {
        boolean valid = true;
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.contains("...")) {
                System.out.println("- Fixing ellipsis in:" + line);
                line = line.replaceAll("\\.\\.\\.", "…");
                lines.set(i, line);
                valid = false;
            }
            if (line.contains("&#8230;")) {
                System.out.println("- Fixing ellipsis in:" + line);
                line = line.replaceAll("&#8230;", "…");
                lines.set(i, line);
                valid = false;
            }
        }
        return valid;
    }
}
