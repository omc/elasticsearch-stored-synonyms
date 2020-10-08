package io.bonsai.plugins.synonyms.rest;

import io.bonsai.plugins.synonyms.StoredSynonyms;
import io.bonsai.plugins.synonyms.StoredSynonymsService;
import io.bonsai.plugins.synonyms.StoredSynonymsXContent;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.action.RestStatusToXContentListener;

/**
 * Rest handler for adding or updating synonym sets.
 *
 * @author Dan Simpson
 */
public class RestSynonymsPutAction extends BaseRestHandler {

  private StoredSynonymsService service;

  public RestSynonymsPutAction(
      Settings _settings, RestController controller, StoredSynonymsService service) {
    this.service = service;
  }

  @Override
  protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client)
      throws IOException {
    StoredSynonyms synonyms = StoredSynonymsXContent.parseSynonymSet(request.contentParser());
    return (channel) -> {
      service.store(
          synonyms, new RestStatusToXContentListener<>(channel, r -> r.getLocation(null)));
    };
  }

  public String getName() {
    return "put_synonyms";
  }

  @Override
  public List<Route> routes() {
    return Collections.singletonList(new Route(Method.PUT, "/_stored_synonyms"));
  }
}
