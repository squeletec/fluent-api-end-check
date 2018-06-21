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

import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;
import static java.lang.Character.toUpperCase;
import static java.time.ZonedDateTime.now;
import static java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class MarkdownReporter implements ITestListener {

    private final String name = System.getProperty("project.name");
    private final Version version = new Version(System.getProperty("project.version"));

    private final PrintWriter output = new PrintWriter(new OutputStreamWriter(new FileOutputStream("reports/" + report(version))));

    public MarkdownReporter() throws FileNotFoundException { }

    private static String report(Object version) {
        return "TEST-REPORT-" + version + ".md";
    }

    @Override
    public void onTestStart(ITestResult iTestResult) { }

    private static String uncamel(String camelCase) {
        if(camelCase.isEmpty()) {
            return camelCase;
        }
        StringBuilder string = new StringBuilder(camelCase.substring(0, 1).toLowerCase());
        for(int i = 1; i < camelCase.length(); i++) {
            char character = camelCase.charAt(i);
            if(isUpperCase(character)) {
                string.append(' ').append(toLowerCase(character));
            } else {
                string.append(character);
            }
        }
        return string.toString();
    }

    private static String capitalize(String string) {
        return toUpperCase(string.charAt(0)) + string.substring(1);
    }

    private void test(String result, ITestResult testResult) {
        output.print("##### ");
        output.print(result);
        output.print(" ");
        output.print(capitalize(uncamel(testResult.getMethod().getMethodName())));
        output.print(" ");
        output.println(stream(testResult.getParameters()).map(parameter -> parameter instanceof Version
                ? version.equals(parameter) ? "_(new in " + parameter + ")_" : "_([since " + parameter + "](" + report(parameter) + "))_"
                : uncamel(String.valueOf(parameter))
        ).collect(joining(" ")));
    }

    @Override
    public void onTestSuccess(ITestResult iTestResult) {
        test("![PASSED](icons8-passed-18.png)", iTestResult);
    }

    @Override
    public void onTestFailure(ITestResult iTestResult) {
        test("![FAILED](icons8-failed-18.png)", iTestResult);
        output.println("```");
        output.println(iTestResult.getThrowable());
        output.println("```");
    }

    @Override
    public void onTestSkipped(ITestResult iTestResult) {

    }

    @Override
    public void onTestFailedButWithinSuccessPercentage(ITestResult iTestResult) {
    }

    @Override
    public void onStart(ITestContext iTestContext) {
        output.println("## " + name + " " + version);
        output.println("#### Test results");
        output.println(now().format(RFC_1123_DATE_TIME));
        output.println();
        output.println("[EndProcessorTest](../src/test/java/fluent/api/EndProcessorTest.java)");
    }

    @Override
    public void onFinish(ITestContext iTestContext) {
        output.close();
    }

}
