package com.gregor0410.speedrunpractice.adapter116;

import com.gregor0410.speedrunpractice.common.adapter.CommandAdapter;
import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.commands.PracticeCommands;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

/**
 * Locks the delegation contracts of the 1.16.1 shell: construction requires
 * a live delegate, every sub-adapter accessor forwards to it, command
 * registration is retained and forwarded, and the shared pure helpers keep
 * their contracts.
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
    public void constructorRejectsNullDelegate() {
        try {
            new AdapterSet116(null);
            fail("expected IllegalArgumentException for a null delegate");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void versionIs116() {
        assertEquals(GameVersion.MC_1_16_1, new AdapterSet116(new FakeMinecraftAdapter()).version());
    }

    @Test
    public void subAdaptersDelegateToLive() {
        FakeMinecraftAdapter live = new FakeMinecraftAdapter();
        AdapterSet116 adapter = new AdapterSet116(live);
        assertSame(live.worlds(), adapter.worlds());
        assertSame(live.players(), adapter.players());
        assertSame(live.inventories(), adapter.inventories());
        assertSame(live.structures(), adapter.structures());
        assertSame(live.portals(), adapter.portals());
        assertSame(live.dragons(), adapter.dragons());
        assertSame(live.registries(), adapter.registries());
        assertSame(live.gui(), adapter.gui());
        assertSame(live.timer(), adapter.timer());
        assertSame(live.seeds(), adapter.seeds());
    }

    @Test
    public void registerRetainsTreeAndExecutor() throws PracticeException {
        AdapterSet116 adapter = new AdapterSet116(new FakeMinecraftAdapter());
        assertNull(adapter.registeredCommands());
        assertNull(adapter.commandExecutor());
        PracticeCommands.Node root = PracticeCommands.buildTree();
        CommandAdapter.CommandExecutor executor = executor();
        adapter.commands().register(root, executor);
        assertSame(root, adapter.registeredCommands());
        assertSame(executor, adapter.commandExecutor());
    }

    @Test
    public void registerForwardsToLive() throws PracticeException {
        FakeMinecraftAdapter live = new FakeMinecraftAdapter();
        AdapterSet116 adapter = new AdapterSet116(live);
        PracticeCommands.Node root = PracticeCommands.buildTree();
        CommandAdapter.CommandExecutor executor = executor();
        adapter.commands().register(root, executor);
        assertSame(root, live.registered());
        assertSame(executor, live.executor());
    }

    @Test
    public void registerRejectsNull() throws PracticeException {
        AdapterSet116 adapter = new AdapterSet116(new FakeMinecraftAdapter());
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
    public void normalizeItemIdContract() {
        assertEquals("minecraft:air", RegistryIds.normalizeItemId(null));
        assertEquals("minecraft:stone", RegistryIds.normalizeItemId("Stone"));
        assertEquals("minecraft:diamond", RegistryIds.normalizeItemId("minecraft:Diamond"));
    }
}
