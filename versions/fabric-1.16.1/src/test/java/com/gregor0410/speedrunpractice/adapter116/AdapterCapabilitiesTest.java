package com.gregor0410.speedrunpractice.adapter116;

import com.gregor0410.speedrunpractice.common.adapter.Capability;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/**
 * Guards plan sections 7 and 98: no capability may be claimed until its
 * implementation exists, compiles, and passes in-game testing on 1.16.1.
 */
public class AdapterCapabilitiesTest {
    @Test
    public void supportsNothingUntilVerifiedInGame() {
        AdapterSet116 adapter = new AdapterSet116();
        for (Capability capability : Capability.values()) {
            assertFalse("must not claim " + capability + " before in-game verification",
                    adapter.supports(capability));
        }
    }
}
