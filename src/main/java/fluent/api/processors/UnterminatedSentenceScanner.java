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
import com.sun.source.util.Trees;

import javax.lang.model.element.*;
import javax.lang.model.type.TypeMirror;

import static java.lang.Boolean.TRUE;
import static javax.lang.model.element.ElementKind.CONSTRUCTOR;
import static javax.lang.model.element.Modifier.STATIC;

/**
 * Compiler plugin scanning the source code for expression, which are supposed to be terminated by special terminal
 * methods (annotated with @End annotation), but were not.
 */
class UnterminatedSentenceScanner extends TreePathScanner<Boolean, String[]> {

	private final AnnotationUtils utils;
	private final Trees trees;

	UnterminatedSentenceScanner(AnnotationUtils utils, Trees trees) {
		this.utils = utils;
		this.trees = trees;
	}

	/*
	 * Top level scanning entry points.
	 *
	 * At the top level, knowing, that expression ends, the fact, that last invoked method is @End method has higher
	 * priority over the fact, that it may be starting new chain requiring end method.
	 */

	private Boolean visitExpression(Tree tree, String[] errorMessage) {
		if(tree.getKind() == Tree.Kind.METHOD_INVOCATION && utils.isEnd(element(tree), errorMessage)) {
			return false;
		}
		if(utils.requiresEnd(type(tree), errorMessage)) {
			return true;
		}
		return tree.accept(this, errorMessage);
	}

	@Override
	public Boolean visitExpressionStatement(ExpressionStatementTree tree, String[] errorMessage) {
		return visitExpression(tree.getExpression(), errorMessage);
	}

	@Override
	public Boolean visitLambdaExpression(LambdaExpressionTree tree, String[] errorMessage) {
		return visitExpression(tree.getBody(), errorMessage);
	}

	@Override
	public Boolean visitMemberReference(MemberReferenceTree tree, String[] errorMessage) {
		Element member = element(tree);
		if(utils.isEnd(member, errorMessage)) {
			return false;
		}
		if(utils.isStart(member, errorMessage) || utils.requiresEnd(typeOf(member), errorMessage)) {
			return true;
		}
		return tree.getQualifierExpression().accept(this, errorMessage);
	}


	/*
		Drill down of statement portions to identify unclosed opening of fluent sentence.
	 */

	@Override
	public Boolean visitMethodInvocation(MethodInvocationTree tree, String[] errorMessage) {
		return tree.getMethodSelect().accept(this, errorMessage);
	}

	@Override
	public Boolean visitNewClass(NewClassTree tree, String[] errorMessage) {
		return utils.requiresEnd(type(tree), errorMessage);
	}

	@Override
	public Boolean visitArrayAccess(ArrayAccessTree tree, String[] strings) {
		return tree.getExpression().accept(this, strings);
	}

	@Override
	public Boolean visitIdentifier(IdentifierTree identifierTree, String[] errorMessage) {
		return utils.isStart(element(identifierTree), errorMessage);
	}

	@Override
	public Boolean visitMemberSelect(MemberSelectTree tree, String[] errorMessage) {
		Element member = element(tree);
		if(utils.isStart(member, errorMessage)) {
			return true;
		}
		if(utils.isEnd(member, errorMessage)) {
			return false;
		}
		if(member.getModifiers().contains(STATIC)) {
			return false;
		}
		if(!"this".equals(tree.getExpression().toString()) && utils.requiresEnd(type(tree.getExpression()), errorMessage)) {
			return true;
		}
		return tree.getExpression().accept(this, errorMessage);
	}

	@Override
	public Boolean visitConditionalExpression(ConditionalExpressionTree tree, String[] strings) {
		return TRUE.equals(tree.getTrueExpression().accept(this, strings)) || TRUE.equals(tree.getFalseExpression().accept(this, strings));
	}

	@Override
	public Boolean visitAssignment(AssignmentTree assignmentTree, String[] strings) {
		return null;
	}

	@Override
	public Boolean visitNewArray(NewArrayTree tree, String[] strings) {
		return null;
	}

	private Element element(Tree tree) {
		return trees.getElement(trees.getPath(getCurrentPath().getCompilationUnit(), tree));
	}

	private TypeMirror type(Tree tree) {
		return trees.getTypeMirror(trees.getPath(getCurrentPath().getCompilationUnit(), tree));
	}

	private TypeMirror typeOf(Element element) {
		return element.accept(new ElementVisitor<TypeMirror, Void>() {
			@Override public TypeMirror visit(Element e, Void aVoid) { return e.accept(this, aVoid); }
			@Override public TypeMirror visit(Element e) { return e.accept(this, null); }
			@Override public TypeMirror visitPackage(PackageElement e, Void aVoid) { return e.asType(); }
			@Override public TypeMirror visitType(TypeElement e, Void aVoid) { return e.asType(); }
			@Override public TypeMirror visitVariable(VariableElement e, Void aVoid) { return e.asType(); }
			@Override public TypeMirror visitExecutable(ExecutableElement e, Void aVoid) { return e.getKind() == CONSTRUCTOR ? visit(e.getEnclosingElement()) : e.getReturnType(); }
			@Override public TypeMirror visitTypeParameter(TypeParameterElement e, Void aVoid) { return e.asType(); }
			@Override public TypeMirror visitUnknown(Element e, Void aVoid) { return e.asType(); }
		}, null);
	}

}
