package io.bonsai.plugins.synonyms.support;

import java.io.IOException;
import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public class BaseClusterTest implements TestSupport {

  protected ElasticsearchClusterRunner cluster;

  @Before
  public void setUp() {
    cluster = new ElasticsearchClusterRunner();
    cluster.build(
        ElasticsearchClusterRunner.newConfigs()
            .numOfNode(1) // Create a test node, default number of node is 3.
            .pluginTypes("io.bonsai.plugins.synonyms.StoredSynonymsPlugin"));
  }

  @After
  public void tearDown() throws IOException {
    cluster.close();
    cluster.clean();
  }

  protected AcknowledgedResponse createIndex(String index, String settingsFile, String mappingsFile)
      throws IOException {
    Settings indexSettings =
        Settings.builder()
            .loadFromSource(new String(getResource(settingsFile)), XContentType.JSON)
            .build();

    AcknowledgedResponse response = cluster.createIndex(index, indexSettings);
    Assert.assertTrue(response.isAcknowledged());
    response = cluster.createMapping(index, new String(getResource(mappingsFile)));
    Assert.assertTrue(response.isAcknowledged());

    return response;
  }
}
