/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2018, Ondrej Fischer
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 *  Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 *  Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package fluent.api.processors;

import com.sun.source.tree.*;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import fluent.api.End;
import fluent.api.IgnoreMissingEndMethod;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.*;

import static com.sun.source.tree.Tree.Kind.ASSIGNMENT;
import static java.lang.Boolean.TRUE;
import static java.util.Collections.emptySet;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * Compiler plugin scanning the source code for expression, which are supposed to be terminated by special terminal
 * methods (annotated with @End annotation), but were not.
 */
class EndScanner extends TreePathScanner<Void, Void> {

	private final Map<String, Set<String>> endMethodsCache;
	private final Trees trees;
	private final Types types;
	private final StartScanner startScanner = new StartScanner();

	EndScanner(Map<String, Set<String>> endMethodsCache, Trees trees, Types types) {
		this.endMethodsCache = endMethodsCache;
		this.trees = trees;
		this.types = types;
	}

	private Set<String> getMethods(Tree tree) {
		if("this".equals(tree.toString())) {
			return emptySet();
		}
		return getMethods(trees.getTypeMirror(trees.getPath(getCurrentPath().getCompilationUnit(), tree)));
	}

	private Set<String> getMethods(TypeMirror typeMirror) {
		Element element = types.asElement(typeMirror);
		if(isNull(element)) {
			return emptySet();
		} else {
			String elementName = element.toString();
			if(!endMethodsCache.containsKey(elementName)) {
				Set<String> methods = getMethods(element);
				endMethodsCache.put(elementName, methods);
			}
			return endMethodsCache.get(elementName);
		}
	}

	private String message(Collection<String> m) {
		return "Method chain must end with " + (m.size() > 1
				? "one of the following methods: "
				: "the method: ") + m;
	}

	private void inspectExpression(ExpressionTree expression, Tree statement) {
		if (expression.getKind() != ASSIGNMENT) {
			Element element = element(expression);
			if (nonNull(element)) {
				Set<String> methods = new HashSet<>(getMethods(expression));
				Boolean hasEnd = expression.accept(startScanner, methods);
				if (!(TRUE.equals(hasEnd) || methods.isEmpty())) {
					trees.printMessage(ERROR, message(methods), statement, getCurrentPath().getCompilationUnit());
				}
			}
		}
	}

	@Override
	public Void visitExpressionStatement(ExpressionStatementTree statement, Void aVoid) {
		inspectExpression(statement.getExpression(), statement);
		return statement.getExpression().accept(this, aVoid);
	}

	private boolean isVoidLambda(Tree tree) {
		return types.asElement(trees.getTypeMirror(trees.getPath(getCurrentPath().getCompilationUnit(), tree))).getEnclosedElements().stream().anyMatch(m -> m.accept(new VoidLambdaDetector(), null));
	}

	@Override
	public Void visitLambdaExpression(LambdaExpressionTree tree, Void aVoid) {
		if(tree.getBodyKind() == LambdaExpressionTree.BodyKind.EXPRESSION && isVoidLambda(tree)) {
			inspectExpression((ExpressionTree) tree.getBody(), tree);
		}
		return tree.getBody().accept(this, aVoid);
	}

	@Override
	public Void visitMemberReference(MemberReferenceTree tree, Void aVoid) {
		if(isVoidLambda(tree)) {
			ExpressionTree expression = tree.getQualifierExpression();
			Set<String> methods = new HashSet<>(getMethods(expression));
			tree.accept(startScanner, methods);
			Element element = element(tree);
			if(!element.accept(new MissingRequiredMethodReferenceDetector(methods), null)) {
				trees.printMessage(ERROR, message(methods), tree, getCurrentPath().getCompilationUnit());
			}
		}
		return tree.getQualifierExpression().accept(this, aVoid);
	}

	private Element element(Tree tree) {
		return trees.getElement(trees.getPath(getCurrentPath().getCompilationUnit(), tree));
	}

	private boolean ignoreCheck(Tree tree) {
		return nonNull(element(tree).getAnnotation(IgnoreMissingEndMethod.class));
	}

	@Override
	public Void visitMethod(MethodTree methodTree, Void aVoid) {
		return ignoreCheck(methodTree) ? aVoid : super.visitMethod(methodTree, aVoid);
	}

	private Set<String> getMethods(Element element) {
		Set<String> methods = element.getEnclosedElements().stream().filter(EndScanner::isAnnotatedEndMethod).map(Object::toString).collect(toSet());
		types.directSupertypes(element.asType()).forEach(type -> methods.addAll(getMethods(type)));
		// Let's save some memory on set instances. All classes without any ending methods share one instance.
		return methods.isEmpty() ? emptySet() : methods;
	}

	private static boolean isAnnotatedEndMethod(Element method) {
		return nonNull(method.getAnnotation(End.class));
	}

	private static boolean isConstructor(Element method) {
		return method.getKind() == CONSTRUCTOR;
	}

	private boolean isExternalEndMethod(Element method) {
		return endMethodsCache.getOrDefault(method.getEnclosingElement().toString(), emptySet()).contains(method.toString());
	}


	private static class VoidLambdaDetector implements ElementVisitor<Boolean, Void> {
		@Override public Boolean visit(Element e, Void o) {
			return false;
		}

		@Override public Boolean visit(Element e) {
			return false;
		}

		@Override public Boolean visitPackage(PackageElement e, Void o) {
			return false;
		}

		@Override public Boolean visitType(TypeElement e, Void o) {
			return false;
		}

		@Override public Boolean visitVariable(VariableElement e, Void o) {
			return false;
		}

		@Override public Boolean visitExecutable(ExecutableElement e, Void o) {
			return !e.isDefault() && !e.getModifiers().contains(Modifier.STATIC) && "void".equals(e.getReturnType().toString());
		}

		@Override public Boolean visitTypeParameter(TypeParameterElement e, Void o) {
			return false;
		}

		@Override public Boolean visitUnknown(Element e, Void o) {
			return false;
		}
	}

	/**
	 * This scanner is drilling down the chain of method calls (fluent API sentence), to identify all points in the chain,
	 * that may require some ending method.
	 */
	private class StartScanner extends TreeScanner<Boolean, Set<String>> {

		@Override public Boolean visitMethodInvocation(MethodInvocationTree tree, Set<String> methods) {
			Element method = element(tree);
			/*
			 * 1. If the method element represents constructor, which is invoked as method (method invocation), then it
			 *    refers to call of super() or this(), which needs to be excluded from the check. Standard usage of
			 *    constructor within "new Object();" is represented by "new class" and not "method invocation".
			 *
			 * 2. If method is annotated with @End annotation, it fulfills the requirement for sentence ending.
			 *
			 * 3. If method is found in cache (and still not annotated with @End), that means, that it was marked in
			 *    external source as ending method, so it fulfills the requirement for sentence ending.
			 */
			if(isConstructor(method) || isAnnotatedEndMethod(method) || isExternalEndMethod(method)) {
				return true;
			}
			// Only drill down if we didn't encounter an ending method.
			tree.getMethodSelect().accept(this, methods);
			return methods.isEmpty();
		}

		@Override public Boolean visitMemberSelect(MemberSelectTree tree, Set<String> methods) {
			// First drill down further.
			tree.getExpression().accept(this, methods);
			// Now get required methods for the type, to which the expression evaluates, on which we select the method.
			methods.addAll(getMethods(tree.getExpression()));
			return methods.isEmpty();
		}

	}

	private class MissingRequiredMethodReferenceDetector implements ElementVisitor<Boolean, Object> {
		private final Set<String> methods;

		private MissingRequiredMethodReferenceDetector(Set<String> methods) {
			this.methods = methods;
		}

		@Override public Boolean visit(Element e, Object o) {
			return true;
		}

		@Override public Boolean visit(Element e) {
			return true;
		}

		@Override public Boolean visitPackage(PackageElement e, Object o) {
			return true;
		}

		@Override public Boolean visitType(TypeElement e, Object o) {
			return true;
		}

		@Override public Boolean visitVariable(VariableElement e, Object o) {
			return true;
		}

		@Override public Boolean visitExecutable(ExecutableElement e, Object o) {
			if(isAnnotatedEndMethod(e)) {
				return true;
			}
			Element returnType = e.getKind() == CONSTRUCTOR ? e.getEnclosingElement() : types.asElement(e.getReturnType());
			methods.addAll(getMethods(returnType));
			return methods.isEmpty();
		}

		@Override public Boolean visitTypeParameter(TypeParameterElement e, Object o) {
			return true;
		}

		@Override public Boolean visitUnknown(Element e, Object o) {
			return true;
		}
	}
}
