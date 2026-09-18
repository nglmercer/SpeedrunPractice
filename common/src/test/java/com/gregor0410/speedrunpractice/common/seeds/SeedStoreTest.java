package com.gregor0410.speedrunpractice.common.seeds;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SeedStoreTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void favoritesCarryTagsAndNotes() throws Exception {
        SeedStore store = new SeedStore(folder.getRoot().toPath());
        assertTrue(store.listFavorites().isEmpty());
        store.addFavorite(42L);
        store.addFavorite(42L);
        assertEquals(1, store.listFavorites().size());
        assertTrue(store.isFavorite(42L));
        store.tagFavorite(42L, "housing");
        store.noteFavorite(42L, "good bastion");
        assertEquals(Arrays.asList("housing"), store.listFavorites().get(0).tags());
        assertEquals("good bastion", store.listFavorites().get(0).note());
        store.removeFavorite(42L);
        assertFalse(store.isFavorite(42L));
    }

    @Test
    public void recentsDedupeAndCap() throws Exception {
        SeedStore store = new SeedStore(folder.getRoot().toPath());
        store.addRecent(1L);
        store.addRecent(2L);
        store.addRecent(1L);
        assertEquals(Arrays.asList(1L, 2L), store.getRecent());
    }

    @Test
    public void searchesSaveLoadList() throws Exception {
        SeedStore store = new SeedStore(folder.getRoot().toPath());
        assertNull(store.loadSearch("missing"));
        store.saveSearch("housing", "{\"filters\":[]}");
        assertEquals("{\"filters\":[]}", store.loadSearch("housing"));
        assertEquals(Arrays.asList("housing"), store.listSearches());
    }

    @Test
    public void importsSkipCommentsBlanksAndGarbage() throws Exception {
        SeedStore store = new SeedStore(folder.getRoot().toPath());
        assertTrue(store.readImport("missing").isEmpty());
        store.writeImport("mine", Arrays.asList("# comment", "", "  7 ", "oops", "-3"));
        assertEquals(Arrays.asList(7L, -3L), store.readImport("mine"));
    }

    @Test
    public void failuresRecordAndClear() throws Exception {
        SeedStore store = new SeedStore(folder.getRoot().toPath());
        store.recordFailure(9L);
        store.recordFailure(9L);
        assertEquals(Arrays.asList(9L), store.getFailed());
        store.clearFailures();
        assertTrue(store.getFailed().isEmpty());
    }
}
