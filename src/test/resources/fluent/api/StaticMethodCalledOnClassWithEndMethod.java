package fluent.api;

import fluent.api.Dsl;

public class StaticMethodCalledOnClassWithEndMethod {

	public void method(Dsl dsl) {
		Dsl.accept(dsl);
	}

}
