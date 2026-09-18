package com.gregor0410.speedrunpractice.common.gui;

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
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePreset;
import com.gregor0410.speedrunpractice.common.api.PracticeSettings;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.seeds.SeedAnalyzer;
import com.gregor0410.speedrunpractice.common.seeds.SeedQuery;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** GUI content models: menu, scenario degradation notes, results, search, loadouts, stats. */
public class PracticeMenuModelTest {
    private static MinecraftAdapter adapterSupporting(final boolean supported) {
        return new MinecraftAdapter() {
            @Override
            public GameVersion version() {
                return GameVersion.MC_1_16_1;
            }

            @Override
            public WorldAdapter worlds() {
                throw new UnsupportedOperationException();
            }

            @Override
            public PlayerAdapter players() {
                throw new UnsupportedOperationException();
            }

            @Override
            public InventoryAdapter inventories() {
                throw new UnsupportedOperationException();
            }

            @Override
            public StructureAdapter structures() {
                throw new UnsupportedOperationException();
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
                throw new UnsupportedOperationException();
            }

            @Override
            public GuiAdapter gui() {
                throw new UnsupportedOperationException();
            }

            @Override
            public TimerAdapter timer() {
                throw new UnsupportedOperationException();
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

            @Override
            public boolean supports(Capability capability) {
                return supported;
            }
        };
    }

    @Test
    public void defaultMenuListsEveryPractice() {
        assertEquals(10, PracticeMenuModel.defaultMenu().size());
        assertEquals(PracticeType.CUSTOM,
                PracticeMenuModel.defaultMenu().get(9).type());
    }

    @Test
    public void bastionScreenDisablesTypeFilterWhenUnsupported() {
        PracticeSettings defaults = new PracticeSettings();
        defaults.set("bastion.type", "housing");
        PracticePreset preset = new PracticePreset(PracticeId.of("bastion_housing"), "Housing",
                PracticeType.BASTION, defaults);
        PracticeMenuModel.ScenarioScreen screen = PracticeMenuModel.ScenarioScreen.forPreset(
                preset, adapterSupporting(false), Collections.<String>emptyList());
        assertFalse(screen.options().containsKey("bastion.type"));
        assertEquals(1, screen.unsupportedNotes().size());
        assertTrue(screen.unsupportedNotes().get(0).contains("1.16.1"));

        PracticeMenuModel.ScenarioScreen supported = PracticeMenuModel.ScenarioScreen.forPreset(
                preset, adapterSupporting(true), Collections.<String>emptyList());
        assertEquals("housing", supported.options().get("bastion.type"));
        assertTrue(supported.unsupportedNotes().isEmpty());
    }

    @Test
    public void oneCycleScreenNotesMissingPerch() {
        PracticePreset preset = new PracticePreset(PracticeId.of("onecycle"), "One Cycle",
                PracticeType.ONE_CYCLE, new PracticeSettings());
        PracticeMenuModel.ScenarioScreen screen = PracticeMenuModel.ScenarioScreen.forPreset(
                preset, adapterSupporting(false), Collections.<String>emptyList());
        assertEquals(1, screen.unsupportedNotes().size());
        assertTrue(screen.unsupportedNotes().get(0).contains("perch"));
    }

    @Test
    public void formatTimePadsAndFloors() {
        assertEquals("00:00.000", PracticeMenuModel.ResultsScreen.formatTime(0L));
        assertEquals("00:00.000", PracticeMenuModel.ResultsScreen.formatTime(-5L));
        assertEquals("01:02.003", PracticeMenuModel.ResultsScreen.formatTime(62003L));
        assertEquals("65:00.000", PracticeMenuModel.ResultsScreen.formatTime(3900000L));
    }

    @Test
    public void resultsActionsCoverRetryPaths() {
        PracticeMenuModel.ResultsScreen screen =
                new PracticeMenuModel.ResultsScreen("End", 9000L, 8000L, 3);
        assertTrue(screen.actions().contains("retry_same"));
        assertTrue(screen.actions().contains("new_seed"));
        assertTrue(screen.actions().contains("previous_seed"));
    }

    @Test
    public void seedSearchScreenCarriesCounters() {
        PracticeMenuModel.SeedSearchScreen screen = new PracticeMenuModel.SeedSearchScreen(
                "housing", 100L, 3L, 2L, 1L, true, false, Arrays.asList(5L, 6L));
        assertEquals("housing", screen.preset());
        assertEquals(100L, screen.tested());
        assertEquals(3L, screen.matched());
        assertEquals(2L, screen.verified());
        assertEquals(1L, screen.failed());
        assertTrue(screen.finished());
        assertFalse(screen.cancelled());
        assertEquals(Arrays.asList(5L, 6L), screen.seeds());
    }

    @Test
    public void loadoutAndStatisticsScreens() {
        PracticeMenuModel.LoadoutScreen loadouts = new PracticeMenuModel.LoadoutScreen(
                Arrays.asList("a", "b"), "a");
        assertEquals(Arrays.asList("a", "b"), loadouts.loadoutIds());
        assertEquals("a", loadouts.selectedId());
        assertNull(new PracticeMenuModel.LoadoutScreen(null, null).selectedId());

        PracticeMenuModel.StatisticsScreen.Row row =
                new PracticeMenuModel.StatisticsScreen.Row("End", 4, 3, 8000L);
        PracticeMenuModel.StatisticsScreen stats = new PracticeMenuModel.StatisticsScreen(
                Collections.singletonList(row));
        assertEquals(1, stats.rows().size());
        assertEquals(3, stats.rows().get(0).completed());
        assertTrue(new PracticeMenuModel.StatisticsScreen(null).rows().isEmpty());
    }
}
