/*
 * Copyright (C) One More Cloud, Inc - All Rights Reserved
 *
 * Unauthorized copying of this file, via any medium is strictly prohibited.
 *
 * Proprietary and confidential.
 *
 */
package io.bonsai.plugins.synonyms;

import io.bonsai.plugins.synonyms.SynonymsTransportInvalidateAction.InvalidateAction;
import io.bonsai.plugins.synonyms.rest.RestSynonymsDeleteAction;
import io.bonsai.plugins.synonyms.rest.RestSynonymsGetAction;
import io.bonsai.plugins.synonyms.rest.RestSynonymsPutAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.Environment;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.repositories.RepositoriesService;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.elasticsearch.script.ScriptService;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.watcher.ResourceWatcherService;

/**
 * A plugin which makes working with synonyms less painful.
 *
 * @author Dan Simpson
 */
public class StoredSynonymsPlugin extends Plugin implements AnalysisPlugin, ActionPlugin {

  private StoredSynonymsService service;

  @Override
  public Collection<Object> createComponents(
      Client client,
      ClusterService clusterService,
      ThreadPool threadPool,
      ResourceWatcherService resourceWatcherService,
      ScriptService scriptService,
      NamedXContentRegistry xContentRegistry,
      Environment environment,
      NodeEnvironment nodeEnvironment,
      NamedWriteableRegistry namedWriteableRegistry,
      IndexNameExpressionResolver indexNameExpressionResolver,
      Supplier<RepositoriesService> repositoriesServiceSupplier) {

    // Set up the synonym service
    service = new StoredSynonymsService(client, clusterService, ".stored_synonyms");

    List<Object> components = new ArrayList<>();
    components.add(service);

    return components;
  }

  @Override
  public Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
    Map<String, AnalysisModule.AnalysisProvider<TokenFilterFactory>> extra = new HashMap<>();
    extra.put(
        "stored_synonyms",
        (indexSettings, environment, name, settings) -> {
          return new StoredSynonymsFilterFactory(
              indexSettings, environment, name, settings, service);
        });
    return extra;
  }

  @Override
  public List<RestHandler> getRestHandlers(
      Settings settings,
      RestController restController,
      ClusterSettings clusterSettings,
      IndexScopedSettings indexScopedSettings,
      SettingsFilter settingsFilter,
      IndexNameExpressionResolver indexNameExpressionResolver,
      Supplier<DiscoveryNodes> nodesInCluster) {
    return Arrays.asList(
        new RestHandler[] {
          new RestSynonymsGetAction(settings, restController, service),
          new RestSynonymsPutAction(settings, restController, service),
          new RestSynonymsDeleteAction(settings, restController, service)
        });
  }

  public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
    return Arrays.asList(
        new ActionHandler<?, ?>[] {
          new ActionHandler<>(InvalidateAction.INSTANCE, SynonymsTransportInvalidateAction.class)
        });
  }
}
