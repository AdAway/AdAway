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
    public final int versionCode;
    public final String changelog;
    public final boolean updateAvailable;

    public Manifest(String manifest, long currentVersionCode) throws JSONException {
        JSONObject manifestObject = new JSONObject(manifest);
        this.version = manifestObject.getString("version");
        this.versionCode = manifestObject.getInt("versionCode");
        this.changelog = manifestObject.getString("changelog");
        this.updateAvailable = this.versionCode > currentVersionCode;
    }
}
