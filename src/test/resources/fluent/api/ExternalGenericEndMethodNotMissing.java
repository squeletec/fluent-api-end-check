package fluent.api;

import fluent.api.Dsl;

public class ExternalGenericEndMethodNotMissing {

	public void method(GenericDsl<String> dsl) {
		dsl.end();
	}

}
