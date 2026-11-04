package edu.yu.cs.com1320.project.demo;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** Reads the facade's JSON in tests. */
final class Responses {

    private Responses() {
    }

    static JsonObject parse(String response) {
        return JsonParser.parseString(response).getAsJsonObject();
    }

    static JsonObject state(String response) {
        return parse(response).getAsJsonObject("state");
    }

    static JsonObject events(String response) {
        return parse(response).getAsJsonObject("events");
    }

    static List<String> strings(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(element.getAsString());
        }
        return values;
    }

    /** Names from an array of objects that each have a "name". */
    static List<String> names(JsonArray array) {
        List<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            values.add(element.getAsJsonObject().get("name").getAsString());
        }
        return values;
    }

    static List<String> ramNames(String response) {
        return names(state(response).getAsJsonArray("ram"));
    }

    static List<String> diskNames(String response) {
        return names(state(response).getAsJsonArray("disk"));
    }
}
