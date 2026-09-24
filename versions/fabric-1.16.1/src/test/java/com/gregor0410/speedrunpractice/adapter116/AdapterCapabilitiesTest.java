package com.gregor0410.speedrunpractice.adapter116;

import com.gregor0410.speedrunpractice.adapter116.live.LiveAdapter116;
import com.gregor0410.speedrunpractice.common.adapter.Capability;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Guards plan sections 7 and 98 through the shell: support flags come only
 * from the live delegate, and the shell adds no claims of its own.
 */
public class AdapterCapabilitiesTest {
    @Test
    public void supportsNothingWhenLiveClaimsNothing() {
        AdapterSet116 adapter = new AdapterSet116(new FakeMinecraftAdapter());
        for (Capability capability : Capability.values()) {
            assertFalse("must not claim " + capability + " before in-game verification",
                    adapter.supports(capability));
        }
    }

    @Test
    public void supportsDelegatesToLive() {
        FakeMinecraftAdapter live = new FakeMinecraftAdapter();
        live.claim(Capability.DRAGON_FORCE_PERCH);
        AdapterSet116 adapter = new AdapterSet116(live);
        assertTrue(adapter.supports(Capability.DRAGON_FORCE_PERCH));
        assertFalse(adapter.supports(Capability.CUSTOM_DIMENSION_RUNTIME));
    }

    @Test
    public void liveClaimsOnlyHeadlessVerifiedCapabilities() {
        // Plan section 15: CUSTOM_DIMENSION_RUNTIME (headless worlds suite
        // 5/5 on 2026-09-22) and BASTION_TYPE_QUERY (live bastion metadata
        // matched the reviewed fixture on all 5 canonical seeds in the
        // headless seed-search differential 5/5) passed on the real 1.16.1
        // dedicated server; the rest stay false (see LiveAdapter116).
        LiveAdapter116 live = new LiveAdapter116();
        assertTrue(live.supports(Capability.CUSTOM_DIMENSION_RUNTIME));
        assertTrue(live.supports(Capability.BASTION_TYPE_QUERY));
        assertFalse(live.supports(Capability.FAST_WORLD_RESET));
        assertFalse(live.supports(Capability.DRAGON_FORCE_PERCH));
        assertFalse(live.supports(Capability.PORTAL_STATE_CAPTURE));
        assertFalse(live.supports(Capability.STRUCTURE_METADATA_SEARCH));
        assertFalse(live.supports(null));
    }
}
