package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.common.util.SimpleJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Machine-readable result for a local verification run.
 *
 * <p>The report is intentionally independent of Gradle and Minecraft. A
 * dedicated-server entrypoint can record results in exactly the same format
 * as a fast unit/fixture check, and the Gradle task only needs to inspect the
 * resulting exit status and files.</p>
 */
public final class VerificationReport {
    public enum Status {
        PASS,
        FAIL
    }

    private static final class Result {
        private final String name;
        private final Status status;
        private final String detail;

        private Result(String name, Status status, String detail) {
            this.name = name;
            this.status = status;
            this.detail = detail;
        }
    }

    private final String version;
    private final List<Result> results = new ArrayList<Result>();

    public VerificationReport(String version) {
        if (version == null || version.trim().isEmpty()) {
            throw new IllegalArgumentException("verification version must not be empty");
        }
        this.version = version.trim();
    }

    public String version() {
        return version;
    }

    public int passed() {
        int count = 0;
        for (Result result : results) {
            if (result.status == Status.PASS) {
                count++;
            }
        }
        return count;
    }

    public int failed() {
        return results.size() - passed();
    }

    public boolean hasFailures() {
        return failed() != 0;
    }

    public VerificationReport pass(String name) {
        return pass(name, null);
    }

    public VerificationReport pass(String name, String detail) {
        add(name, Status.PASS, detail);
        return this;
    }

    public VerificationReport fail(String name, String detail) {
        add(name, Status.FAIL, detail);
        return this;
    }

    /** Runs one test and converts unexpected exceptions into a named failure. */
    public VerificationReport run(VerificationTest test) {
        if (test == null) {
            throw new IllegalArgumentException("verification test must not be null");
        }
        try {
            test.run(this);
        } catch (Exception failure) {
            fail(test.name(), message(failure));
        }
        return this;
    }

    /** Writes report.json and summary.txt beneath {@code outputDirectory}. */
    public void write(Path outputDirectory) throws IOException {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("output directory must not be null");
        }
        Files.createDirectories(outputDirectory);
        Files.write(outputDirectory.resolve("report.json"),
                SimpleJson.toJson(asJson(), true).getBytes(StandardCharsets.UTF_8));
        Files.write(outputDirectory.resolve("summary.txt"),
                summary().getBytes(StandardCharsets.UTF_8));
    }

    public Map<String, Object> asJson() {
        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("version", version);
        root.put("passed", passed());
        root.put("failed", failed());
        List<Object> tests = new ArrayList<Object>();
        for (Result result : results) {
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            item.put("name", result.name);
            item.put("status", result.status.name());
            if (result.detail != null && !result.detail.isEmpty()) {
                item.put("detail", result.detail);
            }
            tests.add(item);
        }
        root.put("tests", tests);
        return root;
    }

    public String summary() {
        StringBuilder out = new StringBuilder();
        out.append("Verification ").append(version).append(':')
                .append(" ").append(passed()).append(" passed, ")
                .append(failed()).append(" failed\n");
        for (Result result : results) {
            out.append(result.status.name()).append(" ").append(result.name);
            if (result.detail != null && !result.detail.isEmpty()) {
                out.append(" — ").append(result.detail);
            }
            out.append('\n');
        }
        if (results.isEmpty()) {
            out.append("No verification tests were registered.\n");
        }
        return out.toString();
    }

    private void add(String name, Status status, String detail) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("verification test name must not be empty");
        }
        results.add(new Result(name.trim(), status, detail));
    }

    private static String message(Exception failure) {
        String message = failure.getMessage();
        return message == null || message.isEmpty() ? failure.getClass().getName() : message;
    }
}
