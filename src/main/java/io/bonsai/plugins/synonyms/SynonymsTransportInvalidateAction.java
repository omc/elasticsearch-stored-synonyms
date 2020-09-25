package io.bonsai.plugins.synonyms;

import io.bonsai.plugins.synonyms.SynonymsTransportInvalidateAction.InvalidateNodeResponse;
import io.bonsai.plugins.synonyms.SynonymsTransportInvalidateAction.InvalidateRequest;
import io.bonsai.plugins.synonyms.SynonymsTransportInvalidateAction.InvalidateResponse;
import io.bonsai.plugins.synonyms.SynonymsTransportInvalidateAction.NodeRequest;
import java.io.IOException;
import java.util.List;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.ActionType;
import org.elasticsearch.action.FailedNodeException;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.nodes.BaseNodeRequest;
import org.elasticsearch.action.support.nodes.BaseNodeResponse;
import org.elasticsearch.action.support.nodes.BaseNodesRequest;
import org.elasticsearch.action.support.nodes.BaseNodesResponse;
import org.elasticsearch.action.support.nodes.NodesOperationRequestBuilder;
import org.elasticsearch.action.support.nodes.TransportNodesAction;
import org.elasticsearch.client.ClusterAdminClient;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.cluster.ClusterName;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNode;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;

/**
 * The invalidation action which informs nodes of changes, prompting a cache reload.
 *
 * @author Dan Simpson
 */
public class SynonymsTransportInvalidateAction
    extends TransportNodesAction<
        InvalidateRequest, InvalidateResponse, NodeRequest, InvalidateNodeResponse> {

  protected static class InvalidateAction extends ActionType<InvalidateResponse> {

    public static final InvalidateAction INSTANCE = new InvalidateAction();
    public static final String NAME = "cluster:admin/synonyms/invalidate";

    protected InvalidateAction() {
      super(NAME, InvalidateResponse::new);
    }

    public InvalidateRequestBuilder newRequestBuilder(final ElasticsearchClient client) {
      return new InvalidateRequestBuilder(client, this);
    }
  }

  protected static class InvalidateRequestBuilder
      extends NodesOperationRequestBuilder<
          InvalidateRequest, InvalidateResponse, InvalidateRequestBuilder> {

    public InvalidateRequestBuilder(final ClusterAdminClient client) {
      this(client, InvalidateAction.INSTANCE);
    }

    public InvalidateRequestBuilder(
        final ElasticsearchClient client, final InvalidateAction action) {
      super(client, action, new InvalidateRequest());
    }

    public InvalidateRequestBuilder setName(final String name) {
      request().setName(name);
      return this;
    }
  }

  protected static class InvalidateNodeResponse extends BaseNodeResponse {

    private boolean success;
    private String name;
    private String message;

    InvalidateNodeResponse(StreamInput in) throws IOException {
      super(in);
      this.success = in.readBoolean();
      this.name = in.readString();
      this.message = in.readOptionalString();
    }

    public InvalidateNodeResponse(
        final DiscoveryNode node, boolean success, String name, String message) {
      super(node);
      this.success = success;
      this.name = name;
      this.message = message;
    }

    public static InvalidateNodeResponse readNodeResponse(StreamInput in) throws IOException {
      InvalidateNodeResponse nodeResponse = new InvalidateNodeResponse(in);
      return nodeResponse;
    }

    public String getName() {
      return name;
    }

    public String getMessage() {
      return message;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
      super.writeTo(out);
      out.writeBoolean(success);
      out.writeString(name);
      out.writeOptionalString(message);
    }

    @Override
    public String toString() {
      return "InvalidateResponse [success="
          + success
          + " name="
          + name
          + ", message="
          + message
          + "]";
    }
  }

  protected static class InvalidateResponse extends BaseNodesResponse<InvalidateNodeResponse> {

    // Nothing to read
    public InvalidateResponse(final StreamInput in) throws IOException {
      super(in);
    }

    public InvalidateResponse(
        final ClusterName clusterName,
        List<InvalidateNodeResponse> nodes,
        List<FailedNodeException> failures) {
      super(clusterName, nodes, failures);
    }

    @Override
    public List<InvalidateNodeResponse> readNodesFrom(final StreamInput in) throws IOException {
      return in.readList(InvalidateNodeResponse::readNodeResponse);
    }

    @Override
    public void writeNodesTo(final StreamOutput out, List<InvalidateNodeResponse> nodes)
        throws IOException {
      out.writeList(nodes);
    }
  }

  protected static class InvalidateRequest extends BaseNodesRequest<InvalidateRequest> {

    private String name;

    public InvalidateRequest(String... nodesIds) {
      super(nodesIds);
    }

    public InvalidateRequest(final String name, String... nodesIds) {
      super(nodesIds);
      this.name = name;
    }

    public InvalidateRequest(StreamInput in) throws IOException {
      super(in);
      name = in.readString();
    }

    @Override
    public void writeTo(final StreamOutput out) throws IOException {
      super.writeTo(out);
      out.writeString(name);
    }

    public String getName() {
      return name;
    }

    public void setName(final String name) {
      this.name = name;
    }

    @Override
    public ActionRequestValidationException validate() {
      if (name == null || name.length() == 0) {
        return new ActionRequestValidationException();
      }
      return null;
    }
  }

  protected static class NodeRequest extends BaseNodeRequest {

    InvalidateRequest request;

    public NodeRequest() {}

    public NodeRequest(StreamInput in) throws IOException {
      super(in);
    }

    public NodeRequest(final InvalidateRequest request) {
      this.request = request;
    }

    @Override
    public void writeTo(final StreamOutput out) throws IOException {
      super.writeTo(out);
      request.writeTo(out);
    }
  }

  private final StoredSynonymsService service;

  @Inject
  public SynonymsTransportInvalidateAction(
      final Settings _settings,
      final ThreadPool threadPool,
      final ClusterService clusterService,
      final TransportService transportService,
      final StoredSynonymsService service,
      final ActionFilters actionFilters,
      final IndexNameExpressionResolver indexNameExpressionResolver) {

    super(
        InvalidateAction.NAME,
        threadPool,
        clusterService,
        transportService,
        actionFilters,
        InvalidateRequest::new,
        NodeRequest::new,
        ThreadPool.Names.MANAGEMENT,
        InvalidateNodeResponse.class);

    this.service = service;
  }

  @Override
  protected NodeRequest newNodeRequest(InvalidateRequest request) {
    return new NodeRequest(request);
  }

  @Override
  protected InvalidateNodeResponse newNodeResponse(StreamInput in) throws IOException {
    return new InvalidateNodeResponse(in);
  }

  @Override
  protected InvalidateResponse newResponse(
      InvalidateRequest request,
      List<InvalidateNodeResponse> responses,
      List<FailedNodeException> failures) {
    return new InvalidateResponse(this.clusterService.getClusterName(), responses, failures);
  }

  @Override
  protected InvalidateNodeResponse nodeOperation(final NodeRequest request) {
    String name = request.request.getName();

    try {
      service.reload(name);
    } catch (Exception e) {
      return new InvalidateNodeResponse(clusterService.localNode(), false, name, e.getMessage());
    }

    return new InvalidateNodeResponse(clusterService.localNode(), true, name, null);
  }

  protected boolean accumulateExceptions() {
    return false;
  }
}
