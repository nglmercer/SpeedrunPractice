package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.adapter.StructureAdapter;
import com.gregor0410.speedrunpractice.common.api.PracticeContext;
import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeId;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;
import com.gregor0410.speedrunpractice.common.api.PracticeType;
import com.gregor0410.speedrunpractice.common.scenario.ScenarioDefinition;
import com.gregor0410.speedrunpractice.common.util.SpeedrunLogger;

import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * Runs a validated {@link ScenarioDefinition}. Definition settings merge into
 * the run settings (explicit run settings win); unknown-but-valid spawn
 * requests fall back to world spawn with a warning, never a crash.
 */
public class CustomScenario extends AbstractPracticeScenario {
    private final ScenarioDefinition definition;

    public CustomScenario(ScenarioDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("definition must not be null");
        }
        this.definition = definition;
    }

    public ScenarioDefinition definition() {
        return definition;
    }

    @Override
    public PracticeId id() {
        return definition.id();
    }

    @Override
    public PracticeType type() {
        return definition.type();
    }

    @Override
    public void prepare(PracticeContext context) throws PracticeException {
        for (Map.Entry<String, String> entry : definition.toSettings().asMap().entrySet()) {
            if (context.settings().get(entry.getKey()) == null) {
                context.settings().set(entry.getKey(), entry.getValue());
            }
        }
        createWorld(context, definition.dimension());
    }

    @Override
    public void start(PracticeContext context) throws PracticeException {
        PracticePosition spawn = spawnOf(context);
        PracticePosition target = spawn;
        String spawnType = definition.spawnType();
        if ("structure".equals(spawnType) || "structure_exterior".equals(spawnType)) {
            int radius = context.settings().getInt("spawn.radius", 10000);
            Optional<StructureAdapter.StructureLocation> found =
                    locate(context, definition.spawnStructure(), spawn, radius);
            if (found.isPresent()) {
                target = found.get().position();
                if ("structure_exterior".equals(spawnType)) {
                    target = target.offset(definition.spawnDistance(), 0.0, 0.0);
                }
            } else {
                SpeedrunLogger.warn("Structure \"" + definition.spawnStructure()
                        + "\" not found in range; using world spawn");
            }
        } else if ("random_nether".equals(spawnType)) {
            Random random = new Random(context.seed());
            double angle = random.nextDouble() * Math.PI * 2.0;
            target = new PracticePosition(spawn.x() + Math.cos(angle) * definition.spawnDistance(), spawn.y(),
                    spawn.z() + Math.sin(angle) * definition.spawnDistance(), 90.0f, 0.0f);
        } else if ("end_platform".equals(spawnType)) {
            target = new PracticePosition(100.0, 49.0, 0.0, 90.0f, 0.0f);
        } else if ("custom".equals(spawnType)) {
            target = new PracticePosition(definition.spawnX(), definition.spawnY(), definition.spawnZ(), 90.0f, 0.0f);
        }
        teleportStart(context, target);
        applyLoadoutSetting(context);
    }

    @Override
    public TickResult tick(PracticeContext context) {
        return TickResult.continueTick();
    }

    @Override
    public void stop(PracticeContext context) {
        deleteWorldQuietly(context);
    }
}
