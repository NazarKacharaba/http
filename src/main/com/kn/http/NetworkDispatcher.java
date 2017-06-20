package com.kn.http;

import com.kn.http.HttpClient.Request;
import com.kn.http.HttpClient.Response;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author nk
 */

public final class NetworkDispatcher {
  final HttpClient httpClient;
  // TODO does not work properly when request's size become more than LinkedBlockingQueue's size
  private final ExecutorService service =
      new ThreadPoolExecutor(0, 6, 60, TimeUnit.SECONDS, new LinkedBlockingQueue<>(6),
          new ThreadFactory() {
            private final AtomicInteger poolNumber = new AtomicInteger(1);

            @Override public Thread newThread(Runnable runnable) {
              return new Thread(runnable,
                  "network-dispatcher-thread-" + poolNumber.getAndIncrement());
            }
          });

  public NetworkDispatcher(HttpClient httpClient) {
    this.httpClient = httpClient;
  }

  public Call execute(final Request request, Callback<Response> callback) {
    WeakReference<Callback<Response>> weakReference = new WeakReference<>(callback);

    CancelableTask runnable = createRunnable(request, weakReference);

    service.execute(runnable);

    return new Call(runnable);
  }

  private final CancelableTask createRunnable(final Request request,
      final WeakReference<Callback<Response>> callback) {
    return new CancelableTask() {
      @Override public void run() {
        if (isCanceled) return;

        try {
          success(httpClient.execute(request));
        } catch (IOException e) {
          failure(e);
        }
      }

      private void success(Response response) {
        if (!isCanceled && callback.get() != null) {
          try {
            callback.get().onSuccess(response);
          } catch (Exception e) {
            failure(e);
          }
        }
      }

      private void failure(Exception e) {
        if (!isCanceled && callback.get() != null) {
          callback.get().onFailure(e);
        }
      }
    };
  }

  /** Cancelable runnable */
  static abstract class CancelableTask implements Runnable {
    volatile boolean isCanceled; //https://stackoverflow.com/a/3787435/1934509

    public void cancel() {
      this.isCanceled = true;
    }
  }

  /** Callback for response */
  public interface Callback<Response> {
    void onSuccess(Response response);

    void onFailure(Exception e);
  }

  /** Represent request call that can be canceled */
  public final static class Call {

    private final CancelableTask runnable;

    private Call(CancelableTask runnable) {
      this.runnable = runnable;
    }

    /** Cancel current http request */
    public void cancel() {
      runnable.cancel();
    }
  }
}
