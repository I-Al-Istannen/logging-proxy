package de.ialistannen.loggingproxy.example;

import de.ialistannen.loggingproxy.GenerateTestProxy;
import de.ialistannen.loggingproxy.GenerateTestProxy.ProxyMethod;

@GenerateTestProxy
public class Calculator {

  @ProxyMethod
  public int add(int a, int b) {
    return a + b;
  }

  public void notLogged() {
  }

  @ProxyMethod
  public static int addStatic(int a, int b) {
    return a + b;
  }
}
