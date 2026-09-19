package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.adapter121.AdapterSet121;
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
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Live 1.21.1 adapter behind the {@link AdapterSet121} shell (plan section
 * 9, landing slice by slice). Every slice is live: commands, worlds,
 * players, inventories, structures, portals, dragons, registries, seeds
 * (including Stage-B lava verification), the GUI, and the timer bridge.
 *
 * <p>World tracking: linked overworld/nether/end triples are remembered by
 * world key so deletion removes the whole triple, spawn lookup finds the
 * sibling overworld, and structure queries resolve cross-dimension
 * generators.
 */
public final class LiveAdapter121 implements MinecraftAdapter {
    private final CommandAdapter commands = new LiveCommands121();

    private final WorldAdapter worlds = new LiveWorlds121(this);

    private final PlayerAdapter players = new LivePlayers121(this);

    private final InventoryAdapter inventories = new LiveInventories121(this);

    private final StructureAdapter structures = new LiveStructures121(this);

    private final PortalAdapter portals = new LivePortals121(this);

    private final DragonAdapter dragons = new LiveDragons121(this);

    private final RegistryAdapter registries = new LiveRegistries121();

    private final GuiAdapter gui = new LiveGui121();

    private final TimerAdapter timer = new LiveTimer121();

    private final SeedAnalyzer seeds = new SeedAnalyzer121(this);

    private volatile MinecraftServer server;
    private final Map<RegistryKey<World>, LiveWorld121> byKey =
            Collections.synchronizedMap(new HashMap<RegistryKey<World>, LiveWorld121>());
    private final Map<RegistryKey<World>, Map<PracticeDimension, LiveWorld121>> triples =
            Collections.synchronizedMap(
                    new HashMap<RegistryKey<World>, Map<PracticeDimension, LiveWorld121>>());

    /** The scenario's world: set on create/reset, cleared on delete. */
    private volatile LiveWorld121 currentWorld;

    /** The server currently running the game, or null outside a session. */
    public MinecraftServer server() {
        return server;
    }

    public void setServer(MinecraftServer server) {
        this.server = server;
        if (server == null) {
            byKey.clear();
            triples.clear();
            currentWorld = null;
        }
    }

    /** Remembers one created practice world for handle resolution. */
    void track(LiveWorld121 handle) {
        byKey.put(handle.world().getRegistryKey(), handle);
    }

    /** Remembers a linked overworld/nether/end triple under every member key. */
    void trackTriple(Map<PracticeDimension, LiveWorld121> triple) {
        Map<PracticeDimension, LiveWorld121> copy =
                Collections.unmodifiableMap(new EnumMap<PracticeDimension, LiveWorld121>(triple));
        for (LiveWorld121 member : copy.values()) {
            track(member);
            triples.put(member.world().getRegistryKey(), copy);
        }
    }

    /** Handle for a live world key, or null when untracked. */
    LiveWorld121 tracked(RegistryKey<World> key) {
        return byKey.get(key);
    }

    LiveWorld121 currentWorld() {
        return currentWorld;
    }

    void setCurrentWorld(PracticeWorld world) {
        currentWorld = (LiveWorld121) world;
    }

    /** Triple containing the given world key, or null when untracked. */
    Map<PracticeDimension, LiveWorld121> triple(RegistryKey<World> key) {
        return triples.get(key);
    }

    /** Forgets a world and, for triple members, the whole triple. */
    Map<PracticeDimension, LiveWorld121> forget(RegistryKey<World> key) {
        Map<PracticeDimension, LiveWorld121> triple = triples.remove(key);
        if (triple != null) {
            for (LiveWorld121 member : triple.values()) {
                RegistryKey<World> memberKey = member.world().getRegistryKey();
                byKey.remove(memberKey);
                triples.remove(memberKey);
            }
            return triple;
        }
        byKey.remove(key);
        return null;
    }

    /** Resolves the backing world for a practice handle, failing readably. */
    static ServerWorld requireWorld(LiveAdapter121 adapter, PracticeWorld world,
            String operation) throws PracticeException {
        if (!(world instanceof LiveWorld121)) {
            throw new PracticeException(operation + " got a foreign world handle",
                    "That practice world belongs to another session. Stop and start it again.");
        }
        ServerWorld backing = ((LiveWorld121) world).world();
        MinecraftServer server = adapter.server();
        if (server == null || server.getWorld(backing.getRegistryKey()) == null) {
            throw new PracticeException(operation + " found no live world",
                    "That practice world is gone (the session ended?). Stop and start it again.");
        }
        return backing;
    }

    @Override
    public GameVersion version() {
        return GameVersion.MC_1_21_1;
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
        // created/reset/deleted across the headless world probes) and
        // BASTION_TYPE_QUERY (housing/bridge metadata from live starts, plus
        // the type-filtered seed probes) passed on the real 1.21.1 dedicated
        // server. The rest stay false: FAST_WORLD_RESET has no recycled-world
        // path (rebuild only), DRAGON_FORCE_PERCH never ran against a living
        // dragon (player-gated spawn), PORTAL_STATE_CAPTURE has no
        // capture/restore API, and seed search reads no portal-room/eye-count
        // metadata.
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
}
