# Fluent API sentence end check
[![Build Status](https://travis-ci.org/c0stra/fluent-api-end-check.svg?branch=master)](https://travis-ci.org/c0stra/fluent-api-end-check)

Compile time check for end of the method chain in fluent API.

With fluent Java API, you are describing a complex action to be done, using a chain of methods. It may be, that the
action only happens, if you call some terminal method, like send(), store(), etc.

```java
config
    // Set method sets the property in memory.
    .set("url", "http://github.com/c0stra/fluent-api-end-check")
    // Store method does really store to a file!
    .store();
```


If by accident such method is forgotten, important things may not get executed, which may have dramatical
consequences.

This module comes with simple compiler extension, driven by annotation processing, that helps avoiding this.

Once you annotate some method with an annotation @End, the annotation processor will check every
statement, if this method wasn't forgotten:

See example of a builder, which needs to be terminated by method store():

```java

public interface Builder {

    Builder set(String name, String value);

    @End
    void store();

}
```

If you annotate a method like in the example above, then you'll get compilation error when you forget to use it:

See wrong code example:
```java
builder.set("key1", "value1")
       .set("key2", "value2");
```
You'll get following compilation error:
```text
error: Method chain must end with the method: store()
```

## User guide
### 1. Mark sentence ending methods
We'll refer to any Java statement, which consists of a chain of methods using fluent API, as _"fluent API sentence"_,
_"fluent sentence"_, or simply _"sentence"_.

_"Sentence ending method"_ is then a method, which needs to end the sentence. On the example from above a sentence with
ending method `store()` can look like this:
```java
config.set("url", "http://github.com/c0stra/fluent-api-end-check/").store();
```

In order to enforce compile time check of the ending method, we need first to be able to mark it.
#### 1.1 Mark using `@End` annotation
In order to be able to mark ending method using `@End` annotation, following dependency need to be used:
```xml
<dependency>
    <groupId>foundation.fluent.api</groupId>
    <artifactId>fluent-api-end-check</artifactId>
    <version>1.1</version>
</dependency>
```
The annotation can then be used to mark the ending method:
```java
public interface Config {

    Config set(String name, String value);

    @End
    void store();
}
```

#### 1.2 Mark using provided list of ending methods
!!! NOT YET SUPPORTED !!!

The project may use 3rd party builders / fluent API, which is not under our control, and therefore ending methods
cannot be annotated. For such case there is a plan to allow providing a simple plain text file containing list of fully qualified methods, which are
the ending methods to check. It would be simply something like:
```text
java.lang.StringBuilder.toString
foundation.fluent.api.Config.store
```
It should be possible to cumulatively collect all such lists of ending methods across all transitive dependencies.
#### 1.3 Mark multiple ending methods
It is possible to mark multiple methods of one interface / class as ending methods. That effectively means, that it has
to end with one of them.

In fact, functionally there is no benefit of marking more methods, only the compile time error may be hinting on
all options, how to end the sentence.
```java
public interface FluentAction {

    FluentAction parameter(String value);

    @End
    void perform();
    
    @End
    void cancel();
}
```
In such case if we call neither `perform()` nor `cancel()`, then the error will mention them both:
```text
error: Method chain must end with one of the following methods: [perform(), cancel()]
```
### 2. Configure maven project to use compile time end check
In order to use the compile time check for fluent API ending methods, you have to activate the annotation processor
in the target project, that should use (not necessarily define) the ending methods. So you may have project A defining
the fluent `Config` class, and then project B, which uses it. The project B is the one, that needs to trigger the
compile time check.

For that reason you can notice, that the `EndProcessor` is not triggered by occurrence of the `@End`
annotation, but used all the time.

#### 2.1 Using standard annotation processor resolution on class-path
The module comes with the annotation processor, and also standard Java service binding of it, so the Java compiler
will find and use the processor.

By default, the Java compiler searches for annotation processors (and such service bindings) on the class path. So
having the dependency mentioned above is sufficient, even as transitive dependency, and you have the compile time check
activated.

In terms of our projects A and B, B depends on A, and A depends on `fluent-api-end-check`, so B has it in transitive
dependencies, and therefore on class-path and the check will work.
#### 2.2 Using compiler annotation processor path
As annotation processors may have their own dependencies (it's not our case), which are solely compile time, and
shouldn't be propagated as transitive dependencies further, Java compiler allows to specify them on annotation processor
path instead of class path.

!__Such configuration will effectively disable annotation processors present on the
class-path__! (in our case as transitive dependency)

See Oracle documentation for `-processorpath` option at:
https://docs.oracle.com/javase/7/docs/technotes/tools/solaris/javac.html#options

If the project, that should be checked for ending methods, uses other processors configured using this option,
__you have to include explicitly the end check processor__ too.

It can be done e.g. using maven compiler plugin:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-compiler-plugin</artifactId>
            <version>3.7.0</version>
            <configuration>
                <annotationProcessorPaths>
                    <annotationProcessorPath>
                        <!-- Your original annotation processor -->
                    </annotationProcessorPath>
                    <annotationProcessorPath>
                        <groupId>foundation.fluent.api</groupId>
                        <artifactId>fluent-api-end-check</artifactId>
                        <version>1.0-SNAPSHOT</version>
                    </annotationProcessorPath>
                </annotationProcessorPaths>
            </configuration>
        </plugin>
    </plugins>
</build>
```

### 3. Compile check of fluent sentence end in action.
Once the annotation processor is active, and there are ending methods configured (marked), compilation may throw
errors mentioned above.

#### 3.1 When is the check applied
It's not desired always to perform the check. So let's have a look at the situations, when it
would apply.

| Situation             | Example                      | Applies or not          |
| --------------------- | ---------------------------- | ----------------------- |
| Expression statement  | config.set("", "");          | __YES__                 |
| Assignment            | config = config.set("", ""); | __NO__ - may end later  |
| Passed as argument    | method(config.set("", ""));  | __NO__ - may end inside |

### 3.1 How to bypass the check explicitly using `@IgnoreMissingEndMethod`
Although the check itself tries to recognize situations, when it shouldn't apply the check, there might
be situations, when it would apply it, but it's still not desired. For such cases an annotation
`@IgnoreMissingEndMethod` can be used on a method, to bypass it's statements for such check.

Typical example would be unit tests:
```java
public class TestFluentApi {

    @Test
    @IgnoreMissingEndMethod
    public void test() {
        // Mock something
        new Config().set("url", "http://github.com/c0stra/fluent-api-end-check/");
        // Perform verifications of the set() method
    }

}
```
Without ignoring the end method check, this test method would throw compilation error.
