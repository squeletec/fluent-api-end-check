package fluent.api;

import fluent.api.Dsl;

public class EndMethodMissingInSequenceChain {

	public void method(Dsl dsl) {
		Dsl.call().parameter1(5);
	}

}
