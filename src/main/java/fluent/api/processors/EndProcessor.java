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


import com.sun.source.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import java.util.Set;

import static com.sun.source.util.TaskEvent.Kind.ANALYZE;

/**
 * Pseudo annotation processor of special annotation @End marking terminal methods in fluent API. It actually doesn't do
 * any annotation processing, only hooks on the compiler, and checks missing terminal methods in expression statements.
 */
@SupportedAnnotationTypes("*")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class EndProcessor extends AbstractProcessor {

	@Override
	public synchronized void init(ProcessingEnvironment env) {
		super.init(env);
		JavacTask.instance(env).addTaskListener(new TaskListener() {

			private EndScanner scanner = new EndScanner(Trees.instance(env), env.getTypeUtils());

			@Override
			public void started(TaskEvent taskEvent) {
				// Nothing to do when task started.
			}

			@Override
			public void finished(TaskEvent taskEvent) {
				if(taskEvent.getKind() == ANALYZE) {
					scanner.scan(taskEvent.getCompilationUnit(), null);
				}
			}
		});
	}

	@Override
	public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
		return false;
	}

}
