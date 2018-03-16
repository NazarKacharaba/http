package com.kn.http;

import com.kn.http.HttpClient.Request;
import com.kn.http.HttpClient.Response;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author nk
 */

public final class NetworkDispatcher {
  static final int MAX_CONCURRENT_CONNECTION = 2;

  private final Deque<CancelableTask> running = new ArrayDeque<>();
  private final Deque<CancelableTask> waiting = new ArrayDeque<>();
  private final HttpClient httpClient;

  ThreadPoolExecutor executorService =
          new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
                  new SynchronousQueue<Runnable>(), new ThreadFactory() {
            private final AtomicInteger poolNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable runnable) {
              return new Thread(runnable, "network-dispatcher-thread-" + poolNumber.getAndIncrement());
            }
          });

  public NetworkDispatcher(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public Cancelable execute(final Request request, Callback<Response> callback) {
    CancelableTask task = createTask(request, callback);
    if (running.size() < MAX_CONCURRENT_CONNECTION) {
      running.add(task);
      task.future = executorService.submit(task);
    } else {
      waiting.add(task);
    }

    return task;
  }

  private CancelableTask createTask(Request request, Callback<Response> callback) {
    return new CancelableTask(request, callback) {
      HttpClient.Call call;

      @Override
      public void run() {
        if (isCanceled) return;

        Response response = null;
        Exception exception = null;
        try {
          call = httpClient.call(request);
          response = call.execute();
        } catch (Exception e) {
          exception = e;
        } finally {
          notifyFinished();
        }

        if (response != null) {
          success(response);
        } else if (exception != null) {
          failure(exception);
        }
      }

      @Override
      public void cancel() {
        if (call != null && !call.isExecuted()) {
          call.cancel();
        }
        super.cancel();
      }

      private void success(Response response) {
        if (!isCanceled && callback != null) {
          callback.onSuccess(response);
        }
      }

      private void failure(Exception e) {
        if (!isCanceled && callback != null) {
          callback.onFailure(e);
        }
      }

      private void notifyFinished() {
        running.remove(this);
        if (waiting.isEmpty()) return;

        CancelableTask nextTask = waiting.pop();
        running.add(nextTask);
        nextTask.future = executorService.submit(nextTask);
      }
    };
  }

  /**
   * Cancelable runnable
   */
  abstract class CancelableTask implements Runnable, Cancelable {
    volatile boolean isCanceled; //https://stackoverflow.com/a/3787435/1934509
    Future future;
    final Callback<Response> callback;
    final Request request;

    private CancelableTask(Request request, Callback<Response> callback) {
      this.request = request;
      this.callback = callback;
    }

    @Override
    public void cancel() {
      if (isCanceled) return;
      isCanceled = true;

      if (!waiting.remove(this)) {
        running.remove(this);
      }
      if (future != null) {
        future.cancel(false);
      }
    }
  }

  /**
   * Callback for response
   */
  public interface Callback<Response> {
    void onSuccess(Response response);

    void onFailure(Exception e);
  }

  public interface Cancelable {
    void cancel();
  }
}