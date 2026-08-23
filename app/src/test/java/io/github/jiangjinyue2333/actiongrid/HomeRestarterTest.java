package io.github.jiangjinyue2333.actiongrid;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HomeRestarterTest {
    @Test
    public void restartNonceAlwaysAdvances() {
        assertTrue(HomeRestarter.nextNonce(Long.MAX_VALUE - 1L) > Long.MAX_VALUE - 1L);
        assertTrue(HomeRestarter.nextNonce(0L) > 0L);
    }
}
