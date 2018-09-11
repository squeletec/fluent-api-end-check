package fluent.api;

import fluent.api.Dsl;

public class NestedEndMethodMissing {

	public void method(Dsl dsl) {
		dsl.end();
		dsl.add().nestedAllowingEnd().next();
	}

}
