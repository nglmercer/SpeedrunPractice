package com.gregor0410.speedrunpractice.common.scenario;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ScenarioLoaderTest {
    @Rule
    public final TemporaryFolder folder = new TemporaryFolder();

    private static final String VALID = "{"
            + "\"id\": \"bastion_housing_default\","
            + "\"type\": \"bastion\","
            + "\"displayName\": \"Housing Bastion Practice\","
            + "\"world\": {\"dimension\": \"nether\"},"
            + "\"seed\": {\"source\": \"search\", \"filters\": [{\"type\": \"bastion_type\", \"value\": \"housing\"}]},"
            + "\"spawn\": {\"type\": \"structure_exterior\", \"structure\": \"bastion_remnant\", \"distance\": 40},"
            + "\"loadout\": \"bastion_default\","
            + "\"timer\": {\"start\": \"player_move\", \"stop\": \"scenario_complete\"}"
            + "}";

    @Test
    public void parsesPlanExample() throws Exception {
        ScenarioDefinition definition = ScenarioLoader.loadFromJson(VALID, "test.json");
        assertEquals("bastion_housing_default", definition.id().value());
        assertEquals(PracticeType.BASTION, definition.type());
        assertEquals(PracticeDimension.NETHER, definition.dimension());
        assertEquals("search", definition.seedSource());
        assertEquals(1, definition.seedFilters().size());
        assertEquals("bastion_type", definition.seedFilters().get(0).type());
        assertEquals("structure_exterior", definition.spawnType());
        assertEquals("bastion_remnant", definition.spawnStructure());
        assertEquals(40, definition.spawnDistance());
        assertEquals("bastion_default", definition.loadout());
        assertEquals("player_move", definition.timerStart());
        assertEquals("housing", definition.toSettings().asMap().get("seed.filters").split("=")[1]);
    }

    @Test
    public void invalidJsonNamesTheFile() {
        try {
            ScenarioLoader.loadFromJson("{oops", "broken.json");
            fail("expected ScenarioLoadException");
        } catch (PracticeException.ScenarioLoadException expected) {
            assertTrue(expected.getUserMessage().contains("broken.json"));
        }
    }

    @Test
    public void badIdIsRejectedReadably() {
        try {
            ScenarioLoader.loadFromJson(VALID.replace("bastion_housing_default", "Bad Id!"), "bad.json");
            fail("expected ScenarioLoadException");
        } catch (PracticeException.ScenarioLoadException expected) {
            assertTrue(expected.getUserMessage().contains("bad.json"));
            assertTrue(expected.getUserMessage().contains("id"));
        }
    }

    @Test
    public void exteriorSpawnNeedsAStructure() {
        String json = VALID.replace("\"structure\": \"bastion_remnant\", ", "");
        try {
            ScenarioLoader.loadFromJson(json, "bad.json");
            fail("expected ScenarioLoadException");
        } catch (PracticeException.ScenarioLoadException expected) {
            assertTrue(expected.getUserMessage().contains("spawn.structure"));
        }
    }

    @Test
    public void loadAllSkipsBadFilesAndDuplicates() throws Exception {
        write("good.json", VALID);
        write("bad.json", "{nope");
        write("dup.json", VALID);
        Map<String, ScenarioDefinition> loaded = ScenarioLoader.loadAll(folder.getRoot().toPath());
        assertEquals(1, loaded.size());
        assertTrue(loaded.containsKey("bastion_housing_default"));
    }

    private void write(String name, String text) throws Exception {
        File file = new File(folder.getRoot(), name);
        Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8);
        try {
            writer.write(text);
        } finally {
            writer.close();
        }
    }
}
