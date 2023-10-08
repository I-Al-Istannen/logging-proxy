package de.ialistannen.loggingproxy;

import com.google.auto.service.AutoService;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.lang.reflect.Method;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.compiler.VirtualFile;

@SupportedAnnotationTypes("de.ialistannen.loggingproxy.GenerateTestProxy")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class ProcessorMain extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      for (Element element : roundEnv.getElementsAnnotatedWith(GenerateTestProxy.class)) {
        Trees trees = Trees.instance(jbUnwrap(ProcessingEnvironment.class, processingEnv));
        TreePath path = trees.getPath(element);

        Launcher launcher = new Launcher();
        launcher.getEnvironment().setAutoImports(true);
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setComplianceLevel(17);
        launcher.addInputResource(new VirtualFile(
          path.getCompilationUnit().getSourceFile().getCharContent(false).toString(),
          path.getCompilationUnit().getSourceFile().toUri().getPath()
        ));
        launcher.getEnvironment().setInputClassLoader(getClass().getClassLoader());
        CtModel model = launcher.buildModel();

        for (CtType<?> type : model.getElements(new TypeFilter<>(CtType.class))) {
          if (type instanceof CtTypeParameter) {
            continue;
          }
          CtType<?> generatedProxy = new ProxyGenerator(type).generateProxy();
          JavaFileObject sourceFile = processingEnv.getFiler()
            .createSourceFile(generatedProxy.getQualifiedName(), element);
          try (Writer writer = sourceFile.openWriter()) {
            writer.write(generatedProxy.toStringWithImports());
          }
        }

        processingEnv.getMessager().printMessage(Kind.OTHER, element.toString());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return true;
  }

  // So running from within Jetbrains IDEs works.
  private static <T> T jbUnwrap(Class<? extends T> iface, T wrapper) {
    T unwrapped = null;
    try {
      final Class<?> apiWrappers = wrapper.getClass().getClassLoader().loadClass("org.jetbrains.jps.javac.APIWrappers");
      final Method unwrapMethod = apiWrappers.getDeclaredMethod("unwrap", Class.class, Object.class);
      unwrapped = iface.cast(unwrapMethod.invoke(null, iface, wrapper));
    } catch (Throwable ignored) {
    }
    return unwrapped != null ? unwrapped : wrapper;
  }
}
