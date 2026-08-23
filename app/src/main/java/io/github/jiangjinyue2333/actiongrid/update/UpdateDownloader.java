package io.github.jiangjinyue2333.actiongrid.update;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import java.io.File;
import java.io.IOException;

public final class UpdateDownloader {
    public static final String ACTION_STATE_CHANGED =
            "io.github.jiangjinyue2333.actiongrid.UPDATE_STATE_CHANGED";

    private UpdateDownloader() {
    }

    public static void enqueue(Context context, UpdateManifest manifest) throws IOException {
        File directory = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        if (directory == null) {
            throw new IOException("External files directory is unavailable");
        }
        File destination = new File(directory,
                "actiongrid-update-" + manifest.versionCode() + ".apk");
        File[] oldUpdates = directory.listFiles((ignored, name) ->
                name.startsWith("actiongrid-update-") && name.endsWith(".apk")
                        && !name.equals(destination.getName()));
        if (oldUpdates != null) {
            for (File oldUpdate : oldUpdates) {
                oldUpdate.delete();
            }
        }
        if (destination.exists() && !destination.delete()) {
            throw new IOException("Cannot clear previous update");
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(manifest.apkUrl()))
                .setAllowedOverRoaming(false)
                .setMimeType("application/vnd.android.package-archive")
                .setTitle("ActionGrid " + manifest.versionName())
                .setDescription("正在下载更新")
                .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE)
                .setDestinationInExternalFilesDir(context,
                        Environment.DIRECTORY_DOWNLOADS, destination.getName());
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        long id = manager.enqueue(request);
        boolean saved = UpdateState.preferences(context).edit()
                .putLong(UpdateState.DOWNLOAD_ID, id)
                .putString(UpdateState.DOWNLOAD_PATH, destination.getAbsolutePath())
                .putString(UpdateState.DOWNLOAD_MANIFEST, manifest.toJson())
                .remove(UpdateState.VERIFIED_PATH)
                .remove(UpdateState.VERIFIED_VERSION_CODE)
                .remove(UpdateState.LAST_ERROR)
                .commit();
        if (!saved) {
            manager.remove(id);
            throw new IOException("Cannot persist download state");
        }
    }
}
