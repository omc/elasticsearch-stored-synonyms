package io.bonsai.plugins.synonyms.rest;

import io.bonsai.plugins.synonyms.StoredSynonymsService;
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
 * Handler for removing stored synonyms by a given name
 *
 * @author Dan Simpson
 */
public class RestSynonymsDeleteAction extends BaseRestHandler {

  private StoredSynonymsService service;

  public RestSynonymsDeleteAction(
      Settings _settings, RestController controller, StoredSynonymsService service) {
    this.service = service;
  }

  @Override
  protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client)
      throws IOException {
    String name = request.param("collection_name");
    return (channel) -> {
      service.delete(name, new RestStatusToXContentListener<>(channel));
    };
  }

  public String getName() {
    return "delete_synonyms";
  }

  @Override
  public List<Route> routes() {
    return Collections.singletonList(
        new Route(Method.DELETE, "/_stored_synonyms/{collection_name}"));
  }
}
