package de.ialistannen.loggingproxy.example;

import static org.assertj.core.api.Assertions.assertThat;

import de.ialistannen.loggingproxy.TestLogger;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;

public class TestProxy {

  @Property(tries = 5)
  void test(@ForAll int a, @ForAll int b) {
    CalculatorProxy proxy = new CalculatorProxy(new MyTestLogger(), new Calculator());
    assertThat(proxy.add(a, b)).isEqualTo(a + b);
  }

  @Property(tries = 5)
  void testStatic(@ForAll int a, @ForAll int b) {
    CalculatorProxy proxy = new CalculatorProxy(new MyTestLogger(), new Calculator());
    assertThat(proxy.addStatic(a, b)).isEqualTo(a + b);
  }

  private static class MyTestLogger implements TestLogger {

    @Override
    public void onMethodCallStart(String method, Object... arguments) {
      String args = Arrays.stream(arguments)
        .map(Objects::toString)
        .collect(Collectors.joining(", ", "(", ")"));
      System.out.println("---> " + method + args);
    }

    @Override
    public void onMethodReturn(String method, Object returnValue) {
      System.out.println("<--- " + returnValue);
    }
  }
}
