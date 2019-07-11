package org.adaway.model.update;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * This class is represent an application manifest.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class Manifest {
    public final String version;
    public final long versionCode;
    public final long fid;
    public final String link;
    public final String changelog;
    public final boolean updateAvailable;

    public Manifest(String manifest, long currentVersionCode) throws JSONException {
        JSONObject manifestObject = new JSONObject(manifest);
        this.version = manifestObject.getString("version");
        this.versionCode = manifestObject.getLong("versionCode");
        this.fid = manifestObject.getLong("fid");
        this.link = manifestObject.getString("link");
        this.changelog = manifestObject.getString("changelog");
        this.updateAvailable = versionCode > currentVersionCode;
    }
}
