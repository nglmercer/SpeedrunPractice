package com.gregor0410.speedrunpractice.verification;

/** One named verification operation. It is deliberately Minecraft-agnostic. */
public interface VerificationTest {
    String name();

    void run(VerificationReport report) throws Exception;
}
