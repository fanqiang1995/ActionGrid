package io.github.jiangjinyue2333.actiongrid.hook;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

final class DiagnosticsClient {
    private static final Uri URI = Uri.parse(
            "content://io.github.jiangjinyue2333.actiongrid.diagnostics");

    private DiagnosticsClient() {
    }

    static void record(Context context, String message) {
        Bundle extras = new Bundle();
        extras.putString("source", "hook");
        extras.putString("message", message);
        try {
            context.getContentResolver().call(URI, "append", null, extras);
        } catch (RuntimeException ignored) {
            // Logging is optional and must not affect Xiaomi Home.
        }
    }
}
