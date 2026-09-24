package com.gregor0410.speedrunpractice.adapter263;

import com.gregor0410.speedrunpractice.adapter263.live.LiveAdapter263;
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
        AdapterSet263 adapter = new AdapterSet263(new FakeMinecraftAdapter());
        for (Capability capability : Capability.values()) {
            assertFalse("must not claim " + capability + " before in-game verification",
                    adapter.supports(capability));
        }
    }

    @Test
    public void supportsDelegatesToLive() {
        FakeMinecraftAdapter live = new FakeMinecraftAdapter();
        live.claim(Capability.DRAGON_FORCE_PERCH);
        AdapterSet263 adapter = new AdapterSet263(live);
        assertTrue(adapter.supports(Capability.DRAGON_FORCE_PERCH));
        assertFalse(adapter.supports(Capability.CUSTOM_DIMENSION_RUNTIME));
    }

    @Test
    public void liveClaimsOnlyHeadlessVerifiedCapabilities() {
        // Plan section 15: CUSTOM_DIMENSION_RUNTIME (linked triples across
        // the headless world probes) and BASTION_TYPE_QUERY (live-start
        // metadata plus type-filtered seed probes) passed on the real 26.3
        // dedicated server; the rest stay false (see LiveAdapter263).
        LiveAdapter263 live = new LiveAdapter263();
        assertTrue(live.supports(Capability.CUSTOM_DIMENSION_RUNTIME));
        assertTrue(live.supports(Capability.BASTION_TYPE_QUERY));
        assertFalse(live.supports(Capability.FAST_WORLD_RESET));
        assertFalse(live.supports(Capability.DRAGON_FORCE_PERCH));
        assertFalse(live.supports(Capability.PORTAL_STATE_CAPTURE));
        assertFalse(live.supports(Capability.STRUCTURE_METADATA_SEARCH));
        assertFalse(live.supports(null));
    }
}
