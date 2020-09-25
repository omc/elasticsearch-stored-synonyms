package io.bonsai.plugins.synonyms;

import io.bonsai.plugins.synonyms.support.BaseClusterTest;
import java.io.IOException;
import org.junit.Test;

public class IndexTests extends BaseClusterTest {

  @Test
  public void testIndexCreationWithSearchAnalysis() throws IOException {
    createIndex(
        "test-index", "files/simple_index_settings.json", "files/simple_index_mappings.json");
    cluster.ensureYellow("test-index");
  }
}
