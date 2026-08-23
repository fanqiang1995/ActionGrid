package io.github.jiangjinyue2333.actiongrid.update;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import io.github.jiangjinyue2333.actiongrid.ActionGridApp;

public final class UpdateDownloadReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            return;
        }
        SharedPreferences preferences = UpdateState.preferences(context);
        long expected = preferences.getLong(UpdateState.DOWNLOAD_ID, -1);
        long completed = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -2);
        if (expected < 0 || expected != completed) {
            return;
        }
        PendingResult pending = goAsync();
        Thread verifier = new Thread(() -> verifyDownload(
                context.getApplicationContext(), completed, pending),
                "ActionGrid-update-verifier");
        verifier.start();
    }

    private static void verifyDownload(Context context, long id, PendingResult pending) {
        SharedPreferences preferences = UpdateState.preferences(context);
        String path = preferences.getString(UpdateState.DOWNLOAD_PATH, null);
        String json = preferences.getString(UpdateState.DOWNLOAD_MANIFEST, null);
        File file = path == null ? null : new File(path);
        String error = downloadSucceeded(context, id) ? null : "download";
        UpdateManifest manifest = null;
        if (error == null) {
            try {
                manifest = UpdateManifest.parse(json == null ? "" : json);
            } catch (IllegalArgumentException exception) {
                error = "manifest";
            }
        }
        if (error == null && manifest != null && file != null) {
            error = UpdateVerifier.verify(context, manifest, file);
        }
        SharedPreferences.Editor editor = preferences.edit()
                .remove(UpdateState.DOWNLOAD_ID)
                .remove(UpdateState.DOWNLOAD_PATH)
                .remove(UpdateState.DOWNLOAD_MANIFEST);
        if (error == null && file != null) {
            editor.putString(UpdateState.VERIFIED_PATH, file.getAbsolutePath())
                    .putInt(UpdateState.VERIFIED_VERSION_CODE, manifest.versionCode())
                    .remove(UpdateState.LAST_ERROR);
        } else {
            if (file != null && file.exists()) {
                deleteUntrusted(file);
            }
            editor.remove(UpdateState.VERIFIED_PATH)
                    .remove(UpdateState.VERIFIED_VERSION_CODE)
                    .putString(UpdateState.LAST_ERROR, error == null ? "unknown" : error);
        }
        boolean saved = editor.commit();
        boolean ready = error == null && file != null && saved;
        if (!saved) {
            preferences.edit()
                    .remove(UpdateState.VERIFIED_PATH)
                    .remove(UpdateState.VERIFIED_VERSION_CODE)
                    .putString(UpdateState.LAST_ERROR, "state")
                    .commit();
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            if (ready) {
                Activity foreground = ActionGridApp.resumedActivity();
                boolean installStarted = foreground != null
                        && UpdateInstallActivity.launch(foreground);
                if (!installStarted) {
                    UpdateNotification.showReady(context);
                }
            }
            context.sendBroadcast(new Intent(UpdateDownloader.ACTION_STATE_CHANGED)
                    .setPackage(context.getPackageName()));
            pending.finish();
        });
    }

    private static void deleteUntrusted(File file) {
        if (file.delete()) {
            return;
        }
        try (FileOutputStream ignored = new FileOutputStream(file, false)) {
            // A zero-length private file cannot be mistaken for an installable update.
        } catch (IOException ignored) {
            // A missing or zero-length file cannot be served as an installable update.
        }
    }

    private static boolean downloadSucceeded(Context context, long id) {
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        try (Cursor cursor = manager.query(new DownloadManager.Query().setFilterById(id))) {
            if (!cursor.moveToFirst()) {
                return false;
            }
            int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            return statusColumn >= 0
                    && cursor.getInt(statusColumn) == DownloadManager.STATUS_SUCCESSFUL;
        }
    }
}
