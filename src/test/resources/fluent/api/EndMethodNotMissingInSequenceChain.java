package fluent.api;

import fluent.api.Dsl;

public class EndMethodNotMissingInSequenceChain {

	public void method(Dsl dsl) {
		Dsl.call().parameter1(5).parameter2("A");
	}

}
