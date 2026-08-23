package io.github.jiangjinyue2333.actiongrid.update;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

import io.github.jiangjinyue2333.actiongrid.BuildConfig;

public final class UpdateState {
    static final String PREFS = "updates";
    static final String DOWNLOAD_ID = "download_id";
    static final String DOWNLOAD_PATH = "download_path";
    static final String DOWNLOAD_MANIFEST = "download_manifest";
    static final String VERIFIED_PATH = "verified_path";
    static final String VERIFIED_VERSION_CODE = "verified_version_code";
    static final String LAST_ERROR = "last_error";
    private static final String IGNORED_VERSION = "ignored_version";

    private UpdateState() {
    }

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int ignoredVersion(Context context) {
        return preferences(context).getInt(IGNORED_VERSION, 0);
    }

    public static void ignore(Context context, int versionCode) {
        preferences(context).edit().putInt(IGNORED_VERSION, versionCode).apply();
    }

    public static void restorePrompts(Context context) {
        preferences(context).edit().remove(IGNORED_VERSION).apply();
    }

    public static File verifiedFile(Context context) {
        SharedPreferences preferences = preferences(context);
        String path = preferences.getString(VERIFIED_PATH, null);
        if (path == null) {
            return null;
        }
        File file = new File(path);
        int versionCode = preferences.getInt(VERIFIED_VERSION_CODE, 0);
        if (versionCode <= BuildConfig.VERSION_CODE) {
            preferences.edit()
                    .remove(VERIFIED_PATH)
                    .remove(VERIFIED_VERSION_CODE)
                    .apply();
            file.delete();
            return null;
        }
        return file.isFile() ? file : null;
    }

    public static String consumeError(Context context) {
        SharedPreferences preferences = preferences(context);
        String error = preferences.getString(LAST_ERROR, null);
        if (error != null) {
            preferences.edit().remove(LAST_ERROR).apply();
        }
        return error;
    }

    static void clearVerified(Context context) {
        preferences(context).edit()
                .remove(VERIFIED_PATH)
                .remove(VERIFIED_VERSION_CODE)
                .apply();
    }

    static void clearDownload(Context context) {
        preferences(context).edit()
                .remove(DOWNLOAD_ID)
                .remove(DOWNLOAD_PATH)
                .remove(DOWNLOAD_MANIFEST)
                .apply();
    }
}
