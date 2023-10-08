package de.ialistannen.loggingproxy;

import de.ialistannen.loggingproxy.GenerateTestProxy.ProxyMethod;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtReturn;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.factory.Factory;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.CtScanner;

@SuppressWarnings({"unchecked", "rawtypes"})
public class ProxyGenerator {

  private final CtType<?> original;
  private final CtClass<?> proxy;
  private final Factory factory;

  public ProxyGenerator(CtType<?> original) {
    this.original = original;
    this.factory = original.getFactory();

    this.proxy = this.factory.createClass(original.getQualifiedName() + "Proxy");
  }

  public CtType<?> generateProxy() {
    this.addProxyFields();
    this.addConstructor();
    this.addProxyMethods();
    this.insertLogCalls();
    this.stripProxyAnnotations();

    return proxy;
  }

  private void addProxyFields() {
    factory.createField(
      proxy, Set.of(), factory.createCtTypeReference(TestLogger.class), "$testLogger$"
    );
  }

  private CtExpression<?> readProxyField(String name) {
    return ((CtFieldRead) (factory.createFieldRead())
      .setTarget(createThisAccess()))
      .setVariable(proxy.getField(name).getReference());
  }

  private CtThisAccess<?> createThisAccess() {
    return factory.createThisAccess(proxy.getReference(), true);
  }

  private void addProxyMethods() {
    boolean addedInnerField = false;
    for (CtMethod<?> method : original.getAllMethods()) {
      if (method.getDeclaringType().getReference().equals(factory.Type().OBJECT)) {
        continue;
      }
      if (method.isPrivate()) {
        continue;
      }

      if (!method.isStatic() && !addedInnerField) {
        addInnerField();
        addedInnerField = true;
      }

      proxy.addMethod(createProxyMethod(method));
    }
  }

  private void addConstructor() {
    CtConstructor constructor = factory.createConstructor(
      proxy,
      Set.of(ModifierKind.PUBLIC),
      List.of(),
      Set.of()
    );
    constructor.setBody(factory.createBlock());

    CtParameter loggerParam = factory.createParameter(
      constructor,
      factory.createCtTypeReference(TestLogger.class),
      "logger"
    );

    constructor.getBody().addStatement(createParameterFieldAssignment("$testLogger$", loggerParam));
  }

  private void addInnerField() {
    factory.createField(
      proxy, Set.of(), original.getReference(), "$inner$"
    );

    CtConstructor<?> constructor = proxy.getConstructors().iterator().next();

    CtParameter innerParam = factory.createParameter(
      constructor,
      original.getReference(),
      "inner"
    );
    constructor.getBody().addStatement(createParameterFieldAssignment("$inner$", innerParam));
  }

  private CtAssignment createParameterFieldAssignment(String fieldName, CtParameter parameter) {
    return (CtAssignment) factory.createAssignment()
      .setAssigned(
        ((CtFieldWrite) factory.createFieldWrite().setTarget(createThisAccess()))
          .setVariable(proxy.getField(fieldName).getReference())
      )
      .setAssignment(factory.createVariableRead(parameter.getReference(), false));
  }

  private CtMethod<?> createProxyMethod(CtMethod<?> originalMethod) {
    CtMethod<?> newMethod = factory.createMethod(
      proxy, originalMethod, true
    );
    newMethod.setModifiers(
      newMethod.getModifiers().stream().filter(it -> it != ModifierKind.STATIC).collect(Collectors.toSet())
    );

    newMethod.setBody(factory.createBlock());
    List<CtExpression<?>> arguments = (List) newMethod.getParameters()
      .stream()
      .map(it -> factory.createVariableRead(it.getReference(), false))
      .toList();

    CtInvocation<?> effect;
    if (!originalMethod.isStatic()) {
      effect = factory.createInvocation(readProxyField("$inner$"), originalMethod.getReference(), arguments);
    } else {
      effect = factory.createInvocation(
        factory.createTypeAccess(original.getReference()),
        originalMethod.getReference(),
        arguments
      );
    }

    if (!newMethod.getType().equals(factory.Type().voidPrimitiveType())) {
      newMethod.getBody().addStatement(((CtReturn) factory.createReturn()).setReturnedExpression(effect));
    } else {
      newMethod.getBody().addStatement(factory.createReturn());
    }

    return newMethod;
  }

  private void insertLogCalls() {
    for (CtMethod<?> method : proxy.getMethods()) {
      if (!method.hasAnnotation(ProxyMethod.class)) {
        continue;
      }
      insertLogCalls(method);
    }
  }

  private void insertLogCalls(CtMethod<?> method) {
    List<CtExpression<?>> startArguments = new ArrayList<>();
    startArguments.add(factory.createLiteral(method.getSimpleName()));
    method.getParameters()
      .stream()
      .map(it -> factory.createVariableRead(it.getReference(), false))
      .forEach(startArguments::add);

    method.getBody().addStatement(
      0,
      factory.createInvocation(readProxyField("$testLogger$"), getLogStartMethodRef(), startArguments)
    );

    List<CtExpression<?>> endArguments = new ArrayList<>();
    endArguments.add(factory.createLiteral(method.getSimpleName()));

    if (!method.getType().equals(factory.Type().voidPrimitiveType())) {
      CtReturn ret = method.getBody().getLastStatement();
      CtLocalVariable resultVar = factory.createLocalVariable(
        ret.getReturnedExpression().getType(),
        "result",
        ret.getReturnedExpression()
      );
      method.getBody().addStatement(1, resultVar);
      ret.setReturnedExpression(factory.createVariableRead(resultVar.getReference(), false));

      endArguments.add(factory.createVariableRead(resultVar.getReference(), false));
    }

    method.getBody().addStatement(
      method.getBody().getStatements().size() - 1,
      factory.createInvocation(
        readProxyField("$testLogger$"),
        getLogEndMethodRef(),
        endArguments
      )
    );
  }

  private <T> CtExecutableReference<T> getLogStartMethodRef() {
    return (CtExecutableReference<T>) factory.Method().createReference(
      factory.createCtTypeReference(TestLogger.class),
      false,
      factory.Type().voidPrimitiveType(),
      "onMethodCallStart",
      factory.Type().stringType(), factory.createArrayReference(factory.Type().objectType())
    );
  }

  private <T> CtExecutableReference<T> getLogEndMethodRef() {
    return (CtExecutableReference<T>) factory.Method().createReference(
      factory.createCtTypeReference(TestLogger.class),
      false,
      factory.Type().voidPrimitiveType(),
      "onMethodReturn",
      factory.Type().stringType(), factory.Type().objectType()
    );
  }

  private void stripProxyAnnotations() {
    proxy.accept(new CtScanner() {
      @Override
      public <A extends Annotation> void visitCtAnnotation(CtAnnotation<A> annotation) {
        if (annotation.getAnnotationType().getQualifiedName().startsWith(GenerateTestProxy.class.getName())) {
          annotation.delete();
        }
      }
    });
  }
}
