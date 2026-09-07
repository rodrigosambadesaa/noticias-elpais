package com.example.muyinteresante.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ActivityRecreationPolicyTest {

    @Test
    public void savedActivityStateSkipsInitialRemoteLoad() {
        assertTrue(ActivityRecreationPolicy.shouldLoadInitialNews(false));
        assertFalse(ActivityRecreationPolicy.shouldLoadInitialNews(true));
    }

    @Test
    public void restoredVisibleCountKeepsLoadedPagesWithinCache() {
        assertEquals(20, ActivityRecreationPolicy.visibleNewsCount(0, 20, 80));
        assertEquals(60, ActivityRecreationPolicy.visibleNewsCount(60, 20, 80));
        assertEquals(80, ActivityRecreationPolicy.visibleNewsCount(200, 20, 80));
    }
}
