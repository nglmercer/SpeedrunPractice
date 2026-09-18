package com.gregor0410.speedrunpractice.practices;

import com.gregor0410.speedrunpractice.common.api.PracticeException;
import com.gregor0410.speedrunpractice.common.api.PracticeScenario;
import com.gregor0410.speedrunpractice.common.api.PracticeType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Creates scenarios by type; versions and tests can register extras. */
public final class ScenarioRegistry {
    /** Factory because scenarios may need construction-time validation. */
    public interface Factory {
        PracticeScenario create() throws PracticeException;
    }

    private static final Map<PracticeType, Factory> REGISTRY = new LinkedHashMap<PracticeType, Factory>();

    static {
        registerDefaults();
    }

    private ScenarioRegistry() {
    }

    public static void registerDefaults() {
        REGISTRY.clear();
        register(PracticeType.OVERWORLD, new Factory() {
            @Override
            public PracticeScenario create() {
                return new OverworldScenario();
            }
        });
        register(PracticeType.BURIED_TREASURE, new Factory() {
            @Override
            public PracticeScenario create() {
                return new BuriedTreasureScenario();
            }
        });
        register(PracticeType.NETHER, new Factory() {
            @Override
            public PracticeScenario create() {
                return new NetherScenario();
            }
        });
        register(PracticeType.BASTION, new Factory() {
            @Override
            public PracticeScenario create() {
                return new BastionScenario();
            }
        });
        register(PracticeType.FORTRESS, new Factory() {
            @Override
            public PracticeScenario create() {
                return new FortressScenario();
            }
        });
        register(PracticeType.BLIND_TRAVEL, new Factory() {
            @Override
            public PracticeScenario create() {
                return new BlindTravelScenario();
            }
        });
        register(PracticeType.POSTBLIND, new Factory() {
            @Override
            public PracticeScenario create() {
                return new PostBlindScenario();
            }
        });
        register(PracticeType.STRONGHOLD, new Factory() {
            @Override
            public PracticeScenario create() {
                return new StrongholdScenario();
            }
        });
        register(PracticeType.END, new Factory() {
            @Override
            public PracticeScenario create() {
                return new EndScenario();
            }
        });
        register(PracticeType.ONE_CYCLE, new Factory() {
            @Override
            public PracticeScenario create() {
                return new OneCycleScenario();
            }
        });
        register(PracticeType.CUSTOM, new Factory() {
            @Override
            public PracticeScenario create() throws PracticeException {
                throw new PracticeException("Custom scenarios need a definition file",
                        "Custom practice needs a scenario file. Pick one from the Custom menu.");
            }
        });
    }

    public static void register(PracticeType type, Factory factory) {
        if (type == null || factory == null) {
            throw new IllegalArgumentException("type and factory must not be null");
        }
        REGISTRY.put(type, factory);
    }

    public static PracticeScenario create(PracticeType type) throws PracticeException {
        Factory factory = REGISTRY.get(type);
        if (factory == null) {
            throw new PracticeException("No scenario for type " + type,
                    "This practice is not available.");
        }
        return factory.create();
    }

    public static List<PracticeType> types() {
        return Collections.unmodifiableList(new ArrayList<PracticeType>(REGISTRY.keySet()));
    }
}
