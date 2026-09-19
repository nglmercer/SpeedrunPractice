package com.gregor0410.speedrunpractice.adapter121.live;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.adapter.WorldAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePlayer;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.api.PracticeResult;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.api.PracticeWorld;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Guards the live 1.21.1 sub-adapters' no-Minecraft paths: foreign handles
 * fail readably, null arguments fail fast, and world creation without a
 * server reports cleanly. Everything touching a real entity or world stays
 * player-gated (verified in-game, like the other versions).
 */
public class LiveGuards121Test {
    private static PracticePlayer foreignPlayer() {
        return new PracticePlayer() {
            @Override
            public String handleId() {
                return "foreign";
            }
        };
    }

    private static void expectForeign(String operation) {
        fail("expected PracticeException for " + operation);
    }

    @Test
    public void playersRejectForeignHandles() {
        LiveAdapter121 adapter = new LiveAdapter121();
        PracticePlayer foreign = foreignPlayer();
        PracticePosition pos = new PracticePosition(0, 64, 0, 90.0f, 0.0f);
        try {
            adapter.players().teleport(foreign, pos);
            expectForeign("teleport");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.players().setHealth(foreign, 10);
            expectForeign("setHealth");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.players().setFood(foreign, 10);
            expectForeign("setFood");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.players().clearEffects(foreign);
            expectForeign("clearEffects");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.players().resetPlayer(foreign);
            expectForeign("resetPlayer");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.players().getPosition(foreign);
            expectForeign("getPosition");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.players().getHealth(foreign);
            expectForeign("getHealth");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.players().getFood(foreign);
            expectForeign("getFood");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.players().getWorld(foreign);
            expectForeign("getWorld");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.players().capturePlayerState(foreign);
            expectForeign("capturePlayerState");
        } catch (PracticeException expected) {
            // The live override runs (foreign guard), not the interface default.
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
    }

    @Test
    public void inventoriesRejectForeignHandles() {
        LiveAdapter121 adapter = new LiveAdapter121();
        PracticePlayer foreign = foreignPlayer();
        try {
            adapter.inventories().applyLoadout(foreign, null);
            expectForeign("applyLoadout");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.inventories().captureLoadout(foreign, "kit");
            expectForeign("captureLoadout");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.inventories().clear(foreign);
            expectForeign("clear");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
    }

    @Test
    public void handlesRejectNulls() {
        try {
            new LivePlayer121(null);
            fail("expected IllegalArgumentException for a null entity");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new LiveWorld121(null, 1L, PracticeDimension.OVERWORLD);
            fail("expected IllegalArgumentException for a null world");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void structuresRejectForeignHandlesNullsAndMissingServer() {
        LiveAdapter121 adapter = new LiveAdapter121();
        PracticeWorld foreign = new PracticeWorld() {
            @Override
            public String handleId() {
                return "foreign";
            }

            @Override
            public long seed() {
                return 1L;
            }

            @Override
            public PracticeDimension dimension() {
                return PracticeDimension.OVERWORLD;
            }

            @Override
            public com.gregor0410.speedrunpractice.common.api.GameVersion version() {
                return com.gregor0410.speedrunpractice.common.api.GameVersion.MC_1_21_1;
            }
        };
        StructureAdapter.StructureQuery query = StructureAdapter.StructureQuery
                .builder("village").radius(1000).build();
        try {
            adapter.structures().locateNearest(foreign, query);
            fail("expected PracticeException for a foreign world handle");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign world handle"));
        }
        try {
            adapter.structures().locate(foreign, query, 1);
            fail("expected PracticeException for a foreign world handle");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign world handle"));
        }
        try {
            adapter.structures().locate(foreign, query, -1);
            fail("expected IllegalArgumentException for a negative limit");
        } catch (IllegalArgumentException expected) {
        } catch (PracticeException unexpected) {
            fail("expected IllegalArgumentException, got " + unexpected);
        }
    }

    @Test
    public void portalsRejectForeignHandlesNullsAndMissingServer() {
        LiveAdapter121 adapter = new LiveAdapter121();
        PracticeWorld foreign = new PracticeWorld() {
            @Override
            public String handleId() {
                return "foreign";
            }

            @Override
            public long seed() {
                return 1L;
            }

            @Override
            public PracticeDimension dimension() {
                return PracticeDimension.OVERWORLD;
            }

            @Override
            public com.gregor0410.speedrunpractice.common.api.GameVersion version() {
                return com.gregor0410.speedrunpractice.common.api.GameVersion.MC_1_21_1;
            }
        };
        PracticePosition pos = new PracticePosition(0, 64, 0, 90.0f, 0.0f);
        try {
            adapter.portals().createNetherPortal(foreign, pos);
            fail("expected PracticeException for a foreign world handle");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign world handle"));
        }
        try {
            adapter.portals().linkPortals(foreign, foreign, pos);
            fail("expected PracticeException for a foreign world handle");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign world handle"));
        }
        try {
            // Handle guard runs before argument checks.
            adapter.portals().createNetherPortal(foreign, null);
            fail("expected PracticeException for a foreign world handle");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign world handle"));
        }
    }

    @Test
    public void dragonsRejectForeignHandles() {
        LiveAdapter121 adapter = new LiveAdapter121();
        PracticeWorld foreign = new PracticeWorld() {
            @Override
            public String handleId() {
                return "foreign";
            }

            @Override
            public long seed() {
                return 1L;
            }

            @Override
            public PracticeDimension dimension() {
                return PracticeDimension.END;
            }

            @Override
            public com.gregor0410.speedrunpractice.common.api.GameVersion version() {
                return com.gregor0410.speedrunpractice.common.api.GameVersion.MC_1_21_1;
            }
        };
        try {
            adapter.dragons().resetFight(foreign);
            fail("expected PracticeException for a foreign world handle");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign world handle"));
        }
        try {
            adapter.dragons().forcePerch(foreign);
            fail("expected PracticeException for a foreign world handle");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign world handle"));
        }
        try {
            adapter.dragons().hasLivingDragon(foreign);
            fail("expected PracticeException for a foreign world handle");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign world handle"));
        }
    }

    @Test
    public void guiRejectsForeignHandles() {
        LiveAdapter121 adapter = new LiveAdapter121();
        PracticePlayer foreign = foreignPlayer();
        PracticePreset preset = new PracticePreset(PracticeId.of("nether"), "Nether",
                PracticeType.NETHER, new PracticeSettings());
        PracticeResult result = new PracticeResult(PracticeId.of("nether"), 1L, 1000L,
                PracticeResult.Status.COMPLETED);
        try {
            adapter.gui().openMainMenu(foreign);
            expectForeign("openMainMenu");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.gui().openScenarioScreen(foreign, preset);
            expectForeign("openScenarioScreen");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
        try {
            adapter.gui().openResultsScreen(foreign, result);
            expectForeign("openResultsScreen");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("foreign player handle"));
        }
    }

    @Test
    public void timerBridgeIsUnavailableNoop() {
        LiveAdapter121 adapter = new LiveAdapter121();
        assertFalse(adapter.timer().isAvailable());
        adapter.timer().resetTimer();
        adapter.timer().startTimer();
        adapter.timer().stopTimer();
        adapter.timer().pauseTimer();
    }

    @Test
    public void worldsRejectNullOptionsAndMissingServer() {
        LiveAdapter121 adapter = new LiveAdapter121();
        try {
            adapter.worlds().createPracticeWorld(1L, null);
            fail("expected IllegalArgumentException for null options");
        } catch (IllegalArgumentException expected) {
        } catch (PracticeException unexpected) {
            fail("expected IllegalArgumentException, got " + unexpected);
        }
        try {
            adapter.worlds().createPracticeWorld(1L, WorldAdapter.PracticeWorldOptions
                    .builder(PracticeDimension.OVERWORLD).build());
            fail("expected PracticeException with no running server");
        } catch (PracticeException expected) {
            assertTrue(expected.getMessage().contains("no running server"));
        }
    }
}
