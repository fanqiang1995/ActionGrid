package io.github.jiangjinyue2333.actiongrid;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;

/** Foreground trampoline used by the launcher process to cross HyperOS BAL. */
public final class WeChatShortcutActivity extends Activity {
    private static final String WECHAT_SCAN =
            "io.github.jiangjinyue2333.actiongrid.WECHAT_SCAN";
    private static final String WECHAT_PAY =
            "io.github.jiangjinyue2333.actiongrid.WECHAT_PAY";
    private static final String WECHAT_QRCODE =
            "io.github.jiangjinyue2333.actiongrid.WECHAT_QRCODE";
    static final String UNINSTALL =
            "io.github.jiangjinyue2333.actiongrid.UNINSTALL";
    static final String EXTRA_PACKAGE =
            "io.github.jiangjinyue2333.actiongrid.EXTRA_PACKAGE";

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        String action = getIntent().getAction();
        if (UNINSTALL.equals(action)) {
            String packageName = getIntent().getStringExtra(EXTRA_PACKAGE);
            if (isTrustedUninstallCaller() && packageName != null
                    && !packageName.isBlank() && !packageName.equals(getPackageName())) {
                startActivity(new Intent(Intent.ACTION_DELETE,
                        Uri.parse("package:" + packageName))
                        .putExtra(Intent.EXTRA_USER, Process.myUserHandle())
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
            finish();
            return;
        }
        String launchType = WECHAT_SCAN.equals(action)
                ? "launch_type_scan_qrcode"
                : WECHAT_PAY.equals(action)
                ? "launch_type_offline_wallet"
                : WECHAT_QRCODE.equals(action)
                ? "launch_type_my_qrcode" : null;
        if (launchType != null) {
            startActivity(new Intent("com.tencent.mm.ui.ShortCutDispatchAction")
                    .setComponent(new ComponentName(
                            "com.tencent.mm", "com.tencent.mm.ui.LauncherUI"))
                    .putExtra("LauncherUI.Shortcut.LaunchType", launchType)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP));
        }
        finish();
    }

    private boolean isTrustedUninstallCaller() {
        try {
            if (!"com.miui.home".equals(getLaunchedFromPackage())) {
                return false;
            }
            int uid = getLaunchedFromUid();
            if (uid < 0) {
                return false;
            }
            String[] packages = getPackageManager().getPackagesForUid(uid);
            if (packages == null) {
                return false;
            }
            for (String packageName : packages) {
                if ("com.miui.home".equals(packageName)) {
                    return true;
                }
            }
        } catch (RuntimeException ignored) {
            // An unverifiable caller must not reach the package installer.
        }
        return false;
    }
}
