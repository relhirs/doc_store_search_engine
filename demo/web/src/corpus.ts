export interface Article {
  name: string;
  title: string;
  topic: string;
  era: string;
  text: string;
}

/** Written for this demo. Words repeat across articles so that prefix search has something to rank. */
export const CORPUS: Article[] = [
  {
    name: 'tries', title: 'Tries and prefix search', topic: 'trees', era: '1950s',
    text: `Tries store words one letter per level, so a search walks down the tree and never compares two whole keys. Rene de la Briandais described the idea in 1959, and Edward Fredkin named it a year later, taking the word from retrieval. Every word that shares a prefix shares a path, which makes prefix search almost free: walk to the last letter typed, then collect everything below that node. The trade is memory, because each node keeps a slot for every letter it might need. A trie answers a search in time set by the length of the word, not the number of words stored.`,
  },
  {
    name: 'patricia-tries', title: 'Patricia tries', topic: 'trees', era: '1960s',
    text: `Patricia tries compress a trie by removing every node that has only one child. Donald Morrison published the structure in 1968 to index large text files, and the name stands for a practical algorithm to retrieve information coded in alphanumeric. A plain trie spends memory on long single tracks of letters, while a Patricia trie keeps a count of how many characters to skip. Network routers later used the same trick to search address prefixes, because a router has to find the longest prefix that matches on every packet.`,
  },
  {
    name: 'binary-search-trees', title: 'Binary search trees', topic: 'trees', era: '1960s',
    text: `Binary search trees keep smaller keys on the left and larger keys on the right, so each comparison discards a whole branch. An inorder traversal visits the keys in sorted order. The weakness is shape. Insert keys that are already sorted and the tree turns into a linked list, and a search that should take a few steps has to touch every node. Most of the trees that came later exist to stop this from happening, and they differ mainly in what they track to keep the tree balanced.`,
  },
  {
    name: 'avl-trees', title: 'AVL trees', topic: 'trees', era: '1960s',
    text: `AVL trees were the first self balancing search trees, published in 1962 by Georgy Adelson-Velsky and Evgenii Landis. Each node tracks the height of its two subtrees, and when they differ by more than one, a rotation restores the balance. A rotation moves only a few links, and the sorted order of the keys stays intact. The tree stays strictly balanced, which keeps searches short, but inserts and deletes pay for that with extra rotations on the way back up.`,
  },
  {
    name: 'red-black-trees', title: 'Red-black trees', topic: 'trees', era: '1970s',
    text: `Red-black trees trade a little balance for cheaper updates. Leonidas Guibas and Robert Sedgewick gave them their colors in 1978, building on an earlier structure by Rudolf Bayer. Every node is red or black, and a few rules about those colors keep the longest path under twice the shortest. Inserts and deletes need only a small number of rotations. The Java TreeMap and many other standard libraries use them, because the balance is good enough and the updates stay fast.`,
  },
  {
    name: 'b-trees', title: 'B-Trees on disk', topic: 'trees', era: '1970s',
    text: `B-Trees were designed for disks. Rudolf Bayer and Edward McCreight developed them at Boeing around 1970, when a single disk seek cost far more than any amount of work in memory. A node holds many keys and fills a whole disk page, so the tree is wide and very shallow, and a search over millions of keys touches only a few pages. Nodes split when they fill, and the split pushes a key up to the parent, so the tree grows from the root and every leaf stays at the same depth. Nearly every database index since has been a descendant of this tree.`,
  },
  {
    name: 'splay-trees', title: 'Splay trees', topic: 'trees', era: '1980s',
    text: `Splay trees keep no balance information at all. Daniel Sleator and Robert Tarjan published them in 1985 with a simple rule: after every access, rotate the node all the way to the root. Keys used recently stay near the top, so the tree adapts to what a program actually searches for, much as a cache does. A single search can be slow, but any long sequence of operations is fast on average, and that was the point of the analysis.`,
  },
  {
    name: 'treaps', title: 'Treaps', topic: 'trees', era: '1980s',
    text: `Treaps combine a tree with a heap, which is where the name comes from. Cecilia Aragon and Raimund Seidel described them in 1989. Keys follow search tree order, while a random priority on each node follows heap order. Those random priorities give the tree the same shape as one built by inserting keys in random order, so it is balanced with high probability and needs no rules about height or color. Treaps split and merge easily, which makes them a favorite for trees that must be cut apart.`,
  },
  {
    name: 'binary-heaps', title: 'Binary heaps', topic: 'heaps', era: '1960s',
    text: `Binary heaps keep the smallest key at the root and promise nothing else. J. W. J. Williams introduced the heap in 1964, stored inside a plain array, with the children of each node found by arithmetic rather than links. Insert a key and it rises until its parent is smaller. Remove the root and the last key takes its place, then sinks. Both take time proportional to the height of the tree. A heap is not sorted, and that loose order is exactly why it is so cheap to maintain.`,
  },
  {
    name: 'heapsort', title: 'Heapsort', topic: 'heaps', era: '1960s',
    text: `Heapsort turns an array into a heap, then removes the root again and again until the keys come out in order. Williams published it in 1964 alongside the heap itself, and Robert Floyd showed the same year that a heap can be built in linear time by sifting from the bottom up. It sorts in place and its worst case is as good as its average case, which merge sort matches only with extra memory. It often loses to quicksort in practice, because its jumps through the array are hard on the cache.`,
  },
  {
    name: 'priority-queues', title: 'Priority queues', topic: 'heaps', era: '1950s',
    text: `Priority queues always hand back the most urgent item, and a heap is the usual way to build one. Edsger Dijkstra designed his shortest path algorithm in 1956 and published it in 1959, and later versions pair it with a heap that holds the nearest node not yet visited. Operating systems use the same idea to schedule work, simulators to order events, and a document store to find the page or file used least recently. The queue never sorts everything, it only knows what comes next.`,
  },
  {
    name: 'fibonacci-heaps', title: 'Fibonacci heaps', topic: 'heaps', era: '1980s',
    text: `Fibonacci heaps put off their tidying until it is unavoidable. Michael Fredman and Robert Tarjan introduced them in 1984, and they improved the running time of shortest path and spanning tree algorithms, because a key can be lowered in constant amortized time. Inserts just add a tree to a list, and the merging waits until someone removes the minimum. On paper they beat binary heaps. In practice the extra pointers and poor cache behavior mean a plain array heap is usually faster.`,
  },
  {
    name: 'disk-drives', title: 'The first disk drives', topic: 'storage', era: '1950s',
    text: `Disk storage began with the IBM 350, which shipped with the RAMAC computer in 1956. It stacked fifty platters, each two feet across, held about five million characters, and filled a large cabinet. A single arm moved between the disks to reach any record, so a program no longer had to read a whole tape to find one entry. Random access changed what software could do. It also created a problem that never left: moving the arm takes far longer than reading the data, so every disk structure since has tried to avoid a seek.`,
  },
  {
    name: 'page-caches', title: 'Page caches', topic: 'storage', era: '1970s',
    text: `Page caches keep recently read disk pages in memory, so the second read of a page costs almost nothing. An operating system gives the cache whatever memory programs are not using, and it writes changed pages back to disk later, in larger batches. Because disks are so slow, a database with a warm cache and one with a cold cache can look like two different programs. The hard question is which page to evict when memory runs out, and most caches answer it with some version of least recently used.`,
  },
  {
    name: 'write-ahead-logs', title: 'Write-ahead logs', topic: 'storage', era: '1990s',
    text: `Write-ahead logs make a simple promise: describe a change on disk before making it. If power fails halfway through, the log tells the system what to redo and what to undo. Writing to the end of a log is a sequential write, so it avoids the disk seeks that updating pages in place would need. The ARIES recovery method, published by C. Mohan and colleagues at IBM in 1992, set out the rules that many databases still follow. Undo in a database and undo in an editor share this idea of a history of commands.`,
  },
  {
    name: 'lsm-trees', title: 'Log-structured merge trees', topic: 'storage', era: '1990s',
    text: `Log-structured merge trees turn random writes into sequential ones. Patrick O'Neil and his colleagues published the design in 1996. New keys collect in a sorted tree in memory, and when it fills, the whole tree is written to disk as one sorted file. Background jobs merge those files, in the same way merge sort combines sorted runs. Writes become very fast, but a search may have to check several files, so these systems often keep a Bloom filter for each file to skip the ones that cannot hold the key.`,
  },
  {
    name: 'virtual-memory', title: 'Virtual memory on Atlas', topic: 'memory', era: '1960s',
    text: `Virtual memory first ran on the Atlas computer at the University of Manchester in 1962. Its designers called it a one level store: programs saw a single large memory, while the machine moved pages between fast core and a slower drum without being asked. Programmers no longer had to plan by hand which data lived where. Every modern operating system does the same thing, and the moment a page is not in memory, the program stops and waits for the disk. A demo memory limit is a small version of the same trade.`,
  },
  {
    name: 'page-replacement', title: 'Choosing what to evict', topic: 'memory', era: '1960s',
    text: `Page replacement decides what leaves memory when something new arrives. Laszlo Belady studied the problem at IBM in 1966 and showed that the best choice is the page needed furthest in the future, which no real system can know. Least recently used is the usual stand-in: assume that what has not been used lately will not be needed soon. A heap ordered by last use time finds that page quickly. The policy works because programs tend to reuse the same memory, and it fails badly on a long scan that touches each page once.`,
  },
  {
    name: 'working-sets', title: 'Working sets', topic: 'memory', era: '1960s',
    text: `Working sets describe the memory a program is really using at a given moment. Peter Denning proposed the model in 1968 to explain thrashing, the collapse that happens when programs keep evicting pages they are about to need. The machine spends its time on disk transfers and almost no time on useful work. The cure is to run fewer programs at once, so that each working set fits. The same pattern appears at any memory limit: set it below what the workload touches and every access pushes out something needed next.`,
  },
  {
    name: 'stacks', title: 'Stacks and undo', topic: 'memory', era: '1950s',
    text: `Stacks remember what happened last. Alan Turing described a way to bury and unbury return addresses in 1946, and Friedrich Bauer and Klaus Samelson patented a stack based design in 1957. Every function call in a modern program still pushes a frame onto a stack and pops it on return. Undo works the same way: each command pushes a note on how to reverse itself, and undo pops the newest note and runs it. Undoing one command from the middle of the history is harder, because the commands above it must stay in place.`,
  },
  {
    name: 'garbage-collection', title: 'Garbage collection', topic: 'memory', era: '1950s',
    text: `Garbage collection frees memory that a program can no longer reach. John McCarthy invented it for Lisp around 1959, because list programs made it hard to know when a cell was finished with. A collector starts from the stack and the global variables, marks everything it can reach, and sweeps away the rest. Later collectors split the heap by age, since most objects die young. Note that this heap has nothing to do with the heap data structure, and the two share a name only by accident.`,
  },
  {
    name: 'hash-tables', title: 'Hash tables', topic: 'search', era: '1950s',
    text: `Hash tables turn a key into a position with arithmetic, so a lookup takes about the same time however much is stored. Hans Peter Luhn wrote an IBM memo describing the idea in 1953, including chaining to handle keys that land on the same slot. A good hash function spreads keys evenly, and the table grows before its chains get long. The cost is order. A hash table can answer whether a key exists, but it cannot do prefix search or return keys in sorted order, and that is where trees and tries come back.`,
  },
  {
    name: 'bloom-filters', title: 'Bloom filters', topic: 'search', era: '1970s',
    text: `Bloom filters answer whether a key might be present using a few bits per key. Burton Bloom published the idea in 1970 for a hyphenation dictionary too large for memory. Several hash functions each set one bit, and a lookup checks those same bits. A clear bit proves the key is absent, while all set bits only suggest it is there, so a filter can return a false positive but never a false negative. Databases keep one in memory to avoid a disk read that would find nothing.`,
  },
  {
    name: 'inverted-indexes', title: 'Inverted indexes', topic: 'search', era: '1960s',
    text: `Inverted indexes flip a collection of documents around: instead of listing the words in each document, they list the documents that hold each word. Gerard Salton built the SMART retrieval system on this idea at Harvard and then Cornell in the 1960s, and Karen Sparck Jones showed in 1972 that rare words deserve more weight in ranking. A search then reads one short list per word rather than scanning any text. Every web search engine is built on this structure, and a trie that maps words to documents is a small one.`,
  },
];
