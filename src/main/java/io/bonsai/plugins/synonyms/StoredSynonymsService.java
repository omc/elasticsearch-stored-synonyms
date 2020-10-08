package io.bonsai.plugins.synonyms;

import io.bonsai.plugins.synonyms.SynonymsTransportInvalidateAction.InvalidateAction;
import io.bonsai.plugins.synonyms.SynonymsTransportInvalidateAction.InvalidateResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthRequest;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.support.ActiveShardCount;
import org.elasticsearch.action.support.WriteRequest.RefreshPolicy;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.health.ClusterHealthStatus;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.bytes.BytesReference;
import org.elasticsearch.common.component.LifecycleListener;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.IndexNotFoundException;
import org.elasticsearch.rest.RestStatus;

/**
 * A service class responsible for handling operations from the HTTP side, as well as the internal
 * transport. Additionally, caches are stored here, and pulled from other places in the application.
 *
 * @author Dan Simpson
 */
public class StoredSynonymsService extends LifecycleListener implements ClusterStateListener {

  private static final String DOC_TYPE = "_doc";
  private static final Logger log = LogManager.getLogger(StoredSynonymsService.class);

  private final Client client;
  private final String indexName;

  private final AtomicBoolean indexExists = new AtomicBoolean(false);

  private final Map<String, StoredSynonyms> cache = new ConcurrentHashMap<>();
  private final Map<String, Map<Object, Consumer<List<String>>>> listeners =
      new ConcurrentHashMap<>();

  public StoredSynonymsService(Client client, ClusterService service, String indexName) {
    this.client = client;
    this.indexName = indexName;
    service.addListener(this);
    service.addLifecycleListener(this);
  }

  @Override
  public void afterStart() {
    new Thread(
            () -> {
              ClusterHealthResponse health = null;
              while (health == null
                  || health.isTimedOut()
                  || health.getStatus() == ClusterHealthStatus.RED) {
                log.info("Waiting for cluster health");
                try {
                  health =
                      client
                          .admin()
                          .cluster()
                          .health(new ClusterHealthRequest().waitForYellowStatus())
                          .actionGet(3000);
                } catch (Throwable error) {
                  try {
                    Thread.sleep(100);
                  } catch (Throwable timeout) {
                    break;
                  }
                }
              }
              reloadAll();
            })
        .start();
  }

  @Override
  public void clusterChanged(ClusterChangedEvent event) {
    event
        .indicesDeleted()
        .forEach(
            index -> {
              // The index was removed, clear the cache
              if (indexName.equals(index.getName())) {
                log.info("Index {} removed; clearing all synonym caches immediately", indexName);
                cache.clear();
              }
            });
  }

  public void store(StoredSynonyms set, ActionListener<IndexResponse> listener) {
    // Validate the passed synonym rules
    try {
      StoredSynonyms.validate(set);
    } catch (StoredSynonymsException error) {
      listener.onFailure(error);
      return;
    }

    indexDocument(
        set,
        ActionListener.wrap(
            (indexResponse) -> {
              invalidateSynonymSet(set.getName());
              listener.onResponse(indexResponse);
            },
            listener::onFailure));
  }

  public void delete(String collectionName, ActionListener<DeleteResponse> listener) {
    log.info("Removing synonym set {}", collectionName);
    deleteDocument(
        collectionName,
        ActionListener.wrap(
            (deleteResponse) -> {
              invalidateSynonymSet(collectionName);
              listener.onResponse(deleteResponse);
            },
            listener::onFailure));
  }

  public void get(String collectionName, ActionListener<StoredSynonyms> listener)
      throws IOException {
    client
        .prepareGet(indexName, DOC_TYPE, collectionName)
        .execute(
            ActionListener.wrap(
                (response) -> {
                  if (!response.isExists()) {
                    listener.onFailure(
                        new StoredSynonymsException(
                            String.format(
                                "StoredSynonym set not found with name %s", collectionName),
                            RestStatus.NOT_FOUND));
                    return;
                  }

                  try (XContentParser parser =
                      JsonXContent.jsonXContent.createParser(
                          NamedXContentRegistry.EMPTY,
                          LoggingDeprecationHandler.INSTANCE,
                          BytesReference.toBytes(response.getSourceAsBytesRef()))) {
                    listener.onResponse(StoredSynonymsXContent.parseSynonymSet(parser));
                  } catch (Exception exception) {
                    listener.onFailure(exception);
                  }
                },
                listener::onFailure));
  }

  private void invalidateSynonymSet(String name) {
    InvalidateResponse response =
        InvalidateAction.INSTANCE.newRequestBuilder(client).setTimeout("10s").setName(name).get();
    log.info("Invalidated {} on {} nodes", name, response.getNodes().size());
  }

  protected void reload(String name) throws Exception {
    GetResponse response = client.prepareGet(indexName, DOC_TYPE, name).get();

    // If the doc no longer exists, this reload is really a remove
    if (!response.isExists()) {
      removeCache(name);
      return;
    }

    try (XContentParser parser =
        JsonXContent.jsonXContent.createParser(
            NamedXContentRegistry.EMPTY,
            LoggingDeprecationHandler.INSTANCE,
            BytesReference.toBytes(response.getSourceAsBytesRef()))) {
      putCache(StoredSynonymsXContent.parseSynonymSet(parser));
    }
  }

  private void reloadAll() {
    SearchResponse response;
    try {
      response = client.prepareSearch(indexName).setSize(1000).get();
    } catch (IndexNotFoundException infe) {
      return;
    } catch (Exception error) {
      log.error("Failed to bulk load stored synonyms!", error);
      return;
    }

    response
        .getHits()
        .forEach(
            hit -> {
              try (XContentParser parser =
                  JsonXContent.jsonXContent.createParser(
                      NamedXContentRegistry.EMPTY,
                      LoggingDeprecationHandler.INSTANCE,
                      BytesReference.toBytes(hit.getSourceRef()))) {
                putCache(StoredSynonymsXContent.parseSynonymSet(parser));
              } catch (Exception error) {
                log.error("Cache load failure", error);
              }
            });
  }

  private void checkIndexExists(ActionListener<Boolean> listener) {
    if (indexExists.get()) {
      listener.onResponse(true);
      return;
    }

    client
        .admin()
        .indices()
        .prepareExists(indexName)
        .setMasterNodeTimeout(TimeValue.timeValueSeconds(30))
        .execute(
            ActionListener.wrap(
                (check) -> {
                  listener.onResponse(check.isExists());
                },
                listener::onFailure));
  }

  private void createIndex(ActionListener<CreateIndexResponse> listener) {
    checkIndexExists(
        ActionListener.wrap(
            (exists) -> {
              // Don't try to create the index
              if (exists) {
                if (!indexExists.get()) {
                  indexExists.set(true);
                }
                listener.onResponse(null);
                return;
              }

              Settings indexSettings =
                  Settings.builder()
                      .put(IndexMetaData.SETTING_NUMBER_OF_SHARDS, 1)
                      .put(IndexMetaData.SETTING_NUMBER_OF_REPLICAS, 1)
                      .build();

              client
                  .admin()
                  .indices()
                  .prepareCreate(indexName)
                  .setSettings(indexSettings)
                  .setWaitForActiveShards(ActiveShardCount.ONE)
                  .setTimeout(TimeValue.timeValueSeconds(30))
                  .addMapping(
                      DOC_TYPE,
                      "name",
                      "type=keyword,index=false",
                      "synonyms",
                      "type=binary,index=false")
                  .execute(listener);
            },
            listener::onFailure));
  }

  private void indexDocument(StoredSynonyms set, ActionListener<IndexResponse> listener) {
    createIndex(
        ActionListener.wrap(
            (available) -> {
              if (available != null) {
                log.info("Created SynonymStore index {}", indexName);
              }

              try {
                client
                    .prepareIndex(indexName, DOC_TYPE, set.getName())
                    .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
                    .setSource(
                        StoredSynonymsXContent.build(
                            set, XContentFactory.contentBuilder(XContentType.JSON)))
                    .execute(listener);
              } catch (IOException e) {
                listener.onFailure(e);
              }
            },
            listener::onFailure));
  }

  private void deleteDocument(String id, ActionListener<DeleteResponse> listener) {
    client
        .prepareDelete(indexName, DOC_TYPE, id)
        .setRefreshPolicy(RefreshPolicy.IMMEDIATE)
        .execute(listener);
  }

  private void putCache(StoredSynonyms synonyms) {
    log.info("Updating rules for {}", synonyms.getName());
    cache.put(synonyms.getName(), synonyms);
    invokeCallbacks(synonyms.getName(), synonyms.getRules());
  }

  private void removeCache(String name) {
    log.info("Removing rules for {}", name);
    cache.remove(name);
    invokeCallbacks(name, new ArrayList<>());
  }

  private void invokeCallbacks(String rulesetName, List<String> rules) {
    Map<Object, Consumer<List<String>>> callbacks = listeners.get(rulesetName);
    if (callbacks == null) {
      return;
    }
    callbacks.forEach(
        (filter, consumer) -> {
          consumer.accept(rules);
        });
  }

  public void listen(String rulesetName, Object object, Consumer<List<String>> handler) {
    listeners
        .computeIfAbsent(rulesetName, (key) -> new WeakHashMap<Object, Consumer<List<String>>>())
        .put(object, handler);

    StoredSynonyms cached = cache.get(rulesetName);
    if (cached != null) {
      handler.accept(cached.getRules());
    }
  }
}
