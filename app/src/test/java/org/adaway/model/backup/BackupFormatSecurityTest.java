package org.adaway.model.backup;

import org.adaway.db.entity.HostListItem;
import org.adaway.db.entity.ListType;
import org.adaway.db.entity.RuleKind;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.ListType.REDIRECTED;

/**
 * Security regression tests for {@link BackupFormat#hostFromJson(JSONObject, ListType)}.
 *
 * <p>Guards ATK-23: redirect IP validation in backup import must reject private/reserved IPs,
 * matching the same check applied by SourceLoader (ATK-01).
 *
 * <p>Must live in the same package as BackupFormat because it is package-private.
 */
public class BackupFormatSecurityTest {

    @Test
    public void atk23_backupRedirectWithLoopbackRejected() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("host", "example.com");
        obj.put("redirect", "127.0.0.1");
        obj.put("enabled", true);
        try {
            BackupFormat.hostFromJson(obj, REDIRECTED);
            fail("Expected JSONException for loopback redirect in backup");
        } catch (JSONException e) {
            assertTrue("Error must mention invalid redirect", e.getMessage().contains("Invalid"));
        }
    }

    @Test
    public void atk23_backupRedirectWithPrivateIpRejected() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("host", "example.com");
        obj.put("redirect", "192.168.1.1");
        obj.put("enabled", true);
        try {
            BackupFormat.hostFromJson(obj, REDIRECTED);
            fail("Expected JSONException for private-IP redirect in backup");
        } catch (JSONException e) {
            assertTrue("Error must mention invalid redirect", e.getMessage().contains("Invalid"));
        }
    }

    @Test
    public void atk23_backupRedirectWithPublicIpAllowed() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("host", "example.com");
        obj.put("redirect", "8.8.8.8");
        obj.put("enabled", true);
        // Must NOT throw — public IP redirect is valid
        assertEquals(REDIRECTED, BackupFormat.hostFromJson(obj, REDIRECTED).getType());
    }

    @Test
    public void atk23_backupRedirectWithNonIpStringRejected() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("host", "example.com");
        obj.put("redirect", "not-an-ip");
        obj.put("enabled", true);
        try {
            BackupFormat.hostFromJson(obj, REDIRECTED);
            fail("Expected JSONException for non-IP redirect string in backup");
        } catch (JSONException e) {
            assertTrue("Error must mention invalid redirect", e.getMessage().contains("Invalid"));
        }
    }

    @Test
    public void atk23_backupEntryWithNoRedirectAllowed() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("host", "example.com");
        obj.put("enabled", true);
        // A blocked rule does not require a redirect field.
        assertEquals(BLOCKED, BackupFormat.hostFromJson(obj, BLOCKED).getType());
    }

    @Test
    public void atk31_backupRoundTripsSuffixRuleKind() throws JSONException {
        HostListItem item = new HostListItem();
        item.setHost("example.com");
        item.setType(BLOCKED);
        item.setKind(RuleKind.SUFFIX);
        item.setEnabled(true);

        JSONObject obj = BackupFormat.hostToJson(item);
        assertEquals("suffix", obj.getString("kind"));

        HostListItem restored = BackupFormat.hostFromJson(obj, BLOCKED);
        assertEquals(RuleKind.SUFFIX, restored.getKind());
    }

    @Test
    public void atk31_backupDefaultsMissingRuleKindToExact() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("host", "example.com");
        obj.put("enabled", true);

        HostListItem restored = BackupFormat.hostFromJson(obj, BLOCKED);

        assertEquals(RuleKind.EXACT, restored.getKind());
    }

    @Test
    public void atk31_backupInvalidRuleKindRejected() throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put("host", "example.com");
        obj.put("enabled", true);
        obj.put("kind", "browser-scriptlet");

        try {
            BackupFormat.hostFromJson(obj, BLOCKED);
            fail("Expected JSONException for unsupported rule kind in backup");
        } catch (JSONException e) {
            assertTrue("Error must mention invalid kind", e.getMessage().contains("Invalid"));
        }
    }
}
