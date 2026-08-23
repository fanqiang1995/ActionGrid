package io.github.jiangjinyue2333.actiongrid.hook;

import android.content.SharedPreferences;
import android.os.Process;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityNodeProvider;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import io.github.libxposed.api.XposedModule;

public final class ActionGridModule extends XposedModule {
    private static final String TARGET_PACKAGE = "com.miui.home";
    private static final String TAG = "ActionGrid";

    private boolean targetProcess;
    private boolean packageAccepted;
    private final Set<String> loggedFailures = new HashSet<>();
    private SharedPreferences restartPreferences;
    private SharedPreferences.OnSharedPreferenceChangeListener restartListener;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        targetProcess = TARGET_PACKAGE.equals(param.getProcessName()) && !param.isSystemServer();
        if (targetProcess) {
            listenForRestartRequests();
        }
    }

    private void listenForRestartRequests() {
        try {
            restartPreferences = getRemotePreferences(HomeRestartProtocol.PREFERENCES_GROUP);
            restartListener = (preferences, key) -> {
                if (!HomeRestartProtocol.REQUEST_NONCE.equals(key)) {
                    return;
                }
                log(Log.INFO, TAG, "Restart requested by module app");
                Process.killProcess(Process.myPid());
            };
            restartPreferences.registerOnSharedPreferenceChangeListener(restartListener);
        } catch (RuntimeException exception) {
            log(Log.WARN, TAG, "Restart listener unavailable: "
                    + exception.getClass().getSimpleName());
        }
    }

    @Override
    public void onPackageLoaded(PackageLoadedParam param) {
        packageAccepted = targetProcess
                && param.isFirstPackage()
                && TARGET_PACKAGE.equals(param.getPackageName());
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!packageAccepted || !TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        try {
            CompatibilityReport report = CapabilityProbe.probe(param.getClassLoader());
            log(Log.INFO, TAG, "Probe " + report.summary());
            if (!report.availableCapabilities().contains(Capability.ICON_BINDING)) {
                return;
            }
            MiuiHomeBridge bridge = MiuiHomeBridge.resolve(
                    param.getClassLoader(), report.bridgeFamily());
            ActionController controller = new ActionController(bridge);
            installIconHooks(controller, bridge);
        } catch (Throwable throwable) {
            log(Log.ERROR, TAG, "Initialization failed: "
                    + throwable.getClass().getSimpleName());
        }
    }

    private void installIconHooks(ActionController controller, MiuiHomeBridge bridge)
            throws NoSuchMethodException {
        Method setTag = View.class.getMethod("setTag", Object.class);
        hook(setTag).intercept(chain -> {
            Object result = chain.proceed();
            if (chain.getThisObject() instanceof View view && controller.isSupportedIcon(view)) {
                view.post(() -> runSafely("bind", () -> controller.onBound(view)));
            }
            return result;
        });

        Method attached = View.class.getDeclaredMethod("onAttachedToWindow");
        attached.setAccessible(true);
        hook(attached).intercept(chain -> {
            Object result = chain.proceed();
            if (chain.getThisObject() instanceof View view && controller.isSupportedIcon(view)) {
                view.post(() -> runSafely("attach", () -> controller.onBound(view)));
            }
            return result;
        });

        Method createNode = View.class.getMethod("createAccessibilityNodeInfo");
        hook(createNode).intercept(chain -> {
            Object result = chain.proceed();
            if (chain.getThisObject() instanceof View view
                    && result instanceof AccessibilityNodeInfo info) {
                runSafely("node", () -> controller.onInitializeNodeInfo(view, info));
            }
            return result;
        });

        Method performAction = View.class.getMethod(
                "performAccessibilityAction", int.class, android.os.Bundle.class);
        hook(performAction).intercept(chain -> {
            if (chain.getThisObject() instanceof View view) {
                try {
                    if (controller.performAccessibilityAction(view, (int) chain.getArg(0))) {
                        return true;
                    }
                } catch (RuntimeException exception) {
                    logHookFailure("action", exception);
                }
            }
            return chain.proceed();
        });

        Method getProvider = View.class.getMethod("getAccessibilityNodeProvider");
        hook(getProvider).intercept(chain -> {
            Object result = chain.proceed();
            if (chain.getThisObject() instanceof View view) {
                try {
                    return controller.positionProvider(view,
                            result instanceof AccessibilityNodeProvider provider ? provider : null);
                } catch (RuntimeException exception) {
                    logHookFailure("provider", exception);
                }
            }
            return result;
        });

        Set<Method> hoverMethods = new HashSet<>();
        for (Class<?> hostClass : bridge.placementHostClasses()) {
            hoverMethods.add(hostClass.getMethod("dispatchHoverEvent", MotionEvent.class));
        }
        for (Method hoverMethod : hoverMethods) {
            hook(hoverMethod).intercept(chain -> {
                if (chain.getThisObject() instanceof View view) {
                    try {
                        if (controller.dispatchHoverEvent(
                                view, (MotionEvent) chain.getArg(0))) {
                            return true;
                        }
                    } catch (RuntimeException exception) {
                        logHookFailure("hover", exception);
                    }
                }
                return chain.proceed();
            });
        }
    }

    private void runSafely(String stage, Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException exception) {
            logHookFailure(stage, exception);
        }
    }

    private synchronized void logHookFailure(String stage, RuntimeException exception) {
        String key = stage + ':' + exception.getClass().getName();
        if (loggedFailures.add(key)) {
            log(Log.WARN, TAG, stage + " failed: " + exception.getClass().getSimpleName());
        }
    }
}
