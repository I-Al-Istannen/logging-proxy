package de.ialistannen.loggingproxy;

import static java.util.stream.Collectors.joining;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

public class TextblockProcessor {

  private static final String METHOD_TEMPLATE_NON_VOID = """
    public {returnType} {name}({paramDecl}) {
      $testLogger$.onMethodCallStart("{name}"{paramNamesLog});
      {returnType} result = {receiver}.{name}({paramNamesForward});
      $testLogger$.onMethodReturn("{name}", result);
      return result;
    }
    """;
  private static final String METHOD_TEMPLATE_VOID = """
    public void {name}({paramDecl}) {
      $testLogger$.onMethodCallStart("{name}"{paramNamesLog});
      {receiver}.{name}({paramNamesForward});
      $testLogger$.onMethodReturn("{name}", null);
    }
    """;
  private static final String CONSTRUCTOR_TEMPLATE_WITH_INNER = """
    public {innerName}Proxy({loggerType} logger, {innerName} inner) {
      this.$testLogger$ = logger;
      this.$inner$ = inner;
    }
    """;
  private static final String CONSTRUCTOR_TEMPLATE_WITHOUT_INNER = """
    public {innerName}Proxy({loggerType} logger) {
      this.$testLogger$ = logger;
    }
    """;

  public String forType(TypeElement element, PackageElement packageOf) {
    String name = element.getSimpleName().toString() + "Proxy";
    String methods = getProxiedMethods(element).stream()
      .sorted(Comparator.comparing(ExecutableElement::getSimpleName, Comparator.comparing(Objects::toString)))
      .map(this::templateMethod)
      .collect(joining("\n"));
    String fields = getFields(element);
    String constructor = getConstructor(element);
    String imports = getImports();

    return """
      package {package};

      {imports}

      public class {name} {
      {fields}
            
      {constructor}
            
      {methods}
      }
      """
      .replace("{package}", packageOf.getQualifiedName())
      .replace("{imports}", imports)
      .replace("{name}", name)
      .replace("{methods}", methods.indent(2).stripTrailing())
      .replace("{fields}", fields.indent(2).stripTrailing())
      .replace("{constructor}", constructor.indent(2).stripTrailing());
  }

  private String getImports() {
    return "import {testLogger};".replace("{testLogger}", TestLogger.class.getName());
  }

  private List<ExecutableElement> getProxiedMethods(TypeElement element) {
    return element.getEnclosedElements().stream().filter(it -> it.getKind() == ElementKind.METHOD)
      .filter(it -> it.getAnnotation(GenerateTestProxy.ProxyMethod.class) != null)
      .map(it -> (ExecutableElement) it)
      .toList();
  }

  private String templateMethod(ExecutableElement element) {
    String methodTemplate = element.getReturnType().getKind() == TypeKind.VOID
      ? METHOD_TEMPLATE_VOID
      : METHOD_TEMPLATE_NON_VOID;

    String paramNames = element.getParameters()
      .stream()
      .map(it -> it.getSimpleName().toString())
      .collect(joining(", "));
    String paramDecl = element.getParameters()
      .stream()
      .map(it -> it.asType() + " " + it.getSimpleName())
      .collect(joining(", "));

    String receiver = element.getModifiers().contains(Modifier.STATIC)
      ? element.getEnclosingElement().getSimpleName().toString()
      : "$inner$";

    methodTemplate = methodTemplate.replace("{name}", element.getSimpleName())
      .replace("{returnType}", element.getReturnType().toString())
      .replace("{receiver}", receiver)
      .replace("{paramDecl}", paramDecl)
      .replace("{paramNamesForward}", paramNames)
      .replace("{paramNamesLog}", paramNames.isBlank() ? "" : ", " + paramNames);

    return methodTemplate;
  }

  private String getFields(TypeElement element) {
    String result = "final {loggerType} $testLogger$;".replace("{loggerType}", TestLogger.class.getSimpleName());

    if (proxiesOnlyStaticMethods(element)) {
      return result;
    }

    String innerField = "final {innerType} $inner$;".replace("{innerType}", element.getSimpleName());
    return result + "\n" + innerField;
  }

  private boolean proxiesOnlyStaticMethods(TypeElement element) {
    return getProxiedMethods(element).stream().allMatch(it -> it.getModifiers().contains(Modifier.STATIC));
  }

  private String getConstructor(TypeElement element) {
    String template = proxiesOnlyStaticMethods(element)
      ? CONSTRUCTOR_TEMPLATE_WITHOUT_INNER
      : CONSTRUCTOR_TEMPLATE_WITH_INNER;
    return template
      .replace("{innerName}", element.getSimpleName())
      .replace("{loggerType}", TestLogger.class.getSimpleName());
  }

}
