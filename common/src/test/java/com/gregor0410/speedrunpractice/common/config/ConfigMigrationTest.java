package com.gregor0410.speedrunpractice.common.config;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ConfigMigrationTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    @Test
    public void migratesV1ToV2() throws Exception {
        Map<String, Object> legacy = new HashMap<String, Object>();
        legacy.put("defaultMaxDist", 1000L);
        Map<String, Object> migrated = ConfigMigrator.migrate(legacy, "test.json");
        assertEquals(2, migrated.get("schemaVersion"));
        assertTrue(migrated.containsKey("keybinds"));
        assertEquals("player_move", migrated.get("timerStart"));
        assertEquals(1000L, migrated.get("defaultMaxDist"));
    }

    @Test
    public void rejectsFutureSchema() {
        Map<String, Object> future = new HashMap<String, Object>();
        future.put("schemaVersion", 99L);
        try {
            ConfigMigrator.migrate(future, "test.json");
            fail("expected PracticeException");
        } catch (PracticeException expected) {
            assertTrue(expected.getUserMessage().contains("newer"));
        }
    }

    @Test
    public void saveLoadRoundTrip() throws Exception {
        SpeedrunPracticeConfig config = new SpeedrunPracticeConfig();
        config.defaultMaxDist = 5;
        config.useSeedList = true;
        config.save(folder.getRoot().toPath());
        SpeedrunPracticeConfig loaded = SpeedrunPracticeConfig.load(folder.getRoot().toPath());
        assertEquals(5, loaded.defaultMaxDist);
        assertTrue(loaded.useSeedList);
        assertEquals(2, loaded.schemaVersion);
    }

    @Test
    public void corruptFileBacksUpAndResets() throws Exception {
        File file = new File(folder.getRoot(), SpeedrunPracticeConfig.FILE_NAME);
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write("{corrupt");
        } finally {
            writer.close();
        }
        SpeedrunPracticeConfig loaded = SpeedrunPracticeConfig.load(folder.getRoot().toPath());
        assertEquals(1000, loaded.defaultMaxDist);
        boolean backupFound = false;
        for (File child : folder.getRoot().listFiles()) {
            if (child.getName().startsWith(SpeedrunPracticeConfig.FILE_NAME + ".bak.")) {
                backupFound = true;
            }
        }
        assertTrue(backupFound);
    }
}
