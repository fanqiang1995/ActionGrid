package io.github.jiangjinyue2333.actiongrid;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import java.lang.ref.WeakReference;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class ActionGridApp extends Application implements
        XposedServiceHelper.OnServiceListener, Application.ActivityLifecycleCallbacks {
    public interface ServiceListener {
        void onServiceChanged(XposedService service);
    }

    private static final Set<ServiceListener> LISTENERS = new CopyOnWriteArraySet<>();
    private static final AtomicBoolean COLD_UPDATE_CHECK = new AtomicBoolean();
    private static volatile XposedService service;
    private static volatile WeakReference<Activity> resumedActivity = new WeakReference<>(null);

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
        XposedServiceHelper.registerListener(this);
    }

    public static void addServiceListener(ServiceListener listener) {
        LISTENERS.add(listener);
        listener.onServiceChanged(service);
    }

    public static void removeServiceListener(ServiceListener listener) {
        LISTENERS.remove(listener);
    }

    public static boolean consumeColdUpdateCheck() {
        return COLD_UPDATE_CHECK.compareAndSet(false, true);
    }

    public static Activity resumedActivity() {
        return resumedActivity.get();
    }

    @Override
    public void onServiceBind(XposedService boundService) {
        service = boundService;
        notifyListeners(boundService);
    }

    @Override
    public void onServiceDied(XposedService deadService) {
        if (service == deadService) {
            service = null;
            notifyListeners(null);
        }
    }

    private static void notifyListeners(XposedService value) {
        for (ServiceListener listener : LISTENERS) {
            listener.onServiceChanged(value);
        }
    }

    @Override
    public void onActivityResumed(Activity activity) {
        resumedActivity = new WeakReference<>(activity);
    }

    @Override
    public void onActivityPaused(Activity activity) {
        if (resumedActivity.get() == activity) {
            resumedActivity = new WeakReference<>(null);
        }
    }

    @Override
    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
