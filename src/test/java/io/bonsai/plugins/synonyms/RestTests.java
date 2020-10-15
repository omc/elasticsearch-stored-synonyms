package io.bonsai.plugins.synonyms;

import io.bonsai.plugins.synonyms.support.BaseClusterTest;
import java.io.IOException;
import org.codelibs.curl.CurlResponse;
import org.codelibs.elasticsearch.runner.net.EcrCurl;
import org.junit.Assert;
import org.junit.Test;

public class RestTests extends BaseClusterTest {

  @Test(timeout = 45000)
  public void testCrud() throws IOException {
    try (CurlResponse curlResponse =
        EcrCurl.put(cluster.node(), "/_stored_synonyms")
            .header("Content-Type", "application/json")
            .body(getResourceStr("files/simple_synonyms.json"))
            .execute()) {
      Assert.assertEquals(201, curlResponse.getHttpStatusCode());
    }

    cluster.ensureYellow(".stored_synonyms");

    try (CurlResponse curlResponse =
        EcrCurl.put(cluster.node(), "/_stored_synonyms")
            .header("Content-Type", "application/json")
            .body(getResourceStr("files/simple_synonyms.json"))
            .execute()) {
      Assert.assertEquals(200, curlResponse.getHttpStatusCode());
    }

    try (CurlResponse curlResponse =
        EcrCurl.get(cluster.node(), "/_stored_synonyms/greetings")
            .header("Content-Type", "application/json")
            .execute()) {
      Assert.assertEquals(200, curlResponse.getHttpStatusCode());
      StoredSynonyms synonyms =
          StoredSynonymsXContent.parse(curlResponse.getContentAsString().getBytes());
      Assert.assertEquals(3, synonyms.getRules().size());
    }

    try (CurlResponse curlResponse =
        EcrCurl.delete(cluster.node(), "/_stored_synonyms/greetings")
            .header("Content-Type", "application/json")
            .execute()) {
      Assert.assertEquals(200, curlResponse.getHttpStatusCode());
    }

    try (CurlResponse curlResponse =
        EcrCurl.get(cluster.node(), "/_stored_synonyms/greetings")
            .header("Content-Type", "application/json")
            .execute()) {
      Assert.assertEquals(404, curlResponse.getHttpStatusCode());
    }
  }

  @Test(timeout = 10000)
  public void testInvalidSynonyms() throws IOException {
    try (CurlResponse curlResponse =
        EcrCurl.put(cluster.node(), "/_stored_synonyms")
            .header("Content-Type", "application/json")
            .body(getResourceStr("files/invalid_synonyms.json"))
            .execute()) {
      Assert.assertEquals(422, curlResponse.getHttpStatusCode());
      Assert.assertTrue(curlResponse.getContentAsString().contains("No rules accepted"));
    }
  }
}
