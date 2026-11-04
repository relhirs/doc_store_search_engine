package edu.yu.cs.com1320.project.stage6.impl;

import edu.yu.cs.com1320.project.stage6.Document;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashMap;


public class DocumentImpl implements Document {
    private URI uri;
    private String txt;
    private long lastUseTime; 
    private byte[] binaryData;

    private Map<String, Integer> wordMap;
    private HashMap<String, String> metadata;
   
    
    public DocumentImpl(URI uri, String txt) {
        if (uri == null || uri.toString().isBlank() || txt == null || txt.isBlank()) {
            throw new IllegalArgumentException();
        }
        this.uri = uri;
        this.txt = txt;
        this.metadata = new HashMap<>();
        this.wordMap = new HashMap<>();
        this.lastUseTime = UseClock.now();
        fillWordMap(txt);
    }

    
    public DocumentImpl(URI uri, byte[] binaryData) {
        if (uri == null || uri.toString().isBlank() || binaryData == null || binaryData.length == 0) {
            throw new IllegalArgumentException();
        }

        this.uri = uri;
        this.binaryData = binaryData;
        this.metadata = new HashMap<>();
        this.wordMap = new HashMap<>();
        this.lastUseTime = UseClock.now();
    }
    
    public DocumentImpl(URI uri, String text, Map<String, Integer> wordCountMap) {
         if (uri == null || uri.toString().isBlank() || text == null || text.isBlank()) {
            throw new IllegalArgumentException();
        }
        this.uri = uri;
        this.txt = text; 
        this.metadata = new HashMap<>();
        this.lastUseTime = UseClock.now();
        
        if (wordCountMap == null) {
            this.wordMap = new HashMap<>();
            fillWordMap(text);  
        } else {
            this.wordMap = new HashMap<>(wordCountMap);  
        }
    }
 
    @Override
    public String setMetadataValue(String key, String value) {
         if (key == null || key.isBlank()) {
            throw new IllegalArgumentException(); 
         }

         return metadata.put(key, value); 
    }

    @Override
    public String getMetadataValue(String key) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException();
        }
        return metadata.get(key);   
    }

    @Override
    public HashMap<String, String> getMetadata() {
        HashMap<String, String> metadataCopy = new HashMap<>();
        for (String key : this.metadata.keySet()) {
            metadataCopy.put(key, this.metadata.get(key));
        }
        return metadataCopy;
    }

    @Override
    public void setMetadata(HashMap<String, String> metadata) {
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            this.metadata.put(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public HashMap<String, Integer> getWordMap() {
        return new HashMap<>(this.wordMap);
    }

    @Override
    public void setWordMap(HashMap<String, Integer> wordMap) {
        this.wordMap = wordMap;
    }

    @Override
    public String getDocumentTxt() {
        return this.txt; 
    }

    @Override
    public byte[] getDocumentBinaryData() {
        return this.binaryData;
    }

    @Override
    public URI getKey() { 
        return this.uri; 
    }

    @Override
    public int compareTo(Document other) {     
        long thisTime = this.getLastUseTime();
        long thatTime = other.getLastUseTime(); 
        
        return Long.compare(thisTime, thatTime); 
    }

    @Override
    public int hashCode() {
        int result = uri.hashCode();
        result = 31 * result + (txt != null ? txt.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(binaryData);
        
        return result & Integer.MAX_VALUE;
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof DocumentImpl)) return false;

        DocumentImpl other = (DocumentImpl) o;
        return this.uri.equals(other.uri)
                && Objects.equals(this.txt, other.txt)
                && Arrays.equals(this.binaryData, other.binaryData);
    }

    private void fillWordMap(String text) {
        String[] words = text.split("[^a-zA-Z0-9]+");

        for (String word : words) {
            if (word.isEmpty()) continue; 

            this.wordMap.put(word, this.wordMap.getOrDefault(word, 0) + 1); 
        }
    }

    @Override
    public int wordCount(String word) {
        return this.wordMap.getOrDefault(word, 0); 
    }

    @Override
    public Set<String> getWords() {
        return this.wordMap.keySet(); 
    }

    @Override
    public long getLastUseTime() {
        return this.lastUseTime;
    }

    @Override
    public void setLastUseTime(long timeInNanoseconds) {
        this.lastUseTime = timeInNanoseconds; 
    }

}

