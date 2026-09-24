package com.gregor0410.speedrunpractice.adapter121.live;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Guards the 1.21.1 completion announcement's parity with 1.16.1/26.3.
 *
 * <p>{@code Runtime121.announceCompletion} needs a live server player, so it
 * cannot run headless. Instead this test scans the compiled
 * {@code Runtime121.class} constant pool (without loading or linking it, so
 * no Minecraft classes are needed) and asserts the announcement both opens
 * the results screen and uses the shared green chat prefix.
 */
public class Runtime121CompletionTest {
    private static final String RUNTIME_CLASS =
            "com/gregor0410/speedrunpractice/adapter121/live/Runtime121.class";
    private static final String GUI_OWNER =
            "com/gregor0410/speedrunpractice/common/adapter/GuiAdapter";

    @Test
    public void announceCompletionOpensResultsScreen() throws Exception {
        boolean found = false;
        for (String[] ref : memberRefs(classBytes())) {
            if (ref[0].equals(GUI_OWNER) && ref[1].equals("openResultsScreen")) {
                found = true;
                break;
            }
        }
        assertTrue("Runtime121 must call GuiAdapter.openResultsScreen on completion"
                + " (parity with Runtime116/Runtime263)", found);
    }

    @Test
    public void announceCompletionUsesSharedChatPrefix() throws Exception {
        boolean found = false;
        for (String text : utf8Constants(classBytes())) {
            if (text.contains("§aCompleted ")) {
                found = true;
                break;
            }
        }
        assertTrue("Runtime121 must announce \"§aCompleted ...\" like Runtime116/Runtime263", found);
    }

    private static byte[] classBytes() throws Exception {
        InputStream in = Runtime121CompletionTest.class.getClassLoader()
                .getResourceAsStream(RUNTIME_CLASS);
        if (in == null) {
            fail("main classes missing from test classpath: " + RUNTIME_CLASS);
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bytes.write(buf, 0, n);
        }
        in.close();
        return bytes.toByteArray();
    }

    /** All (owner, name) pairs of method refs in a class file's constant pool. */
    private static List<String[]> memberRefs(byte[] bytes) {
        ConstantPool pool = ConstantPool.parse(bytes);
        List<String[]> refs = new ArrayList<String[]>();
        for (int[] m : pool.methods) {
            String owner = pool.utf8[pool.classes[m[0]]];
            String name = pool.utf8[pool.nameTypes[m[1]][0]];
            if (owner != null && name != null) {
                refs.add(new String[]{owner, name});
            }
        }
        return refs;
    }

    /** All UTF-8 string constants in a class file's constant pool. */
    private static List<String> utf8Constants(byte[] bytes) {
        ConstantPool pool = ConstantPool.parse(bytes);
        List<String> out = new ArrayList<String>();
        for (String text : pool.utf8) {
            if (text != null) {
                out.add(text);
            }
        }
        return out;
    }

    /** Minimal constant-pool reader (method refs and UTF-8 entries only). */
    private static final class ConstantPool {
        private final String[] utf8;
        private final int[] classes;
        private final int[][] nameTypes;
        private final List<int[]> methods;

        private ConstantPool(String[] utf8, int[] classes, int[][] nameTypes, List<int[]> methods) {
            this.utf8 = utf8;
            this.classes = classes;
            this.nameTypes = nameTypes;
            this.methods = methods;
        }

        private static ConstantPool parse(byte[] bytes) {
            int[] pos = {8}; // skip magic + minor + major
            int count = u2(bytes, pos);
            String[] utf8 = new String[count];
            int[] classes = new int[count];
            int[][] nameTypes = new int[count][];
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
                        classes[i] = u2(bytes, pos);
                        break;
                    case 12: {
                        int name = u2(bytes, pos);
                        int type = u2(bytes, pos);
                        nameTypes[i] = new int[]{name, type};
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
            return new ConstantPool(utf8, classes, nameTypes, methods);
        }

        private static int u2(byte[] bytes, int[] pos) {
            int value = ((bytes[pos[0]] & 0xFF) << 8) | (bytes[pos[0] + 1] & 0xFF);
            pos[0] += 2;
            return value;
        }
    }
}
