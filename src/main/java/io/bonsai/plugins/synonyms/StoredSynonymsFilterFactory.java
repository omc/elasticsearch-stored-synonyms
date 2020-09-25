package io.bonsai.plugins.synonyms;

import java.util.List;
import java.util.function.Function;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;
import org.elasticsearch.index.analysis.AnalysisMode;
import org.elasticsearch.index.analysis.CharFilterFactory;
import org.elasticsearch.index.analysis.CustomAnalyzer;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;

/**
 * A synonyms filter factory which generates a special Synonym Filter which is capable of on-demand
 * updates.
 *
 * @author Dan Simpson
 */
public class StoredSynonymsFilterFactory extends AbstractTokenFilterFactory {

  private final String rulsetName;
  private final StoredSynonymsService service;
  private final Boolean ignoreCase;
  private final AnalysisMode analysisMode;

  public StoredSynonymsFilterFactory(
      IndexSettings indexSettings,
      Environment environment,
      String name,
      Settings settings,
      StoredSynonymsService service) {
    super(indexSettings, name, settings);

    this.rulsetName = settings.get("name");
    this.service = service;
    this.ignoreCase = settings.getAsBoolean("ignore_case", false);
    this.analysisMode = AnalysisMode.ALL;
  }

  @Override
  public AnalysisMode getAnalysisMode() {
    return analysisMode;
  }

  @Override
  public TokenStream create(TokenStream tokenStream) {
    throw new IllegalStateException(
        "Call createPerAnalyzerSynonymFactory to specialize this factory for an analysis chain first");
  }

  @Override
  public TokenFilterFactory getChainAwareTokenFilterFactory(
      TokenizerFactory tokenizer,
      List<CharFilterFactory> charFilters,
      List<TokenFilterFactory> previousTokenFilters,
      Function<String, TokenFilterFactory> allFilters) {

    final Analyzer analyzer =
        buildSynonymAnalyzer(tokenizer, charFilters, previousTokenFilters, allFilters);
    final String name = name();

    return new TokenFilterFactory() {

      @Override
      public String name() {
        return name;
      }

      @Override
      public TokenStream create(TokenStream tokenStream) {
        return new StoredSynonymFilter(service, rulsetName, analyzer, tokenStream, ignoreCase);
      }

      @Override
      public TokenFilterFactory getSynonymFilter() {
        // In order to allow chained synonym filters, we return IDENTITY here to
        // ensure that synonyms don't get applied to the synonym map itself,
        // which doesn't support stacked input tokens
        return IDENTITY_FILTER;
      }

      @Override
      public AnalysisMode getAnalysisMode() {
        return analysisMode;
      }
    };
  }

  private Analyzer buildSynonymAnalyzer(
      TokenizerFactory tokenizer,
      List<CharFilterFactory> charFilters,
      List<TokenFilterFactory> tokenFilters,
      Function<String, TokenFilterFactory> allFilters) {
    return new CustomAnalyzer(
        tokenizer,
        charFilters.toArray(new CharFilterFactory[0]),
        tokenFilters.stream()
            .map(TokenFilterFactory::getSynonymFilter)
            .toArray(TokenFilterFactory[]::new));
  }
}
