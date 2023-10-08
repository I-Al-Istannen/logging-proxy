package de.ialistannen.loggingproxy;

import com.google.auto.service.AutoService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.Set;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes("de.ialistannen.loggingproxy.GenerateTestProxy")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@AutoService(Processor.class)
public class ProcessorMain extends AbstractProcessor {

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    try {
      for (Element element : roundEnv.getElementsAnnotatedWith(GenerateTestProxy.class)) {
        if (!(element instanceof TypeElement type)) {
          processingEnv.getMessager().printError("Found annotated non-type", element);
          continue;
        }

        String generated = new TextblockProcessor().forType(
          (TypeElement) element,
          processingEnv.getElementUtils().getPackageOf(element)
        );

        JavaFileObject sourceFile = processingEnv.getFiler()
          .createSourceFile(type.getQualifiedName() + "Proxy", element);
        try (Writer writer = sourceFile.openWriter()) {
          writer.write(generated);
        }

        processingEnv.getMessager().printMessage(Kind.OTHER, element.toString());
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }

    return true;
  }

}
