package com.gregor0410.speedrunpractice.adapter121;

import com.gregor0410.speedrunpractice.common.adapter.CommandAdapter;
import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.commands.PracticeCommands;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Locks the documented interim contracts of the 1.21.1 skeleton: command
 * registration is retained for the porting entrypoint, registry answers stay
 * optimistic until live registries are reachable, and the seed analyzer
 * matches only unconstrained queries until real seed math lands.
 */
public class AdapterInterimContractTest {
    private static CommandAdapter.CommandExecutor executor() {
        return new CommandAdapter.CommandExecutor() {
            @Override
            public int execute(CommandAdapter.CommandContextView context) {
                return 1;
            }
        };
    }

    @Test
    public void versionIs121() {
        assertEquals(GameVersion.MC_1_21_1, new AdapterSet121().version());
    }

    @Test
    public void registerRetainsTreeAndExecutor() throws PracticeException {
        AdapterSet121 adapter = new AdapterSet121();
        assertNull(adapter.registeredCommands());
        assertNull(adapter.commandExecutor());
        PracticeCommands.Node root = PracticeCommands.buildTree();
        CommandAdapter.CommandExecutor executor = executor();
        adapter.commands().register(root, executor);
        assertSame(root, adapter.registeredCommands());
        assertSame(executor, adapter.commandExecutor());
    }

    @Test
    public void registerRejectsNull() throws PracticeException {
        AdapterSet121 adapter = new AdapterSet121();
        try {
            adapter.commands().register(null, executor());
            fail("expected IllegalArgumentException for a null root");
        } catch (IllegalArgumentException expected) {
        }
        try {
            adapter.commands().register(PracticeCommands.buildTree(), null);
            fail("expected IllegalArgumentException for a null executor");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void registryInterimContract() {
        AdapterSet121 adapter = new AdapterSet121();
        assertEquals("minecraft:air", adapter.registries().normalizeItemId(null));
        assertEquals("minecraft:stone", adapter.registries().normalizeItemId("Stone"));
        assertEquals("minecraft:diamond", adapter.registries().normalizeItemId("minecraft:Diamond"));
        assertTrue(adapter.registries().itemExists("minecraft:stone"));
        assertFalse(adapter.registries().itemExists(null));
        assertFalse(adapter.registries().itemExists("   "));
        assertEquals(64, adapter.registries().maxStackSize("minecraft:stone"));
    }

    @Test
    public void analyzerMatchesOnlyUnconstrainedQueries() {
        AdapterSet121 adapter = new AdapterSet121();
        SeedQuery open = SeedQuery.builder().version(GameVersion.MC_1_21_1).build();
        assertTrue(adapter.seeds().matches(123L, open));
        assertTrue(adapter.seeds().analyze(123L, open).matches());
        SeedQuery biome = SeedQuery.builder().version(GameVersion.MC_1_21_1).requireBiome("beach").build();
        assertFalse(adapter.seeds().matches(123L, biome));
        assertFalse(adapter.seeds().analyze(123L, biome).matches());
        SeedQuery structure = SeedQuery.builder().version(GameVersion.MC_1_21_1)
                .requireStructure("fortress").build();
        assertFalse(adapter.seeds().matches(123L, structure));
    }

    @Test
    public void liveWorldCallStillPending() {
        AdapterSet121 adapter = new AdapterSet121();
        try {
            adapter.worlds().createPracticeWorld(1L, null);
            fail("expected the skeleton to stay pending until the 1.21.1 port lands");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("not ported"));
        }
    }
}
