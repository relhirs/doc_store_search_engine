package edu.yu.cs.com1320.project.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;

import edu.yu.cs.com1320.project.Trie; 


public class TrieImpl<Value> implements Trie<Value> {

    private static final int alphabetSize = 256;
    private Node<Value> root; 

    private static class Node<Value> {
        protected Set<Value> values; 
        protected Node[] links;
        
        private Node() {
            this.values = new LinkedHashSet<>();
            this.links =  new Node[alphabetSize]; 
        }    
        
    }

    public TrieImpl() {
        this.root = new Node<>(); 
    }
    
    @Override
    public void put(String key, Value val) {
        if (key == null || val == null) {
            return; 
        }
        for (int i = 0; i < key.length(); i++) {
            if (key.charAt(i) >= alphabetSize) {
                throw new IllegalArgumentException("Key contains a character outside the trie's alphabet");
            }
        }
        this.root = put(this.root, key, val, 0);     
    }

    private Node<Value> put(Node<Value> x, String key, Value val, int d) {
        //create a new node
        if (x == null) {
            x = new Node<>();
        }
        //if we've reached the last node in the key, set the value for the key and return the node
        if (d == key.length()) {
            x.values.add(val);
            return x;
        }
        //proceed to the next node in the chain of nodes that forms the desired key
        char c = key.charAt(d);
        x.links[c] = this.put(x.links[c], key, val, d + 1);
        return x;
    }

    @Override
    public Set<Value> deleteAll(String key) {
        // 1. Fetch the data currently stored at this key
        Set<Value> dataToDelete = this.get(key); 
        
        // If there is nothing there, return an empty set immediately
        if (dataToDelete == null || dataToDelete.isEmpty()) {
            return new HashSet<>(); 
        }
        
        // 2. Make an independent copy of the data 
        Set<Value> savedData = new LinkedHashSet<>(dataToDelete); 

        this.root = deleteAll(this.root, key, 0);
        return savedData; 
    }

    
    private Node<Value> deleteAll(Node<Value> x, String key, int d) {
        if (x == null) {
            return null;
        }
        //we're at the node to del - set the val to null
        if (d == key.length()) {
            x.values.clear();
        }
        //continue down the trie to the target node
        else {
            char c = key.charAt(d);
            x.links[c] = this.deleteAll(x.links[c], key, d + 1);
        }
   
        // this node also makes up another smaller word
        if (!x.values.isEmpty()) {
            return x;
        }
        // This node in the array is a prefix for other existing words	
        for (int i = 0; i < alphabetSize; i++) {
            if (x.links[i] != null) {
                return x; //not empty
            }
        }
        //empty - set this link to null in the parent
        return null;
    }

    @Override
    public Set<Value> deleteAllWithPrefix(String prefix) {
        if (prefix == null) {
            return new HashSet<>();
        }
        Node<Value> prefixNode = get(this.root, prefix, 0);
        Set<Value> allValues = new LinkedHashSet<>();
        if (prefixNode == null) {
            return allValues;
        }
        collectAllInSubtree(prefixNode, allValues); 
        this.root = deleteAllWithPrefix(this.root, prefix, 0);
        return allValues; 
    }

    private Node<Value> deleteAllWithPrefix(Node<Value> x, String prefix, int d) {
        if (x == null) return null; 

         //we're at the node to del - set the val to null
        if (d == prefix.length()) {
            return null; 
        }
        //continue down the path 
        char c = prefix.charAt(d);
        x.links[c] = this.deleteAllWithPrefix(x.links[c], prefix, d + 1);
          
        // this node also makes up another smaller word
        if (!x.values.isEmpty()) {
           return x; 
        }
        // This node in the array is a prefix for other existing words	
        for (int i = 0; i < alphabetSize; i++) {
            if (x.links[i] != null) {
                return x; 
            }
        }
        return null; 
    }

    @Override
    public Value delete(String key, Value val) {
        Node<Value> targetNode = get(this.root, key, 0);
        if (targetNode == null || !targetNode.values.contains(val)) {
            return null; 
        }
        if (!targetNode.values.remove(val)) {
            return null; 
        }
        if (targetNode.values.isEmpty()) {
            this.root = deleteAll(this.root, key, 0); 
        }

        return val;  
    }


    @Override
    public List<Value> getSorted(String key, Comparator<Value> comparator) {
        Set<Value> docs = this.get(key);

        if (docs == null) {
            return new ArrayList<>();
        } 
        List<Value> sortedDocs = new ArrayList<>(docs); 
        sortedDocs.sort(comparator);
        
        return sortedDocs; 
    }

    @Override
    public Set<Value> get(String key) {
        if (key == null) {
            return new HashSet<>();
        }
        Node<Value> result = get(this.root, key, 0);
        if (result == null) {
            return new HashSet<>(); 
        }
        Set<Value> docs = new LinkedHashSet<>(result.values);
        return docs; 
    }

    private Node<Value> get(Node<Value> x, String key, int d) {
        if (x == null) {
            return null; 
        }
        if (d == key.length()) {
            return x;
        }     
        //proceed to the next node in the chain of nodes that forms the desired key
        char c = key.charAt(d);
        //a character outside the alphabet can't be in the trie
        if (c >= alphabetSize) {
            return null;
        }
        return this.get(x.links[c], key, d + 1);
    }

    @Override
    public List<Value> getAllWithPrefixSorted(String prefix, Comparator<Value> comparator) {
        if (prefix == null) {
            return new ArrayList<>();
        }
        Node<Value> prefixNode = get(this.root, prefix, 0);
        if (prefixNode == null) {
            return new ArrayList<>();
        }
        Set<Value> allValues = new LinkedHashSet<>();
        collectAllInSubtree(prefixNode, allValues);

        List<Value> sortedByPrefix = new ArrayList<>(allValues);
        sortedByPrefix.sort(comparator);
        return sortedByPrefix;    
    }

    private void collectAllInSubtree(Node<Value> x, Set<Value> allValues) {
        if (x == null) return;

        // Add this node's values to master set
        allValues.addAll(x.values);
        // Recursively visit every single possible child 
        for (int i = 0; i < alphabetSize; i++) {
            collectAllInSubtree(x.links[i], allValues);
        }
        
    }   
}