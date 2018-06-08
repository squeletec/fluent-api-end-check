package fluent.api;

import fluent.api.Dsl;

public class EndMethodMissingInNesting {

	public void method(Dsl dsl) {
		dsl.nested();
	}

}
