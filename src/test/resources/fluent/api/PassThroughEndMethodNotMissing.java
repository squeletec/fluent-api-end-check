package fluent.api;

import fluent.api.Dsl;

public class PassThroughEndMethodNotMissing {

	public void method(Dsl dsl) {
		dsl.end();
		dsl.add().cancel();
	}

}
