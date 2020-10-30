package io.bonsai.plugins.synonyms;

import io.bonsai.plugins.synonyms.support.BaseClusterTest;
import java.io.IOException;
import org.codelibs.curl.CurlResponse;
import org.codelibs.elasticsearch.runner.net.EcrCurl;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.junit.Assert;
import org.junit.Test;

public class AnalysisTests extends BaseClusterTest {

  @Test(timeout = 45000)
  public void testSearchAnalysis() throws IOException, InterruptedException {
    String index = "myindex";
    try (CurlResponse curlResponse =
        EcrCurl.put(cluster.node(), "/_stored_synonyms")
            .header("Content-Type", "application/json")
            .body(getResourceStr("files/simple_synonyms.json"))
            .execute()) {
      Assert.assertEquals(201, curlResponse.getHttpStatusCode());
    }

    createIndex(index, "files/simple_index_settings.json", "files/simple_index_mappings.json");
    cluster.ensureYellow();

    // Index some data
    cluster.insert(index, "1", "{ \"greeting\": \"hello\"}");
    cluster.insert(index, "2", "{ \"greeting\": \"goodbye\"}");
    cluster.insert(index, "3", "{ \"greeting\": \"danke\"}");
    cluster.refresh();

    SearchResponse response =
        cluster.search(
            "myindex",
            QueryBuilders.matchQuery("greeting", "hello"),
            SortBuilders.scoreSort(),
            0,
            10);
    Assert.assertEquals(1, response.getHits().getTotalHits().value);

    response =
        cluster.search(
            "myindex",
            QueryBuilders.matchQuery("greeting", "aloha"),
            SortBuilders.scoreSort(),
            0,
            10);
    Assert.assertEquals(2, response.getHits().getTotalHits().value);

    // Upload slight change and verify search results change
    try (CurlResponse curlResponse =
        EcrCurl.put(cluster.node(), "/_stored_synonyms")
            .header("Content-Type", "application/json")
            .body(getResourceStr("files/simple_synonyms_mod.json"))
            .execute()) {
      Assert.assertEquals(200, curlResponse.getHttpStatusCode());
    }

    response =
        cluster.search(
            "myindex",
            QueryBuilders.matchQuery("greeting", "aloha"),
            SortBuilders.scoreSort(),
            0,
            10);

    System.err.println("Pre assert after search");

    Assert.assertEquals(3, response.getHits().getTotalHits().value);
    Assert.assertEquals(3, response.getHits().getHits().length);

    // Remove the synonyms and ensure aloha no longer matches
    try (CurlResponse curlResponse =
        EcrCurl.delete(cluster.node(), "/_stored_synonyms/greetings")
            .header("Content-Type", "application/json")
            .execute()) {
      Assert.assertEquals(200, curlResponse.getHttpStatusCode());
    }

    response =
        cluster.search(
            "myindex",
            QueryBuilders.matchQuery("greeting", "aloha"),
            SortBuilders.scoreSort(),
            0,
            10);
    Assert.assertEquals(0, response.getHits().getTotalHits().value);
  }
}
