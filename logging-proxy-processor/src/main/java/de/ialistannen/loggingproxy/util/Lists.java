package de.ialistannen.loggingproxy.util;

import java.util.Arrays;
import java.util.List;

public class Lists {

  @SafeVarargs
  public static <T> List<T> of(T... values) {
    return Arrays.asList(values);
  }

}
