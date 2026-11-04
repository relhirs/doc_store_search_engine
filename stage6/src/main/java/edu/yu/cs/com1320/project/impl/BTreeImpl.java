package edu.yu.cs.com1320.project.impl;

import edu.yu.cs.com1320.project.BTree;
import edu.yu.cs.com1320.project.stage6.PersistenceManager;
import java.io.IOException;
import java.io.UncheckedIOException;




public class BTreeImpl<Key extends Comparable<Key>, Value> implements BTree<Key, Value> {
    // Max children per B-tree node = MAX-1 
    private final static int MAX = 4;
    private Node root; // The top of your tree
    private int height; // Current height of the B-tree
    private int n; // Total number of key-value pairs
    private PersistenceManager<Key, Value> pm;

    public BTreeImpl() {
        this.root = new Node(0);
    }
    
      
    private static class Entry {
        private Comparable key;
        private Object val;
        private Node child;

        public Entry(Comparable key, Object val, Node child) {
            this.key = key;
            this.val = val;
            this.child = child;
        }

        public Object getValue() {
            return this.val;
        }

        public Comparable getKey() {
            return this.key;
        }
    }
    

    
    private final static class Node {
        private int entryCount; // How many entries are currently in this node
        private Entry[] entries = new Entry[MAX];
        private Node next;
        private Node previous;

        // Create a node with k entries
        private Node(int k) {
            this.entryCount = k;
        }

        private void setNext(Node next) {
            this.next = next;
        }

        private Node getNext() {
            return this.next;
        }

        private void setPrevious(Node previous) {
            this.previous = previous;
        }
    }


@Override
public void setPersistenceManager(PersistenceManager<Key, Value> pm) {
    this.pm = pm;
}



@Override
public void moveToDisk(Key k) throws IOException {

    if (k == null) {
        throw new IllegalArgumentException("arguemnt to moveToDisk() is null");
    }
    Entry entry = get(this.root, k, this.height);
    if (entry != null) {
        Value value = (Value) entry.val;
        if (value != null) {
            pm.serialize(k, value);
            entry.val = null; 
        }    
    }
    
}


@Override
    public Value get(Key key) {
        if (key == null) throw new IllegalArgumentException();
        Entry entry = this.get(this.root, key, this.height);
        if (entry == null) return null;
        if (entry.val != null) return (Value) entry.val;
        if (pm == null) return null;

        try {
            Value val = pm.deserialize(key);
            if (val == null) return null; // The file wasn't on disk
            entry.val = val;
            pm.delete(key);
            return val;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + key + " from disk", e);
        }
    }

private boolean isEqual(Comparable k1, Comparable k2) {
    return k1.compareTo(k2) == 0;
}

    
private Entry get(Node currentNode, Key key, int height) {
    Entry[] entries = currentNode.entries;

    //current node is external (i.e. height == 0)
    if (height == 0) {
        for (int j = 0; j < currentNode.entryCount; j++) {
            if (isEqual(key, entries[j].key)) {
                return entries[j]; //found desired key. Return its value
            }
        }
        return null; //didn't find the key
    } else { //current node is internal (height > 0)
        for (int j = 0; j < currentNode.entryCount; j++) {
            //if (we are at the last key in this node OR the key we
            //are looking for is less than the next key, i.e. the
            //desired key must be in the subtree below the current entry),
            //then recurse into the current entry’s child
            if (j + 1 == currentNode.entryCount || less(key, entries[j + 1].key)) {
                return this.get(entries[j].child, key, height - 1);
            }
        }
        return null; //didn't find the key
    }
}



/**
 * Inserts the key-value pair into the symbol table, overwriting the old
 * value with the new value if the key is already in the symbol table. If
 * the value is {@code null}, this effectively deletes the key from the
 * symbol table.
 *
 * @param key the key
 * @param val the value
 * @throws IllegalArgumentException if {@code key} is {@code null}
 */
@Override
public Value put(Key key, Value val) {
    if (key == null) throw new IllegalArgumentException();
    
    // 1. Check if key exists (RAM or Disk)
    Entry alreadyThere = this.get(this.root, key, this.height);
    if (alreadyThere != null) {
        Value oldValue = (Value) alreadyThere.val;
        // If it was on disk, we MUST fetch and delete that file now
        if (pm != null) {
            try {
                if (oldValue == null) oldValue = pm.deserialize(key);
                pm.delete(key);
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to replace " + key + " on disk", e);
            }
        }

        alreadyThere.val = val;
        return oldValue;
    }

    // 2. If deleting a non-existent key, do nothing
    if (val == null) return null;

    // 3. Structural Insert
    Node newNode = this.put(this.root, key, val, this.height);
    this.n++; // Increment size only for new keys
    if (newNode != null) {
        rootSplitHelper(newNode);
    }
    return null;
}

private void rootSplitHelper(Node leftHalf) {
    Node newRoot = new Node(2);
    // Root split: entries[0] points to old root, entries[1] points to the new sibling
    newRoot.entries[0] = new Entry(this.root.entries[0].key, null, this.root);
    newRoot.entries[1] = new Entry(leftHalf.entries[0].key, null, leftHalf);
    this.root = newRoot;
    this.height++;
}
private boolean less(Comparable k1, Comparable k2) {
    return k1.compareTo(k2) < 0;
}

private Node put(Node h, Key key, Value val, int height) {
    int j;
    Entry newEntry = null;

    if (height == 0) {
        // External node: Create the entry and find its sorted index
        newEntry = new Entry(key, val, null);
        for (j = 0; j < h.entryCount; j++) {
            if (less(key, h.entries[j].key)) {
                break;
            }
        }
    } else {
        // Internal node: Find the branch to descend
        for (j = 0; j < h.entryCount; j++) {
            if ((j + 1 == h.entryCount) || less(key, h.entries[j + 1].key)) {
                Node u = put(h.entries[j].child, key, val, height - 1);
                if (u == null) return null; // No split occurred below

                // Child split! The new sibling goes directly after the child that split.
                // Its key can't place it: entries[0].key goes stale as smaller keys arrive.
                newEntry = new Entry(u.entries[0].key, null, u);
                j++;
                break; // Break so we exit the loop and call shiftEntries once
            }
        }
    }
    return shiftEntries(h, newEntry, j, height);
}

private Node shiftEntries(Node h, Entry newEntry, int j, int height) {
    // 1. Shift entries to the right to make room for newEntry
    for (int i = h.entryCount; i > j; i--) {
        h.entries[i] = h.entries[i - 1];
    }

    // 2. Insert newEntry and update count
    h.entries[j] = newEntry;
    h.entryCount++;

    // 3. Split if node capacity (MAX=4) is reached
    if (h.entryCount < MAX) {
        return null;
    } else {
        return split(h, height);
    }
}
/**
 * split node in half
* @param currentNode
* @return new node
 */
private Node split(Node currentNode, int height) {
    Node newNode = new Node(MAX / 2);
    //by changing currentNode.entryCount, we will treat any value
    //at index higher than the new currentNode.entryCount as if
    //it doesn't exist
    currentNode.entryCount = MAX / 2;
    //copy top half of h into t
    for (int j = 0; j < MAX / 2; j++) {
        newNode.entries[j] = currentNode.entries[MAX / 2 + j];
    }

    //external node
    if (height == 0) {
        newNode.setNext(currentNode.getNext());
        newNode.setPrevious(currentNode);
        currentNode.setNext(newNode);

        if (newNode.getNext() != null) {
            newNode.getNext().setPrevious(newNode);
        }
    }
    return newNode;
}



}