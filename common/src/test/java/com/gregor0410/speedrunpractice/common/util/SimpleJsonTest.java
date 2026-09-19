package com.gregor0410.speedrunpractice.common.util;

import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SimpleJsonTest {
    @Test
    public void parsesPrimitives() {
        Map<String, Object> map = SimpleJson.parseObject(
                "{\"s\":\"hi\",\"n\":42,\"neg\":-7,\"d\":1.5,\"t\":true,\"f\":false,\"z\":null}");
        assertEquals("hi", map.get("s"));
        assertEquals(42L, map.get("n"));
        assertEquals(-7L, map.get("neg"));
        assertEquals(1.5, (Double) map.get("d"), 0.0);
        assertEquals(Boolean.TRUE, map.get("t"));
        assertEquals(Boolean.FALSE, map.get("f"));
        assertTrue(map.containsKey("z"));
        assertNull(map.get("z"));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void parsesNestedArraysObjectsAndEscapes() {
        Map<String, Object> map = SimpleJson.parseObject("{\"a\":[1,\"x\\nA\\\"q\\\"\",{}],\"o\":{\"k\":\"v\"}}");
        List<Object> list = (List<Object>) map.get("a");
        assertEquals(3, list.size());
        assertEquals(1L, list.get(0));
        assertEquals("x\nA\"q\"", list.get(1));
        assertEquals("v", ((Map<String, Object>) map.get("o")).get("k"));
    }

    @Test
    public void roundTrips() {
        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("id", "bastion_default");
        List<Object> items = new ArrayList<Object>();
        Map<String, Object> row = new LinkedHashMap<String, Object>();
        row.put("item", "minecraft:stone");
        row.put("count", 64L);
        items.add(row);
        map.put("items", items);
        Map<String, Object> back = SimpleJson.parseObject(SimpleJson.toJson(map, true));
        assertEquals(map, back);
    }

    @Test(expected = SimpleJson.JsonException.class)
    public void rejectsTrailingContent() {
        SimpleJson.parse("{} trailing");
    }

    @Test
    public void skipsLeadingByteOrderMark() {
        String bom = Character.toString((char) 0xFEFF);
        Map<String, Object> map = SimpleJson.parseObject(bom + "{\"n\":42}");
        assertEquals(42L, map.get("n"));
    }

    @Test(expected = SimpleJson.JsonException.class)
    public void rejectsUnterminatedInput() {
        SimpleJson.parse("{\"a\":");
    }

    @Test(expected = SimpleJson.JsonException.class)
    public void parseObjectRejectsArrays() {
        SimpleJson.parseObject("[1,2]");
    }
}
