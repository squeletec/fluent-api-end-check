package fluent.api;

import fluent.api.Dsl;

public class ExternalGenericEndMethodWithParameterNotMissing {

	public void method(GenericDsl<String> dsl) {
		dsl.end("Hi");
	}

}
