package fluent.api;

import fluent.api.Dsl;

public class ImmediateEndMethodMissingAfterConstructor {

	public void method(Dsl dsl) {
		new Dsl();
	}

}
