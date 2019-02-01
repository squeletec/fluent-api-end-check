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
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import fluent.api.End;
import fluent.api.Start;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toSet;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * Compiler plugin scanning the source code for expression, which are supposed to be terminated by special terminal
 * methods (annotated with @End annotation), but were not.
 */
class UnterminatedSentenceScanner extends TreePathScanner<Void, Tree> {

	private final Map<String, Set<String>> endMethodsCache;
	private final Trees trees;
	private final Types types;
	private String lastError = "";

	UnterminatedSentenceScanner(Map<String, Set<String>> endMethodsCache, Trees trees, Types types) {
		this.endMethodsCache = endMethodsCache;
		this.trees = trees;
		this.types = types;
	}

	private boolean isStartExpression(Element element, Tree statement) {
		if(statement.toString().startsWith("super(") || statement.toString().startsWith("this(")) {
			return false;
		}
		Set<String> methods = element.getKind() == CONSTRUCTOR ? getMethods(element.getEnclosingElement().asType()) : getMethods(((ExecutableElement) element).getReturnType());
		if(!methods.isEmpty() || isAnnotatedStartMethod(element)) {
			trees.printMessage(ERROR, message(methods), statement, getCurrentPath().getCompilationUnit());
			return true;
		}
		return false;
	}

	private boolean endOrStartFound(ExpressionTree tree, Tree statement) {
		Element element = element(tree);
		return isEndMethod(element) || isStartExpression(element, statement) || isConstructorOrStaticMethod(element);
	}

	private Void visitExpression(ExpressionTree tree, Tree statement) {
		if(tree.accept(new SimpleTreeVisitor<Boolean, Void>(false) {
			@Override public Boolean visitMethodInvocation(MethodInvocationTree methodInvocationTree, Void aVoid) {
				return isEndMethod(element(tree));
			}
		}, null)) {
			return null;
		}
		Set<String> endMethods = new HashSet<>(getMethods(tree));
		tree.accept(this, statement);
		if (!endMethods.isEmpty()) {
			trees.printMessage(ERROR, message(endMethods), statement, getCurrentPath().getCompilationUnit());
		}
		return null;
	}

	@Override
	public Void visitExpressionStatement(ExpressionStatementTree expressionStatementTree, Tree tree) {
		return visitExpression(expressionStatementTree.getExpression(), tree);
	}

	@Override
	public Void visitMethodInvocation(MethodInvocationTree tree, Tree statement) {
		return endOrStartFound(tree, statement) ? null : tree.getMethodSelect().accept(this, statement);
	}

	@Override
	public Void visitMemberSelect(MemberSelectTree tree, Tree statement) {
		return isStartExpression(element(tree), statement) ? null : visitExpression(tree.getExpression(), statement);
	}

	@Override
	public Void visitMemberReference(MemberReferenceTree tree, Tree statement) {
		return endOrStartFound(tree, statement) ? null : super.visitMemberReference(tree, statement);
	}

	@Override
	public Void visitLambdaExpression(LambdaExpressionTree lambdaExpressionTree, Tree tree) {
		return visitExpression((ExpressionTree) lambdaExpressionTree.getBody(), tree);
	}

	private Set<String> getMethods(Tree tree) {
		return "this".equals(tree.toString()) ? emptySet() : getMethods(trees.getTypeMirror(trees.getPath(getCurrentPath().getCompilationUnit(), tree)));
	}

	private Set<String> getMethods(TypeMirror typeMirror) {
		Element element = types.asElement(typeMirror);
		if(isNull(element)) {
			return emptySet();
		}
		String elementName = element.toString();
		if(!endMethodsCache.containsKey(elementName)) {
			endMethodsCache.put(elementName, getMethods(element));
		}
		return endMethodsCache.get(elementName);
	}

	private String message(Collection<String> m) {
		return lastError.isEmpty() ? "Method chain must end with " + (m.size() > 1 ? "one of the following methods: " : "method: ") + m : lastError;
	}

	private Element element(Tree tree) {
		return trees.getElement(trees.getPath(getCurrentPath().getCompilationUnit(), tree));
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

	private boolean isAnnotatedStartMethod(Element method) {
		Start start = method.getAnnotation(Start.class);
		if(isNull(start)) {
			return false;
		}
		lastError = start.value();
		return true;
	}

	private static boolean isConstructorOrStaticMethod(Element method) {
		return method.getKind() == CONSTRUCTOR || method.getModifiers().contains(STATIC);
	}

	private boolean isEndMethod(Element method) {
		return isAnnotatedEndMethod(method) || endMethodsCache.getOrDefault(method.getEnclosingElement().toString(), emptySet()).contains(method.toString());
	}

}
