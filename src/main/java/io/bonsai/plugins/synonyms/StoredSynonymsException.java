package io.bonsai.plugins.synonyms;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.rest.RestStatus;

/**
 * Error wrapper with RestStatus
 *
 * @author Dan Simpson
 */
public class StoredSynonymsException extends ElasticsearchException {

  private static final long serialVersionUID = -8644601360701645644L;
  private final RestStatus status;

  public StoredSynonymsException(String message) {
    this(message, RestStatus.BAD_REQUEST);
  }

  public StoredSynonymsException(String message, RestStatus status) {
    super(message);
    this.status = status;
  }

  public StoredSynonymsException(String message, Exception base, RestStatus status) {
    super(message, base);
    this.status = status;
  }

  public StoredSynonymsException(Exception base, RestStatus status) {
    super(base);
    this.status = status;
  }

  @Override
  public RestStatus status() {
    return status;
  }
}
