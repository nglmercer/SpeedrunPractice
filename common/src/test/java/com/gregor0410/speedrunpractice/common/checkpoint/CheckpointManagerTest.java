package com.gregor0410.speedrunpractice.common.checkpoint;

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
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.api.PracticeResult;
import com.gregor0410.speedrunpractice.common.api.PracticeSession;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import com.gregor0410.speedrunpractice.common.commands.PracticeCommands;
import com.gregor0410.speedrunpractice.common.loadout.Loadout;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import com.gregor0410.speedrunpractice.common.timer.MonotonicPracticeTimer;
import com.gregor0410.speedrunpractice.common.timer.PracticeTimer;
import org.junit.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Checkpoint save/restore against a minimal inline adapter (common tests must
 * not depend on :test-support, which itself depends on :common).
 */
public class CheckpointManagerTest {
    private static final class FakeWorld implements PracticeWorld {
        @Override
        public String handleId() {
            return "w";
        }

        @Override
        public long seed() {
            return 42L;
        }

        @Override
        public PracticeDimension dimension() {
            return PracticeDimension.OVERWORLD;
        }

        @Override
        public GameVersion version() {
            return GameVersion.MC_1_16_1;
        }
    }

    private static final class FakePlayer implements PracticePlayer {
        @Override
        public String handleId() {
            return "p";
        }
    }

    private static final class FakeAdapter implements MinecraftAdapter {
        private PracticePosition position = new PracticePosition(1.0, 64.0, 2.0);
        private double health = 20.0;
        private int food = 20;
        private Loadout loadout;

        @Override
        public GameVersion version() {
            return GameVersion.MC_1_16_1;
        }

        @Override
        public boolean supports(Capability capability) {
            return true;
        }

        @Override
        public WorldAdapter worlds() {
            throw new UnsupportedOperationException();
        }

        @Override
        public PlayerAdapter players() {
            final FakeAdapter self = this;
            return new PlayerAdapter() {
                @Override
                public void teleport(PracticePlayer player, PracticePosition value) {
                    self.position = value;
                }

                @Override
                public void setHealth(PracticePlayer player, double value) {
                    self.health = value;
                }

                @Override
                public void setFood(PracticePlayer player, int value) {
                    self.food = value;
                }

                @Override
                public void clearEffects(PracticePlayer player) {
                }

                @Override
                public void applyLoadout(PracticePlayer player, Loadout value) {
                    self.loadout = value;
                }

                @Override
                public void resetPlayer(PracticePlayer player) {
                    self.health = 20.0;
                    self.food = 20;
                }

                @Override
                public PracticePosition getPosition(PracticePlayer player) {
                    return self.position;
                }

                @Override
                public double getHealth(PracticePlayer player) {
                    return self.health;
                }

                @Override
                public int getFood(PracticePlayer player) {
                    return self.food;
                }

                @Override
                public PracticeWorld getWorld(PracticePlayer player) {
                    return new FakeWorld();
                }
            };
        }

        @Override
        public InventoryAdapter inventories() {
            final FakeAdapter self = this;
            return new InventoryAdapter() {
                @Override
                public void applyLoadout(PracticePlayer player, Loadout value) {
                    self.loadout = value;
                }

                @Override
                public Loadout captureLoadout(PracticePlayer player, String id) {
                    return self.loadout == null ? new Loadout(id, null) : new Loadout(id, self.loadout.items());
                }

                @Override
                public void clear(PracticePlayer player) {
                    self.loadout = null;
                }
            };
        }

        @Override
        public StructureAdapter structures() {
            return new StructureAdapter() {
                @Override
                public Optional<StructureLocation> locateNearest(PracticeWorld world, StructureQuery query) {
                    return Optional.empty();
                }

                @Override
                public List<StructureLocation> locate(PracticeWorld world, StructureQuery query, int limit) {
                    return java.util.Collections.emptyList();
                }
            };
        }

        @Override
        public PortalAdapter portals() {
            throw new UnsupportedOperationException();
        }

        @Override
        public DragonAdapter dragons() {
            throw new UnsupportedOperationException();
        }

        @Override
        public RegistryAdapter registries() {
            throw new UnsupportedOperationException();
        }

        @Override
        public CommandAdapter commands() {
            return new CommandAdapter() {
                @Override
                public void register(PracticeCommands.Node root, CommandExecutor executor) {
                }
            };
        }

        @Override
        public GuiAdapter gui() {
            return new GuiAdapter() {
                @Override
                public void openMainMenu(PracticePlayer player) {
                }

                @Override
                public void openScenarioScreen(PracticePlayer player, PracticePreset preset) {
                }

                @Override
                public void openResultsScreen(PracticePlayer player, PracticeResult result) {
                }

                @Override
                public boolean isAvailable() {
                    return false;
                }
            };
        }

        @Override
        public TimerAdapter timer() {
            return new TimerAdapter() {
                @Override
                public boolean isAvailable() {
                    return false;
                }

                @Override
                public void resetTimer() {
                }

                @Override
                public void startTimer() {
                }

                @Override
                public void stopTimer() {
                }

                @Override
                public void pauseTimer() {
                }
            };
        }

        @Override
        public SeedAnalyzer seeds() {
            return new SeedAnalyzer() {
                @Override
                public SeedAnalysis analyze(long seed, SeedQuery query) {
                    return SeedAnalysis.of(true, null);
                }

                @Override
                public boolean matches(long seed, SeedQuery query) {
                    return true;
                }
            };
        }
    }

    @Test
    public void saveRestoreClearRoundTrip() throws Exception {
        FakeAdapter adapter = new FakeAdapter();
        PracticeTimer timer = new MonotonicPracticeTimer();
        CheckpointManager manager = new CheckpointManager.InMemoryCheckpointManager(adapter, timer);
        PracticeSession session = new PracticeSession(PracticeId.of("overworld"), 42L);
        PracticeContext context = new PracticeContext(session, adapter, new FakeWorld(), new FakePlayer(),
                new PracticeSettings(), 42L);

        timer.setElapsedMs(1234L);
        manager.save(context);
        assertTrue(manager.has(PracticeId.of("overworld")));

        adapter.players().teleport(new FakePlayer(), new PracticePosition(500.0, 70.0, 500.0));
        adapter.players().setHealth(new FakePlayer(), 3.0);
        timer.setElapsedMs(9999L);

        manager.restore(context);
        assertEquals(1.0, adapter.players().getPosition(new FakePlayer()).x(), 0.0);
        assertEquals(20.0, adapter.players().getHealth(new FakePlayer()), 0.0);
        assertTrue(timer.elapsedMs() >= 1234L && timer.elapsedMs() < 2000L);

        manager.clear(PracticeId.of("overworld"));
        assertFalse(manager.has(PracticeId.of("overworld")));
    }

    @Test
    public void restoreWithoutSaveFailsReadably() throws Exception {
        FakeAdapter adapter = new FakeAdapter();
        CheckpointManager manager = new CheckpointManager.InMemoryCheckpointManager(adapter, new MonotonicPracticeTimer());
        PracticeSession session = new PracticeSession(PracticeId.of("end"), 1L);
        PracticeContext context = new PracticeContext(session, adapter, new FakeWorld(), new FakePlayer(),
                new PracticeSettings(), 1L);
        try {
            manager.restore(context);
            fail("expected CheckpointException");
        } catch (PracticeException.CheckpointException expected) {
            assertTrue(expected.getUserMessage().contains("No checkpoint"));
        }
    }
}
