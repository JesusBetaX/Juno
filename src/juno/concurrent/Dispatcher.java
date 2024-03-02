package juno.concurrent;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import juno.Platform;
import juno.util.Types;

public final class Dispatcher implements ThreadFactory {
  private static Dispatcher instance;
  
  public final String poolName;
  public final int threadLimit;
  private int nextId;

  /** Livera las respuestas al hilo de la UI. */
  private Executor executorDelivery;
  
  /** Ejecuta las llamadas "Call". */
  private ExecutorService executorService;
  
  public Dispatcher(String poolName, int threadLimit) {
    this.poolName = poolName;
    this.threadLimit = threadLimit;
  }
  
  public synchronized static Dispatcher get() {
    if (instance == null) {
      instance = new Dispatcher("Juno-Dispatcher", 4);
    }
    return instance;
  }

  public synchronized static void set(Dispatcher instance) {
    Dispatcher.instance = instance;
  }
  
  @Override public Thread newThread(Runnable runnable) {
    final Thread result = new Thread(runnable, poolName + "-" + nextId);
    nextId++;
    result.setPriority(Thread.MIN_PRIORITY);
    return result;
  }
  
  public synchronized ExecutorService getExecutorService() {
    if (executorService == null) {
      executorService = new ThreadPoolExecutor(threadLimit, threadLimit, 
              0L, TimeUnit.MILLISECONDS, 
              new LinkedBlockingQueue<Runnable>(), this);
    }
    return executorService;
  }
  
  public void setExecutorService(ExecutorService es) {
    executorService = es;
  }
  
  public Executor getExecutorDelivery() {
    if (executorDelivery == null) {
      executorDelivery = Platform.get();
    }
    return executorDelivery;
  }
  
  public void setExecutorDelivery(Executor executor) {
    executorDelivery = executor;
  }
  
  /** 
   * Ejecuta la llamada en la cola de peticiones.
   */
  public Future<?> submit(Runnable runnable) { 
    // Propone una tarea Runnable para la ejecución y devuelve un Futuro.
    return getExecutorService().submit(runnable);
  }
  
  /** 
   * Executes the given command at some time in the future
   */
  public void execute(Runnable runnable) { 
    // Propone una tarea Runnable para la ejecución y devuelve un Futuro.
    getExecutorService().execute(runnable);
  }
    
  public void delivery(Runnable runnable) {
    getExecutorDelivery().execute(runnable);
  }
  
  /**
   * Metodo que se encarga de liverar la respuesta obtenida, al hilo de la UI.
   * @param <V>
   * @param callback
   * @param result
   */
  public <V> void deliveryResponse(final Callback<V> callback, final V result) {
    if (callback == null) return;
    delivery(new Runnable() {  
      @Override public void run() {
        try {
          callback.onResponse(result);
        } catch (Exception error) {
          callback.onFailure(error);
        }
      }
    });
  }

  /**
   * Metodo que se encarga de liverar el error obtenido, al hilo de la UI.
   * @param callback
   * @param error
   */
  public void deliveryError(final Callback<?> callback, final Exception error) {
    if (callback == null) return;
    delivery(new Runnable() {
      @Override public void run() {
        callback.onFailure(error);
      }
    });
  }
    
  public <V> AsyncTask<V> newCallUserfunc(
    final Object obj, 
    final String method, 
    final Object... params
  ) { 
    return new AsyncTask<V>(new Task<V>() {
        @Override
        public V call() throws Exception {
            Class<?>[] types = Types.getTypes(params);
            final Method instanceMethod = obj.getClass()
                    .getDeclaredMethod(method, types);

            return (V) instanceMethod.invoke(obj, params);
        }
    }, this);
  }
    
  /**
   * 
   * @param <V>
   * @param clazz
   * @param method
   * @param params
   * @return 
   */
  public <V> AsyncTask<V> newCallUserfunc(
    final Class clazz, 
    final String method, 
    final Object... params
  ) {
    return new AsyncTask<V>(new Task<V>() {
        @Override
        public V call() throws Exception {
            Class<?>[] types = Types.getTypes(params);
            final Method instanceMethod = clazz
                    .getDeclaredMethod(method, types);

            return (V) instanceMethod.invoke(null, params);
        }
    }, this);
  }
  
  /**
   * 
   * @param <V>
   * @param obj
   * @param method
   * @param params
   * @return 
   */
  public static <V> AsyncTask<V> callUserfunc(
    final Object obj, 
    final String method, 
    final Object... params
  ) { 
    return Dispatcher.get().newCallUserfunc(obj, method, params);
  }
  
  /**
   * 
   * @param <V>
   * @param clazz
   * @param method
   * @param params
   * @return 
   */
  public static <V> AsyncTask<V> callUserfunc(
    final Class clazz, 
    final String method, 
    final Object... params
  ) {
    return Dispatcher.get().newCallUserfunc(clazz, method, params);
  }
  

}