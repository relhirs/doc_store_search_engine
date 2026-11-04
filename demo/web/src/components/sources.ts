export const REPOSITORY = 'https://github.com/relhirs/doc_store_search_engine';

const STAGE6 = `${REPOSITORY}/blob/main/stage6/src/main/java/edu/yu/cs/com1320/project`;

export const SOURCES = {
  trie: { label: 'TrieImpl.java', href: `${STAGE6}/impl/TrieImpl.java` },
  btree: { label: 'BTreeImpl.java', href: `${STAGE6}/impl/BTreeImpl.java` },
  heap: { label: 'MinHeapImpl.java', href: `${STAGE6}/impl/MinHeapImpl.java` },
  stack: { label: 'StackImpl.java', href: `${STAGE6}/impl/StackImpl.java` },
  store: { label: 'DocumentStoreImpl.java', href: `${STAGE6}/stage6/impl/DocumentStoreImpl.java` },
  persistence: { label: 'DocumentPersistenceManager.java', href: `${STAGE6}/stage6/impl/DocumentPersistenceManager.java` },
};
