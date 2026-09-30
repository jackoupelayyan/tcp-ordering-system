package com.jackoupelayyan.ordering;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A minimal, zero-dependency JSON parser and writer.
 *
 * <p>Supports the subset of JSON used by the ordering protocol: objects, arrays,
 * strings (with escapes), numbers, booleans and null. Parsed values are returned
 * as plain Java types:
 *
 * <ul>
 *   <li>JSON object -&gt; {@code Map<String, Object>} (insertion ordered)</li>
 *   <li>JSON array  -&gt; {@code List<Object>}</li>
 *   <li>JSON string -&gt; {@code String}</li>
 *   <li>JSON number -&gt; {@code Long} when it has no fraction/exponent, otherwise {@code Double}</li>
 *   <li>{@code true}/{@code false} -&gt; {@code Boolean}, {@code null} -&gt; {@code null}</li>
 * </ul>
 *
 * <p>Not a general-purpose JSON library — it exists so this project compiles and
 * runs with plain {@code javac}, no external dependencies.
 */
public final class Json {

    private Json() {
        // utility class
    }

    /** Parses a JSON text into plain Java objects. */
    public static Object parse(String text) {
        if (text == null) {
            throw new JsonParseException("cannot parse null");
        }
        Parser parser = new Parser(text);
        Object value = parser.parseValue();
        parser.skipWhitespace();
        if (parser.hasMore()) {
            throw new JsonParseException("trailing characters after JSON value at index " + parser.pos);
        }
        return value;
    }

    /** Serializes plain Java objects (Map, List, String, Number, Boolean, null) to JSON text. */
    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    /** Convenience: builds an insertion-ordered JSON object from alternating key/value pairs. */
    public static Map<String, Object> obj(Object... keysAndValues) {
        if (keysAndValues.length % 2 != 0) {
            throw new IllegalArgumentException("obj() needs an even number of arguments");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keysAndValues.length; i += 2) {
            map.put(String.valueOf(keysAndValues[i]), keysAndValues[i + 1]);
        }
        return map;
    }

    /** Convenience: builds a JSON array. */
    public static List<Object> arr(Object... values) {
        List<Object> list = new ArrayList<>(values.length);
        for (Object v : values) {
            list.add(v);
        }
        return list;
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(e.getKey()));
                sb.append(':');
                writeValue(sb, e.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, item);
            }
            sb.append(']');
        } else if (value instanceof Double d) {
            // Avoid "29.990000000000002" style output for integral doubles.
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                sb.append(Long.toString(d.longValue()));
            } else {
                sb.append(Double.toString(d));
            }
        } else if (value instanceof Float f) {
            sb.append(Double.toString(f.doubleValue()));
        } else if (value instanceof Number n) {
            sb.append(n.toString());
        } else if (value instanceof Boolean b) {
            sb.append(b.toString());
        } else {
            // Fallback: treat anything else as a string.
            writeString(sb, value.toString());
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** Thrown when input text is not valid JSON. */
    public static final class JsonParseException extends RuntimeException {
        public JsonParseException(String message) {
            super(message);
        }
    }

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean hasMore() {
            return pos < text.length();
        }

        void skipWhitespace() {
            while (hasMore()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (!hasMore()) {
                throw new JsonParseException("unexpected end of input");
            }
            char c = text.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't' -> {
                    expectLiteral("true");
                    yield Boolean.TRUE;
                }
                case 'f' -> {
                    expectLiteral("false");
                    yield Boolean.FALSE;
                }
                case 'n' -> {
                    expectLiteral("null");
                    yield null;
                }
                default -> parseNumber();
            };
        }

        Map<String, Object> parseObject() {
            pos++; // consume '{'
            Map<String, Object> map = new LinkedHashMap<>();
            skipWhitespace();
            if (hasMore() && text.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (!hasMore() || text.charAt(pos) != '"') {
                    throw new JsonParseException("expected string key at index " + pos);
                }
                String key = parseString();
                skipWhitespace();
                expectChar(':');
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (!hasMore()) {
                    throw new JsonParseException("unterminated object");
                }
                char c = text.charAt(pos);
                if (c == ',') {
                    pos++;
                } else if (c == '}') {
                    pos++;
                    return map;
                } else {
                    throw new JsonParseException("expected ',' or '}' at index " + pos);
                }
            }
        }

        List<Object> parseArray() {
            pos++; // consume '['
            List<Object> list = new ArrayList<>();
            skipWhitespace();
            if (hasMore() && text.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(parseValue());
                skipWhitespace();
                if (!hasMore()) {
                    throw new JsonParseException("unterminated array");
                }
                char c = text.charAt(pos);
                if (c == ',') {
                    pos++;
                } else if (c == ']') {
                    pos++;
                    return list;
                } else {
                    throw new JsonParseException("expected ',' or ']' at index " + pos);
                }
            }
        }

        String parseString() {
            pos++; // consume opening quote
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (!hasMore()) {
                    throw new JsonParseException("unterminated string");
                }
                char c = text.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (!hasMore()) {
                        throw new JsonParseException("unterminated escape sequence");
                    }
                    char e = text.charAt(pos++);
                    switch (e) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            if (pos + 4 > text.length()) {
                                throw new JsonParseException("bad unicode escape");
                            }
                            try {
                                int code = Integer.parseInt(text.substring(pos, pos + 4), 16);
                                sb.append((char) code);
                                pos += 4;
                            } catch (NumberFormatException ex) {
                                throw new JsonParseException("bad unicode escape");
                            }
                        }
                        default -> throw new JsonParseException("bad escape sequence: \\" + e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Number parseNumber() {
            int start = pos;
            if (hasMore() && text.charAt(pos) == '-') {
                pos++;
            }
            if (!hasMore()) {
                throw new JsonParseException("bad number at index " + start);
            }
            boolean isDouble = false;
            while (hasMore()) {
                char c = text.charAt(pos);
                if (c >= '0' && c <= '9') {
                    pos++;
                } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                    // '+'/'-' are only legal inside an exponent; a full numeric grammar is
                    // overkill here — Double.parseDouble validates the token below.
                    isDouble = true;
                    pos++;
                } else {
                    break;
                }
            }
            String token = text.substring(start, pos);
            try {
                if (isDouble) {
                    return Double.parseDouble(token);
                }
                return Long.parseLong(token);
            } catch (NumberFormatException e) {
                throw new JsonParseException("bad number: " + token);
            }
        }

        void expectLiteral(String literal) {
            if (text.startsWith(literal, pos)) {
                pos += literal.length();
            } else {
                throw new JsonParseException("expected '" + literal + "' at index " + pos);
            }
        }

        void expectChar(char expected) {
            if (!hasMore() || text.charAt(pos) != expected) {
                throw new JsonParseException("expected '" + expected + "' at index " + pos);
            }
            pos++;
        }
    }
}
