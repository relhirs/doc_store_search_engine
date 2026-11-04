package edu.yu.cs.com1320.project.stage6.impl;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.PersistenceManager;

import java.io.*;
import java.lang.reflect.Type;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;


public class DocumentPersistenceManager implements PersistenceManager<URI, Document> {

    private final File baseDir;
    private final Gson gson;


     private class DocumentSerializer implements JsonSerializer<Document> {
        @Override
        public JsonElement serialize(Document src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject jsonObject = new JsonObject();
            
            jsonObject.addProperty("uri", src.getKey().toString());
            
            if (src.getDocumentTxt() != null) {
                jsonObject.addProperty("txt", src.getDocumentTxt());
            } else if (src.getDocumentBinaryData() != null) {
                String base64Data = Base64.getEncoder().encodeToString(src.getDocumentBinaryData());
                jsonObject.addProperty("binaryData", base64Data);
            }
            
            jsonObject.add("wordMap", context.serialize(src.getWordMap()));
            jsonObject.add("metadata", context.serialize(src.getMetadata()));
            
            return jsonObject;
        }
    }

    private class DocumentDeserializer implements JsonDeserializer<Document> {
        @Override
        public Document deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject jsonObject = json.getAsJsonObject();

            URI uri = URI.create(jsonObject.get("uri").getAsString());
            Type mapType = new TypeToken<HashMap<String, Integer>>() {
            }.getType();
            HashMap<String, Integer> wordMap = context.deserialize(jsonObject.get("wordMap"), mapType);
            DocumentImpl doc;
            if (jsonObject.has("txt")) {
                String text = jsonObject.get("txt").getAsString();
                doc = new DocumentImpl(uri, text, wordMap);
            } else {
                byte[] binaryData = Base64.getDecoder().decode(jsonObject.get("binaryData").getAsString());
                doc = new DocumentImpl(uri, binaryData);
            }
            Type metaType = new TypeToken<HashMap<String, String>>() {
            }.getType();
            HashMap<String, String> metadata = context.deserialize(jsonObject.get("metadata"), metaType);
            if (metadata != null) {
                for (Map.Entry<String, String> entry : metadata.entrySet()) {
                    doc.setMetadataValue(entry.getKey(), entry.getValue());
                }
            }
            return doc;
        }
    }
    
    public DocumentPersistenceManager(File baseDir) {
        if (baseDir != null) {
            this.baseDir = baseDir;
        } else {
            this.baseDir = new File(System.getProperty("user.dir"));
        }

        this.gson = new GsonBuilder()
                .registerTypeHierarchyAdapter(Document.class, new DocumentSerializer())
                .registerTypeHierarchyAdapter(Document.class, new DocumentDeserializer())
                .setPrettyPrinting()
                .create();
    }

    @Override
    public void serialize(URI uri, Document val) throws IOException {
        if (uri == null || val == null) {
            throw new IllegalArgumentException("URI and Document cannot be null");
        }

        File targetFile = getFileFromUri(uri);
        targetFile.getParentFile().mkdirs(); 

        try (FileWriter writer = new FileWriter(targetFile, StandardCharsets.UTF_8)) {
            this.gson.toJson(val, writer);
        }
    }

    @Override
    public Document deserialize(URI uri) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }

        File targetFile = getFileFromUri(uri);
        if (!targetFile.exists()) {
            return null; // Return null if the document isn't stored on disk
        }

        try (FileReader reader = new FileReader(targetFile, StandardCharsets.UTF_8)) {
            return this.gson.fromJson(reader, Document.class);
        }
    }

    @Override
    public boolean delete(URI uri) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("URI cannot be null");
        }
        
        File targetFile = getFileFromUri(uri);
        return targetFile.delete();
    }

    
    private File getFileFromUri(URI uri) {
        String uriString = uri.toString();
        
        if (uriString.startsWith("http://")) {
            uriString = uriString.substring(7);
        } else if (uriString.startsWith("https://")) {
            // Kept in its own folder so http and https versions of a URI never share a file
            uriString = "https/" + uriString.substring(8);
        }
        // A ".." segment must not be able to walk out of the base directory
        uriString = uriString.replace("..", "%2E%2E");

        return new File(this.baseDir, uriString + ".json");
    }
}