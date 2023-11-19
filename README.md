<div align="center">
  <h1>Logging Proxy</h1>
</div>

## What is this?
A small annotation processor (and example usage) that generates proxy classes
at compile-time using an annotation processor.

## Why create this?
This is useful if you load the actual implementation from somewhere else (e.g.
a student submission), but want a simple interface to program tests against.
If you just use your own implementation, the students will only receive the
assertion failures — but no history up to them.
This project automates generating a proxy at compile-time that traces all calls
made by the testing infrastructure and allows you to present the full call
history to students in any way you like.

## Use in your project
1. Add the jitpack repository
   ```xml
   <repositories>
     <repository>
       <id>jitpack.io</id>
       <url>https://jitpack.io</url>
     </repository>
   </repositories>
   ```
2. Add it as a depdendency
   ```xml
    <dependency>
      <groupId>com.github.I-Al-Istannen</groupId>
      <artifactId>logging-proxy</artifactId>
      <version>6880313241395d6350518d0b2b15d5ee068b20e9</version>
    </dependency>
   ```
3. Configure your maven compiler plugin to use the processor
   ```xml
   <plugin>
     <groupId>org.apache.maven.plugins</groupId>
     <artifactId>maven-compiler-plugin</artifactId>
     <configuration>
       <annotationProcessorPaths>
         <dependency>
           <groupId>com.github.I-Al-Istannen</groupId>
           <artifactId>logging-proxy</artifactId>
           <version>6880313241395d6350518d0b2b15d5ee068b20e9</version>
         </dependency>
       </annotationProcessorPaths>
     </configuration>
   </plugin>
   ```

## Example
Consider the following simple shim (or real implementation) for a student task.
```java
@GenerateTestProxy
public class Calculator {
  @ProxyMethod
  public int add(int a, int b) {
    return a + b;
  }

  public void notLogged() {} // Calls are not logged. Maybe useful for setup methods.

  @ProxyMethod
  public static int addTwo(int a, int b) {
    return a + b;
  }
}
```

<br>
These annotations are enough to trigger generation of the following proxy class.

```java
package de.ialistannen.loggingproxy.example;
import de.ialistannen.loggingproxy.TestLogger;
class CalculatorProxy {
    public int add(int a, int b) {
        $testLogger$.onMethodCallStart("add", a, b);
        int result = $inner$.add(a, b);
        $testLogger$.onMethodReturn("add", result);
        return result;
    }

    public int addStatic(int a, int b) {
        $testLogger$.onMethodCallStart("addTwo", a, b);
        int result = Calculator.addTwo(a, b);
        $testLogger$.onMethodReturn("addTwo", result);
        return result;
    }

    TestLogger $testLogger$;

    public CalculatorProxy(TestLogger logger, Calculator inner) {
        $testLogger$ = logger;
        $inner$ = inner;
    }

    Calculator $inner$;
}
```
<br>

This can then easily be used to test the calculator and provide feedback, for
example using property based testing:
```java
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
```
