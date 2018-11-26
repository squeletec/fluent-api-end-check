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
import com.sun.source.util.TaskEvent;
import com.sun.source.util.TaskListener;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import fluent.api.End;
import fluent.api.IgnoreMissingEndMethod;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.*;

import static com.sun.source.tree.Tree.Kind.ASSIGNMENT;
import static com.sun.source.util.TaskEvent.Kind.ANALYZE;
import static java.util.Collections.emptySet;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;

/**
 * Compiler plugin scanning the source code for expression, which are supposed to be terminated by special terminal
 * methods (annotated with @End annotation), but were not.
 */
class EndScanner extends TreePathScanner<Void, Set<String>> implements TaskListener {

	private final Map<String, Set<String>> endMethodsCache;
	private final Trees trees;
	private final Types types;
	private String lastErrorMessage = "";

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
		return lastErrorMessage.length() > 0
				? lastErrorMessage
				: "Method chain must end with " + (m.size() > 1 ? "one of the following methods: " : "the method: ") + m;
	}

	private Void visitExpression(ExpressionTree tree, Tree statement) {
		if(tree.getKind() == ASSIGNMENT) {
			return tree.accept(this, null);
		} else {
			Set<String> endMethods = new HashSet<>(getMethods(tree));
			tree.accept(this, endMethods);
			if (!endMethods.isEmpty()) {
				trees.printMessage(ERROR, message(endMethods), statement, getCurrentPath().getCompilationUnit());
			}
			return null;
		}
	}

	@Override
	public Void visitExpressionStatement(ExpressionStatementTree statement, Set<String> endMethods) {
		return visitExpression(statement.getExpression(), statement);
	}

	@Override
	public Void visitMethodInvocation(MethodInvocationTree tree, Set<String> endMethods) {
		tree.getArguments().forEach(argument -> argument.accept(this, null));
		if(endMethods == null) {
			return tree.getMethodSelect().accept(this, null);
		}
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
		Element method = element(tree);
		if(isAnnotatedEndMethod(method) || isExternalEndMethod(method)) {
			endMethods.clear();
			return tree.getMethodSelect().accept(this, null);
		} else {
			endMethods.addAll(getMethods(tree));
			return tree.getMethodSelect().accept(this, isConstructor(method) || isStaticMethod(method) ? null : endMethods);
		}
	}

	@Override
	public Void visitMemberSelect(MemberSelectTree tree, Set<String> endMethods) {
		if(endMethods == null) {
			return super.visitMemberSelect(tree, null);
		}
		// First drill down further.
		tree.getExpression().accept(this, endMethods);
		// Now get required methods for the type, to which the expression evaluates, on which we select the method.
		endMethods.addAll(getMethods(tree.getExpression()));
		return null;
	}

	private boolean isVoidLambda(Tree tree) {
		ExecutableElementTest<Void> test = new ExecutableElementTest<>((e, o) -> !e.isDefault() && !e.getModifiers().contains(STATIC) && "void".equals(e.getReturnType().toString()));
		return types.asElement(trees.getTypeMirror(trees.getPath(getCurrentPath().getCompilationUnit(), tree))).getEnclosedElements().stream().anyMatch(m -> m.accept(test, null));
	}

	@Override
	public Void visitLambdaExpression(LambdaExpressionTree tree, Set<String> endMethods) {
		if(tree.getBodyKind() == LambdaExpressionTree.BodyKind.EXPRESSION && isVoidLambda(tree)) {
			return visitExpression((ExpressionTree) tree.getBody(), tree);
		} else {
			return tree.getBody().accept(this, null);
		}
	}

	@Override
	public Void visitMemberReference(MemberReferenceTree tree, Set<String> endMethods) {
		if(isVoidLambda(tree)) {
			ExpressionTree expression = tree.getQualifierExpression();
			Set<String> methods = new HashSet<>(getMethods(expression));
			visitExpression(tree.getQualifierExpression(), tree);
			Element element = element(tree);
			if(element.accept(new ExecutableElementTest<>(this::isMethodReferenceEndMethodMissing), methods)) {
				trees.printMessage(ERROR, message(methods), tree, getCurrentPath().getCompilationUnit());
			}
		}
		return tree.getQualifierExpression().accept(this, endMethods);
	}

	private Element element(Tree tree) {
		return trees.getElement(trees.getPath(getCurrentPath().getCompilationUnit(), tree));
	}

	private boolean ignoreCheck(Tree tree) {
		return nonNull(element(tree).getAnnotation(IgnoreMissingEndMethod.class));
	}

	@Override
	public Void visitMethod(MethodTree methodTree, Set<String> endMethods) {
		return ignoreCheck(methodTree) ? null : super.visitMethod(methodTree, endMethods);
	}

	private Set<String> getMethods(Element element) {
		Set<String> methods = element.getEnclosedElements().stream().filter(this::isAnnotatedEndMethod).map(Object::toString).collect(toSet());
		types.directSupertypes(element.asType()).forEach(type -> methods.addAll(getMethods(type)));
		// Let's save some memory on set instances. All classes without any ending methods share one instance.
		return methods.isEmpty() ? emptySet() : methods;
	}

	private boolean isAnnotatedEndMethod(Element method) {
		End end = method.getAnnotation(End.class);
		if(isNull(end)) {
			return false;
		}
		if(end.message().length() > 0) {
			lastErrorMessage = end.message();
		}
		return true;
	}

	private static boolean isConstructor(Element method) {
		return method.getKind() == CONSTRUCTOR;
	}

	private static boolean isStaticMethod(Element method) {
		return method.getModifiers().contains(STATIC);
	}

	private boolean isExternalEndMethod(Element method) {
		return endMethodsCache.getOrDefault(method.getEnclosingElement().toString(), emptySet()).contains(method.toString());
	}

	private boolean isMethodReferenceEndMethodMissing(ExecutableElement e, Set<String> methods) {
		if(isAnnotatedEndMethod(e)) {
			return true;
		}
		Element returnType = e.getKind() == CONSTRUCTOR ? e.getEnclosingElement() : types.asElement(e.getReturnType());
		methods.addAll(getMethods(returnType));
		return !methods.isEmpty();
	}

	@Override
	public void started(TaskEvent taskEvent) {
		// Nothing to do on task started event.
	}

	@Override
	public void finished(TaskEvent taskEvent) {
		if(taskEvent.getKind() == ANALYZE) try {
			scan(taskEvent.getCompilationUnit(), null);
		} catch (RuntimeException runtimeException) {
			trees.printMessage(WARNING, "Unable to finish @End method check: " + runtimeException, taskEvent.getCompilationUnit(), getCurrentPath().getCompilationUnit());
		}
	}

}
