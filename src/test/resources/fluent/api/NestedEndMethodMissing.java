package fluent.api;

import fluent.api.Dsl;

public class NestedEndMethodNotMissing {

	public void method(Dsl dsl) {
		dsl.end();
		dsl.add().nestedAllowingEnd().next();
	}

}
