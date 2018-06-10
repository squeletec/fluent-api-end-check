package fluent.api;

import fluent.api.Dsl;

public class ExternalGenericEndMethodWithGenericParameterNotMissing {

	public void method(GenericDsl<String> dsl) {
		dsl.genericEnd(1);
	}

}
