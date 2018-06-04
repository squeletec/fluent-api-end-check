package fluent.api;

import fluent.api.Dsl;

public class EndMethodMissingInAssignment {

	public void method(Dsl dsl) {
		Dsl var = null;
		var = dsl.add();
	}

}
