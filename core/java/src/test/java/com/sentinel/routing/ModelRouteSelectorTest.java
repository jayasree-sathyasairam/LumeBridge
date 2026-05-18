package com.sentinel.routing;

import com.lumebridge.SentinelConstants;
import com.lumebridge.routing.ModelRouteSelector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModelRouteSelectorTest {

    @Test
    void tieredRoutesFollowComplexityAndTokens() {
        assertEquals(SentinelConstants.ROUTE_GPT4O_MINI,
                ModelRouteSelector.selectPrimaryRoute(ModelRouteSelector.Complexity.LOW, 100, false));
        assertEquals(SentinelConstants.ROUTE_GPT4O,
                ModelRouteSelector.selectPrimaryRoute(ModelRouteSelector.Complexity.MEDIUM, 100, false));
        assertEquals(SentinelConstants.ROUTE_GPT4_TURBO,
                ModelRouteSelector.selectPrimaryRoute(ModelRouteSelector.Complexity.HIGH, 100, false));
        assertEquals(SentinelConstants.ROUTE_GPT4_TURBO,
                ModelRouteSelector.selectPrimaryRoute(ModelRouteSelector.Complexity.LOW, 2500, false));
    }
}
