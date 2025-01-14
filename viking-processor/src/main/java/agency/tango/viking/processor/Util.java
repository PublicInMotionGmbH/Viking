package agency.tango.viking.processor;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.WildcardTypeName;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.AnnotationValueVisitor;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.util.SimpleAnnotationValueVisitor6;
import javax.lang.model.util.SimpleTypeVisitor6;

/**
 * Utilities for handling types in annotation processors
 */
public final class Util {
  // Class<?>[].
  public static final TypeName ARRAY_OF_CLASS = ArrayTypeName.of(ParameterizedTypeName.get(
      ClassName.get(Class.class), WildcardTypeName.subtypeOf(Object.class)));
  // @SuppressWarnings("unchecked")
  public static final AnnotationSpec UNCHECKED = AnnotationSpec.builder(SuppressWarnings.class)
      .addMember("value", "$S", "unchecked")
      .build();

  private Util() {
  }

  public static String getPackageName(TypeMirror typeMirror) {
    List<String> splitName = splitQualifiedName(typeMirror);
    return Joiner.on(".").join(Iterables.limit(splitName, splitName.size() - 1));
  }

  public static String getSimpleTypeName(TypeMirror typeMirror) {
    List<String> splitName = splitQualifiedName(typeMirror);
    return Iterables.getLast(splitName);
  }

  public static PackageElement getPackage(Element type) {
    while (type.getKind() != ElementKind.PACKAGE) {
      type = type.getEnclosingElement();
    }
    return (PackageElement) type;
  }

  /**
   * Returns a class name to complement {@code type}.
   */
  public static ClassName adapterName(ClassName type, String suffix) {
    return ClassName.get(type.packageName(),
        Joiner.on('$').join(type.simpleNames()) + suffix);
  }

  /**
   * Returns a string for {@code type}. Primitive types are always boxed.
   */
  public static String typeToString(TypeMirror type) {
    StringBuilder result = new StringBuilder();
    typeToString(type, result, '.');
    return result.toString();
  }

  /**
   * Returns a string for the raw type of {@code type}. Primitive types are always boxed.
   */
  public static String rawTypeToString(TypeMirror type, char innerClassSeparator) {
    if (!(type instanceof DeclaredType)) {
      throw new IllegalArgumentException("Unexpected type: " + type);
    }
    StringBuilder result = new StringBuilder();
    DeclaredType declaredType = (DeclaredType) type;
    rawTypeToString(result, (TypeElement) declaredType.asElement(), innerClassSeparator);
    return result.toString();
  }

  /**
   * Appends a string for {@code type} to {@code result}. Primitive types are
   * always boxed.
   *
   * @param innerClassSeparator either '.' or '$', which will appear in a
   * class name like "java.lang.Map.Entry" or "java.lang.Map$Entry".
   * Use '.' for references to existing types in code. Use '$' to define new
   * class names and for strings that will be used by runtime reflection.
   */
  public static void typeToString(final TypeMirror type, final StringBuilder result,
      final char innerClassSeparator) {
    type.accept(new SimpleTypeVisitor6<Void, Void>() {
      @Override
      public Void visitDeclared(DeclaredType declaredType, Void v) {
        TypeElement typeElement = (TypeElement) declaredType.asElement();
        rawTypeToString(result, typeElement, innerClassSeparator);
        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (!typeArguments.isEmpty()) {
          result.append("<");
          for (int i = 0; i < typeArguments.size(); i++) {
            if (i != 0) {
              result.append(", ");
            }
            typeToString(typeArguments.get(i), result, innerClassSeparator);
          }
          result.append(">");
        }
        return null;
      }

      @Override
      public Void visitPrimitive(PrimitiveType primitiveType, Void v) {
        result.append(box((PrimitiveType) type));
        return null;
      }

      @Override
      public Void visitArray(ArrayType arrayType, Void v) {
        TypeMirror type = arrayType.getComponentType();
        if (type instanceof PrimitiveType) {
          result.append(type.toString()); // Don't box, since this is an array.
        } else {
          typeToString(arrayType.getComponentType(), result, innerClassSeparator);
        }
        result.append("[]");
        return null;
      }

      @Override
      public Void visitTypeVariable(TypeVariable typeVariable, Void v) {
        result.append(typeVariable.asElement().getSimpleName());
        return null;
      }

      @Override
      public Void visitError(ErrorType errorType, Void v) {
        // Error type found, a type may not yet have been generated, but we need the type
        // so we can generate the correct code in anticipation of the type being available
        // to the compiler.

        // Paramterized types which don't exist are returned as an error type whose name is "<any>"
        if ("<any>".equals(errorType.toString())) {
          throw new CodeGenerationIncompleteException(
              "Type reported as <any> is likely a not-yet generated parameterized type.");
        }
        // TODO(cgruber): Figure out a strategy for non-FQCN cases.
        result.append(errorType.toString());
        return null;
      }

      @Override
      protected Void defaultAction(TypeMirror typeMirror, Void v) {
        throw new UnsupportedOperationException(
            "Unexpected TypeKind " + typeMirror.getKind() + " for " + typeMirror);
      }
    }, null);
  }

  /**
   * Returns a string for {@code type}. Primitive types are always boxed.
   */
  public static TypeName injectableType(TypeMirror type) {
    return type.accept(new SimpleTypeVisitor6<TypeName, Void>() {
      @Override
      public TypeName visitPrimitive(PrimitiveType primitiveType, Void v) {
        return box(primitiveType);
      }

      @Override
      public TypeName visitError(ErrorType errorType, Void v) {
        // Error type found, a type may not yet have been generated, but we need the type
        // so we can generate the correct code in anticipation of the type being available
        // to the compiler.

        // Paramterized types which don't exist are returned as an error type whose name is "<any>"
        if ("<any>".equals(errorType.toString())) {
          throw new CodeGenerationIncompleteException(
              "Type reported as <any> is likely a not-yet generated parameterized type.");
        }

        return ClassName.bestGuess(errorType.toString());
      }

      @Override
      protected TypeName defaultAction(TypeMirror typeMirror, Void v) {
        return TypeName.get(typeMirror);
      }
    }, null);
  }

  private static final AnnotationValueVisitor<Object, Void> VALUE_EXTRACTOR =
      new SimpleAnnotationValueVisitor6<Object, Void>() {
        @Override
        public Object visitString(String s, Void p) {
          if ("<error>".equals(s)) {
            throw new CodeGenerationIncompleteException("Unknown type returned as <error>.");
          } else if ("<any>".equals(s)) {
            throw new CodeGenerationIncompleteException("Unknown type returned as <any>.");
          }
          return s;
        }

        @Override
        public Object visitType(TypeMirror t, Void p) {
          return t;
        }

        @Override
        protected Object defaultAction(Object o, Void v) {
          return o;
        }

        @Override
        public Object visitArray(List<? extends AnnotationValue> values, Void v) {
          Object[] result = new Object[values.size()];
          for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i).accept(this, null);
          }
          return result;
        }
      };

  /**
   * Returns the annotation on {@code element} formatted as a Map. This returns
   * a Map rather than an instance of the annotation interface to work-around
   * the fact that Class and Class[] fields won't work at code generation time.
   * See http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=5089128
   */
  public static Map<String, Object> getAnnotation(Class<?> annotationType, Element element) {
    for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
      if (!rawTypeToString(annotation.getAnnotationType(), '$')
          .equals(annotationType.getName())) {
        continue;
      }

      Map<String, Object> result = new LinkedHashMap<String, Object>();
      for (Method m : annotationType.getMethods()) {
        result.put(m.getName(), m.getDefaultValue());
      }
      for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> e
          : annotation.getElementValues().entrySet()) {
        String name = e.getKey().getSimpleName().toString();
        Object value = e.getValue().accept(VALUE_EXTRACTOR, null);
        Object defaultValue = result.get(name);
        if (!lenientIsInstance(defaultValue.getClass(), value)) {
          throw new IllegalStateException(String.format(
              "Value of %s.%s is a %s but expected a %s\n    value: %s",
              annotationType, name, value.getClass().getName(), defaultValue.getClass().getName(),
              value instanceof Object[] ? Arrays.toString((Object[]) value) : value));
        }
        result.put(name, value);
      }
      return result;
    }
    return null; // Annotation not found.
  }

  /**
   * Returns true if {@code value} can be assigned to {@code expectedClass}.
   * Like {@link Class#isInstance} but more lenient for {@code Class<?>} values.
   */
  private static boolean lenientIsInstance(Class<?> expectedClass, Object value) {
    if (expectedClass.isArray()) {
      Class<?> componentType = expectedClass.getComponentType();
      if (!(value instanceof Object[])) {
        return false;
      }
      for (Object element : (Object[]) value) {
        if (!lenientIsInstance(componentType, element)) {
          return false;
        }
      }
      return true;
    } else if (expectedClass == Class.class) {
      return value instanceof TypeMirror;
    } else {
      return expectedClass == value.getClass();
    }
  }

  // TODO(sgoldfed): better format for other types of elements?
  static String elementToString(Element element) {
    switch (element.getKind()) {
      case FIELD:
        // fall through
      case CONSTRUCTOR:
        // fall through
      case METHOD:
        return element.getEnclosingElement() + "." + element;
      default:
        return element.toString();
    }
  }

  static void rawTypeToString(StringBuilder result, TypeElement type,
      char innerClassSeparator) {
    String packageName = getPackage(type).getQualifiedName().toString();
    String qualifiedName = type.getQualifiedName().toString();
    if (packageName.isEmpty()) {
      result.append(qualifiedName.replace('.', innerClassSeparator));
    } else {
      result.append(packageName);
      result.append('.');
      result.append(
          qualifiedName.substring(packageName.length() + 1).replace('.', innerClassSeparator));
    }
  }

  static TypeName box(PrimitiveType primitiveType) {
    switch (primitiveType.getKind()) {
      case BYTE:
        return ClassName.get(Byte.class);
      case SHORT:
        return ClassName.get(Short.class);
      case INT:
        return ClassName.get(Integer.class);
      case LONG:
        return ClassName.get(Long.class);
      case FLOAT:
        return ClassName.get(Float.class);
      case DOUBLE:
        return ClassName.get(Double.class);
      case BOOLEAN:
        return ClassName.get(Boolean.class);
      case CHAR:
        return ClassName.get(Character.class);
      case VOID:
        return ClassName.get(Void.class);
      default:
        throw new AssertionError();
    }
  }

  /**
   * Returns the no-args constructor for {@code type}, or null if no such
   * constructor exists.
   */
  public static ExecutableElement getNoArgsConstructor(TypeElement type) {
    for (Element enclosed : type.getEnclosedElements()) {
      if (enclosed.getKind() != ElementKind.CONSTRUCTOR) {
        continue;
      }
      ExecutableElement constructor = (ExecutableElement) enclosed;
      if (constructor.getParameters().isEmpty()) {
        return constructor;
      }
    }
    return null;
  }

  /**
   * Returns true if generated code can invoke {@code constructor}. That is, if
   * the constructor is non-private and its enclosing class is either a
   * top-level class or a static nested class.
   */
  public static boolean isCallableConstructor(ExecutableElement constructor) {
    if (constructor.getModifiers().contains(Modifier.PRIVATE)) {
      return false;
    }
    TypeElement type = (TypeElement) constructor.getEnclosingElement();
    return type.getEnclosingElement().getKind() == ElementKind.PACKAGE
        || type.getModifiers().contains(Modifier.STATIC);
  }

  /**
   * Returns a user-presentable string like {@code coffee.CoffeeModule}.
   */
  public static String className(ExecutableElement method) {
    return ((TypeElement) method.getEnclosingElement()).getQualifiedName().toString();
  }

  public static boolean isInterface(TypeMirror typeMirror) {
    return typeMirror instanceof DeclaredType
        && ((DeclaredType) typeMirror).asElement().getKind() == ElementKind.INTERFACE;
  }

  static boolean isStatic(Element element) {
    for (Modifier modifier : element.getModifiers()) {
      if (modifier.equals(Modifier.STATIC)) {
        return true;
      }
    }
    return false;
  }

  private static List<String> splitQualifiedName(TypeMirror typeMirror) {
    return Splitter.on(".").trimResults().splitToList(TypeName.get(typeMirror).toString());
  }

  /**
   * An exception thrown when a type is not extant (returns as an error type),
   * usually as a result of another processor not having yet generated its types upon
   * which a dagger-annotated type depends.
   */
  final static class CodeGenerationIncompleteException extends IllegalStateException {
    CodeGenerationIncompleteException(String s) {
      super(s);
    }
  }
}
