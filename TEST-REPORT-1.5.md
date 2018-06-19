## Fluent API end check v1.5
#### Test results
Tue, 19 Jun 2018 17:16:07 +0200

[EndProcessorTest](src/test/java/fluent/api/EndProcessorTest.java)
##### ✔  compilationShouldFailWhen ImmediateEndMethodMissing
##### ✔  compilationShouldFailWhen ImmediateEndMethodMissingAfterConstructor
##### ✔  compilationShouldFailWhen EndMethodMissing
##### ✔  compilationShouldFailWhen EndMethodMissingInNesting
##### ✔  compilationShouldFailWhen UnmarkedEndMethod
##### ✔  compilationShouldFailWhen NestedEndMethodMissing
##### ✔  compilationShouldFailWhen ExternalEndMethodMissing
##### ✔  compilationShouldFailWhen ExternalGenericEndMethodMissing
##### ✔  compilationShouldFailWhen ImmediateEndMethodMissingAfterAnonymousClass
##### ✔  compilationShouldPassWhen EndMethodNotMissing
##### ✔  compilationShouldPassWhen PassThroughEndMethodNotMissing
##### ✔  compilationShouldPassWhen EndMethodMissingInAssignment
##### ✔  compilationShouldPassWhen EndMethodCheckIgnored
##### ✔  compilationShouldPassWhen EndMethodNotMissingInNesting
##### ✔  compilationShouldPassWhen NestedEndMethodNotMissing
##### ✔  compilationShouldPassWhen ExternalEndMethodNotMissing
##### ✔  compilationShouldPassWhen ExternalGenericEndMethodNotMissing
##### ✔  compilationShouldPassWhen ExternalGenericEndMethodWithParameterNotMissing
##### ✔  compilationShouldPassWhen ExternalGenericEndMethodWithGenericParameterNotMissing
