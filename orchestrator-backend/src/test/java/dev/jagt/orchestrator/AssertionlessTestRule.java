package dev.jagt.orchestrator;

import java.util.Set;
import java.util.stream.Collectors;
import net.sourceforge.pmd.lang.LanguageRegistry;
import net.sourceforge.pmd.lang.java.ast.ASTAnnotation;
import net.sourceforge.pmd.lang.java.ast.ASTMethodCall;
import net.sourceforge.pmd.lang.java.ast.ASTMethodDeclaration;
import net.sourceforge.pmd.lang.java.rule.AbstractJavaRule;

final class AssertionlessTestRule extends AbstractJavaRule {

  private static final Set<String> TEST_ANNOTATIONS =
      Set.of("Test", "ParameterizedTest", "RepeatedTest", "Property");
  private static final Set<String> ASSERTION_PREFIXES =
      Set.of("assert", "verify", "await", "check", "andExpect");

  AssertionlessTestRule() {
    setName("AssertionlessTestRule");
    setMessage("{0}");
    setLanguage(LanguageRegistry.PMD.getLanguageById("java"));
  }

  @Override
  @SuppressWarnings("unchecked")
  public Object visit(ASTMethodDeclaration method, Object data) {
    if (isTest(method) && !asserts(method) && !callsAssertingSibling(method)) {
      asCtx(data)
          .addViolationWithMessage(
              method, method.getEnclosingType().getSimpleName() + "." + method.getName());
    }
    return super.visit(method, data);
  }

  private static boolean isTest(ASTMethodDeclaration method) {
    return method.getDeclaredAnnotations().toStream().anyMatch(AssertionlessTestRule::marksTest);
  }

  private static boolean marksTest(ASTAnnotation annotation) {
    return TEST_ANNOTATIONS.contains(annotation.getSimpleName());
  }

  private static boolean asserts(ASTMethodDeclaration method) {
    return method
        .descendants(ASTMethodCall.class)
        .crossFindBoundaries()
        .toStream()
        .anyMatch(call -> ASSERTION_PREFIXES.stream().anyMatch(call.getMethodName()::startsWith));
  }

  private static boolean callsAssertingSibling(ASTMethodDeclaration method) {
    Set<String> called =
        method
            .descendants(ASTMethodCall.class)
            .crossFindBoundaries()
            .toStream()
            .map(ASTMethodCall::getMethodName)
            .collect(Collectors.toSet());
    return method
        .getEnclosingType()
        .getDeclarations(ASTMethodDeclaration.class)
        .filter(sibling -> called.contains(sibling.getName()))
        .toStream()
        .anyMatch(AssertionlessTestRule::asserts);
  }
}
