package edu.yu.cs.com1320.project.demo;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

/**
 * Just enough JSON writing for the facade. Gson needs reflection, which TeaVM doesn't have.
 */
final class Json {

    private Json() {
    }

    static String quote(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c < 0x20 || isLoneSurrogate(text, i)) {
                out.append(unicodeEscape(c));
            } else {
                out.append(c);
            }
        }
        return out.append('"').toString();
    }

    private static boolean isLoneSurrogate(String text, int i) {
        char c = text.charAt(i);
        if (Character.isHighSurrogate(c)) {
            return i + 1 == text.length() || !Character.isLowSurrogate(text.charAt(i + 1));
        }
        return Character.isLowSurrogate(c) && (i == 0 || !Character.isHighSurrogate(text.charAt(i - 1)));
    }

    static String unicodeEscape(char c) {
        String hex = Integer.toHexString(c);
        return "\\u" + "0000".substring(hex.length()) + hex;
    }

    /**
     * @param values JSON values, already written
     */
    static String array(Collection<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (Iterator<String> it = values.iterator(); it.hasNext(); ) {
            out.append(it.next());
            if (it.hasNext()) {
                out.append(',');
            }
        }
        return out.append(']').toString();
    }

    static String stringArray(Collection<String> values) {
        StringBuilder out = new StringBuilder("[");
        for (Iterator<String> it = values.iterator(); it.hasNext(); ) {
            out.append(quote(it.next()));
            if (it.hasNext()) {
                out.append(',');
            }
        }
        return out.append(']').toString();
    }

    /**
     * @param sorted a map that iterates in a fixed order, because HashMap order differs under TeaVM
     */
    static String stringMap(Map<String, String> sorted) {
        StringBuilder out = new StringBuilder("{");
        for (Iterator<Map.Entry<String, String>> it = sorted.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, String> entry = it.next();
            out.append(quote(entry.getKey())).append(':').append(quote(entry.getValue()));
            if (it.hasNext()) {
                out.append(',');
            }
        }
        return out.append('}').toString();
    }
}
