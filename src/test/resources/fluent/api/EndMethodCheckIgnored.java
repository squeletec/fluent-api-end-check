package fluent.api;

import fluent.api.Dsl;

public class EndMethodCheckIgnored {

	@IgnoreMissingEndMethod
	public void method(Dsl dsl) {
		dsl.add();
	}

}
