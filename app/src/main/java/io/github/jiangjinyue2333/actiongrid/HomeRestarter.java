package io.github.jiangjinyue2333.actiongrid;

import android.content.SharedPreferences;

import io.github.jiangjinyue2333.actiongrid.hook.HomeRestartProtocol;
import io.github.libxposed.service.HookedTarget;
import io.github.libxposed.service.XposedService;

final class HomeRestarter {
    private static final String TARGET_PACKAGE = "com.miui.home";

    enum Result {
        REQUESTED,
        SERVICE_UNAVAILABLE,
        API_UNSUPPORTED,
        REMOTE_PREFERENCES_UNAVAILABLE,
        SCOPE_MISSING,
        MODULE_NOT_CURRENT,
        WRITE_FAILED
    }

    private HomeRestarter() {
    }

    static Result requestXiaomiHomeRestart(XposedService service) {
        if (service == null) {
            return Result.SERVICE_UNAVAILABLE;
        }
        try {
            if (service.getApiVersion() != XposedService.API_102) {
                return Result.API_UNSUPPORTED;
            }
            if ((service.getFrameworkProperties() & XposedService.PROP_CAP_REMOTE) == 0) {
                return Result.REMOTE_PREFERENCES_UNAVAILABLE;
            }
            if (!service.getScope().contains(TARGET_PACKAGE)) {
                return Result.SCOPE_MISSING;
            }
            HookedTarget runningHome = service.getRunningTargets().stream()
                    .filter(target -> TARGET_PACKAGE.equals(target.getProcessName()))
                    .findFirst()
                    .orElse(null);
            if (runningHome == null
                    || runningHome.getLoadedVersionCode() != BuildConfig.VERSION_CODE) {
                return Result.MODULE_NOT_CURRENT;
            }
            SharedPreferences preferences = service.getRemotePreferences(
                    HomeRestartProtocol.PREFERENCES_GROUP);
            long previous = preferences.getLong(HomeRestartProtocol.REQUEST_NONCE, 0L);
            boolean committed = preferences.edit()
                    .putLong(HomeRestartProtocol.REQUEST_NONCE, nextNonce(previous))
                    .commit();
            return committed ? Result.REQUESTED : Result.WRITE_FAILED;
        } catch (RuntimeException exception) {
            return Result.WRITE_FAILED;
        }
    }

    static long nextNonce(long previous) {
        long now = System.currentTimeMillis();
        return now > previous ? now : previous + 1L;
    }
}
