package io.github.jiangjinyue2333.actiongrid.hook;

import java.util.EnumSet;

public final class CapabilityProbe {
    private static final String LEGACY_LAUNCHER = "com.miui.home.launcher.Launcher";
    private static final String MODERN_LAUNCHER = "com.miui.home.launcher.BaseLauncher";

    private CapabilityProbe() {
    }

    public static CompatibilityReport probe(ClassLoader loader) {
        BridgeFamily family = classExists(loader, MODERN_LAUNCHER)
                ? BridgeFamily.MODERN_7X
                : classExists(loader, LEGACY_LAUNCHER)
                ? BridgeFamily.LEGACY_4X
                : BridgeFamily.UNSUPPORTED;
        EnumSet<Capability> capabilities = EnumSet.noneOf(Capability.class);
        if (family == BridgeFamily.UNSUPPORTED) {
            return new CompatibilityReport(family, capabilities);
        }
        String launcher = family == BridgeFamily.MODERN_7X ? MODERN_LAUNCHER : LEGACY_LAUNCHER;
        if (hasMethod(loader, launcher, "onLongClick", "android.view.View")
                && classExists(loader, "com.miui.home.launcher.ShortcutIcon")) {
            capabilities.add(Capability.ICON_BINDING);
            capabilities.add(Capability.NATIVE_DRAG);
        }
        if (hasMethod(loader, "com.miui.home.launcher.DragController", "cancelDrag")) {
            capabilities.add(Capability.NATIVE_CANCEL);
        }
        String folderIcon = family == BridgeFamily.MODERN_7X
                ? "com.miui.home.folder.FolderIcon"
                : "com.miui.home.launcher.FolderIcon";
        if (classExists(loader, folderIcon)) {
            capabilities.add(Capability.FOLDER);
        }
        if (hasMethod(loader, "com.miui.home.launcher.ShortcutInfo", "getComponentName")
                && hasMethod(loader,
                family == BridgeFamily.MODERN_7X
                        ? "com.miui.home.model.api.ItemInfo"
                        : "com.miui.home.launcher.ItemInfo",
                "getUser")) {
            capabilities.add(Capability.SHORTCUTS);
        }
        if (hasMethodNamed(loader, launcher, "dragMultiItems")) {
            capabilities.add(Capability.MULTI_SELECT);
        }
        return new CompatibilityReport(family, capabilities);
    }

    private static boolean classExists(ClassLoader loader, String className) {
        try {
            Class.forName(className, false, loader);
            return true;
        } catch (ClassNotFoundException | LinkageError ignored) {
            return false;
        }
    }

    private static boolean hasMethodNamed(ClassLoader loader, String className, String methodName) {
        try {
            for (var method : Class.forName(className, false, loader).getDeclaredMethods()) {
                if (method.getName().equals(methodName)) {
                    return true;
                }
            }
        } catch (ClassNotFoundException | LinkageError ignored) {
            // The capability remains unavailable.
        }
        return false;
    }

    private static boolean hasMethod(ClassLoader loader, String className, String methodName,
                                     String... parameterClassNames) {
        try {
            Class<?>[] parameters = new Class<?>[parameterClassNames.length];
            for (int index = 0; index < parameterClassNames.length; index++) {
                parameters[index] = Class.forName(parameterClassNames[index], false, loader);
            }
            Class.forName(className, false, loader).getMethod(methodName, parameters);
            return true;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }
}
