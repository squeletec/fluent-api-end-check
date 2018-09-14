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


import com.sun.source.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static com.sun.source.util.TaskEvent.Kind.ANALYZE;
import static java.lang.ClassLoader.getSystemResources;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.tools.Diagnostic.Kind.WARNING;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;

/**
 * Pseudo annotation processor of special annotation @End marking terminal methods in fluent API. It actually doesn't do
 * any annotation processing, only hooks on the compiler, and checks missing terminal methods in expression statements.
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class EndProcessor extends AbstractProcessor {

	private static final String resources = "fluent-api-check-methods.txt";

	@Override
	public synchronized void init(ProcessingEnvironment env) {
		super.init(env);
		markChecked(env.getFiler());
		JavacTask.instance(env).addTaskListener(new TaskListener() {
			private EndScanner scanner = new EndScanner(loadEndMethodsFromFiles(), Trees.instance(env), env.getTypeUtils());

			@Override public void started(TaskEvent taskEvent) { }

			@Override public void finished(TaskEvent taskEvent) {
				if(taskEvent.getKind() == ANALYZE) try {
					scanner.scan(taskEvent.getCompilationUnit(), null);
				} catch (RuntimeException runtimeException) {
					env.getMessager().printMessage(Diagnostic.Kind.WARNING, "Unable to finish @End method check: " + runtimeException, taskEvent.getTypeElement());
				}
			}
		});
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		return false;
	}

	private void markChecked(Filer filer) {
		try(Writer writer = filer.createResource(SOURCE_OUTPUT, "", "required-method.checked").openWriter()) {
			writer.write("Checked\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private Map<String, Set<String>> loadEndMethodsFromFiles() {
		Map<String, Set<String>> map = new ConcurrentHashMap<>();
		try {
			Enumeration<URL> endingMethodResources = getSystemResources(resources);
			while(endingMethodResources.hasMoreElements()) {
				URL url = endingMethodResources.nextElement();
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
					reader.lines().forEach(line -> addExternalEndingMethod(line, map, url));
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return map;
	}

	private void addExternalEndingMethod(String line, Map<String, Set<String>> map, URL url) {
		int i = line.lastIndexOf('.');
		TypeElement type = processingEnv.getElementUtils().getTypeElement(line.substring(0, i));
		if(isNull(type)) {
			warning(line, url, "Class not found");
		} else if(methodsOf(type).filter(method -> method.equals(line.substring(i + 1))).peek(
				method -> map.computeIfAbsent(type.toString(), key -> new HashSet<>()).add(method)
		).count() == 0) {
			warning(line, url, "Method not found. Candidates are: " + methodsOf(type).collect(joining(", ")));
		}
	}

	private void warning(String line, URL url, String reason) {
		processingEnv.getMessager().printMessage(WARNING, "Not recognized ending method " + line + " defined in " + url + ": " + reason + "!");
	}

	private Stream<? extends String> methodsOf(Element type) {
		return type.getEnclosedElements().stream().filter(member -> member.getKind() == METHOD).map(Object::toString);
	}

}
