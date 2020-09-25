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
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.RestActionListener;

/**
 * Handler for retrieving stored synonyms by a given name
 *
 * @author Dan Simpson
 */
public class RestSynonymsGetAction extends BaseRestHandler {

  private StoredSynonymsService service;

  public RestSynonymsGetAction(
      Settings _settings, RestController controller, StoredSynonymsService service) {
    this.service = service;
  }

  @Override
  protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client)
      throws IOException {
    String name = request.param("collection_name");

    return (channel) -> {
      service.get(
          name,
          new RestActionListener<StoredSynonyms>(channel) {

            @Override
            protected void processResponse(StoredSynonyms synonyms) throws Exception {
              channel.sendResponse(
                  new BytesRestResponse(
                      RestStatus.OK,
                      StoredSynonymsXContent.build(
                          synonyms, channel.newBuilder(request.getXContentType(), false))));
            }
          });
    };
  }

  public String getName() {
    return "get_synonyms";
  }

  @Override
  public List<Route> routes() {
    return Collections.singletonList(new Route(Method.GET, "/_stored_synonyms/{collection_name}"));
  }
}
