package com.kn.http;

import com.kn.http.HttpClient.Request;
import com.kn.http.HttpClient.Response;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author nk
 */

public final class NetworkDispatcher {
  static final int MAX_CONCURRENT_CONNECTION = 2;

  private final Object LOCK = new Object();
  private final Deque<CancelableTask> running = new ArrayDeque<>();
  private final Deque<CancelableTask> waiting = new ArrayDeque<>();
  private final HttpClient httpClient;

  private ExecutorService executorService =
      new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
          new SynchronousQueue<Runnable>(), new ThreadFactory() {
        private final AtomicInteger poolNumber = new AtomicInteger(1);

        @Override public Thread newThread(Runnable runnable) {
          return new Thread(runnable, "network-dispatcher-thread-" + poolNumber.getAndIncrement());
        }
      });

  public NetworkDispatcher(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public Cancelable execute(final Request request, Callback<Response> callback) {
    if (request == null) throw new NullPointerException("Request is null");

    CancelableTask task = createTask(request, callback);
    if (running.size() < MAX_CONCURRENT_CONNECTION) {
      running.add(task);
      executorService.execute(task);
    } else {
      waiting.add(task);
    }

    return task;
  }

  private CancelableTask createTask(Request request, Callback<Response> callback) {
    return new CancelableTask(request, callback) {
      HttpClient.Call call;

      @Override public void run() {
        if (isCanceled) return;

        Response response = null;
        try {
          call = httpClient.call(request);
          response = call.execute();
        } catch (IOException e) {
          failure(e);
        }

        if (response != null) {
          success(response);
        }

        notifyFinished();
      }

      @Override public void cancel() {
        if (call != null && call.isExecuted()) {
          call.cancel();
        }
        super.cancel();
      }

      private void success(Response response) {
        if (!isCanceled && callback != null) {
          callback.onSuccess(response);
        }
      }

      private void failure(IOException e) {
        if (!isCanceled && callback != null) {
          callback.onFailure(e);
        }
      }

      private void notifyFinished() {
        running.remove(this);
        CancelableTask nextTask;

        // make safe concurrent mutation of 'waiting' deque
        synchronized (LOCK) {
          if (waiting.isEmpty()) return;

          nextTask = waiting.pop();
        }

        running.add(nextTask);
        executorService.execute(nextTask);
      }
    };
  }

  /** Cancelable runnable */
  abstract class CancelableTask implements Runnable, Cancelable {
    volatile boolean isCanceled; //https://stackoverflow.com/a/3787435/1934509
    final Callback<Response> callback;
    final Request request;

    private CancelableTask(Request request, Callback<Response> callback) {
      this.request = request;
      this.callback = callback;
    }

    @Override public void cancel() {
      if (isCanceled) return;
      isCanceled = true;

      if (!waiting.remove(this)) {
        running.remove(this);
      }
    }
  }

  /** Callback for response */
  public interface Callback<Response> {
    void onSuccess(Response response);

    void onFailure(IOException e);
  }

  public interface Cancelable {
    void cancel();
  }
}