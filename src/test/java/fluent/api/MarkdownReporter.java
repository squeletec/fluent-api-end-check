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
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

public class MarkdownReporter implements ITestListener {

    private final String name = "Fluent API end check";
    private final String version = "1.5";

    private final PrintWriter output = new PrintWriter(new OutputStreamWriter(new FileOutputStream("TEST-REPORT-" + version + ".md")));

    public MarkdownReporter() throws FileNotFoundException {
    }

    @Override
    public void onTestStart(ITestResult iTestResult) {
    }

    private void test(String result, ITestResult testResult) {
        output.println("##### " + result + "  " + stream(testResult.getParameters()).map(Object::toString).collect(joining(" ")));
    }
    @Override
    public void onTestSuccess(ITestResult iTestResult) {
        test("✔", iTestResult);
    }

    @Override
    public void onTestFailure(ITestResult iTestResult) {
        test("✘", iTestResult);
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
        output.println("## " + name + " v" + version);
        output.println("#### Test results");
        output.println(ZonedDateTime.now().format(DateTimeFormatter.RFC_1123_DATE_TIME));
        output.println();
        output.println("[EndProcessorTest](src/test/java/fluent/api/EndProcessorTest.java)");
    }

    @Override
    public void onFinish(ITestContext iTestContext) {
        output.close();
    }

}
