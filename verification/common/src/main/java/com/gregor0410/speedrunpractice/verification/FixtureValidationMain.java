package com.gregor0410.speedrunpractice.verification;

import java.nio.file.Path;
import java.nio.file.Paths;

/** Small JavaExec entrypoint used by Gradle verification tasks. */
public final class FixtureValidationMain {
    private FixtureValidationMain() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: FixtureValidationMain <version> <fixture> <report-directory>");
        }
        String version = args[0];
        Path fixture = Paths.get(args[1]);
        Path output = Paths.get(args[2]);
        VerificationReport report = new VerificationReport(version);
        try {
            FixtureValidator.validate(fixture, version);
            report.pass("fixture.canonical", fixture.toString());
        } catch (Exception failure) {
            report.fail("fixture.canonical", failure.getMessage());
        }
        report.write(output);
        if (report.hasFailures()) {
            System.err.print(report.summary());
            System.exit(1);
        }
        System.out.print(report.summary());
    }
}
