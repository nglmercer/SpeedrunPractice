package com.gregor0410.speedrunpractice.seedsearch;

import com.gregor0410.speedrunpractice.common.util.SimpleJson;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Guards the plan-section-14 verified seed fixtures: every
 * {@code fixtures/seeds-*.json} must parse and carry complete entries
 * (spawn, biome, lava flag, structure positions plus bastion type and
 * portal room where the collector records them), and 26.3 must hold at
 * least five entries flagged {@code "verified": true}. The values
 * themselves were observed in the real game, not invented: see the
 * fixture's {@code method} note.
 */
public class SeedFixtureTest {
    private static final Set<String> KNOWN_BASTION_TYPES = new HashSet<String>(Arrays.asList(
            "housing", "stables", "treasure", "bridge"));

    @Test
    public void fixturesAreComplete() throws Exception {
        Map<String, Object> root = load("fixtures/seeds-26.3.json");
        assertEquals("26.3", root.get("minecraftVersion"));
        assertEquals(Boolean.TRUE, root.get("verified"));
        assertNotNull(root.get("verifiedOn"));
        assertNotNull(root.get("method"));
        Object seeds = root.get("seeds");
        assertTrue(seeds instanceof List);
        List<?> entries = (List<?>) seeds;
        int verified = 0;
        for (Object entry : entries) {
            assertTrue(entry instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> seed = (Map<String, Object>) entry;
            assertTrue(seed.get("seed") instanceof Number);
            if (Boolean.TRUE.equals(seed.get("verified"))) {
                verified++;
            }
            assertPosition(secret(seed, "spawn"), "spawn");
            assertNotNull(secret(seed, "spawnBiome"));
            assertTrue(secret(seed, "lavaAvailable") instanceof Boolean);
            Object structures = secret(seed, "structures");
            assertTrue(structures instanceof Map);
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) structures;
            assertPosition(secret(map, "village"), "village");
            Map<String, Object> stronghold = assertPosition(secret(map, "stronghold"), "stronghold");
            assertNotNull(stronghold.get("portalRoom"));
            assertPosition(secret(map, "fortress"), "fortress");
            Map<String, Object> bastion = assertPosition(secret(map, "bastion_remnant"), "bastion");
            assertTrue("unknown bastion type " + bastion.get("bastionType"),
                    KNOWN_BASTION_TYPES.contains(String.valueOf(bastion.get("bastionType"))));
        }
        assertTrue("need at least 5 verified 26.3 seeds, found " + verified, verified >= 5);
    }

    private static Map<String, Object> assertPosition(Object value, String what) {
        assertTrue(what + " must be an object", value instanceof Map);
        @SuppressWarnings("unchecked")
        Map<String, Object> pos = (Map<String, Object>) value;
        assertTrue(what + ".x must be a number", secret(pos, "x") instanceof Number);
        assertTrue(what + ".y must be a number", secret(pos, "y") instanceof Number);
        assertTrue(what + ".z must be a number", secret(pos, "z") instanceof Number);
        return pos;
    }

    private static Object secret(Map<String, Object> map, String key) {
        if (!map.containsKey(key)) {
            fail("missing required key \"" + key + "\"");
        }
        return map.get(key);
    }

    private static Map<String, Object> load(String resource) throws Exception {
        InputStream in = SeedFixtureTest.class.getClassLoader().getResourceAsStream(resource);
        assertNotNull("missing test resource " + resource, in);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return SimpleJson.parseObject(new String(out.toByteArray(), "UTF-8"));
        } finally {
            in.close();
        }
    }
}
