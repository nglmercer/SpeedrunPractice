package com.gregor0410.speedrunpractice.common.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal dependency-free JSON parser/serializer.
 *
 * <p>Keeps {@code common} free of third-party libraries so every Minecraft
 * target can consume it. Supports the JSON subset used by scenarios,
 * loadouts, configs and statistics: objects, arrays, strings (with escapes),
 * integers, decimals, booleans and null.
 */
public final class SimpleJson {
    private SimpleJson() {
    }

    /** Parses JSON text into Maps, Lists, Strings, Longs, Doubles, Booleans or null. */
    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new JsonException("Unexpected trailing content at index " + parser.index);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String text) {
        Object value = parse(text);
        if (!(value instanceof Map)) {
            throw new JsonException("Expected a JSON object but got: " + value);
        }
        return (Map<String, Object>) value;
    }

    public static String toJson(Object value) {
        return toJson(value, false);
    }

    public static String toJson(Object value, boolean pretty) {
        StringBuilder out = new StringBuilder();
        write(value, out, pretty, 0);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out, boolean pretty, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            writeString((String) value, out);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value.toString());
        } else if (value instanceof Map) {
            writeObject((Map<?, ?>) value, out, pretty, depth);
        } else if (value instanceof List) {
            writeArray((List<?>) value, out, pretty, depth);
        } else {
            writeString(value.toString(), out);
        }
    }

    private static void writeObject(Map<?, ?> map, StringBuilder out, boolean pretty, int depth) {
        out.append('{');
        boolean first = true;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            if (pretty) {
                out.append('\n');
                indent(out, depth + 1);
            }
            writeString(String.valueOf(entry.getKey()), out);
            out.append(pretty ? ": " : ":");
            write(entry.getValue(), out, pretty, depth + 1);
            first = false;
        }
        if (pretty && !map.isEmpty()) {
            out.append('\n');
            indent(out, depth);
        }
        out.append('}');
    }

    private static void writeArray(List<?> list, StringBuilder out, boolean pretty, int depth) {
        out.append('[');
        boolean first = true;
        for (Object item : list) {
            if (!first) {
                out.append(',');
            }
            if (pretty) {
                out.append('\n');
                indent(out, depth + 1);
            }
            write(item, out, pretty, depth + 1);
            first = false;
        }
        if (pretty && !list.isEmpty()) {
            out.append('\n');
            indent(out, depth);
        }
        out.append(']');
    }

    private static void indent(StringBuilder out, int depth) {
        for (int i = 0; i < depth; i++) {
            out.append("  ");
        }
    }

    private static void writeString(String value, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                    break;
            }
        }
        out.append('"');
    }

    private static final class Parser {
        private final String text;
        private int index;

        private Parser(String text) {
            String clean = text == null ? "" : text;
            if (!clean.isEmpty() && clean.charAt(0) == 0xFEFF) {
                clean = clean.substring(1);
            }
            this.text = clean;
        }

        private boolean atEnd() {
            return index >= text.length();
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char c = text.charAt(index);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    index++;
                } else {
                    return;
                }
            }
        }

        private Object parseValue() {
            skipWhitespace();
            if (atEnd()) {
                throw new JsonException("Unexpected end of input");
            }
            char c = text.charAt(index);
            switch (c) {
                case '{':
                    return parseObject();
                case '[':
                    return parseArray();
                case '"':
                    return parseString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return parseNumber();
            }
        }

        private Map<String, Object> parseObject() {
            index++; // {
            Map<String, Object> map = new LinkedHashMap<String, Object>();
            skipWhitespace();
            if (!atEnd() && text.charAt(index) == '}') {
                index++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (atEnd() || text.charAt(index) != '"') {
                    throw new JsonException("Expected string key at index " + index);
                }
                String key = parseString();
                skipWhitespace();
                if (atEnd() || text.charAt(index) != ':') {
                    throw new JsonException("Expected ':' after key at index " + index);
                }
                index++;
                map.put(key, parseValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonException("Unterminated object");
                }
                char c = text.charAt(index++);
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw new JsonException("Expected ',' or '}' at index " + (index - 1));
                }
            }
        }

        private List<Object> parseArray() {
            index++; // [
            List<Object> list = new ArrayList<Object>();
            skipWhitespace();
            if (!atEnd() && text.charAt(index) == ']') {
                index++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (atEnd()) {
                    throw new JsonException("Unterminated array");
                }
                char c = text.charAt(index++);
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw new JsonException("Expected ',' or ']' at index " + (index - 1));
                }
            }
        }

        private String parseString() {
            index++; // opening quote
            StringBuilder out = new StringBuilder();
            while (true) {
                if (atEnd()) {
                    throw new JsonException("Unterminated string");
                }
                char c = text.charAt(index++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (atEnd()) {
                        throw new JsonException("Unterminated escape");
                    }
                    char e = text.charAt(index++);
                    switch (e) {
                        case '"':
                            out.append('"');
                            break;
                        case '\\':
                            out.append('\\');
                            break;
                        case '/':
                            out.append('/');
                            break;
                        case 'n':
                            out.append('\n');
                            break;
                        case 'r':
                            out.append('\r');
                            break;
                        case 't':
                            out.append('\t');
                            break;
                        case 'u':
                            if (index + 4 > text.length()) {
                                throw new JsonException("Bad unicode escape at index " + (index - 2));
                            }
                            try {
                                out.append((char) Integer.parseInt(text.substring(index, index + 4), 16));
                            } catch (NumberFormatException bad) {
                                throw new JsonException("Bad unicode escape at index " + (index - 2));
                            }
                            index += 4;
                            break;
                        default:
                            throw new JsonException("Bad escape '\\" + e + "' at index " + (index - 2));
                    }
                } else {
                    out.append(c);
                }
            }
        }

        private Number parseNumber() {
            int start = index;
            if (!atEnd() && text.charAt(index) == '-') {
                index++;
            }
            while (!atEnd() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            boolean decimal = false;
            if (!atEnd() && text.charAt(index) == '.') {
                decimal = true;
                index++;
                while (!atEnd() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            if (!atEnd() && (text.charAt(index) == 'e' || text.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (!atEnd() && (text.charAt(index) == '+' || text.charAt(index) == '-')) {
                    index++;
                }
                while (!atEnd() && Character.isDigit(text.charAt(index))) {
                    index++;
                }
            }
            String raw = text.substring(start, index);
            try {
                if (decimal) {
                    return Double.valueOf(raw);
                }
                return Long.valueOf(raw);
            } catch (NumberFormatException bad) {
                throw new JsonException("Bad number '" + raw + "' at index " + start);
            }
        }

        private void expect(String literal) {
            if (!text.startsWith(literal, index)) {
                throw new JsonException("Expected '" + literal + "' at index " + index);
            }
            index += literal.length();
        }
    }

    /** Thrown for malformed JSON; callers convert it to readable user errors. */
    public static final class JsonException extends RuntimeException {
        public JsonException(String message) {
            super(message);
        }
    }
}
