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

package fluent.api;

import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import javax.tools.*;
import java.io.File;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.List;

import static fluent.api.EndProcessorTest.Expectation.FailWhen;
import static fluent.api.EndProcessorTest.Expectation.PassWhen;
import static fluent.api.Version.since;
import static java.util.Collections.emptyList;

@Listeners(MarkdownReporter.class)
public class EndProcessorTest {

    enum Expectation {PassWhen, FailWhen;}

    @DataProvider
    public static Object[][] sourceFiles() {
        return new Object[][]{
                {PassWhen, "EndMethodNotMissing", since(1.0)},
                {PassWhen, "ContinueAfterEndMethod", since(1.8)},
                {PassWhen, "PassThroughEndMethodNotMissing", since(1.1)},
                {PassWhen, "EndMethodMissingInAssignment", since(1.0)},
                {PassWhen, "EndMethodCheckIgnored", since(1.0)},
                {PassWhen, "EndMethodNotMissingInNesting", since(1.1)},
                {PassWhen, "NestedEndMethodNotMissing", since(1.1)},
                {PassWhen, "ExternalEndMethodNotMissing", since(1.2)},
                {PassWhen, "ExternalGenericEndMethodNotMissing", since(1.3)},
                {PassWhen, "ExternalGenericEndMethodWithParameterNotMissing", since(1.3)},
                {PassWhen, "ExternalGenericEndMethodWithGenericParameterNotMissing", since(1.3)},
                {PassWhen, "EndMethodNotMissingInConsumerExpression", since(1.8)},
                {PassWhen, "EndMethodMissingInFunctionExpression", since(1.8)},
                {PassWhen, "IgnoreEndMethodOnThis", since(1.9)},

                {FailWhen, "ImmediateEndMethodMissing", since(1.3)},
                {FailWhen, "ImmediateEndMethodMissingAfterConstructor", since(1.4)},
                {FailWhen, "EndMethodMissing", since(1.0)},
                {FailWhen, "EndMethodMissingInNesting", since(1.1)},
                {FailWhen, "UnmarkedEndMethod", since(1.1)},
                {FailWhen, "NestedEndMethodMissing", since(1.1)},
                {FailWhen, "ExternalEndMethodMissing", since(1.2)},
                {FailWhen, "ExternalGenericEndMethodMissing", since(1.3)},
                {FailWhen, "ImmediateEndMethodMissingAfterAnonymousClass", since(1.6)},
                {FailWhen, "EndMethodMissingInConsumerExpression", since(1.8)},
                {FailWhen, "EndMethodMissingInConsumerReference", since(1.8)},
                {FailWhen, "EndMethodMissingInConsumerConstructor", since(1.8)},
                {FailWhen, "ChainStartsWithThis", since(1.9)}
        };
    }

    @Test(dataProvider = "sourceFiles")
    public void compilationShould(Expectation expected, String className, Version since) throws URISyntaxException {
        DiagnosticCollector<JavaFileObject> listener = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> fileObjects = fileManager.getJavaFileObjects(new File(getClass().getResource(className + ".java").toURI()));
        JavaCompiler.CompilationTask task = compiler.getTask(new StringWriter(), fileManager, listener, emptyList(), null, fileObjects);
        boolean result = task.call();
        List<Diagnostic<? extends JavaFileObject>> diagnostics = listener.getDiagnostics();
        if (!diagnostics.isEmpty()) {
            System.out.println(diagnostics);
        }
        Assert.assertEquals(result, expected == PassWhen, diagnostics.toString());
        if(expected == FailWhen) {
            Assert.assertTrue(diagnostics.toString().contains("Method chain must end with "));
        }
    }

}
