package fluent.api;

import fluent.api.Dsl;

public class ImmediateEndMethodMissingAfterAnonymousClass {

	public void method(Dsl dsl) {
		new Dsl() {
			@Override
			public String toString() {
				return "AAA";
			}
		};
	}

}
