package com.gregor0410.speedrunpractice.verification;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Process-local bridge used by a verification-only Minecraft mod. The
 * command starts a session, the version-local server probe completes it, and
 * both report files are written from the same shared implementation.
 */
public final class VerificationSession {
    private static VerificationReport active;
    private static Path activeOutput;
    private static String activeSuite;

    private VerificationSession() {
    }

    public static synchronized boolean start(String version, String suite) throws IOException {
        if (active != null) {
            return false;
        }
        active = new VerificationReport(version);
        activeSuite = suite;
        activeOutput = outputDirectory(version);
        active.pass("suite.started", suite);
        active.write(activeOutput);
        return true;
    }

    public static synchronized void complete(String test, int matches, int total, String detail)
            throws IOException {
        if (active == null) {
            return;
        }
        if (total > 0 && matches == total) {
            active.pass(test, detail);
        } else {
            active.fail(test, (detail == null ? "" : detail + "; ")
                    + "matched " + matches + "/" + total);
        }
        active.write(activeOutput);
        active = null;
        activeOutput = null;
        activeSuite = null;
    }

    public static synchronized void fail(String test, String detail) throws IOException {
        if (active == null) {
            return;
        }
        active.fail(test, detail);
        active.write(activeOutput);
        active = null;
        activeOutput = null;
        activeSuite = null;
    }

    private static Path outputDirectory(String version) {
        String configured = System.getProperty("speedrun.practice.verification.output");
        if (configured == null || configured.trim().isEmpty()) {
            configured = System.getenv("SPEEDRUN_PRACTICE_VERIFICATION_OUTPUT");
        }
        if (configured != null && !configured.trim().isEmpty()) {
            return Paths.get(configured).resolve(version);
        }
        return Paths.get("build", "verification", version);
    }
}
