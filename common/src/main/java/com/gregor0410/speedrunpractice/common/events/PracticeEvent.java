package com.gregor0410.speedrunpractice.common.events;

import com.gregor0410.speedrunpractice.common.api.PracticeDimension;
import com.gregor0410.speedrunpractice.common.api.PracticePosition;

/**
 * Shared practice events (plan section 21). Version adapters translate actual
 * Minecraft events into these; the engine dispatches them to the active
 * scenario and uses timer-relevant ones to start/stop the timer.
 */
public interface PracticeEvent {

    /** The player moved beyond the version's movement threshold. */
    final class PlayerMovedEvent implements PracticeEvent {
        private final PracticePosition from;
        private final PracticePosition to;

        public PlayerMovedEvent(PracticePosition from, PracticePosition to) {
            if (to == null) {
                throw new IllegalArgumentException("destination must not be null");
            }
            this.from = from;
            this.to = to;
        }

        /** Null when the previous position is unknown. */
        public PracticePosition from() {
            return from;
        }

        public PracticePosition to() {
            return to;
        }
    }

    /** The player changed dimension (including practice-world switches). */
    final class DimensionChangedEvent implements PracticeEvent {
        private final PracticeDimension from;
        private final PracticeDimension to;

        public DimensionChangedEvent(PracticeDimension from, PracticeDimension to) {
            if (to == null) {
                throw new IllegalArgumentException("destination dimension must not be null");
            }
            this.from = from;
            this.to = to;
        }

        /** Null when the previous dimension is unknown. */
        public PracticeDimension from() {
            return from;
        }

        public PracticeDimension to() {
            return to;
        }
    }

    /** The player exited a Nether portal into the given dimension. */
    final class PortalExitEvent implements PracticeEvent {
        private final PracticeDimension entered;
        private final PracticePosition position;

        public PortalExitEvent(PracticeDimension entered, PracticePosition position) {
            if (entered == null || position == null) {
                throw new IllegalArgumentException("dimension and position must not be null");
            }
            this.entered = entered;
            this.position = position;
        }

        public PracticeDimension entered() {
            return entered;
        }

        public PracticePosition position() {
            return position;
        }
    }

    /** The player entered a structure's bounding area. */
    final class StructureEnteredEvent implements PracticeEvent {
        private final String structureId;
        private final PracticePosition position;

        public StructureEnteredEvent(String structureId, PracticePosition position) {
            if (structureId == null || structureId.trim().isEmpty() || position == null) {
                throw new IllegalArgumentException("structure id and position must be set");
            }
            this.structureId = structureId.trim();
            this.position = position;
        }

        public String structureId() {
            return structureId;
        }

        public PracticePosition position() {
            return position;
        }
    }

    /** The practice world's dragon died. */
    final class DragonKilledEvent implements PracticeEvent {
        private final String worldHandle;

        public DragonKilledEvent(String worldHandle) {
            if (worldHandle == null || worldHandle.trim().isEmpty()) {
                throw new IllegalArgumentException("world handle must not be empty");
            }
            this.worldHandle = worldHandle;
        }

        public String worldHandle() {
            return worldHandle;
        }
    }

    /** The player's inventory changed (loot, pickup, trade, craft). */
    final class InventoryChangedEvent implements PracticeEvent {
        public InventoryChangedEvent() {
        }
    }

    /** The player placed a block (portal frames, beds, obsidian setups). */
    final class BlockPlacedEvent implements PracticeEvent {
        private final PracticePosition position;
        private final String blockId;

        public BlockPlacedEvent(PracticePosition position, String blockId) {
            if (position == null || blockId == null || blockId.trim().isEmpty()) {
                throw new IllegalArgumentException("position and block id must be set");
            }
            this.position = position;
            this.blockId = blockId.trim();
        }

        public PracticePosition position() {
            return position;
        }

        public String blockId() {
            return blockId;
        }
    }

    /** The player killed an entity. */
    final class EntityKilledEvent implements PracticeEvent {
        private final String entityId;

        public EntityKilledEvent(String entityId) {
            if (entityId == null || entityId.trim().isEmpty()) {
                throw new IllegalArgumentException("entity id must not be empty");
            }
            this.entityId = entityId.trim();
        }

        public String entityId() {
            return entityId;
        }
    }

    /** The player pressed the manual timer-start binding/command. */
    final class ManualTimerStartEvent implements PracticeEvent {
        public ManualTimerStartEvent() {
        }
    }

    /** The player pressed the manual timer-stop binding/command. */
    final class ManualTimerStopEvent implements PracticeEvent {
        public ManualTimerStopEvent() {
        }
    }
}
