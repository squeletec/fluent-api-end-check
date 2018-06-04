package fluent.api;

import fluent.api.Dsl;

public class EndMethodNotMissing {

	public void method(Dsl dsl) {
		dsl.end();
		dsl.add().end();
	}

}
