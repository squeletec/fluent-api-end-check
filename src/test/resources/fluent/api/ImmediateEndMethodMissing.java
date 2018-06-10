package fluent.api;

import fluent.api.Dsl;

public class ImmediateEndMethodMissing {

	public void method(Dsl dsl) {
		provide(dsl);
	}

	public Dsl provide(Dsl dsl) {
		return dsl;
	}
}
