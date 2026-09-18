package com.gregor0410.speedrunpractice.adapter263;

import com.gregor0410.speedrunpractice.common.adapter.Capability;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * Guards plan sections 7 and 98: no capability may be claimed until its
 * implementation exists, compiles, and passes in-game testing on 26.3.
 */
public class AdapterCapabilitiesTest {
    @Test
    public void supportsNothingUntilVerifiedInGame() {
        AdapterSet263 adapter = new AdapterSet263();
        for (Capability capability : Capability.values()) {
            assertFalse("must not claim " + capability + " before in-game verification",
                    adapter.supports(capability));
        }
    }
}
