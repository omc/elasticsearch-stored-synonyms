package io.bonsai.plugins.synonyms.support;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public interface TestSupport {

  public default byte[] getResource(String path) throws IOException {
    return Files.readAllBytes(
        new File(ClassLoader.getSystemClassLoader().getResource(path).getFile()).toPath());
  }

  public default String getResourceStr(String path) throws IOException {
    return new String(getResource(path));
  }
}
