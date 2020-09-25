package io.bonsai.plugins.synonyms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.elasticsearch.common.ParsingException;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;

/**
 * XContent Parser/Generator for the StoredSynonyms model.
 *
 * @author Dan Simpson
 */
public class StoredSynonymsXContent {

  private static final String NAME_FIELD = "name";
  private static final String RULES_FIELD = "rules";

  public static StoredSynonyms parseSynonymSet(XContentParser parser) throws IOException {
    StoredSynonyms set = new StoredSynonyms();

    String fieldName = null;
    if (parser.currentToken() == null) {
      parser.nextToken();
    }

    if (parser.currentToken() != XContentParser.Token.START_OBJECT) {
      throw new ParsingException(parser.getTokenLocation(), "current token must be a start object");
    }

    XContentParser.Token token;
    while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
      if (token == XContentParser.Token.FIELD_NAME) {
        fieldName = parser.currentName();
      } else if (token.isValue()) {
        if (NAME_FIELD.equals(fieldName)) {
          set.setName(parser.text());
        } else {
          throw new ParsingException(
              parser.getTokenLocation(), "unexpected field [" + fieldName + "]");
        }
      } else if (token == XContentParser.Token.START_ARRAY) {

        if (RULES_FIELD.equals(fieldName)) {
          set.setRules(parseStringArray(parser));
        } else {
          throw new ParsingException(
              parser.getTokenLocation(),
              "unexpected token [" + token + "] for [" + fieldName + "]");
        }
      } else {
        throw new ParsingException(
            parser.getTokenLocation(),
            "unexpected token [" + token + "] after [" + fieldName + "]");
      }
    }

    return set;
  }

  private static List<String> parseStringArray(XContentParser parser) throws IOException {
    XContentParser.Token token = parser.currentToken();
    if (token != XContentParser.Token.START_ARRAY) {
      throw new ParsingException(parser.getTokenLocation(), "current token must be a start object");
    }

    List<String> items = new ArrayList<>();
    while ((token = parser.nextToken()) != XContentParser.Token.END_ARRAY) {
      items.add(parser.text());
    }

    return items;
  }

  public static XContentBuilder build(StoredSynonyms synonymSet, final XContentBuilder builder)
      throws IOException {
    builder.startObject();
    builder.field(NAME_FIELD, synonymSet.getName());
    builder.field(RULES_FIELD, synonymSet.getRules());
    builder.endObject();
    return builder;
  }

  public static StoredSynonyms parse(byte[] bytes) throws IOException {
    try (XContentParser parser =
        JsonXContent.jsonXContent.createParser(
            NamedXContentRegistry.EMPTY, LoggingDeprecationHandler.INSTANCE, bytes)) {
      return parseSynonymSet(parser);
    }
  }
}
