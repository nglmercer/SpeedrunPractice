package com.gregor0410.speedrunpractice.common.scenario;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/** Custom completion block + capability requirement (plan sections 33, 68). */
public class ScenarioDefinitionCompletionTest {
    private static final String BASE = "{\"id\": \"custom_hunt\", \"type\": \"custom\",";

    @Test
    public void defaultsToManualWithoutCompletion() throws Exception {
        ScenarioDefinition definition = ScenarioLoader.loadFromJson(
                BASE + "\"seed\": {\"source\": \"random\"}}", "test");
        assertEquals("manual", definition.completionType());
        assertNull(definition.completionDimension());
        assertNull(definition.completionStructure());
        assertNull(definition.requiresCapability());
    }

    @Test
    public void parsesStructureCompletion() throws Exception {
        ScenarioDefinition definition = ScenarioLoader.loadFromJson(BASE
                + "\"completion\": {\"type\": \"structure_reached\","
                + " \"structure\": \"stronghold\", \"radius\": 12}}", "test");
        assertEquals("structure_reached", definition.completionType());
        assertEquals("stronghold", definition.completionStructure());
        assertEquals(12, definition.completionRadius());
        assertEquals("structure_reached", definition.toSettings().get("completion.type"));
        assertEquals("stronghold", definition.toSettings().get("completion.structure"));
    }

    @Test
    public void parsesDimensionCompletion() throws Exception {
        ScenarioDefinition definition = ScenarioLoader.loadFromJson(BASE
                + "\"completion\": {\"type\": \"dimension_entry\", \"dimension\": \"nether\"}}", "test");
        assertEquals("dimension_entry", definition.completionType());
        assertEquals("nether", definition.completionDimension());
    }

    @Test
    public void parsesRequiresCapability() throws Exception {
        ScenarioDefinition definition = ScenarioLoader.loadFromJson(BASE
                + "\"requires\": \"dragon_force_perch\"}", "test");
        assertEquals("DRAGON_FORCE_PERCH", definition.requiresCapability());
        assertEquals("DRAGON_FORCE_PERCH", definition.toSettings().get("requires"));
    }

    @Test
    public void rejectsUnknownCompletionType() {
        try {
            ScenarioLoader.loadFromJson(BASE + "\"completion\": {\"type\": \"speedrun\"}}", "test");
            fail("expected ScenarioLoadException");
        } catch (PracticeException.ScenarioLoadException expected) {
            assertEquals(true, expected.getUserMessage().contains("completion.type"));
        }
    }

    @Test
    public void rejectsStructureCompletionWithoutTarget() {
        try {
            ScenarioLoader.loadFromJson(BASE + "\"completion\": {\"type\": \"structure_reached\"}}", "test");
            fail("expected ScenarioLoadException");
        } catch (PracticeException.ScenarioLoadException expected) {
            assertEquals(true, expected.getUserMessage().contains("completion.structure"));
        }
    }

    @Test
    public void rejectsDimensionCompletionWithoutTarget() {
        try {
            ScenarioLoader.loadFromJson(BASE + "\"completion\": {\"type\": \"dimension_entry\"}}", "test");
            fail("expected ScenarioLoadException");
        } catch (PracticeException.ScenarioLoadException expected) {
            assertEquals(true, expected.getUserMessage().contains("completion.dimension"));
        }
    }

    @Test
    public void rejectsBadDimension() {
        try {
            ScenarioLoader.loadFromJson(BASE
                    + "\"completion\": {\"type\": \"dimension_entry\", \"dimension\": \"moon\"}}", "test");
            fail("expected ScenarioLoadException");
        } catch (PracticeException.ScenarioLoadException expected) {
            assertEquals(true, expected.getUserMessage().contains("completion.dimension"));
        }
    }

    @Test
    public void rejectsNegativeRadius() {
        try {
            ScenarioLoader.loadFromJson(BASE
                    + "\"completion\": {\"type\": \"structure_reached\","
                    + " \"structure\": \"fortress\", \"radius\": -1}}", "test");
            fail("expected ScenarioLoadException");
        } catch (PracticeException.ScenarioLoadException expected) {
            assertEquals(true, expected.getUserMessage().contains("completion.radius"));
        }
    }

    @Test
    public void rejectsUnknownCapability() {
        try {
            ScenarioLoader.loadFromJson(BASE + "\"requires\": \"fly_mode\"}", "test");
            fail("expected ScenarioLoadException");
        } catch (PracticeException.ScenarioLoadException expected) {
            assertEquals(true, expected.getUserMessage().contains("requires"));
        }
    }
}
