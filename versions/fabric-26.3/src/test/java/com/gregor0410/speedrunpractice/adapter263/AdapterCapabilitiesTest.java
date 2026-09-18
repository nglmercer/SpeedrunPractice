package com.gregor0410.speedrunpractice.adapter263;

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
}
