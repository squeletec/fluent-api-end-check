package fluent.api;

import fluent.api.Dsl;

public class EndMethodNotMissingInNesting {

	public void method(Dsl dsl) {
		dsl.end();
		dsl.add().nested().done().end();
	}

}
