package io.bonsai.plugins.synonyms;

import io.bonsai.plugins.synonyms.support.TestSupport;
import java.io.IOException;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.junit.Assert;
import org.junit.Test;

public class XContentTests implements TestSupport {

  @Test
  public void testParsing() throws IOException {
    StoredSynonyms synonyms =
        StoredSynonymsXContent.parse(getResource("files/simple_synonyms.json"));
    Assert.assertEquals("greetings", synonyms.getName());
    Assert.assertEquals(3, synonyms.getRules().size());
    Assert.assertArrayEquals(
        new String[] {"hello,aloha", "goodbye,aloha", "weee,aloha"},
        synonyms.getRules().toArray(new String[0]));
  }

  @Test
  public void testBuilding() throws IOException {
    StoredSynonyms synonyms =
        StoredSynonymsXContent.parse(getResource("files/simple_synonyms.json"));
    synonyms.setName("nawww");
    synonyms.getRules().remove(0);

    byte[] raw =
        BytesReference.toBytes(
            BytesReference.bytes(
                StoredSynonymsXContent.build(synonyms, JsonXContent.contentBuilder())));

    StoredSynonyms synonyms2 = StoredSynonymsXContent.parse(raw);
    Assert.assertEquals("nawww", synonyms2.getName());
    Assert.assertEquals(2, synonyms2.getRules().size());
  }
}
