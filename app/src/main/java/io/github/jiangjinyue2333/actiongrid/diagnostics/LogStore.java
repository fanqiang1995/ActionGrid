package io.github.jiangjinyue2333.actiongrid.diagnostics;

import android.app.Application;
import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public final class LogStore {
    public static final String FILE_NAME = "actiongrid-diagnostics.log";
    private static final String PREFS_NAME = "diagnostics";
    private static final String ENABLED_KEY = "logging_enabled";
    private static final long MAX_SIZE_BYTES = 512 * 1024;
    private static final Object LOCK = new Object();

    private LogStore() {
    }

    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(ENABLED_KEY, false);
    }

    public static void setEnabled(Context context, boolean enabled) {
        Context appContext = context.getApplicationContext();
        synchronized (LOCK) {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(ENABLED_KEY, enabled)
                    .apply();
            deleteFile(appContext);
            if (enabled) {
                appendLocked(appContext, "app", "日志记录已开启");
            }
        }
    }

    public static void append(Context context, String source, String message) {
        Context appContext = context.getApplicationContext();
        synchronized (LOCK) {
            if (!isEnabled(appContext)) {
                return;
            }
            appendLocked(appContext, source, message);
        }
    }

    public static File file(Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    public static boolean hasLogs(Context context) {
        File file = file(context);
        return file.isFile() && file.length() > 0;
    }

    private static void appendLocked(Context context, String source, String message) {
        File file = file(context);
        if (file.length() >= MAX_SIZE_BYTES) {
            deleteFile(context);
        }
        String safeSource = sanitize(source, 48);
        String safeMessage = sanitize(message, 2048);
        String line = Instant.now() + " [" + Application.getProcessName() + "] "
                + safeSource + ": " + safeMessage + System.lineSeparator();
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(file, true), StandardCharsets.UTF_8)) {
            writer.write(line);
        } catch (IOException ignored) {
            // Diagnostics must never affect the app or the hooked launcher.
        }
    }

    private static String sanitize(String value, int maxLength) {
        String safe = value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength);
    }

    private static void deleteFile(Context context) {
        File file = file(context);
        if (file.exists() && !file.delete()) {
            try (FileOutputStream ignored = new FileOutputStream(file, false)) {
                // Truncation is the fallback when deletion is unavailable.
            } catch (IOException ignored) {
                // Diagnostics cleanup must never make the app unusable.
            }
        }
    }
}
