package io.bonsai.plugins.synonyms;

import org.junit.Test;

public class StoredSynonymsValidationTest {

  @Test(expected = StoredSynonymsException.class)
  public void testParseList() throws StoredSynonymsException {
    StoredSynonyms syn = new StoredSynonyms();
    syn.setName("test");
    syn.getRules().add("]");
    StoredSynonyms.validate(syn);
  }

  @Test(expected = StoredSynonymsException.class)
  public void testEmptyFst() throws StoredSynonymsException {
    StoredSynonyms syn = new StoredSynonyms();
    syn.setName("test");
    syn.getRules().add(",,,");
    StoredSynonyms.validate(syn);
  }

  @Test
  public void testValid() throws StoredSynonymsException {
    StoredSynonyms syn = new StoredSynonyms();
    syn.setName("test");
    syn.getRules().add("name,surname");
    StoredSynonyms.validate(syn);
  }
}
