/*
 * Copyright © 2018 Ondrej Fischer. All Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that
 * the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the
 *    following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
 *    following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * 3. The name of the author may not be used to endorse or promote products derived from this software without
 *     specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY [LICENSOR] "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

package fluent.api.processors;

import com.sun.source.tree.*;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import fluent.api.End;
import fluent.api.IgnoreMissingEndMethod;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static com.sun.source.tree.Tree.Kind.ASSIGNMENT;
import static java.lang.Boolean.FALSE;
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

	private final Map<Element, Set<Element>> endMethodsCache = new ConcurrentHashMap<>();
	private final Trees trees;
	private final Types types;
	private final StartScanner startScanner = new StartScanner();

	EndScanner(Trees trees, Types types) {
		this.trees = trees;
		this.types = types;
	}

	private Set<Element> getMethods(Tree tree) {
		return getMethods(trees.getTypeMirror(trees.getPath(getCurrentPath().getCompilationUnit(), tree)));
	}

	private Set<Element> getMethods(TypeMirror typeMirror) {
		Element element = types.asElement(typeMirror);
		if(isNull(element)) {
			return emptySet();
		} else {
			if(!endMethodsCache.containsKey(element)) {
				Set<Element> methods = getMethods(element);
				endMethodsCache.put(element, methods);
			}
			return endMethodsCache.get(element);
		}
	}

	private String message(Collection<Element> m) {
		return "Method chain must end with " + (m.size() > 1
				? "one of the following methods: "
				: "the method: ") + m;
	}

	@Override
	public Void visitExpressionStatement(ExpressionStatementTree statement, Void aVoid) {
		ExpressionTree expression = statement.getExpression();
		if (expression.getKind() != ASSIGNMENT) {
			Element element = element(expression);
			if(nonNull(element) && element.getKind() != CONSTRUCTOR) {
				Set<Element> methods = new HashSet<>();
				if(FALSE.equals(expression.accept(startScanner, methods))) {
					trees.printMessage(ERROR, message(methods), statement, getCurrentPath().getCompilationUnit());
				}
			}
		}
		return aVoid;
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

	private Set<Element> getMethods(Element element) {
		Set<Element> methods = element.getEnclosedElements().stream().filter(this::isEndMethod).collect(toSet());
		types.directSupertypes(element.asType()).forEach(type -> methods.addAll(getMethods(type)));
		return methods;
	}

	private boolean isEndMethod(Element element) {
		return nonNull(element.getAnnotation(End.class));
	}

	/**
	 * This scanner is
	 */
	private class StartScanner extends TreeScanner<Boolean, Set<Element>> {

		@Override
		public Boolean visitExpressionStatement(ExpressionStatementTree tree, Set<Element> methods) {
			return tree.getExpression().accept(this, methods);
		}

		@Override
		public Boolean visitMethodInvocation(MethodInvocationTree tree, Set<Element> methods) {
			super.visitMethodInvocation(tree, methods);
			return methods.isEmpty() || isEndMethod(element(tree));
		}

		@Override
		public Boolean visitMemberSelect(MemberSelectTree tree, Set<Element> methods) {
			tree.getExpression().accept(this, methods);
			methods.addAll(getMethods(tree.getExpression()));
			return methods.isEmpty();
		}

	}

}
