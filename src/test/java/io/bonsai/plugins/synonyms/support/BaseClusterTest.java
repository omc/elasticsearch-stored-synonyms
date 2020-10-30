package io.bonsai.plugins.synonyms.support;

import java.io.IOException;
import org.codelibs.elasticsearch.runner.ElasticsearchClusterRunner;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.Settings.Builder;
import org.elasticsearch.common.xcontent.XContentType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;

public class BaseClusterTest implements TestSupport {

  protected ElasticsearchClusterRunner cluster;

  @Before
  public void setUp() {
    String clusterName = "es-cl-run-" + System.currentTimeMillis();
    cluster = new ElasticsearchClusterRunner();

    cluster.onBuild(
        new ElasticsearchClusterRunner.Builder() {
          @Override
          public void build(final int number, final Builder settingsBuilder) {
            settingsBuilder.put("http.cors.enabled", true);
            settingsBuilder.put("http.cors.allow-origin", "*");
            settingsBuilder.putList(
                "discovery.seed_hosts", "127.0.0.1:9300", "127.0.0.1:9301", "127.0.0.1:9302");
            settingsBuilder.putList("cluster.initial_master_nodes", "127.0.0.1:9300");
          }
        });

    cluster.build(
        ElasticsearchClusterRunner.newConfigs()
            .clusterName(clusterName)
            .useLogger()
            .disableESLogger()
            .useLogger()
            .numOfNode(3)
            .pluginTypes("io.bonsai.plugins.synonyms.StoredSynonymsPlugin"));

    cluster.ensureYellow();
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
