package io.github.jiangjinyue2333.actiongrid;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

public final class ReleaseRegressionContractTest {
    @Test
    public void desktopRestartDoesNotInvokeRoot() throws IOException {
        String source = source("io/github/jiangjinyue2333/actiongrid/HomeRestarter.java");

        assertFalse(source.contains("\"su\""));
        assertTrue(source.contains("XposedService"));
    }

    @Test
    public void sharedLogExposesMetadataAndPropagatesUriGrant() throws IOException {
        String activity = source("io/github/jiangjinyue2333/actiongrid/MainActivity.java");
        String provider = source(
                "io/github/jiangjinyue2333/actiongrid/diagnostics/DiagnosticsProvider.java");
        String share = between(activity, "private void shareLogs", "private void checkUpdates");

        assertTrue(share.contains("ClipData.newRawUri"));
        assertTrue(provider.contains("OpenableColumns.DISPLAY_NAME"));
        assertTrue(provider.contains("OpenableColumns.SIZE"));
    }

    @Test
    public void verifiedUpdateDirectlyLaunchesInstallerWithReadableMetadata() throws IOException {
        String receiver = source(
                "io/github/jiangjinyue2333/actiongrid/update/UpdateDownloadReceiver.java");
        String installer = source(
                "io/github/jiangjinyue2333/actiongrid/update/UpdateInstallActivity.java");
        String provider = source(
                "io/github/jiangjinyue2333/actiongrid/update/UpdateFileProvider.java");
        String state = source("io/github/jiangjinyue2333/actiongrid/update/UpdateState.java");

        assertTrue(receiver.contains("UpdateInstallActivity.launch(foreground)"));
        assertTrue(installer.contains("ClipData.newRawUri"));
        assertTrue(installer.contains("openFileDescriptor(updateUri, \"r\")"));
        assertTrue(provider.contains("OpenableColumns.DISPLAY_NAME"));
        assertTrue(provider.contains("OpenableColumns.SIZE"));
        assertTrue(state.contains("VERIFIED_VERSION_CODE"));
        assertTrue(state.contains("versionCode <= BuildConfig.VERSION_CODE"));
    }

    @Test
    public void updatePromptReadsUpdateCancelThenIgnore() throws IOException {
        String activity = source("io/github/jiangjinyue2333/actiongrid/MainActivity.java");
        String prompt = between(activity, "private void handleUpdateResult",
                "private void startDownload");

        assertTrue(prompt.contains(".setNeutralButton(R.string.update_now"));
        assertTrue(prompt.contains(".setNegativeButton(R.string.cancel"));
        assertTrue(prompt.contains(".setPositiveButton(R.string.ignore_version"));
    }

    @Test
    public void diagnosticFileOpenUsesPlatformUriGrantEnforcement() throws IOException {
        String provider = source(
                "io/github/jiangjinyue2333/actiongrid/diagnostics/DiagnosticsProvider.java");
        String openFile = between(provider, "public ParcelFileDescriptor openFile",
                "private boolean isTrustedCaller");
        String manifest = manifest();

        assertFalse(openFile.contains("checkUriPermission"));
        assertFalse(openFile.contains("Binder.getCallingUid"));
        assertTrue(manifest.contains("<path-permission"));
        assertTrue(manifest.contains("android:readPermission="));
    }

    @Test
    public void downloadCompletionReachesVerifierAndRequiresRecoveryNotification()
            throws IOException {
        String activity = source("io/github/jiangjinyue2333/actiongrid/MainActivity.java");
        String startDownload = between(activity, "private void startDownload",
                "private void beginDownload");
        String manifest = manifest();
        String receiver = between(manifest,
                "android:name=\".update.UpdateDownloadReceiver\"", "</receiver>");

        assertTrue(startDownload.contains("UpdateNotification.canNotify(this)"));
        assertTrue(receiver.contains("android:exported=\"true\""));
        assertTrue(receiver.contains(
                "android:permission=\"android.permission.SEND_DOWNLOAD_COMPLETED_INTENTS\""));
    }

    @Test
    public void verifiedDownloadUsesAppOwnedInstallerEntryInsteadOfDownloadsNotification()
            throws IOException {
        String downloader = source(
                "io/github/jiangjinyue2333/actiongrid/update/UpdateDownloader.java");
        String receiver = source(
                "io/github/jiangjinyue2333/actiongrid/update/UpdateDownloadReceiver.java");
        String installer = source(
                "io/github/jiangjinyue2333/actiongrid/update/UpdateInstallActivity.java");
        String provider = source(
                "io/github/jiangjinyue2333/actiongrid/update/UpdateFileProvider.java");
        String notification = source(
                "io/github/jiangjinyue2333/actiongrid/update/UpdateNotification.java");
        String manifest = manifest();

        assertTrue(downloader.contains("setDestinationInExternalFilesDir"));
        assertTrue(downloader.contains("DownloadManager.Request.VISIBILITY_VISIBLE"));
        assertFalse(downloader.contains("VISIBILITY_VISIBLE_NOTIFY_COMPLETED"));
        assertFalse(downloader.contains("setDestinationUri"));
        assertTrue(receiver.contains("ActionGridApp.resumedActivity()"));
        assertTrue(receiver.contains("UpdateInstallActivity.launch"));
        assertTrue(receiver.contains("UpdateNotification.showReady"));
        assertTrue(installer.contains("UpdateFileProvider.uriFor"));
        assertTrue(installer.contains("ClipData.newRawUri"));
        assertTrue(installer.indexOf("startActivity(install)")
                < installer.indexOf("UpdateState.clearVerified(this)"));
        assertFalse(provider.contains("UpdateState.verifiedFile"));
        assertTrue(provider.contains("new File(root, uri.getLastPathSegment())"));
        assertTrue(notification.contains("PendingIntent.getActivity"));
        assertTrue(notification.contains("UpdateInstallActivity.intent"));
        assertTrue(manifest.contains("android.permission.POST_NOTIFICATIONS"));
        assertTrue(manifest.contains("android:name=\".update.UpdateInstallActivity\""));
    }

    private static String between(String source, String start, String end) {
        int from = source.indexOf(start);
        int to = source.indexOf(end, from);
        assertTrue(from >= 0 && to > from);
        return source.substring(from, to);
    }

    private static String source(String relative) throws IOException {
        Path local = Path.of("src", "main", "java").resolve(relative);
        Path path = Files.exists(local) ? local : Path.of("app").resolve(local);
        return Files.readString(path);
    }

    private static String manifest() throws IOException {
        Path local = Path.of("src", "main", "AndroidManifest.xml");
        Path path = Files.exists(local) ? local : Path.of("app").resolve(local);
        return Files.readString(path);
    }
}
