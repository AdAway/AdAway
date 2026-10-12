package org.adaway.model.root;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class RootModelShellSafetyTest {
    @Test
    public void failedCopyCannotBeMaskedBySuccessfulChmod() throws Exception {
        Path directory = Files.createTempDirectory("adaway-shell-test");
        Path target = directory.resolve("hosts ' quoted");
        try {
            Files.write(target, "original\n".getBytes(StandardCharsets.UTF_8));
            Files.setPosixFilePermissions(target, PosixFilePermissions.fromString("rw-------"));
            String command = ShellUtils.hostsCopyCommand(
                    directory.resolve("missing ' source").toString(), target.toString());
            Process process = new ProcessBuilder("sh", "-c", command)
                    .redirectErrorStream(true).start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new AssertionError("Copy command did not finish.");
            }
            assertNotEquals(0, process.exitValue());
            assertEquals("original\n",
                    new String(Files.readAllBytes(target), StandardCharsets.UTF_8));
            assertEquals(PosixFilePermissions.fromString("rw-------"),
                    Files.getPosixFilePermissions(target));
        } finally {
            Files.deleteIfExists(target);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    public void sourceMetadataCannotInjectHostsFileLines() {
        String header = RootModel.sourceHeader("Custom\r\n192.0.2.1 injected.invalid",
                "https://example.invalid/list\n192.0.2.2 another.invalid");
        assertFalse(header.contains("\n"));
        assertFalse(header.contains("\r"));
        assertEquals(0, header.indexOf("# - "));
    }
}
