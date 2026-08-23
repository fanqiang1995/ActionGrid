package io.github.jiangjinyue2333.actiongrid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.github.jiangjinyue2333.actiongrid.diagnostics.DiagnosticsProvider;
import io.github.jiangjinyue2333.actiongrid.diagnostics.LogStore;
import io.github.jiangjinyue2333.actiongrid.update.UpdateClient;
import io.github.jiangjinyue2333.actiongrid.update.UpdateDownloader;
import io.github.jiangjinyue2333.actiongrid.update.UpdateManifest;
import io.github.jiangjinyue2333.actiongrid.update.UpdateNotification;
import io.github.jiangjinyue2333.actiongrid.update.UpdateState;
import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;

public final class MainActivity extends Activity implements ActionGridApp.ServiceListener {
    private static final String TARGET_PACKAGE = "com.miui.home";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1002;

    private LinearLayout statusPage;
    private LinearLayout aboutPage;
    private TextView moduleStatus;
    private TextView scopeStatus;
    private TextView homeStatus;
    private TextView statusMessage;
    private TextView aboutStatus;
    private Button openAbout;
    private Button restartHome;
    private Button checkUpdates;
    private Switch loggingSwitch;
    private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
    private final BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePendingState(false);
        }
    };
    private boolean showingAbout;
    private boolean updateReceiverRegistered;
    private UpdateManifest pendingUpdateAfterNotificationPermission;
    private boolean homeRestartInProgress;
    private XposedService service;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        bindViews();
        configureActions();
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0, this::handleBack);
        refreshStatus();
        updateIgnoredStatus();
        if (ActionGridApp.consumeColdUpdateCheck()) {
            checkUpdates(false);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        ActionGridApp.addServiceListener(this);
        registerReceiver(updateReceiver,
                new IntentFilter(UpdateDownloader.ACTION_STATE_CHANGED), RECEIVER_NOT_EXPORTED);
        updateReceiverRegistered = true;
        updatePendingState(true);
    }

    @Override
    protected void onStop() {
        ActionGridApp.removeServiceListener(this);
        if (updateReceiverRegistered) {
            unregisterReceiver(updateReceiver);
            updateReceiverRegistered = false;
        }
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingUpdateAfterNotificationPermission != null
                && UpdateNotification.canNotify(this)) {
            UpdateManifest pending = pendingUpdateAfterNotificationPermission;
            pendingUpdateAfterNotificationPermission = null;
            beginDownload(pending);
        }
    }

    @Override
    protected void onDestroy() {
        backgroundExecutor.shutdownNow();
        super.onDestroy();
    }

    @Override
    public void onServiceChanged(XposedService value) {
        service = value;
        runOnUiThread(this::refreshStatus);
    }

    private void bindViews() {
        statusPage = findViewById(R.id.status_page);
        aboutPage = findViewById(R.id.about_page);
        moduleStatus = findViewById(R.id.module_status_value);
        scopeStatus = findViewById(R.id.scope_status_value);
        homeStatus = findViewById(R.id.home_status_value);
        statusMessage = findViewById(R.id.status_message);
        aboutStatus = findViewById(R.id.about_status);
        openAbout = findViewById(R.id.open_about);
        restartHome = findViewById(R.id.restart_home);
        checkUpdates = findViewById(R.id.check_updates);
        loggingSwitch = findViewById(R.id.logging_switch);
        ((TextView) findViewById(R.id.version_text)).setText(
                getString(R.string.version_format, BuildConfig.VERSION_NAME));
        loggingSwitch.setChecked(LogStore.isEnabled(this));
    }

    private void configureActions() {
        findViewById(R.id.refresh_status).setOnClickListener(view -> refreshStatus());
        restartHome.setOnClickListener(view -> confirmHomeRestart());
        openAbout.setOnClickListener(view -> showAboutPage());
        findViewById(R.id.back_to_status).setOnClickListener(view -> showStatusPage());
        loggingSwitch.setOnCheckedChangeListener((button, enabled) -> {
            LogStore.setEnabled(this, enabled);
            aboutStatus.setText(enabled ? "已开始记录新日志" : "日志记录已关闭，旧日志已清除");
            findViewById(R.id.send_logs).setEnabled(enabled && LogStore.hasLogs(this));
        });
        findViewById(R.id.send_logs).setOnClickListener(view -> shareLogs());
        checkUpdates.setOnClickListener(view -> checkUpdates(true));
        findViewById(R.id.restore_update_prompts).setOnClickListener(view -> {
            UpdateState.restorePrompts(this);
            updateIgnoredStatus();
            aboutStatus.setText(R.string.update_prompts_restored);
        });
        findViewById(R.id.send_logs).setEnabled(LogStore.hasLogs(this));
    }

    private void confirmHomeRestart() {
        if (homeRestartInProgress) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.restart_home)
                .setMessage(R.string.restart_home_confirmation)
                .setPositiveButton(R.string.restart_home_confirm,
                        (dialog, which) -> restartHome())
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(dialog -> {
                    if (!homeRestartInProgress) {
                        focusForAccessibility(restartHome);
                    }
                })
                .show();
    }

    private void restartHome() {
        Intent home = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(TARGET_PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (home.resolveActivity(getPackageManager()) == null) {
            statusMessage.setText(R.string.restart_home_unavailable);
            focusForAccessibility(restartHome);
            return;
        }
        homeRestartInProgress = true;
        restartHome.setEnabled(false);
        statusMessage.setText(R.string.restarting_home);
        LogStore.append(this, "app", "用户已确认重启小米桌面");
        XposedService currentService = service;
        backgroundExecutor.execute(() -> {
            HomeRestarter.Result result = HomeRestarter.requestXiaomiHomeRestart(currentService);
            runOnUiThread(() -> finishHomeRestart(home, result));
        });
    }

    private void finishHomeRestart(Intent home, HomeRestarter.Result result) {
        if (isDestroyed()) {
            return;
        }
        homeRestartInProgress = false;
        restartHome.setEnabled(true);
        if (result != HomeRestarter.Result.REQUESTED) {
            statusMessage.setText(restartFailureMessage(result));
            LogStore.append(this, "app", "重启小米桌面失败 " + result);
            focusForAccessibility(restartHome);
            return;
        }
        statusMessage.setText(R.string.restart_home_success);
        LogStore.append(this, "app", "小米桌面进程已停止并准备重新启动");
        statusMessage.postDelayed(() -> {
            try {
                startActivity(home);
            } catch (RuntimeException exception) {
                statusMessage.setText(R.string.restart_home_failed);
                LogStore.append(this, "app", "重新打开小米桌面失败 "
                        + exception.getClass().getSimpleName());
                focusForAccessibility(restartHome);
            }
        }, 800L);
    }

    private int restartFailureMessage(HomeRestarter.Result result) {
        return switch (result) {
            case SERVICE_UNAVAILABLE -> R.string.restart_home_service_unavailable;
            case API_UNSUPPORTED -> R.string.restart_home_api_unsupported;
            case REMOTE_PREFERENCES_UNAVAILABLE -> R.string.restart_home_remote_unavailable;
            case SCOPE_MISSING -> R.string.restart_home_scope_missing;
            case MODULE_NOT_CURRENT -> R.string.restart_home_module_not_current;
            case WRITE_FAILED, REQUESTED -> R.string.restart_home_failed;
        };
    }

    private void refreshStatus() {
        String home = installedHomeVersion();
        setStatusRow(R.id.home_status_row, homeStatus, "小米桌面", home);
        if (service == null) {
            setStatusRow(R.id.module_status_row, moduleStatus, "模块", "libxposed 服务未连接");
            setStatusRow(R.id.scope_status_row, scopeStatus, "作用域", "无法读取");
            statusMessage.setText(R.string.enable_module_hint);
            return;
        }
        try {
            int api = service.getApiVersion();
            String module = api == 102 ? runningTargetStatus(service.getRunningTargets())
                    : "框架 API " + api + "，需要 API 102";
            List<String> scope = service.getScope();
            String scopeText = scope.size() == 1 && scope.contains(TARGET_PACKAGE)
                    ? "仅小米桌面，已启用" : "当前范围：" + scope;
            setStatusRow(R.id.module_status_row, moduleStatus, "模块", module);
            setStatusRow(R.id.scope_status_row, scopeStatus, "作用域", scopeText);
            statusMessage.setText(api == 102 ? module : "框架 API 不兼容");
        } catch (RuntimeException exception) {
            setStatusRow(R.id.module_status_row, moduleStatus, "模块", "读取失败");
            statusMessage.setText("读取模块状态失败");
            LogStore.append(this, "app", "读取框架状态失败 " + exception.getClass().getSimpleName());
        }
    }

    private String runningTargetStatus(List<HookedTarget> targets) {
        for (HookedTarget target : targets) {
            if (TARGET_PACKAGE.equals(target.getProcessName())) {
                return target.getLoadedVersionCode() == BuildConfig.VERSION_CODE
                        ? "模块已加载" : "小米桌面仍在运行旧版模块";
            }
        }
        return "小米桌面未运行或模块尚未加载";
    }

    private String installedHomeVersion() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(
                    TARGET_PACKAGE, PackageManager.PackageInfoFlags.of(0));
            String family = info.versionName != null && info.versionName.startsWith("7.")
                    ? "7.x 桥" : "4.x/兼容探测";
            return "版本 " + info.versionName + "，" + family;
        } catch (PackageManager.NameNotFoundException exception) {
            return "未安装";
        }
    }

    private void setStatusRow(int rowId, TextView valueView, String label, String value) {
        valueView.setText(value);
        findViewById(rowId).setContentDescription(label + "，" + value);
    }

    private void showAboutPage() {
        showingAbout = true;
        statusPage.setVisibility(View.GONE);
        aboutPage.setVisibility(View.VISIBLE);
        updateIgnoredStatus();
        focusForAccessibility(findViewById(R.id.back_to_status));
    }

    private void showStatusPage() {
        showingAbout = false;
        aboutPage.setVisibility(View.GONE);
        statusPage.setVisibility(View.VISIBLE);
        focusForAccessibility(openAbout);
    }

    private void handleBack() {
        if (showingAbout) {
            showStatusPage();
        } else {
            finish();
        }
    }

    @SuppressLint("AccessibilityFocus")
    private static void focusForAccessibility(View view) {
        view.post(() -> {
            view.requestFocus();
            view.performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        });
    }

    private void shareLogs() {
        if (!LogStore.hasLogs(this)) {
            aboutStatus.setText(R.string.no_logs);
            return;
        }
        LogStore.append(this, "app", "用户请求发送日志");
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_STREAM, DiagnosticsProvider.LOG_URI)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        send.setClipData(ClipData.newRawUri(LogStore.FILE_NAME,
                DiagnosticsProvider.LOG_URI));
        Intent chooser = Intent.createChooser(send, getString(R.string.share_logs))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(chooser);
    }

    private void checkUpdates(boolean manual) {
        checkUpdates.setEnabled(false);
        if (manual) {
            aboutStatus.setText(R.string.checking_updates);
        }
        backgroundExecutor.execute(() -> {
            try {
                UpdateManifest manifest = UpdateClient.fetch();
                runOnUiThread(() -> handleUpdateResult(manifest, manual));
            } catch (IOException | IllegalArgumentException exception) {
                LogStore.append(this, "app", "更新检查失败 "
                        + exception.getClass().getSimpleName());
                runOnUiThread(() -> {
                    checkUpdates.setEnabled(true);
                    if (manual) {
                        aboutStatus.setText(R.string.update_failed);
                    }
                });
            }
        });
    }

    private void handleUpdateResult(UpdateManifest manifest, boolean manual) {
        checkUpdates.setEnabled(true);
        if (isDestroyed()) {
            return;
        }
        if (manifest.versionCode() <= BuildConfig.VERSION_CODE) {
            if (manual) {
                aboutStatus.setText(R.string.latest_version);
            }
            return;
        }
        if (!manual && UpdateState.ignoredVersion(this) == manifest.versionCode()) {
            return;
        }
        String changelog = manifest.changelog().isEmpty()
                ? "暂无更新说明"
                : "• " + String.join("\n• ", manifest.changelog());
        new AlertDialog.Builder(this)
                .setTitle("ActionGrid 更新")
                .setMessage(getString(R.string.update_available, manifest.versionName(), changelog))
                .setNeutralButton(R.string.update_now,
                        (dialog, which) -> startDownload(manifest))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.ignore_version, (dialog, which) -> {
                    UpdateState.ignore(this, manifest.versionCode());
                    updateIgnoredStatus();
                })
                .show();
    }

    private void startDownload(UpdateManifest manifest) {
        if (!UpdateNotification.canNotify(this)) {
            pendingUpdateAfterNotificationPermission = manifest;
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                aboutStatus.setText(R.string.update_notification_permission);
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        NOTIFICATION_PERMISSION_REQUEST);
            } else {
                aboutStatus.setText(R.string.update_notifications_disabled);
                try {
                    startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                            .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                } catch (RuntimeException exception) {
                    pendingUpdateAfterNotificationPermission = null;
                    focusForAccessibility(checkUpdates);
                }
            }
            return;
        }
        beginDownload(manifest);
    }

    private void beginDownload(UpdateManifest manifest) {
        try {
            UpdateDownloader.enqueue(this, manifest);
            aboutStatus.setText(R.string.downloading_update);
            LogStore.append(this, "app", "用户已开始下载更新");
        } catch (IOException | RuntimeException exception) {
            aboutStatus.setText(R.string.download_failed);
            LogStore.append(this, "app", "开始下载失败 "
                    + exception.getClass().getSimpleName());
        }
    }

    private void updateIgnoredStatus() {
        int ignored = UpdateState.ignoredVersion(this);
        TextView text = findViewById(R.id.ignored_version);
        View restore = findViewById(R.id.restore_update_prompts);
        if (ignored <= 0) {
            text.setVisibility(View.GONE);
            restore.setVisibility(View.GONE);
        } else {
            text.setText(getString(R.string.update_ignored, ignored));
            text.setVisibility(View.VISIBLE);
            restore.setVisibility(View.VISIBLE);
        }
    }

    private void updatePendingState(boolean ensureNotification) {
        String error = UpdateState.consumeError(this);
        if (error != null) {
            aboutStatus.setText(R.string.download_failed);
            LogStore.append(this, "app", "更新校验失败 " + error);
        }
        File verified = UpdateState.verifiedFile(this);
        if (verified == null) {
            return;
        }
        aboutStatus.setText(R.string.update_ready_notification);
        if (ensureNotification) {
            UpdateNotification.showReady(this);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != NOTIFICATION_PERMISSION_REQUEST) {
            return;
        }
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED
                && pendingUpdateAfterNotificationPermission != null
                && UpdateNotification.canNotify(this)) {
            UpdateManifest pending = pendingUpdateAfterNotificationPermission;
            pendingUpdateAfterNotificationPermission = null;
            beginDownload(pending);
            return;
        }
        if (grantResults.length == 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            pendingUpdateAfterNotificationPermission = null;
            aboutStatus.setText(R.string.update_notification_permission_denied);
            focusForAccessibility(checkUpdates);
        } else {
            aboutStatus.setText(R.string.update_notifications_disabled);
        }
    }
}
