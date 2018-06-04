# Fluent API sentence end check
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
error: Method chain must end with the following method: store()
```

[![Build Status](https://travis-ci.org/c0stra/fluent-api-end-check.svg?branch=master)](https://travis-ci.org/c0stra/fluent-api-end-check)
