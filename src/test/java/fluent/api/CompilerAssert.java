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

package fluent.api;

import org.testng.Assert;

import javax.tools.*;
import java.io.File;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;

/**
 * Simple test base utility for testing compilation of provided sources.
 * Test methods are returning diagnostics, so tests can continue with assertions on diagnostic messages (errors,
 * warnings, info).
 */
class CompilerAssert {

    /**
     * Assert that compilation of provided file (from the same package as the test class) results to expected value.
     *
     * @param file     File name, relative to the package of the test class.
     * @param expected Expected result of the compilation.
     * @throws URISyntaxException If URI doesn't follow proper URI syntax.
     */
    private List<Diagnostic<? extends JavaFileObject>> assertCompilationResult(String file, Boolean expected, String... options) throws URISyntaxException {
        DiagnosticCollector<JavaFileObject> listener = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);
        Iterable<? extends JavaFileObject> fileObjects = fileManager.getJavaFileObjects(new File(getClass().getResource(file).toURI()));
        JavaCompiler.CompilationTask task = compiler.getTask(new StringWriter(), fileManager, listener, Arrays.asList(options), null, fileObjects);
        Boolean result = task.call();
        List<Diagnostic<? extends JavaFileObject>> diagnostics = listener.getDiagnostics();
        if (!diagnostics.isEmpty()) {
            System.out.println(diagnostics);
        }
        Assert.assertEquals(result, expected, diagnostics.toString());
        return diagnostics;
    }

    /**
     * Assert that compilation of provided file (from the same package as the test class) passes.
     *
     * @param file File name, relative to the package of the test class.
     * @throws URISyntaxException If URI doesn't follow proper URI syntax.
     */
    List<Diagnostic<? extends JavaFileObject>> assertCompilationPass(String file, String... options) throws URISyntaxException {
        return assertCompilationResult(file, Boolean.TRUE);
    }

    /**
     * Assert that compilation of provided file (from the same package as the test class) fails.
     *
     * @param file File name, relative to the package of the test class.
     * @throws URISyntaxException If URI doesn't follow proper URI syntax.
     */
    List<Diagnostic<? extends JavaFileObject>> assertCompilationFails(String file, String... options) throws URISyntaxException {
        return assertCompilationResult(file, Boolean.FALSE);
    }

}
