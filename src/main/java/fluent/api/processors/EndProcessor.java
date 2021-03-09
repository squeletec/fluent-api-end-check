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
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import fluent.api.EndMethodCheckFile;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Types;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.util.Set;
import java.util.Map;
import java.util.HashSet;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import static java.lang.ClassLoader.getSystemResources;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.joining;
import static javax.lang.model.element.ElementKind.METHOD;
import static javax.tools.Diagnostic.Kind.ERROR;
import static javax.tools.Diagnostic.Kind.WARNING;
import static javax.tools.StandardLocation.SOURCE_OUTPUT;

/**
 * Pseudo annotation processor of special annotation @End marking terminal methods in fluent API. It actually doesn't do
 * any annotation processing, only hooks on the compiler, and checks missing terminal methods in expression statements.
 */
@SupportedAnnotationTypes("fluent.api.EndMethodCheckFile")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class EndProcessor extends AbstractProcessor {

	private static final String EXTERNAL_END_METHOD_FILE = "fluent-api-check-methods.txt";

	/**
	 *  With the introduction of IntelliJ Idea 2020.3 release the ProcessingEnvironment
	 *  is not of type com.sun.tools.javac.processing.JavacProcessingEnvironment
	 *  but java.lang.reflect.Proxy.
	 *  The com.sun.source.util.Trees.instance() throws an IllegalArgumentException when the proxied processingEnv is passed.
	 *
	 * @param env possible proxied
	 * @return ProcessingEnvironment unwrapped from the proxy if proxied or the original processingEnv
	 */
	private static ProcessingEnvironment unwrap(ProcessingEnvironment env) {
		if (Proxy.isProxyClass(env.getClass())) {
			InvocationHandler invocationHandler = Proxy.getInvocationHandler(env);
			try {
				Field field = invocationHandler.getClass().getDeclaredField("val$delegateTo");
				field.setAccessible(true);
				Object o = field.get(invocationHandler);
				field.setAccessible(false);
				if (o instanceof ProcessingEnvironment) {
					return (ProcessingEnvironment) o;
				} else {
					env.getMessager().printMessage(ERROR, "got " + o.getClass() + " expected instanceof " + JavacProcessingEnvironment.class);
				}
			} catch (NoSuchFieldException | IllegalAccessException e) {
				env.getMessager().printMessage(ERROR, e.getMessage());
			}
		}
		return env;
	}

	@Override
	public synchronized void init(ProcessingEnvironment processingEnv) {
		super.init(processingEnv);
		ProcessingEnvironment env = unwrap(processingEnv);
		Trees trees = Trees.instance(env);
		Types types = env.getTypeUtils();
		DslScanner endScanner = new DslScanner(new UnterminatedSentenceScanner(new AnnotationUtils(loadEndMethodsFromFiles(), types), trees), trees, types);
		JavacTask.instance(env).addTaskListener(endScanner);
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		roundEnv.getElementsAnnotatedWith(EndMethodCheckFile.class).forEach(element -> {
			EndMethodCheckFile checkFile = element.getAnnotation(EndMethodCheckFile.class);
			try(Writer writer = processingEnv.getFiler().createResource(SOURCE_OUTPUT, "", checkFile.uniqueFileName()).openWriter()) {
				writer.write(checkFile.content());
			} catch (IOException e) {
				e.printStackTrace();
			}
		});
		return false;
	}

	private Map<String, Set<String>> loadEndMethodsFromFiles() {
		Map<String, Set<String>> map = new ConcurrentHashMap<>();
		try {
			Enumeration<URL> endingMethodResources = getSystemResources(EXTERNAL_END_METHOD_FILE);
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

	/**
	 * Assertion method to check, that requested EndMethodCheckFile got created.
	 * @param uniqueFileName Unique file name to be checked, which should have been previously requested to generate
	 *                       using the annotation EndMethodCheckFile(uniqueFileName)
	 * @throws IOException in case of any unexpected IO problems while accessing the file (not that it doesn't exist).
	 */
	public static void assertThatEndMethodCheckFileExists(String uniqueFileName) throws IOException {
		Enumeration<URL> resources = ClassLoader.getSystemResources(uniqueFileName);
		if(!resources.hasMoreElements()) {
			throw new AssertionError("End method check uniqueFileName named: " + uniqueFileName + " doesn't exist.\n" +
					"Either you didn't use anywhere the annotation @EndMethodCheckFile(\"" + uniqueFileName + "\")\n" +
					"or the annotation processor wasn't invoked by the compiler and you have to check it's configuration.\n" +
					"For more about annotation processor configuration and possible issues see:\n" +
					"https://github.com/c0stra/fluent-api-end-check");
		}
		URL url = resources.nextElement();
		if(resources.hasMoreElements()) {
			throw new IllegalArgumentException("Too many files with the same name: " + uniqueFileName + " found on class-path.\n" +
					"Chosen end method check file name is not unique.\n" +
					"Files found:\n" +
					url + "\n" + resources.nextElement());
		}
	}

}
