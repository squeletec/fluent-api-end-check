package fluent.api;

import fluent.api.Dsl;

public class UnmarkedEndMethod {

	public void method(Dsl dsl) {
		dsl.add().wrongEnd();
	}

}
