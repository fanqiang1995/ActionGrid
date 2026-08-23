package io.github.jiangjinyue2333.actiongrid.update;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import io.github.jiangjinyue2333.actiongrid.R;
import io.github.jiangjinyue2333.actiongrid.diagnostics.LogStore;

public final class UpdateInstallActivity extends Activity {
    private static final int INSTALL_PERMISSION_REQUEST = 1001;

    public static Intent intent(Context context) {
        Intent intent = new Intent(context, UpdateInstallActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return intent;
    }

    public static boolean launch(Activity activity) {
        try {
            activity.startActivity(intent(activity));
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        continueInstall();
    }

    private void continueInstall() {
        if (!getPackageManager().canRequestPackageInstalls()) {
            startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName())), INSTALL_PERMISSION_REQUEST);
            return;
        }
        installVerifiedUpdate();
    }

    private void installVerifiedUpdate() {
        File verified = UpdateState.verifiedFile(this);
        if (verified == null) {
            showAndFinish(R.string.update_file_missing);
            return;
        }
        Uri updateUri = UpdateFileProvider.uriFor(verified);
        try (ParcelFileDescriptor ignored =
                     getContentResolver().openFileDescriptor(updateUri, "r")) {
            if (ignored == null) {
                showAndFinish(R.string.update_file_missing);
                return;
            }
        } catch (IOException | RuntimeException exception) {
            showAndFinish(R.string.update_file_missing);
            return;
        }
        Intent install = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(updateUri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        install.setClipData(ClipData.newRawUri(verified.getName(), updateUri));
        try {
            startActivity(install);
            UpdateNotification.cancel(this);
            UpdateState.clearVerified(this);
            LogStore.append(this, "app", "已通过应用更新 Provider 拉起系统安装器");
        } catch (RuntimeException exception) {
            UpdateNotification.showReady(this);
            LogStore.append(this, "app", "拉起系统安装器失败 "
                    + exception.getClass().getSimpleName());
            Toast.makeText(this, R.string.installer_unavailable, Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != INSTALL_PERMISSION_REQUEST) {
            return;
        }
        if (getPackageManager().canRequestPackageInstalls()) {
            installVerifiedUpdate();
        } else {
            UpdateNotification.showReady(this);
            showAndFinish(R.string.install_permission_missing);
        }
    }

    private void showAndFinish(int message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }
}
