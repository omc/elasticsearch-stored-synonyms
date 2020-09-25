package io.bonsai.plugins.synonyms;

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;

/**
 * A synonym filter which branches to either the original input, or a SynonymGraphFilter. In the
 * event that synonyms change at runtime, a new SynonymGraphFilter is generated with a new synonym
 * map, and the filter will do a swap when reset is called.
 *
 * @author Dan Simpson
 */
public final class StoredSynonymFilter extends TokenFilter {

  private final boolean ignoreCase;
  private final TokenStream input;
  private TokenStream base;
  private TokenStream next;

  protected StoredSynonymFilter(
      StoredSynonymsService service,
      String name,
      Analyzer analyzer,
      TokenStream input,
      boolean ignoreCase) {
    super(input);

    this.input = input;
    this.ignoreCase = ignoreCase;

    // Listen for changes to the synonym rules
    service.listen(
        name,
        this,
        (rules) -> {
          updateSynonymMap(analyzer, rules);
        });

    // The callback above will invoke the callback immediately if synonyms are available
    if (next != null) {
      base = next;
    } else {
      base = input;
    }
  }

  private void updateSynonymMap(Analyzer analyzer, List<String> rules) {
    if (rules == null || rules.isEmpty()) {
      next = input;
      return;
    }

    try {
      // We set next since this filter could be in use, in some unknown state.  Wait for
      // the reset method to be called and do the swap there.
      next =
          new SynonymGraphFilter(
              input, StoredSynonyms.parseSynonymMap(analyzer, rules), ignoreCase);
    } catch (Throwable t) {
      next = input;
    }
  }

  public void end() throws IOException {
    base.end();
  }

  public void close() throws IOException {
    base.close();
  }

  public boolean incrementToken() throws IOException {
    return base.incrementToken();
  }

  /** Reset the delegated filter, optionally swapping it out if changes were detected. */
  public void reset() throws IOException {
    if (next != null) {
      base = next;
    }
    base.reset();
  }
}
