package com.gregor0410.speedrunpractice.common.api;

/**
 * Domain exceptions with user-facing messages (plan section 40). Expected
 * failures (bad scenario JSON, missing structure, unsupported capability)
 * surface as these; raw runtime exceptions must never reach the player.
 */
public class PracticeException extends Exception {
    private final String userMessage;

    public PracticeException(String message) {
        this(message, message);
    }

    public PracticeException(String message, String userMessage) {
        super(message);
        this.userMessage = userMessage == null ? message : userMessage;
    }

    public PracticeException(String message, Throwable cause) {
        this(message, message, cause);
    }

    public PracticeException(String message, String userMessage, Throwable cause) {
        super(message, cause);
        this.userMessage = userMessage == null ? message : userMessage;
    }

    /** Safe to show in chat/GUI; never a stack-trace-ism like NullPointerException. */
    public String getUserMessage() {
        return userMessage;
    }

    /** A scenario definition failed to load or validate. */
    public static class ScenarioLoadException extends PracticeException {
        public ScenarioLoadException(String message) {
            super(message);
        }

        public ScenarioLoadException(String message, String userMessage) {
            super(message, userMessage);
        }
    }

    /** Seed search or seed resolution failed. */
    public static class SeedSearchException extends PracticeException {
        public SeedSearchException(String message) {
            super(message);
        }

        public SeedSearchException(String message, String userMessage) {
            super(message, userMessage);
        }
    }

    /** A version adapter cannot perform the requested operation here. */
    public static class AdapterException extends PracticeException {
        public AdapterException(String message) {
            super(message);
        }

        public AdapterException(String message, String userMessage) {
            super(message, userMessage);
        }
    }

    /** Checkpoint save/restore failed. */
    public static class CheckpointException extends PracticeException {
        public CheckpointException(String message) {
            super(message);
        }

        public CheckpointException(String message, String userMessage) {
            super(message, userMessage);
        }
    }
}
