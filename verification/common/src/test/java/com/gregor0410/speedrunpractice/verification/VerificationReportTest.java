package com.gregor0410.speedrunpractice.verification;

import com.gregor0410.speedrunpractice.common.util.SimpleJson;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class VerificationReportTest {
    @Test
    public void writesMachineReadableReportAndSummary() throws Exception {
        VerificationReport report = new VerificationReport("1.21.1")
                .pass("world.create.seed")
                .fail("world.delete", "directory still exists");
        Path output = Files.createTempDirectory("speedrun-verification-");

        report.write(output);

        Map<String, Object> json = SimpleJson.parseObject(new String(
                Files.readAllBytes(output.resolve("report.json")), StandardCharsets.UTF_8));
        assertEquals(1L, json.get("passed"));
        assertEquals(1L, json.get("failed"));
        assertTrue(new String(Files.readAllBytes(output.resolve("summary.txt")),
                StandardCharsets.UTF_8).contains("world.delete"));
    }
}
