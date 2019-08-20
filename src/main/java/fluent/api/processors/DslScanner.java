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
import com.sun.source.util.*;
import fluent.api.IgnoreMissingEndMethod;

import javax.lang.model.element.Element;
import javax.lang.model.util.Types;

import java.io.PrintWriter;
import java.io.StringWriter;

import static com.sun.source.tree.LambdaExpressionTree.BodyKind.EXPRESSION;
import static com.sun.source.tree.Tree.Kind.ASSIGNMENT;
import static com.sun.source.util.TaskEvent.Kind.ANALYZE;
import static java.lang.Boolean.TRUE;
import static java.util.Objects.nonNull;
import static javax.lang.model.element.Modifier.STATIC;
import static javax.tools.Diagnostic.Kind.ERROR;

/**
 * Compiler plugin scanning the source code for expression, which are supposed to be terminated by special terminal
 * methods (annotated with @End annotation), but were not.
 */
class DslScanner extends TreePathScanner<Void, Void> implements TaskListener {

	private final UnterminatedSentenceScanner unterminatedSentenceScanner;
	private final Trees trees;
	private final Types types;

	DslScanner(UnterminatedSentenceScanner unterminatedSentenceScanner, Trees trees, Types types) {
		this.unterminatedSentenceScanner = unterminatedSentenceScanner;
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
			StringWriter writer = new StringWriter();
			runtimeException.printStackTrace(new PrintWriter(writer));
			trees.printMessage(ERROR, "@End method check failed. Please raise report at: https://github.com/c0stra/fluent-api-end-check/issues with following details: " + writer, taskEvent.getCompilationUnit(), taskEvent.getCompilationUnit());
		}
	}

	@Override
	public Void visitMethod(MethodTree methodTree, Void aVoid) {
		Element element = trees.getElement(trees.getPath(getCurrentPath().getCompilationUnit(), methodTree));
		return nonNull(element.getAnnotation(IgnoreMissingEndMethod.class)) ? null : super.visitMethod(methodTree, aVoid);
	}

	@Override
	public Void visitExpressionStatement(ExpressionStatementTree statement, Void aVoid) {
		if(statement.getExpression().getKind() != ASSIGNMENT) {
			scan(statement.getExpression());
		}
		return super.visitExpressionStatement(statement, aVoid);
	}

	@Override
	public Void visitLambdaExpression(LambdaExpressionTree tree, Void aVoid) {
		if(tree.getBodyKind() == EXPRESSION && isVoidLambda(tree) && tree.getBody().getKind() != ASSIGNMENT) {
			scan(tree.getBody());
		}
		return super.visitLambdaExpression(tree, null);
	}

	@Override
	public Void visitMemberReference(MemberReferenceTree tree, Void aVoid) {
		if(isVoidLambda(tree)) {
			scan(tree);
		}
		return tree.getQualifierExpression().accept(this, null);
	}

	private void scan(Tree tree) {
		String[] errorMessage = new String[1];
		if(TRUE.equals(unterminatedSentenceScanner.scan(getCurrentPath(), errorMessage))) {
			trees.printMessage(ERROR, errorMessage[0], tree, getCurrentPath().getCompilationUnit());
		}
	}

	private boolean isVoidLambda(Tree tree) {
		ExecutableElementTest<Void> test = new ExecutableElementTest<>((e, o) -> !e.isDefault() && !e.getModifiers().contains(STATIC) && "void".equals(e.getReturnType().toString()));
		return types.asElement(trees.getTypeMirror(trees.getPath(getCurrentPath().getCompilationUnit(), tree))).getEnclosedElements().stream().anyMatch(m -> m.accept(test, null));
	}

}
