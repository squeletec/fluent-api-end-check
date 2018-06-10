package fluent.api;

public interface GenericDsl<T> {

    GenericDsl<T> next();

    void end();

    void end(T t);

    <U> void genericEnd(U u);

}
