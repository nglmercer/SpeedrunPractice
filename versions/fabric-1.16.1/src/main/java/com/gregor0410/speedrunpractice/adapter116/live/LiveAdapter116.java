package com.gregor0410.speedrunpractice.adapter116.live;

import com.gregor0410.speedrunpractice.common.adapter.Capability;
import com.gregor0410.speedrunpractice.common.adapter.CommandAdapter;
import com.gregor0410.speedrunpractice.common.adapter.DragonAdapter;
import com.gregor0410.speedrunpractice.common.adapter.GuiAdapter;
import com.gregor0410.speedrunpractice.common.adapter.InventoryAdapter;
import com.gregor0410.speedrunpractice.common.adapter.MinecraftAdapter;
import com.gregor0410.speedrunpractice.common.adapter.PlayerAdapter;
import com.gregor0410.speedrunpractice.common.adapter.PortalAdapter;
import com.gregor0410.speedrunpractice.common.adapter.RegistryAdapter;
import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.adapter.TimerAdapter;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.GameVersion;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Live 1.16.1 adapter: every call reaches the real game through the legacy
 * world/player/command machinery in this module. Single-player scoped (plan
 * section 1): at most one server, one practicing player, and one active
 * practice world set at a time.
 *
 * <p>World tracking: linked overworld/nether/end triples are remembered by
 * world key so deletion removes the whole triple, spawn lookup finds the
 * sibling overworld, and portal linkage rebuilds the legacy key map.
 */
public final class LiveAdapter116 implements MinecraftAdapter {
    private final WorldAdapter worlds = new LiveWorlds(this);
    private final PlayerAdapter players = new LivePlayers(this);
    private final InventoryAdapter inventories = new LiveInventories(this);
    private final StructureAdapter structures = new LiveStructures(this);
    private final PortalAdapter portals = new LivePortals(this);
    private final DragonAdapter dragons = new LiveDragons();
    private final RegistryAdapter registries = new LiveRegistries();
    private final CommandAdapter commands = new LiveCommands();
    private final GuiAdapter gui = new LiveGui();
    private final TimerAdapter timer = new LiveTimer();
    private final SeedAnalyzer seeds = new SeedAnalyzer116(this);

    private volatile MinecraftServer server;
    private final Map<RegistryKey<World>, LiveWorld> byKey =
            Collections.synchronizedMap(new HashMap<RegistryKey<World>, LiveWorld>());
    private final Map<RegistryKey<World>, Map<PracticeDimension, LiveWorld>> triples =
            Collections.synchronizedMap(new HashMap<RegistryKey<World>, Map<PracticeDimension, LiveWorld>>());

    /** The server currently running the game, or null outside a session. */
    public MinecraftServer server() {
        return server;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
        if (server == null) {
            byKey.clear();
            triples.clear();
        }
    }

    /** Verification-only count of handles that should disappear after delete. */
    public int trackedPracticeWorlds() {
        return byKey.size();
    }

    /** Remembers one created practice world for handle resolution. */
    void track(LiveWorld handle) {
        byKey.put(handle.world().getRegistryKey(), handle);
    }

    /** Remembers a linked overworld/nether/end triple under every member key. */
    void trackTriple(Map<PracticeDimension, LiveWorld> triple) {
        Map<PracticeDimension, LiveWorld> copy =
                Collections.unmodifiableMap(new EnumMap<PracticeDimension, LiveWorld>(triple));
        for (LiveWorld member : copy.values()) {
            track(member);
            triples.put(member.world().getRegistryKey(), copy);
        }
    }

    /** Handle for a live world key, or null when untracked. */
    LiveWorld tracked(RegistryKey<World> key) {
        return byKey.get(key);
    }

    /** The scenario's world: set on create/reset, cleared on delete. */
    private volatile LiveWorld currentWorld;

    LiveWorld currentWorld() {
        return currentWorld;
    }

    void setCurrentWorld(LiveWorld currentWorld) {
        this.currentWorld = currentWorld;
    }

    /** Sibling triple for a member key, or null for single worlds. */
    Map<PracticeDimension, LiveWorld> triple(RegistryKey<World> key) {
        return triples.get(key);
    }

    /** Forgets a world and, for triple members, the whole triple. */
    Map<PracticeDimension, LiveWorld> forget(RegistryKey<World> key) {
        Map<PracticeDimension, LiveWorld> triple = triples.remove(key);
        if (triple != null) {
            for (LiveWorld member : triple.values()) {
                RegistryKey<World> memberKey = member.world().getRegistryKey();
                byKey.remove(memberKey);
                triples.remove(memberKey);
            }
            return triple;
        }
        byKey.remove(key);
        return null;
    }

    @Override
    public GameVersion version() {
        return GameVersion.MC_1_16_1;
    }

    @Override
    public WorldAdapter worlds() {
        return worlds;
    }

    @Override
    public PlayerAdapter players() {
        return players;
    }

    @Override
    public InventoryAdapter inventories() {
        return inventories;
    }

    @Override
    public StructureAdapter structures() {
        return structures;
    }

    @Override
    public PortalAdapter portals() {
        return portals;
    }

    @Override
    public DragonAdapter dragons() {
        return dragons;
    }

    @Override
    public RegistryAdapter registries() {
        return registries;
    }

    @Override
    public CommandAdapter commands() {
        return commands;
    }

    @Override
    public GuiAdapter gui() {
        return gui;
    }

    @Override
    public TimerAdapter timer() {
        return timer;
    }

    @Override
    public SeedAnalyzer seeds() {
        return seeds;
    }

    @Override
    public boolean supports(Capability capability) {
        // Plan section 15: true only when implemented, compiled, and
        // runtime-tested. CUSTOM_DIMENSION_RUNTIME (linked practice triples
        // created/reset/deleted: headless worlds suite 5/5 on 2026-09-22,
        // incl. the 50-cycle lifecycle, seed, spawn and cleanup checks) and
        // BASTION_TYPE_QUERY (live bastion-start metadata matched the
        // reviewed fixture type on all 5 canonical seeds in the headless
        // seed-search differential 5/5 on 2026-09-22) passed on the real
        // 1.16.1 dedicated server. The rest stay false: FAST_WORLD_RESET has
        // no recycled-world path (rebuild only), DRAGON_FORCE_PERCH never ran
        // against a living dragon (player-gated spawn; the dragon suite only
        // exercises resetFight), PORTAL_STATE_CAPTURE has no capture/restore
        // API, and seed search reads no portal-room/eye-count metadata.
        if (capability == null) {
            return false;
        }
        switch (capability) {
            case CUSTOM_DIMENSION_RUNTIME:
            case BASTION_TYPE_QUERY:
                return true;
            default:
                return false;
        }
    }

    /** Resolves the backing world for a practice handle, failing readably. */
    static ServerWorld requireWorld(LiveAdapter116 adapter, com.gregor0410.speedrunpractice.common.api.PracticeWorld world,
            String operation) throws com.gregor0410.speedrunpractice.common.api.PracticeException {
        if (!(world instanceof LiveWorld)) {
            throw new com.gregor0410.speedrunpractice.common.api.PracticeException(
                    operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        ServerWorld backing = ((LiveWorld) world).world();
        MinecraftServer server = adapter.server();
        if (server == null || server.getWorld(backing.getRegistryKey()) == null) {
            throw new com.gregor0410.speedrunpractice.common.api.PracticeException(
                    operation + " found no live world",
                    "That practice world is gone (the session ended?). Stop and start it again.");
        }
        return backing;
    }
}
