/*
 * BSD 2-Clause License
 *
 * Copyright (c) 2019, Ondrej Fischer
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

import fluent.api.End;
import fluent.api.Start;

import javax.lang.model.element.Element;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.emptySet;
import static java.util.Objects.isNull;
import static java.util.stream.Collectors.toSet;

/**
 * Class with utility methods for testing Java source elements for annotations indicating @Start or @End of fluent API
 * sentence completeness checks.
 */
class AnnotationUtils {

	private final Map<String, Set<String>> endMethodsCache;
	private final Types types;

	AnnotationUtils(Map<String, Set<String>> endMethodsCache, Types types) {
		this.endMethodsCache = endMethodsCache;
		this.types = types;
	}

	boolean isStart(Element element, String[] errorMessage) {
		Start start = element.getAnnotation(Start.class);
		if(isNull(start)) {
			return false;
		}
		errorMessage[0] = start.value();
		return true;
	}

	boolean isEnd(Element element, String[] errorMessage) {
        End end = element.getAnnotation(End.class);
        if(isNull(end)) {
            return endMethodsCache.getOrDefault(element.getEnclosingElement().toString(), emptySet()).contains(element.toString());
        }
        if(!end.message().isEmpty()) {
            errorMessage[0] = end.message();
        }
        return true;
	}

	boolean requiresEnd(TypeMirror tree, String[] errorMessage) {
		Set<String> methods = getEndMethods(tree, errorMessage);
		if(!methods.isEmpty()) {
			if(errorMessage[0] == null) errorMessage[0] = "Method chain must end with " + (methods.size() > 1 ? "one of the following methods: " : "method: ") + methods;
			return true;
		}
		return false;
	}

	private Set<String> getEndMethods(TypeMirror typeMirror, String[] errorMessage) {
		Element element = types.asElement(typeMirror);
		if(isNull(element)) {
			return emptySet();
		}
		String elementName = element.toString();
		if(!endMethodsCache.containsKey(elementName)) {
			endMethodsCache.put(elementName, getEndMethods(element, errorMessage));
		}
		return endMethodsCache.get(elementName);
	}

	private Set<String> getEndMethods(Element element, String[] errorMessage) {
		Set<String> methods = element.getEnclosedElements().stream().filter(e -> isEnd(e, errorMessage)).map(Object::toString).collect(toSet());
		types.directSupertypes(element.asType()).forEach(type -> methods.addAll(getEndMethods(type, errorMessage)));
		return methods.isEmpty() ? emptySet() : methods;
	}

}
