package edu.yu.cs.com1320.project.stage6;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.io.TempDir;

import edu.yu.cs.com1320.project.stage6.DocumentStore.DocumentFormat;
import edu.yu.cs.com1320.project.stage6.impl.DocumentStoreImpl;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.Set;



public class DocumentStoreImplTest {
    private URI uri;
    private DocumentStoreImpl docStore;

    @TempDir
    File baseDir;
    

    @BeforeEach
    void setUp() {
        this.uri = URI.create("http://site.com");
        this.docStore = new DocumentStoreImpl(this.baseDir);
    }

private static Stream<Arguments> invalidInputs_Get_Set_Metadata() {
    return Stream.of(
        Arguments.of(null, "validKey"),             
        Arguments.of(URI.create(""), "validKey"),   
        Arguments.of(URI.create("http://site.com"), null), 
        Arguments.of(URI.create("http://site.com"), "")    
    );
}

private static Stream<Arguments> invalidInputs_Put_Metadata() {
    return Stream.of(
        Arguments.of(null, DocumentFormat.TXT),
        Arguments.of(URI.create(""), DocumentFormat.BINARY),
        Arguments.of(URI.create("http://site.com"), null)
    );
}

@ParameterizedTest
@MethodSource("invalidInputs_Get_Set_Metadata")
void testSetMetadata(URI uri, String key) {
    assertThrows(IllegalArgumentException.class, () -> {
        docStore.setMetadata(uri, key, "value"); 
    });
}

@ParameterizedTest
@MethodSource("invalidInputs_Get_Set_Metadata")
void testGetMetadata(URI uri, String key) {
    assertThrows(IllegalArgumentException.class, () -> {
        docStore.getMetadata(uri, key);
    });
}

@Test
@DisplayName("undoing setMetadata should restore the previous value")
void test_Undo_Set_Metadata() throws Exception {
    String key = "Author";
    String originalValue = "Ariel";
    String newValue = "Hirsch";

    InputStream input = new ByteArrayInputStream("Test Document".getBytes());
    docStore.put(input, uri, DocumentFormat.TXT);
    docStore.setMetadata(uri, key, originalValue);
    docStore.setMetadata(uri, key, newValue); 
    assertEquals(newValue, docStore.getMetadata(uri, key), "Metadata should be updated to newValue.");
    docStore.undo();
    assertEquals(originalValue, docStore.getMetadata(uri, key), "Metadata should have reverted to originalValue after undo.");
}

@ParameterizedTest
@MethodSource("invalidInputs_Put_Metadata")
void testPut_Into_Storage_HashTable(URI uri, DocumentFormat format) {
    InputStream dummyInput = new ByteArrayInputStream("data".getBytes()); 
    assertThrows(IllegalArgumentException.class, () -> {
        docStore.put(dummyInput, uri, format); 
    }); 
}

@Test
void test_Undo_Put_Metadata_Null_Input() throws IOException {
    String input = "initial Text"; 
    InputStream dummyInput = new ByteArrayInputStream(input.getBytes()); 
    docStore.put(dummyInput, uri, DocumentFormat.TXT); 

    Document initialDoc =  docStore.get(uri);
    int expectedHash = initialDoc.hashCode();

    int returnedHash = docStore.put(null, uri, DocumentFormat.TXT);
    assertEquals(expectedHash, returnedHash, "deleting a doc should return hashCode of it"); 
    assertNull(docStore.get(uri), "Document should be null in storage after put(null)"); 
    docStore.undo();
    Document restoredDoc =  docStore.get(uri);

    assertNotNull(restoredDoc, "Document should not be null in storage after a undo()");
    assertEquals(initialDoc, restoredDoc);
    assertEquals(input, restoredDoc.getDocumentTxt());
}

@Test
void test_Undo_Put_Metadata_Null_Doc() throws IOException {
    InputStream dummyInput = new ByteArrayInputStream("data".getBytes()); 
    int result = docStore.put(dummyInput, uri, DocumentFormat.TXT); 

    assertEquals(0, result, "put of a new doc returns zero");
    assertNotNull(docStore.get(uri), "doc must be in the store");
    docStore.undo();
    assertNull(docStore.get(uri), "Document should be removed after undoing a new put");
}    


    

@Test
void testPut_Delete_Via_Null_Input() throws IOException {
    InputStream dummyInput = new ByteArrayInputStream("data".getBytes()); 
    docStore.put(dummyInput, uri, DocumentFormat.TXT);

    int expectedHash = docStore.get(uri).hashCode(); 
    int returnedHash = docStore.put(null, uri, DocumentFormat.TXT);
    assertEquals(expectedHash, returnedHash, "deleting should return the hashcode of the document being removed");
    
    assertNull(docStore.get(uri), "Document must be null after deletion");
}

@Test
void testPut_Delete_Returns_Zero() throws IOException {
    int result = docStore.put(null, uri, DocumentFormat.TXT);
    assertEquals(0, result, "should return 0 when deleting a null document"); 

}

@Test
void testPut_Binary_Document() throws IOException {
    byte[] expectedData = "data".getBytes();
     InputStream dummyInput = new ByteArrayInputStream(expectedData); 
     docStore.put(dummyInput, uri, DocumentFormat.BINARY);

     Document currentDoc = docStore.get(uri);
     assertArrayEquals(currentDoc.getDocumentBinaryData(), expectedData);

}

@Test
void testPut_Text_Document() throws IOException {
    InputStream txtStream = new ByteArrayInputStream("Sample Text".getBytes());

    docStore.put(txtStream, uri, DocumentFormat.TXT);

    Document currentDoc = docStore.get(uri);
    assertEquals("Sample Text", currentDoc.getDocumentTxt());
}

@Test
void testPut_Overwrite() throws IOException {
    String txtDoc1 = "Sample Text";
    InputStream textStream = new ByteArrayInputStream(txtDoc1.getBytes());

    String textDoc2 = "Sample Text 2";
    InputStream textStream2 = new ByteArrayInputStream(textDoc2.getBytes());

    int firstDoc = docStore.put(textStream, uri, DocumentFormat.TXT);
    assertEquals(0, firstDoc, "First 'put' at a URI should return 0");
    Document originalDoc = docStore.get(uri);
    int originalHash = originalDoc.hashCode();

    int returnedHash = docStore.put(textStream2, uri, DocumentFormat.TXT); 
    assertEquals(originalHash, returnedHash, "adding another document to HashTable with same URI should return same hashcode"); 
}

@Test
void test_Get_Document_Confirm_Exists() throws IOException {
    InputStream textStream = new ByteArrayInputStream("data".getBytes());
    docStore.put(textStream, uri, DocumentFormat.TXT);

    Document originalDoc = docStore.get(uri);
    assertSame(originalDoc, docStore.get(uri));
}

@Test
void test_Get_Document_When_Null() throws IOException {

    Document doc = docStore.get(uri);
    assertNull(doc, "calling 'get' on null URI must return null");

}

@Test
void test_Get_Document_When_Deleted() throws IOException {
    InputStream textStream = new ByteArrayInputStream("data".getBytes());
    docStore.put(textStream, uri, DocumentFormat.TXT);
    assertNotNull(docStore.get(uri));

    //deleting the doc by calling null Input
    docStore.put(null, uri, DocumentFormat.TXT);
    assertNull(docStore.get(uri), "deleting a document by putting in a null input should return null ");

    InputStream textStream2 = new ByteArrayInputStream("data2".getBytes());
    docStore.put(textStream2, uri, DocumentFormat.TXT);

    boolean deletedDoc = docStore.delete(uri);
    assertTrue(deletedDoc, "deleting a  non-null doc by calling 'delete' must return true");
    assertNull(docStore.get(uri));

}

@Test
void test_Delete_Exisiting_Document() throws IOException {
    InputStream textStream = new ByteArrayInputStream("data".getBytes());
    docStore.put(textStream, uri, DocumentFormat.BINARY);

    boolean deletedDoc = docStore.delete(uri);
    assertTrue(deletedDoc, "Deleting a document that is not null must return true");

}

@Test
void test_Delete_Null_Document() throws IOException {
    docStore.put(null, uri, DocumentFormat.BINARY);
    boolean deletedDoc = docStore.delete(uri);

    assertFalse(deletedDoc, "Deleting a document that is null must return false");
}

// ===== undo(URI) — tests GenericCommand targeted undo =====

@Test
void test_Undo_URI_Specific_Reverts_Correct_Action() throws IOException {
    URI uri2 = URI.create("http://other.com");
    docStore.put(new ByteArrayInputStream("doc1".getBytes()), uri, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("doc2".getBytes()), uri2, DocumentFormat.TXT);
    docStore.undo(uri);
    assertNull(docStore.get(uri), "uri should be undone");
    assertNotNull(docStore.get(uri2), "uri2 should be unaffected");
}

@Test
void test_Undo_EmptyStack_ThrowsIllegalState() {
    assertThrows(IllegalStateException.class, () -> docStore.undo());
}

@Test
void test_Undo_URI_NotOnStack_ThrowsIllegalState() throws IOException {
    docStore.put(new ByteArrayInputStream("data".getBytes()), uri, DocumentFormat.TXT);
    URI missing = URI.create("http://missing.com");
    assertThrows(IllegalStateException.class, () -> docStore.undo(missing));
}

// ===== search =====

@Test
void search_ReturnsDocsSortedByWordCount() throws IOException {
    URI uri2 = URI.create("http://site2.com");
    docStore.put(new ByteArrayInputStream("hello hello hello".getBytes()), uri, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("hello".getBytes()), uri2, DocumentFormat.TXT);
    List<Document> results = docStore.search("hello");
    assertEquals(2, results.size());
    assertEquals(docStore.get(uri), results.get(0));
}

@Test
void search_KeywordNotPresent_ReturnsEmptyList() throws IOException {
    docStore.put(new ByteArrayInputStream("hello world".getBytes()), uri, DocumentFormat.TXT);
    assertTrue(docStore.search("nothere").isEmpty());
}

@Test
void search_NullKeyword_ReturnsEmptyList() {
    assertTrue(docStore.search(null).isEmpty());
}

// ===== searchByPrefix =====

@Test
void searchByPrefix_ReturnsDocsWithMatchingPrefix() throws IOException {
    URI uri2 = URI.create("http://site2.com");
    docStore.put(new ByteArrayInputStream("application apple".getBytes()), uri, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("banana".getBytes()), uri2, DocumentFormat.TXT);
    List<Document> results = docStore.searchByPrefix("app");
    assertEquals(1, results.size());
    assertEquals(docStore.get(uri), results.get(0));
}

@Test
void searchByPrefix_NullPrefix_ReturnsEmptyList() {
    assertTrue(docStore.searchByPrefix(null).isEmpty());
}

// ===== deleteAll — tests CommandSet undo for bulk deletes =====

@Test
void deleteAll_DeletesAllDocsWithKeyword() throws IOException {
    URI uri2 = URI.create("http://site2.com");
    docStore.put(new ByteArrayInputStream("hello world".getBytes()), uri, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("hello there".getBytes()), uri2, DocumentFormat.TXT);
    Set<URI> deleted = docStore.deleteAll("hello");
    assertEquals(2, deleted.size());
    assertNull(docStore.get(uri));
    assertNull(docStore.get(uri2));
}

@Test
void deleteAll_CanBeUndone_CommandSetUndo() throws IOException {
    docStore.put(new ByteArrayInputStream("hello world".getBytes()), uri, DocumentFormat.TXT);
    docStore.deleteAll("hello");
    docStore.undo(); // undoes the CommandSet
    assertNotNull(docStore.get(uri));
}

@Test
void deleteAll_KeywordNotPresent_ReturnsEmptySet() throws IOException {
    docStore.put(new ByteArrayInputStream("hello world".getBytes()), uri, DocumentFormat.TXT);
    assertTrue(docStore.deleteAll("nothere").isEmpty());
}

// ===== deleteAllWithPrefix =====

@Test
void deleteAllWithPrefix_DeletesAllDocsMatchingPrefix() throws IOException {
    URI uri2 = URI.create("http://site2.com");
    URI uri3 = URI.create("http://site3.com");
    docStore.put(new ByteArrayInputStream("apple".getBytes()), uri, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("application".getBytes()), uri2, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("banana".getBytes()), uri3, DocumentFormat.TXT);
    Set<URI> deleted = docStore.deleteAllWithPrefix("app");
    assertEquals(2, deleted.size());
    assertNull(docStore.get(uri));
    assertNull(docStore.get(uri2));
    assertNotNull(docStore.get(uri3));
}

@Test
void deleteAllWithPrefix_CanBeUndone_CommandSetUndo() throws IOException {
    docStore.put(new ByteArrayInputStream("apple".getBytes()), uri, DocumentFormat.TXT);
    docStore.deleteAllWithPrefix("app");
    docStore.undo();
    assertNotNull(docStore.get(uri));
}

// ===== searchByMetadata =====

@Test
void searchByMetadata_ReturnsMatchingDocs() throws IOException {
    URI uri2 = URI.create("http://site2.com");
    docStore.put(new ByteArrayInputStream("text".getBytes()), uri, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("text2".getBytes()), uri2, DocumentFormat.TXT);
    docStore.setMetadata(uri, "author", "Alice");
    docStore.setMetadata(uri2, "author", "Bob");
    List<Document> results = docStore.searchByMetadata(Map.of("author", "Alice"));
    assertEquals(1, results.size());
    assertEquals(docStore.get(uri), results.get(0));
}

@Test
void searchByMetadata_NoMatch_ReturnsEmptyList() throws IOException {
    docStore.put(new ByteArrayInputStream("text".getBytes()), uri, DocumentFormat.TXT);
    docStore.setMetadata(uri, "author", "Alice");
    assertTrue(docStore.searchByMetadata(Map.of("author", "Bob")).isEmpty());
}

// ===== searchByKeywordAndMetadata =====

@Test
void searchByKeywordAndMetadata_ReturnsOnlyMatchingDocs() throws IOException {
    URI uri2 = URI.create("http://site2.com");
    docStore.put(new ByteArrayInputStream("hello world".getBytes()), uri, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("hello there".getBytes()), uri2, DocumentFormat.TXT);
    docStore.setMetadata(uri, "lang", "en");
    docStore.setMetadata(uri2, "lang", "fr");
    List<Document> results = docStore.searchByKeywordAndMetadata("hello", Map.of("lang", "en"));
    assertEquals(1, results.size());
    assertEquals(docStore.get(uri), results.get(0));
}

// ===== searchByPrefixAndMetadata =====

@Test
void searchByPrefixAndMetadata_ReturnsOnlyMatchingDocs() throws IOException {
    URI uri2 = URI.create("http://site2.com");
    docStore.put(new ByteArrayInputStream("application".getBytes()), uri, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("apple".getBytes()), uri2, DocumentFormat.TXT);
    docStore.setMetadata(uri, "type", "tech");
    docStore.setMetadata(uri2, "type", "food");
    List<Document> results = docStore.searchByPrefixAndMetadata("app", Map.of("type", "tech"));
    assertEquals(1, results.size());
    assertEquals(docStore.get(uri), results.get(0));
}

// ===== deleteAllWithMetadata =====

@Test
void deleteAllWithMetadata_DeletesMatchingDocs() throws IOException {
    URI uri2 = URI.create("http://site2.com");
    docStore.put(new ByteArrayInputStream("text1".getBytes()), uri, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("text2".getBytes()), uri2, DocumentFormat.TXT);
    docStore.setMetadata(uri, "author", "Alice");
    docStore.setMetadata(uri2, "author", "Bob");
    Set<URI> deleted = docStore.deleteAllWithMetadata(Map.of("author", "Alice"));
    assertEquals(1, deleted.size());
    assertNull(docStore.get(uri));
    assertNotNull(docStore.get(uri2));
}

// ===== deleteAllWithKeywordAndMetadata =====

@Test
void deleteAllWithKeywordAndMetadata_DeletesMatchingDocs() throws IOException {
    URI uri2 = URI.create("http://site2.com");
    docStore.put(new ByteArrayInputStream("hello world".getBytes()), uri, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("hello there".getBytes()), uri2, DocumentFormat.TXT);
    docStore.setMetadata(uri, "lang", "en");
    docStore.setMetadata(uri2, "lang", "fr");
    Set<URI> deleted = docStore.deleteAllWithKeywordAndMetadata("hello", Map.of("lang", "en"));
    assertEquals(1, deleted.size());
    assertNull(docStore.get(uri));
    assertNotNull(docStore.get(uri2));
}

// ===== deleteAllWithPrefixAndMetadata =====

@Test
void deleteAllWithPrefixAndMetadata_DeletesMatchingDocs() throws IOException {
    URI uri2 = URI.create("http://site2.com");
    docStore.put(new ByteArrayInputStream("apple".getBytes()), uri, DocumentFormat.TXT);
    docStore.put(new ByteArrayInputStream("application".getBytes()), uri2, DocumentFormat.TXT);
    docStore.setMetadata(uri, "type", "fruit");
    docStore.setMetadata(uri2, "type", "tech");
    Set<URI> deleted = docStore.deleteAllWithPrefixAndMetadata("app", Map.of("type", "tech"));
    assertEquals(1, deleted.size());
    assertNull(docStore.get(uri2));
    assertNotNull(docStore.get(uri));
}

@Test
void test_setMaxDocumentCount() throws IOException {
    docStore.setMaxDocumentCount(2);
    InputStream input = new ByteArrayInputStream("Test Document".getBytes());
    docStore.put(input, uri, DocumentFormat.TXT);

    URI uri2 = URI.create("http://site2.com");
    InputStream input2 = new ByteArrayInputStream("Test Document2".getBytes());
    docStore.put(input2, uri2, DocumentFormat.TXT);

    URI uri3 = URI.create("http://site3.com");
    InputStream input3 = new ByteArrayInputStream("Test Document3".getBytes());
    docStore.put(input3, uri3, DocumentFormat.TXT);

    assertNotNull(docStore.get(uri), "a document pushed out of RAM should still be readable from disk");
    assertNotNull(docStore.get(uri2));
    assertNotNull(docStore.get(uri3));

}

@Test
void test_setMaxDocumentBytes() throws IOException {
    docStore.setMaxDocumentBytes(20);
    
    byte[] binaryData1 = new byte[10];
    InputStream input1 = new ByteArrayInputStream(binaryData1);
    docStore.put(input1, uri, DocumentFormat.TXT);

    URI uri2 = URI.create("http://site2.com");
    InputStream input2 = new ByteArrayInputStream("Hello".getBytes());
    docStore.put(input2, uri2, DocumentFormat.TXT);

    URI uri3 = URI.create("http://site3.com");
    InputStream input3 = new ByteArrayInputStream("Test Document3".getBytes());
    docStore.put(input3, uri3, DocumentFormat.TXT);

    assertNotNull(docStore.get(uri), "a document pushed out of RAM should still be readable from disk");
    assertNotNull(docStore.get(uri2));
    assertNotNull(docStore.get(uri3));


}

}