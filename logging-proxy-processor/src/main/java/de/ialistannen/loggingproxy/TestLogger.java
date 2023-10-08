package de.ialistannen.loggingproxy;

public interface TestLogger {

  void onMethodCallStart(String method, Object... arguments);

  void onMethodReturn(String method, Object returnValue);

}
