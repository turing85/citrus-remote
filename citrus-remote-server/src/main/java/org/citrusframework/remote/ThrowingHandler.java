package org.citrusframework.remote;

public interface ThrowingHandler<T> {
    void handle(T t) throws Exception;
}
