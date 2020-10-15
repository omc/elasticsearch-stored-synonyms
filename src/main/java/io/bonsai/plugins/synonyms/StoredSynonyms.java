package io.bonsai.plugins.synonyms;

import java.io.IOException;
import java.io.StringReader;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.elasticsearch.rest.RestStatus;

/**
 * A named collection of synonym rules with cached factory methods for lucene synonym structures.
 *
 * @author Dan Simpson
 */
public class StoredSynonyms {

  private String name;
  private List<String> rules = new ArrayList<>();

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public List<String> getRules() {
    return rules;
  }

  public void setRules(List<String> synonyms) {
    this.rules = synonyms;
  }

  /**
   * Parse the synonyms input to create a new SynonymMap
   *
   * @param analyzer - the analyzer
   * @param rules - the list of synonym rules
   * @return
   * @throws IOException
   * @throws ParseException
   */
  public static SynonymMap parseSynonymMap(Analyzer analyzer, List<String> rules)
      throws IOException, ParseException {
    StringBuilder sb = new StringBuilder();
    for (String line : rules) {
      sb.append(line).append(System.lineSeparator());
    }
    SolrSynonymParser parser = new SolrSynonymParser(true, true, analyzer);
    parser.parse(new StringReader(sb.toString()));
    return parser.build();
  }

  /**
   * Validate a StoredSynonyms object by constructing a synonym map with a simple analyzer.
   *
   * @param synonyms
   */
  public static void validate(StoredSynonyms synonyms) {
    SynonymMap map;
    try {
      map = parseSynonymMap(new WhitespaceAnalyzer(), synonyms.getRules());
    } catch (Exception error) {
      throw new StoredSynonymsException(error, RestStatus.UNPROCESSABLE_ENTITY);
    }

    if (map.fst == null) {
      throw new StoredSynonymsException(
          "No rules accepted by the FST! Check your synonym rule syntax!",
          RestStatus.UNPROCESSABLE_ENTITY);
    }
  }

  public static Optional<SynonymMap> parseSynonymMapSafe(
      Analyzer analyzer, List<String> synonymRules) {
    try {
      return Optional.of(parseSynonymMap(analyzer, synonymRules));
    } catch (Throwable t) {
      return Optional.empty();
    }
  }
}
