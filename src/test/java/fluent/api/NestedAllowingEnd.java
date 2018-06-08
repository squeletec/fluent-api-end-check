package fluent.api;

public interface NestedAllowingEnd {

    @End
    void endAll();

    NestedAllowingEnd next();

}
