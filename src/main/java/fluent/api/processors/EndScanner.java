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
import com.sun.source.util.Trees;
import fluent.api.End;
import fluent.api.IgnoreMissingEndMethod;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.emptyList;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toList;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * Compiler plugin scanning the source code for expression, which are supposed to be terminated by special terminal
 * methods (annotated with @End annotation), but were not.
 */
class EndScanner extends TreePathScanner<Void, Void> {

	private final Map<Element, List<Element>> endMethodsCache = new ConcurrentHashMap<>();
	private final Trees trees;
	private final Types types;

	EndScanner(Trees trees, Types types) {
		this.trees = trees;
		this.types = types;
	}

	private List<Element> getMethods(Tree tree) {
		return getMethods(trees.getTypeMirror(trees.getPath(getCurrentPath().getCompilationUnit(), tree)));
	}

	private List<Element> getMethods(TypeMirror typeMirror) {
		Element element = types.asElement(typeMirror);
		if(isNull(element)) {
			return emptyList();
		} else {
			if(!endMethodsCache.containsKey(element)) {
				List<Element> methods = getMethods(element);
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
		if (expression.getKind() != Tree.Kind.ASSIGNMENT) {
			Element element = element(expression);
			if(nonNull(element) && element.getKind() != ElementKind.CONSTRUCTOR) {
				List<Element> mandatoryMethods = getMethods(expression);
				if(!mandatoryMethods.isEmpty()) {
					trees.printMessage(ERROR, message(mandatoryMethods), statement, getCurrentPath().getCompilationUnit());
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

	private List<Element> getMethods(Element element) {
		List<Element> methods = element.getEnclosedElements().stream().filter(e -> nonNull(e.getAnnotation(End.class))).collect(toList());
		types.directSupertypes(element.asType()).forEach(type -> methods.addAll(getMethods(type)));
		return methods;
	}

}
