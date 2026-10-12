package org.adaway.model.backup;

import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.adaway.db.entity.RuleKind;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;

public class BackupValidationTest {
    @Test
    public void rejectsMalformedHostsAndMissingOrPrivateRedirects() throws Exception {
        for (String host : new String[]{"", "https://example.com/path", "bad\nhost.example"}) {
            assertThrows(JSONException.class, () -> BackupFormat.hostFromJson(rule(host), BLOCKED));
        }
        assertThrows(JSONException.class,
                () -> BackupFormat.hostFromJson(rule("example.com"), REDIRECTED));
        assertThrows(JSONException.class, () -> BackupFormat.hostFromJson(
                rule("example.com").put("redirect", "127.0.0.1"), REDIRECTED));
    }

    @Test
    public void preservesLegacyWildcardAllowsAndSuffixKinds() throws Exception {
        assertEquals("*.example.com",
                BackupFormat.hostFromJson(rule("*.example.com"), ALLOWED).getHost());
        assertEquals(RuleKind.SUFFIX, BackupFormat.hostFromJson(
                rule("EXAMPLE.COM").put("kind", "suffix"), BLOCKED).getKind());
        assertEquals("8.8.8.8", BackupFormat.hostFromJson(
                rule("example.com").put("redirect", "8.8.8.8"), REDIRECTED).getRedirection());
    }

    @Test
    public void doesNotAcceptWildcardBlockOrSuffixRules() throws Exception {
        assertThrows(JSONException.class,
                () -> BackupFormat.hostFromJson(rule("*.example.com"), BLOCKED));
        assertThrows(JSONException.class, () -> BackupFormat.hostFromJson(
                rule("*.example.com").put("kind", "suffix"), ALLOWED));
    }

    @Test
    public void boundedReaderPreservesLineBreaksForJsonValidation() throws Exception {
        String text = "{\n\"host\":\"bad\nhost.example\"\n}";
        assertEquals(text, BackupImporter.readBackup(new StringReader(text)));
    }

    @Test
    public void rejectsOversizedSingleLineBeforeParsingJson() {
        Reader reader = new Reader() {
            private int remaining = BackupImporter.MAX_IMPORT_CHARACTERS + 1;

            @Override
            public int read(char[] buffer, int offset, int length) {
                if (remaining == 0) return -1;
                int count = Math.min(length, remaining);
                Arrays.fill(buffer, offset, offset + count, ' ');
                remaining -= count;
                return count;
            }

            @Override
            public void close() { }
        };
        assertThrows(IOException.class, () -> BackupImporter.readBackup(reader));
    }

    private static JSONObject rule(String host) throws JSONException {
        return new JSONObject().put("host", host).put("enabled", true);
    }
}
