package com.gregor0410.speedrunpractice.adapter116;

import com.gregor0410.speedrunpractice.common.util.SimpleJson;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Dedicated-server member guard for 1.16.1.
 *
 * <p>The merged yarn jar contains client-only members that Fabric strips on
 * dedicated servers ({@code EnvironmentStrippingData}); calling one crashes
 * with {@code NoSuchMethodError} even though the code compiles. Each entry
 * below was proven stripped by a real headless-server run:
 *
 * <ul>
 * <li>{@code SaveProperties.getLevelInfo} / {@code LevelProperties.getLevelInfo}
 * (the server's {@code SaveProperties} has neither; read the
 * {@code levelInfo} field via {@code LevelPropertiesAccess} instead)</li>
 * <li>{@code BiomeSource.withSeed} (build the seeded source directly,
 * e.g. via {@code DimensionTypeAccess.invokeCreateNetherGenerator})</li>
 * </ul>
 *
 * <p>Client-only classes (the {@code client} mixin section plus the
 * {@code adapter116.client} package) are exempt: they never load on a
 * dedicated server.
 */
public class ServerSafeMemberRefs116Test {
    private static final String[][] FORBIDDEN = {
            {"net/minecraft/world/SaveProperties", "getLevelInfo"},
            {"net/minecraft/world/level/LevelProperties", "getLevelInfo"},
            {"net/minecraft/world/SaveProperties", "method_29588"},
            {"net/minecraft/world/biome/source/BiomeSource", "withSeed"},
    };

    @Test
    public void serverLoadedClassesAvoidStrippedMembers() throws Exception {
        Path mainClasses = mainClassesDir();
        Set<String> exempt = exemptClasses();
        List<String> violations = new ArrayList<String>();
        Files.walk(mainClasses).forEach(path -> {
            if (!path.toString().endsWith(".class")) {
                return;
            }
            String binary = mainClasses.relativize(path).toString()
                    .replace('\\', '.').replace('/', '.');
            String className = binary.substring(0, binary.length() - ".class".length());
            if (exempt.contains(className) || className.startsWith("com.gregor0410.speedrunpractice.adapter116.client.")) {
                return;
            }
            try {
                for (String[] ref : memberRefs(Files.readAllBytes(path))) {
                    for (String[] forbidden : FORBIDDEN) {
                        if (ref[0].equals(forbidden[0]) && ref[1].equals(forbidden[1])) {
                            violations.add(className + " references stripped "
                                    + ref[0].replace('/', '.') + "." + ref[1]);
                        }
                    }
                }
            } catch (Exception bad) {
                fail("cannot scan " + className + ": " + bad.getMessage());
            }
        });
        assertTrue("client-only member refs unreachable on dedicated servers:\n"
                + join(violations), violations.isEmpty());
    }

    private static Path mainClassesDir() throws Exception {
        URL testClasses = ServerSafeMemberRefs116Test.class.getProtectionDomain()
                .getCodeSource().getLocation();
        Path main = Paths.get(testClasses.toURI()).resolveSibling("main");
        assertTrue("main classes dir missing: " + main, Files.isDirectory(main));
        return main;
    }

    /** Classes in the {@code client} mixin section, resolved from mixins.json. */
    @SuppressWarnings("unchecked")
    private static Set<String> exemptClasses() throws Exception {
        Set<String> exempt = new HashSet<String>();
        InputStream in = ServerSafeMemberRefs116Test.class.getClassLoader()
                .getResourceAsStream("SpeedrunPractice.mixins.json");
        if (in == null) {
            return exempt;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bytes.write(buf, 0, n);
        }
        in.close();
        Map<String, Object> json = (Map<String, Object>) SimpleJson.parse(
                new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        String pkg = String.valueOf(json.get("package"));
        Object client = json.get("client");
        if (client instanceof List) {
            for (Object mixin : (List<Object>) client) {
                exempt.add(pkg + "." + mixin);
            }
        }
        return exempt;
    }

    /** All (owner, name) pairs of method refs in a class file's constant pool. */
    private static List<String[]> memberRefs(byte[] bytes) {
        List<String[]> refs = new ArrayList<String[]>();
        int[] pos = {8}; // skip magic + minor + major
        int count = u2(bytes, pos);
        String[] utf8 = new String[count];
        int[] clsName = new int[count];
        int[][] nameType = new int[count][];
        List<int[]> methods = new ArrayList<int[]>();
        for (int i = 1; i < count; i++) {
            int tag = bytes[pos[0]++] & 0xFF;
            switch (tag) {
                case 1: {
                    int len = u2(bytes, pos);
                    utf8[i] = new String(bytes, pos[0], len, StandardCharsets.UTF_8);
                    pos[0] += len;
                    break;
                }
                case 7:
                    clsName[i] = u2(bytes, pos);
                    break;
                case 12: {
                    int name = u2(bytes, pos);
                    int type = u2(bytes, pos);
                    nameType[i] = new int[]{name, type};
                    break;
                }
                case 10:
                case 11: {
                    int owner = u2(bytes, pos);
                    int nt = u2(bytes, pos);
                    methods.add(new int[]{owner, nt});
                    break;
                }
                case 8:
                case 16:
                case 19:
                case 20:
                    pos[0] += 2;
                    break;
                case 15:
                    pos[0] += 3;
                    break;
                case 9:
                case 14:
                case 17:
                case 18:
                    pos[0] += 4;
                    break;
                case 3:
                case 4:
                    pos[0] += 4;
                    break;
                case 5:
                case 6:
                    pos[0] += 8;
                    i++;
                    break;
                default:
                    throw new IllegalArgumentException("bad constant tag " + tag);
            }
        }
        for (int[] m : methods) {
            String owner = utf8[clsName[m[0]]];
            String name = utf8[nameType[m[1]][0]];
            if (owner != null && name != null) {
                refs.add(new String[]{owner, name});
            }
        }
        return refs;
    }

    private static int u2(byte[] bytes, int[] pos) {
        int value = ((bytes[pos[0]] & 0xFF) << 8) | (bytes[pos[0] + 1] & 0xFF);
        pos[0] += 2;
        return value;
    }

    private static String join(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            out.append("  ").append(line).append('\n');
        }
        return out.toString();
    }
}
