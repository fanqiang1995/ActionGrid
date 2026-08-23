package io.github.jiangjinyue2333.actiongrid.hook;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public record CompatibilityReport(
        BridgeFamily bridgeFamily,
        Set<Capability> availableCapabilities
) {
    public CompatibilityReport {
        availableCapabilities = availableCapabilities.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(availableCapabilities));
    }

    public String summary() {
        return bridgeFamily + " capabilities=" + availableCapabilities.size();
    }
}
