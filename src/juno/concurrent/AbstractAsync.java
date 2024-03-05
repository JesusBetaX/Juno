package juno.concurrent;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public abstract class AbstractAsync<T>
        implements Async<T>, Callback<T>, Callable<T> {

    Dispatcher dispatcher;
    Callback<T> callback;
    Future future;
    volatile boolean isCancel;
    volatile boolean isRunning = false;

    public AbstractAsync() {
        this(Dispatcher.get());
    }

    public AbstractAsync(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    public void setDispatcher(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @Override
    public boolean isCancelled() {
        return (future != null) ? future.isCancelled() : isCancel;
    }

    @Override
    public boolean isDone() {
        return (future != null) ? future.isDone() : false;
    }

    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public void execute(Callback<T> callback) {
        this.callback = callback;
        execute();
    }

    @Override
    public void execute(final OnResponse<T> onResponse, final OnError onError) {
        this.execute(new CallbackAdapter<T>(onResponse, onError));
    }

    @Override
    public synchronized T await() throws Exception {
        return call();
    }

    public void execute() {
        isRunning = true;
        future = this.dispatcher.submit(new Runnable() {

            @Override
            public void run() {
                try {
                    final T result = call();
                    delivery(new Delivery<T>(AbstractAsync.this, result, null));

                } catch (final Exception error) {
                    delivery(new Delivery<T>(AbstractAsync.this, null, error));

                } finally {
                    isRunning = false;
                }
            }
        });
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        isRunning = false;
        if (future != null) {
            return future.cancel(mayInterruptIfRunning);
        } else {
            isCancel = true;
            return false;
        }
    }

    @Override
    public void onResponse(T result) throws Exception {
        if (callback != null) {
            callback.onResponse(result);
        }
    }

    @Override
    public void onFailure(Exception e) {
        if (callback != null) {
            callback.onFailure(e);
        }
    }

    /**
     * Libera la respuesta obtenida, al hilo de la UI.
     *
     * @param run
     */
    public void delivery(Runnable run) {
        dispatcher.delivery(run);
    }

    /**
     * Libera el resutado
     *
     * @param <V>
     */
    static final class Delivery<V> implements Runnable {

        public final Callback<V> callback;
        public final V result;
        public final Exception error;

        public Delivery(Callback<V> callback, V result, Exception error) {
            this.callback = callback;
            this.result = result;
            this.error = error;
        }

        @Override
        public void run() {
            if (result != null) {
                try {
                    callback.onResponse(result);
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            } else if (error != null) {
                callback.onFailure(error);
            }
        }

    }
}
