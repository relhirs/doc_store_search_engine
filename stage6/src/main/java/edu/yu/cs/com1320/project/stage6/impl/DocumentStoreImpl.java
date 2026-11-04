package edu.yu.cs.com1320.project.stage6.impl;

import edu.yu.cs.com1320.project.stage6.Document;
import edu.yu.cs.com1320.project.stage6.DocumentStore;
import edu.yu.cs.com1320.project.stage6.PersistenceManager;
import edu.yu.cs.com1320.project.impl.BTreeImpl;
import edu.yu.cs.com1320.project.impl.MinHeapImpl;
import edu.yu.cs.com1320.project.impl.StackImpl;
import edu.yu.cs.com1320.project.impl.TrieImpl;

import edu.yu.cs.com1320.project.MinHeap;
import edu.yu.cs.com1320.project.Stack;
import edu.yu.cs.com1320.project.Trie;
import edu.yu.cs.com1320.project.undo.CommandSet;
import edu.yu.cs.com1320.project.undo.GenericCommand;
import edu.yu.cs.com1320.project.undo.Undoable;
import edu.yu.cs.com1320.project.BTree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;



import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

public class DocumentStoreImpl implements DocumentStore {
    private BTree<URI, Document> storage;
    private PersistenceManager<URI, Document> pm;
    private Stack<Undoable> commandStack;
    private Trie<URI> trie; 
    private MinHeap<DocumentURIWrapper> minHeap;
    private Set<URI> docsInRam;
    private Set<URI> allURIs; 
    private int maxDocCount = -1; // -1 indicates no limit
    private int maxDocBytes = -1;
    private int currentDocCount = 0;
    private int currentDocBytes = 0;

    public DocumentStoreImpl() {
        this(null); 
    }


    public DocumentStoreImpl(File baseDIR) {
        this.storage = new BTreeImpl<URI, Document>();
        this.pm = new DocumentPersistenceManager(baseDIR);
        this.storage.setPersistenceManager(this.pm);
        this.commandStack = new StackImpl<Undoable>();
        this.trie = new TrieImpl<>();
        this.minHeap = new MinHeapImpl<>();
        this.docsInRam = new HashSet<>();
        this.allURIs = new HashSet<>();
    }

    private class DocumentURIWrapper implements Comparable<DocumentURIWrapper> {
        private URI uri;

        public DocumentURIWrapper(URI uri) {
            this.uri = uri;
        }
        
        @Override 
        public int compareTo(DocumentURIWrapper other) {
            Document d1 = storage.get(this.uri);
            Document d2 = storage.get(other.uri);
            return d1.compareTo(d2);
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof DocumentURIWrapper) || o == null) {
                return false;
            }
            DocumentURIWrapper other = (DocumentURIWrapper) o;
            return Objects.equals(this.uri, other.uri);
        }
        
        @Override
        public int hashCode() {
            return this.uri.hashCode();
        }
    }
    @Override
    public String setMetadata(URI uri, String key, String value) {
        if (uri == null || uri.toString().isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("URI/Key cannot be null or blank");
        }
        Document doc = this.get(uri);
        if (doc == null) {
            throw new IllegalArgumentException("No document stored at that URI");
        }
        String oldValue = applyMetadata(doc, key, value);

		Consumer<URI> undoLogic = (URI targetUri) -> applyMetadata(this.get(targetUri), key, oldValue);
        
        this.commandStack.push(new GenericCommand<URI>(uri, undoLogic)); 
        return oldValue;  
    }

    private String applyMetadata(Document doc, String key, String value) {
        URI uri = doc.getKey();
        String oldValue = doc.setMetadataValue(key, value);
        if (docsInRam.contains(uri)) {
            doc.setLastUseTime(UseClock.now());
            minHeap.reHeapify(new DocumentURIWrapper(uri));
        } else {
            // The doc is bigger than the byte limit, so it only lives on disk
            try {
                this.pm.serialize(uri, doc);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to save metadata for " + uri, e);
            }
        }
        return oldValue;
    }

    @Override
    public String getMetadata(URI uri, String key) {
        if (uri == null || uri.toString().isBlank() || key == null || key.isBlank()) {
            throw new IllegalArgumentException("URI/Key cannot be null or blank");
        }
        Document doc = this.get(uri);   
        if (doc == null) {
            throw new IllegalArgumentException("No Document stored at that URI");
        }
        return doc.getMetadataValue(key); 
    }

    @Override
    public int put(InputStream input, URI uri, DocumentFormat format) throws IOException {
        if (uri == null || uri.toString().isBlank() || format == null) {
            throw new IllegalArgumentException("URI cannot be null or empty. Format cannot be null");
        }
        Document existingDoc = peek(uri);
        if (input == null) {
            if (existingDoc != null) {
                removeFromSystem(existingDoc);
                this.storage.put(uri, null); 
                this.allURIs.remove(uri); 
                undoLogic(uri, existingDoc);
            return existingDoc.hashCode();
        }
            return 0; 
        }
        Document newDoc = makeDoc(input, uri, format); 
        if (maxDocBytes != -1 && getDocSize(newDoc) > maxDocBytes) {
            throw new IllegalArgumentException("Doc being added can't exceed threshold for doc size"); 
        }
        if (existingDoc != null) {
            removeFromSystem(existingDoc);
        }
        this.storage.put(uri, newDoc);
        addToSystem(newDoc);
        this.allURIs.add(uri);
        enforceLimits();    
        undoLogicHelper(existingDoc, uri); 
        return (existingDoc != null) ? existingDoc.hashCode() : 0;          
    }

     private void undoLogicHelper(Document oldDoc, URI uri) {
         Consumer<URI> undoLogic = (URI targetUri) -> {
             // Look the doc up: a trip to disk and back replaces the object that was put
             removeFromSystem(peek(targetUri));
            this.storage.put(targetUri, oldDoc);
                if (oldDoc != null) {
                    addToSystem(oldDoc);
                    enforceLimits();
                } else {
                    this.allURIs.remove(uri); 
                }
            };
            this.commandStack.push(new GenericCommand<URI>(uri, undoLogic)); 
    }


    private void undoLogic(URI uri, Document doc) {
        Consumer<URI> undoLogic = (URI targetUri) -> {
            this.storage.put(targetUri, doc);
            this.allURIs.add(targetUri); 
            addToSystem(doc);
            enforceLimits();   
        };
        this.commandStack.push(new GenericCommand<URI>(uri, undoLogic));
    }

    private void enforceLimits() {
        while (maxDocCount != -1 && currentDocCount > maxDocCount ||  maxDocBytes != -1 && currentDocBytes > maxDocBytes) {
			DocumentURIWrapper removedWrapper = minHeap.remove();
			Document docToRemove = this.storage.get(removedWrapper.uri);
			int size = getDocSize(docToRemove);
            try {
                this.storage.moveToDisk(removedWrapper.uri);
            } catch (IOException e) {
                throw new RuntimeException("Failed to transfer to disk", e); 
            }
            docsInRam.remove(removedWrapper.uri); 

            this.currentDocCount--;
            this.currentDocBytes -= size;
        }
    }

    private int getDocSize(Document doc) {
        if (doc == null) return 0;

        if (doc.getDocumentTxt() != null) {
            return doc.getDocumentTxt().getBytes(StandardCharsets.UTF_8).length;
        } else {
            return doc.getDocumentBinaryData().length; 
        }
    }
    
    private void addToSystem(Document doc) {
        addDocToTrie(doc); 
        doc.setLastUseTime(UseClock.now());
        minHeap.insert(new DocumentURIWrapper(doc.getKey()));
        this.docsInRam.add(doc.getKey());
        
        currentDocCount++;
        currentDocBytes += getDocSize(doc);
    }
    
    
    private void removeFromSystem(Document doc) {
        removeDocFromTrie(doc);
        
        if (docsInRam.contains(doc.getKey())) {
            doc.setLastUseTime(Long.MIN_VALUE);
            minHeap.reHeapify(new DocumentURIWrapper(doc.getKey()));
            this.docsInRam.remove(doc.getKey());
            this.minHeap.remove();
            this.currentDocCount--;
            this.currentDocBytes -= getDocSize(doc);
        }   
    }

    @Override
    public void setMaxDocumentCount(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit cannot be less than 1");
        }
        this.maxDocCount = limit;
        enforceLimits();
    }

    @Override
    public void setMaxDocumentBytes(int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("Limit cannot be less than 1");
        }
        this.maxDocBytes = limit; 
        enforceLimits(); 
    }


    private void addDocToTrie(Document doc) {
        for (String word : doc.getWords()) {
            this.trie.put(word, doc.getKey()); 
        }
    }

    private void removeDocFromTrie(Document doc) {
        for (String word : doc.getWords()) {
            this.trie.delete(word, doc.getKey()); 
        }
    }

   

    private Document makeDoc(InputStream input, URI uri, DocumentFormat format) throws IOException {
        Document document = null; 
        String text = null; 

        byte[] data = input.readAllBytes();
        switch(format) {
            case TXT:
                text = new String(data, StandardCharsets.UTF_8);
                document = new DocumentImpl(uri, text);
                break;
            case BINARY: 
                document = new DocumentImpl(uri, data); 
                break;
        }
        return document; 
    }

    
    @Override
    public Document get(URI url) {        
        Document doc =  this.storage.get(url); 

        if (doc != null) {
            if (docsInRam.contains(url)) {
                doc.setLastUseTime(UseClock.now());
                minHeap.reHeapify(new DocumentURIWrapper(url));
            } else {
                addToSystem(doc); 
            }     
        }
        enforceLimits(); 
        return doc; 
    }

    /**
     * Read a document without using it: its last use time doesn't change, and a document on disk stays there.
     */
    private Document peek(URI uri) {
        if (!this.allURIs.contains(uri)) {
            return null;
        }
        if (this.docsInRam.contains(uri)) {
            return this.storage.get(uri);
        }
        try {
            return this.pm.deserialize(uri);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + uri + " from disk", e);
        }
    }

    @Override
    public boolean delete(URI url) {
        Document deletedDoc = peek(url);
        if (deletedDoc == null) {
            return false;
        }
        removeFromSystem(deletedDoc);
        this.storage.put(url, null);
        this.allURIs.remove(url); 
        undoLogic(url, deletedDoc);
        return true; 
    }

    @Override
    public void undo() throws IllegalStateException {
        if (commandStack.size() == 0) {
            throw new IllegalStateException("Cannot undo action on an empty stack");
        }
        Undoable poppedCommand = commandStack.pop();
        poppedCommand.undo();
        enforceLimits();
    }

    @Override
    public void undo(URI url) throws IllegalStateException {
        if (commandStack.size() == 0) {
            throw new IllegalStateException("Cannot undo action on an empty stack");
        }
        Stack<Undoable> otherStack = new StackImpl<>();
        boolean found = undoCommandStack(this.commandStack, otherStack, url);        
        while (otherStack.size() > 0) {
            commandStack.push(otherStack.pop()); 
        }  
        if (!found) {
            throw new IllegalStateException("Cannot undo action for a URL that doesn't exist on the stack");
        }
        enforceLimits();
    }

    private boolean undoCommandStack (Stack<Undoable> stack, Stack<Undoable> otherStack, URI url) {
        while (stack.size() > 0) {
            Undoable current = stack.pop();
            if (current instanceof GenericCommand) {
                GenericCommand<URI> generic = (GenericCommand<URI>) current; 
                if (generic.getTarget().equals(url)) {
                    generic.undo();
                    return true; 
                } else {
                    otherStack.push(current); 
                }
            } else if (current instanceof CommandSet) {
                CommandSet<URI> set = (CommandSet<URI>) current; 
                if (set.containsTarget(url)) {
                    set.undo(url);
                    if (set.size() > 0) {
                        otherStack.push(set);
                    }
                    return true; 
                } else {
                    otherStack.push(current); 
                }
            }
        }
            return false; 
    }

    private List<Document> updateUsage(List<Document> results) {
        long now = UseClock.now();

		for (Document doc : results) {
			URI uri = doc.getKey();
            if (docsInRam.contains(uri)) {
                doc.setLastUseTime(now);
                this.minHeap.reHeapify(new DocumentURIWrapper(uri));
            }     
        }
        return results; 
    }

    /**
     * Rank URIs by score, highest first, reading each document once. Ties go to the lower URI.
     */
    private Comparator<URI> byRelevance(ToIntFunction<Document> score) {
        Map<URI, Integer> scores = new HashMap<>();
        return (uri1, uri2) -> {
            int count1 = scores.computeIfAbsent(uri1, uri -> score.applyAsInt(peek(uri)));
            int count2 = scores.computeIfAbsent(uri2, uri -> score.applyAsInt(peek(uri)));
            int byCount = Integer.compare(count2, count1);
            return byCount != 0 ? byCount : uri1.compareTo(uri2);
        };
    }

    private List<Document> getResults(List<URI> uris) {
        List<Document> documentResults = new ArrayList<>();
        for (URI uri : uris) {
            documentResults.add(this.get(uri));
        }
        return updateUsage(documentResults);
    }

    @Override
    public List<Document> search(String keyword) {
        if (keyword == null) return new ArrayList<>();

		return getResults(this.trie.getSorted(keyword, byRelevance(doc -> doc.wordCount(keyword))));
    }

   @Override
   public List<Document> searchByPrefix(String keywordPrefix) {
        if (keywordPrefix == null || keywordPrefix.isBlank()) {
            return new ArrayList<>();
        }
        return getResults(this.trie.getAllWithPrefixSorted(keywordPrefix, byRelevance(doc -> calculateDocPrecedence(doc, keywordPrefix))));
   }

   private int calculateDocPrecedence(Document doc, String keywordPrefix) {
        int totalCount = 0; 
        for (String word : doc.getWords()) {
            if (word.startsWith(keywordPrefix)) {
                totalCount += doc.wordCount(word); 
            }
        }
        return totalCount; 
   }

   @Override
   public Set<URI> deleteAll(String keyword) {
        Set<URI> deletedUris = new HashSet<>();
        CommandSet<URI> commandSet = new CommandSet<>(); 
        
		Set<URI> urisToDelete = this.trie.get(keyword);
		if (urisToDelete == null || urisToDelete.isEmpty()) {
        	return new HashSet<>();
    	}
        Set<Document> docsToDelete = new HashSet<>();
        
        for (URI uri : urisToDelete) {
            Document doc = peek(uri);
            if (doc != null) docsToDelete.add(doc);
        }
        if (docsToDelete == null || docsToDelete.isEmpty()) {
            return new HashSet<>();
        }
        List<Document> copyOfDocsToDelete = new ArrayList<>(docsToDelete);
        deleteDocFromSystem(copyOfDocsToDelete, deletedUris, commandSet); 
        return deletedUris;  
   }

   @Override
   public Set<URI> deleteAllWithPrefix(String keywordPrefix) {
        if (keywordPrefix == null || keywordPrefix.isBlank()) {
            return new HashSet<>();
        }
        Set<URI> deletedUris = new HashSet<>(); 
        CommandSet<URI> commandSet = new CommandSet<>();

        List<URI> prefixUris = this.trie.getAllWithPrefixSorted(keywordPrefix, (uri1, uri2) -> 0); 
        if (prefixUris== null || prefixUris.isEmpty()) {
            return new HashSet<>();
        }
		List<Document> copyOfDocsToDelete = new ArrayList<>();
		for (URI uri : prefixUris) {
			Document doc = peek(uri);
				if (doc != null) {
        			copyOfDocsToDelete.add(doc); 
    			}
		}
         deleteDocFromSystem(copyOfDocsToDelete, deletedUris, commandSet); 
        return deletedUris;  
   }

   @Override
   public List<Document> searchByMetadata(Map<String, String> keysValues) {
       if (keysValues == null || keysValues.isEmpty()) {
           return new ArrayList<>();
       }
       return getResults(urisWithMetadata(this.keySet(), keysValues));
   }

   @Override
   public List<Document> searchByKeywordAndMetadata(String keyword, Map<String, String> keysValues) {
        if (keyword == null || keyword.isBlank() || keysValues == null || keysValues.isEmpty()) {
            return new ArrayList<>();
        }
        List<URI> keywordURIs = this.trie.getSorted(keyword, byRelevance(doc -> doc.wordCount(keyword))); 
        return getResults(urisWithMetadata(keywordURIs, keysValues)); 
   }

   private List<URI> urisWithMetadata(List<URI> uris, Map<String, String> keysValues) {
        List<URI> matches = new ArrayList<>();
        for (URI uri : uris) {
            Document doc = peek(uri);
            if (doc != null && hasMetadata(doc, keysValues)) {
                matches.add(uri);
            }
        }
        return matches;
   }

   private boolean hasMetadata(Document doc, Map<String, String> keysValues) {
        for (Map.Entry<String, String> entry : keysValues.entrySet()) {
            if (!Objects.equals(doc.getMetadataValue(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
   }

   @Override
   public List<Document> searchByPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) {
        if (keywordPrefix == null || keywordPrefix.isBlank() || keysValues == null || keysValues.isEmpty()) {
            return new ArrayList<>();
        }
        List<URI> prefixUris = this.trie.getAllWithPrefixSorted(keywordPrefix, byRelevance(doc -> calculateDocPrecedence(doc, keywordPrefix)));
        return getResults(urisWithMetadata(prefixUris, keysValues));     
   }

   @Override
   public Set<URI> deleteAllWithMetadata(Map<String, String> keysValues) {
       if (keysValues == null || keysValues.isEmpty()) {
           return new HashSet<>();
       }
        Set<Document> docsToDelete = new HashSet<>();

        for (URI uri : urisWithMetadata(this.keySet(), keysValues)) {
            docsToDelete.add(peek(uri));
        }

        CommandSet<URI> commandSet = new CommandSet<>();
        Set<URI> deletedUris = new HashSet<>();

        deleteDocFromSystem(docsToDelete, deletedUris, commandSet);
        return deletedUris;
    }

    private void deleteDocFromSystem(Collection<Document> docsToDelete, Set<URI> deletedUris, CommandSet<URI> commandSet) {
        List<Document> docsInUriOrder = new ArrayList<>(docsToDelete);
        docsInUriOrder.sort(Comparator.comparing(Document::getKey));
        for (Document doc : docsInUriOrder) {
            URI uri = doc.getKey();
            removeFromSystem(doc);
            this.storage.put(uri, null);
            deletedUris.add(uri);
            this.allURIs.remove(uri); 
            
            Consumer<URI> undoLogic = (targetURI) -> {
                this.storage.put(targetURI, doc);
                this.allURIs.add(targetURI); 
                addToSystem(doc);
                enforceLimits();
        
            }; 
            GenericCommand<URI> command = new GenericCommand<>(uri, undoLogic); 
            commandSet.addCommand(command);       
            }
            if (commandSet.size() > 0) {
                this.commandStack.push(commandSet);
            }
    }

    @Override
    public Set<URI> deleteAllWithKeywordAndMetadata(String keyword, Map<String, String> keysValues) {
        if (keyword == null || keyword.isBlank() || keysValues == null || keysValues.isEmpty()) {
            return new HashSet<>();
        }    
		Set<URI> urisToDelete = this.trie.get(keyword);
		if (urisToDelete == null || urisToDelete.isEmpty()) {
			return new HashSet<>();
		}
        Set<Document> docsToDelete = new HashSet<>();
        for (URI uri : urisToDelete) {
            Document doc = peek(uri);
            if (doc != null) docsToDelete.add(doc);
        }
        if (docsToDelete.isEmpty()) {
            return new HashSet<>();
        }
		filterDocsByMetadata(docsToDelete, keysValues);
		
		if (docsToDelete.isEmpty()) {
            return new HashSet<>();
        }
       
        CommandSet<URI> commandSet = new CommandSet<>();
        Set<URI> deletedUris = new HashSet<>();
        deleteDocFromSystem(docsToDelete, deletedUris, commandSet); 
        return deletedUris; 
    }


    private void filterDocsByMetadata(Collection<Document> docsToDelete, Map<String, String> keysValues) {
        docsToDelete.removeIf(doc -> doc == null || !hasMetadata(doc, keysValues));
    }

    @Override
    public Set<URI> deleteAllWithPrefixAndMetadata(String keywordPrefix, Map<String, String> keysValues) {
        if (keywordPrefix == null || keywordPrefix.isBlank() || keysValues == null || keysValues.isEmpty()) {
            return new HashSet<>();
        }
        Set<URI> deletedUris = new HashSet<>();
        List<URI> prefixUrisToDelete = this.trie.getAllWithPrefixSorted(keywordPrefix, (doc1, doc2) -> 0);
		if (prefixUrisToDelete.isEmpty()) {
			return new HashSet<>();
		}
		List<Document> prefixDocsToDelete = new ArrayList<>();
    	for (URI uri : prefixUrisToDelete){
       		Document doc = peek(uri);
        	if (doc != null) prefixDocsToDelete.add(doc);
    	}
        filterDocsByMetadata(prefixDocsToDelete, keysValues);
        CommandSet<URI> commandSet = new CommandSet<>();
        deleteDocFromSystem(prefixDocsToDelete, deletedUris, commandSet);
        return deletedUris;
    }
    
    private List<URI> keySet() {
        List<URI> uris = new ArrayList<>(this.allURIs);
        Collections.sort(uris);
        return uris;
    }

}
