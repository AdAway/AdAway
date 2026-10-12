package org.adaway.model.backup;

import static org.adaway.db.entity.HostsSource.USER_SOURCE_ID;
import static org.adaway.db.entity.ListType.ALLOWED;
import static org.adaway.db.entity.ListType.BLOCKED;
import static org.adaway.db.entity.RuleKind.EXACT;
import static org.adaway.db.entity.RuleKind.SUFFIX;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import android.database.sqlite.SQLiteException;

import org.adaway.db.DbTest;
import org.adaway.db.entity.HostListItem;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.List;

public class BackupImportAtomicityTest extends DbTest {
    private static final String HOST = "restore-atomic.example";
    private static final String SOURCE = "https://backup-atomic.example/hosts";

    @Test
    public void malformedLaterListLeavesSourcesAndExistingRulesUnchanged() throws Exception {
        insertBlockedHost(HOST, USER_SOURCE_ID);
        JSONObject backup = backup();
        backup.getJSONArray("allowed").put(rule(HOST, "exact"));
        backup.getJSONArray("redirected").put(rule("missing-ip.example", "exact"));

        assertThrows(JSONException.class, () -> BackupImporter.importBackup(db, backup));

        assertFalse(hostsSourceDao.getByUrl(SOURCE).isPresent());
        assertEquals(1, hostListItemDao.getUserList().size());
        assertEquals(BLOCKED, hostListItemDao.getUserList().get(0).getType());
    }

    @Test
    public void databaseFailureRollsBackEarlierSourceAndRuleWrites() throws Exception {
        insertBlockedHost(HOST, USER_SOURCE_ID);
        db.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER reject_test_restore BEFORE INSERT ON hosts_lists "
                        + "WHEN NEW.host = 'fail-restore.example' "
                        + "BEGIN SELECT RAISE(ABORT, 'test failure'); END");
        JSONObject backup = backup();
        backup.getJSONArray("allowed").put(rule(HOST, "exact"))
                .put(rule("fail-restore.example", "exact"));

        assertThrows(SQLiteException.class, () -> BackupImporter.importBackup(db, backup));

        assertFalse(hostsSourceDao.getByUrl(SOURCE).isPresent());
        assertEquals(1, hostListItemDao.getUserList().size());
        assertEquals(BLOCKED, hostListItemDao.getUserList().get(0).getType());
    }

    @Test
    public void exactAndSuffixRulesSurviveRepeatedRestoreWithoutCollapsing() throws Exception {
        JSONObject backup = backup();
        backup.getJSONArray("blocked").put(rule(HOST, "suffix"));
        backup.getJSONArray("allowed").put(rule(HOST, "exact"));
        BackupImporter.importBackup(db, backup);
        BackupImporter.importBackup(db, backup);

        List<HostListItem> rows = hostListItemDao.getUserList();
        assertEquals(2, rows.size());
        assertEquals(1, rows.stream().filter(row -> row.getKind() == EXACT
                && row.getType() == ALLOWED).count());
        assertEquals(1, rows.stream().filter(row -> row.getKind() == SUFFIX
                && row.getType() == BLOCKED).count());
        assertFalse(hostsSourceDao.getByUrl(SOURCE).get().isRedirectEnabled());
    }

    @Test
    public void overLimitDocumentIsRejectedInsteadOfPartiallyImported() throws Exception {
        JSONObject backup = backup();
        JSONObject rule = rule(HOST, "exact");
        for (int index = 0; index < 50_001; index++) {
            backup.getJSONArray(index % 2 == 0 ? "blocked" : "allowed").put(rule);
        }
        assertThrows(JSONException.class, () -> BackupImporter.importBackup(db, backup));
        assertEquals(0, hostListItemDao.getUserList().size());
        assertFalse(hostsSourceDao.getByUrl(SOURCE).isPresent());
    }

    private static JSONObject backup() throws JSONException {
        return new JSONObject()
                .put("sources", new JSONArray().put(new JSONObject()
                        .put("label", "Restore test").put("url", SOURCE)
                        .put("enabled", true).put("allow", true).put("redirect", true)))
                .put("blocked", new JSONArray()).put("allowed", new JSONArray())
                .put("redirected", new JSONArray());
    }

    private static JSONObject rule(String host, String kind) throws JSONException {
        return new JSONObject().put("host", host).put("kind", kind).put("enabled", true);
    }
}
