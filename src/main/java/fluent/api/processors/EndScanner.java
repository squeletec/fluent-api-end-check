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

import static com.sun.source.tree.LambdaExpressionTree.BodyKind.EXPRESSION;
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
	private String lastError = "";

	EndScanner(Map<String, Set<String>> endMethodsCache, Trees trees, Types types) {
		this.endMethodsCache = endMethodsCache;
		this.trees = trees;
		this.types = types;
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

	@Override
	public Void visitMethod(MethodTree methodTree, Set<String> endMethods) {
		return ignoreCheck(methodTree) ? null : super.visitMethod(methodTree, endMethods);
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
		tree.getExpression().accept(this, endMethods);
		endMethods.addAll(getMethods(tree.getExpression()));
		return null;
	}

	@Override
	public Void visitLambdaExpression(LambdaExpressionTree tree, Set<String> endMethods) {
		if(tree.getBodyKind() == EXPRESSION && isVoidLambda(tree)) {
			return visitExpression((ExpressionTree) tree.getBody(), tree);
		} else {
			return super.visitLambdaExpression(tree, null);
		}
	}

	@Override
	public Void visitMemberReference(MemberReferenceTree tree, Set<String> endMethods) {
		ExpressionTree expression = tree.getQualifierExpression();
		if(isVoidLambda(tree)) {
			Set<String> methods = new HashSet<>(getMethods(expression));
			expression.accept(this, methods);
			if(element(tree).accept(new ExecutableElementTest<>(this::isMethodReferenceEndMethodMissing), methods)) {
				trees.printMessage(ERROR, message(methods), tree, getCurrentPath().getCompilationUnit());
			}
			return null;
		} else {
			return expression.accept(this, null);
		}
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

	private Set<String> getMethods(Tree tree) {
		return "this".equals(tree.toString()) ? emptySet() : getMethods(trees.getTypeMirror(trees.getPath(getCurrentPath().getCompilationUnit(), tree)));
	}

	private Set<String> getMethods(TypeMirror typeMirror) {
		Element element = types.asElement(typeMirror);
		return isNull(element) ? emptySet() : endMethodsCache.computeIfAbsent(element.toString(), elementName -> getMethods(element));
	}

	private String message(Collection<String> m) {
		return lastError.isEmpty() ? "Method chain must end with " + (m.size() > 1 ? "one of the following methods: " : "method: ") + m : lastError;
	}

	private boolean isVoidLambda(Tree tree) {
		ExecutableElementTest<Void> test = new ExecutableElementTest<>((e, o) -> !e.isDefault() && !e.getModifiers().contains(STATIC) && "void".equals(e.getReturnType().toString()));
		return types.asElement(trees.getTypeMirror(trees.getPath(getCurrentPath().getCompilationUnit(), tree))).getEnclosedElements().stream().anyMatch(m -> m.accept(test, null));
	}

	private Element element(Tree tree) {
		return trees.getElement(trees.getPath(getCurrentPath().getCompilationUnit(), tree));
	}

	private boolean ignoreCheck(Tree tree) {
		return nonNull(element(tree).getAnnotation(IgnoreMissingEndMethod.class));
	}

	private Set<String> getMethods(Element element) {
		Set<String> methods = element.getEnclosedElements().stream().filter(this::isAnnotatedEndMethod).map(Object::toString).collect(toSet());
		types.directSupertypes(element.asType()).forEach(type -> methods.addAll(getMethods(type)));
		return methods.isEmpty() ? emptySet() : methods;
	}

	private boolean isAnnotatedEndMethod(Element method) {
		End end = method.getAnnotation(End.class);
		if(isNull(end)) {
			return false;
		}
		if(!end.message().isEmpty()) {
			lastError = end.message();
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
		if(isAnnotatedEndMethod(e) || isExternalEndMethod(e)) {
			return false;
		}
		Element returnType = e.getKind() == CONSTRUCTOR ? e.getEnclosingElement() : types.asElement(e.getReturnType());
		methods.addAll(getMethods(returnType));
		return !methods.isEmpty();
	}

}
