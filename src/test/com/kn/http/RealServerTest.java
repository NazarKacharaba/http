package com.kn.http;

import com.kn.http.NetworkDispatcher.Callback;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.kn.http.HttpClient.*;
import static org.junit.Assert.assertTrue;

/**
 * @author nk
 */
public class RealServerTest {

  static final String BASE = "http://i.imgur.com/";
  static final String EXT = ".jpg";
  static final String[] IMAGE_URLS = {
          BASE + "CqmBjo5" + EXT, BASE + "zkaAooq" + EXT, BASE + "0gqnEaY" + EXT,
          BASE + "9gbQ7YR" + EXT, BASE + "aFhEEby" + EXT, BASE + "0E2tgV7" + EXT,
          BASE + "P5JLfjk" + EXT, BASE + "nz67a4F" + EXT, BASE + "dFH34N5" + EXT,
          BASE + "FI49ftb" + EXT, BASE + "DvpvklR" + EXT, BASE + "DNKnbG8" + EXT,
          BASE + "yAdbrLp" + EXT, BASE + "55w5Km7" + EXT, BASE + "NIwNTMR" + EXT,
          BASE + "DAl0KB8" + EXT, BASE + "xZLIYFV" + EXT, BASE + "HvTyeh3" + EXT,
          BASE + "Ig9oHCM" + EXT, BASE + "7GUv9qa" + EXT, BASE + "i5vXmXp" + EXT,
          BASE + "glyvuXg" + EXT, BASE + "u6JF6JZ" + EXT, BASE + "ExwR7ap" + EXT,
          BASE + "Q54zMKT" + EXT, BASE + "9t6hLbm" + EXT, BASE + "F8n3Ic6" + EXT,
          BASE + "P5ZRSvT" + EXT, BASE + "jbemFzr" + EXT, BASE + "8B7haIK" + EXT,
          BASE + "aSeTYQr" + EXT, BASE + "OKvWoTh" + EXT, BASE + "zD3gT4Z" + EXT,
          BASE + "z77CaIt" + EXT,
  };


  private HttpClient client;
  private NetworkDispatcher dispatcher;

  @Before
  public void setUp() {
    client = new HttpClient();
    dispatcher = new NetworkDispatcher(client);
  }

  @Test
  public void pureHttpClient() throws IOException {
    HttpClient client = new HttpClient();
    Request request = new Request.Builder()
            .method(HttpMethod.GET)
            .url("https://api.github.com/repos/zikil/http/contributors")
            .build();

    Response response = client.call(request).execute();
    if (response.isOk()) {
      System.out.println("Response succeed " + response.string());
    } else {
      System.out.println("Request failed " + response.string());
    }
  }

  @Test
  public void networkDispatcher() throws InterruptedException {
    int count = IMAGE_URLS.length;
    CountDownLatch latch = new CountDownLatch(count);
    for (int i = 0; i < IMAGE_URLS.length; i++) {
      int finalI = i;
      Request request = new Request.Builder()
              .method(HttpMethod.GET)
              .url(IMAGE_URLS[i])
              .build();

      dispatcher.execute(request, new Callback<Response>() {
        @Override
        public void onSuccess(Response response) {
          System.out.println("finished " + IMAGE_URLS[finalI]);
          latch.countDown();
        }

        @Override
        public void onFailure(Exception e) {
          e.printStackTrace();
          latch.countDown();
        }
      });
    }

    int maxCount[] = new int[1];

    new Thread(new Runnable() {
      @Override
      public void run() {
        while (latch.getCount() != 0) {
          int currActiveThreads = dispatcher.executorService.getActiveCount();
          if (maxCount[0] < currActiveThreads) {
            maxCount[0] = currActiveThreads;
          }
          try {
            Thread.sleep(20);
          } catch (InterruptedException e) {
            e.printStackTrace();
          }
        }
      }
    }).start();

    boolean waitedTillTheEnd = latch.await(20, TimeUnit.SECONDS);

    assertTrue("Did not finish all requests", waitedTillTheEnd);
    assertTrue("Exceeded maximum number of threads:" + maxCount[0], maxCount[0] <= NetworkDispatcher.MAX_CONCURRENT_CONNECTION);
  }

  @Test
  public void delayTest() throws IOException {
    Request request = new Request.Builder()
            .method(HttpMethod.GET)
            .url("http://httpbin.org/delay/3")
            .build();

    Response response = client.call(request).execute();
    if (response.isOk()) {
      System.out.println("Response " + response.string());
    } else {
      System.out.println("Request failed " + response.string());
    }
  }


  @Test
  public void redirectTest() throws IOException {
    Request request = new Request.Builder()
            .method(HttpMethod.GET)
            .url("http://httpbin.org/redirect/6")
            .build();

    Response response = client.call(request).execute();
    if (response.isOk()) {
      System.out.println("Response " + response.string());
    } else {
      System.out.println("Request failed " + response.string());
    }
  }

  @Test
  public void cancelTest() throws InterruptedException {
    Request request = new Request.Builder()
            .method(HttpMethod.GET)
            .url("http://httpbin.org/delay/6")
            .build();

    final Call call = client.call(request);
    new Thread(new Runnable() {
      @Override
      public void run() {
        try {
          Response execute = call.execute();
          System.err.println("executed " + execute.string());
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }).start();

    Thread.sleep(2000);
    call.cancel();
  }

  @Test
  public void cancelNetworkDispatcherTest() throws InterruptedException {
    ArrayList<NetworkDispatcher.Cancelable> calls = new ArrayList<>();
    Request request = new Request.Builder()
            .method(HttpMethod.GET)
            .url("http://httpbin.org/delay/5")
            .build();
    final Object[] networkResult = new Response[1];
    final Exception[] networkError = new Exception[1];

    Callback<Response> callback = new Callback<Response>() {
      @Override
      public void onSuccess(Response response) {
        networkResult[0] = response;
      }

      @Override
      public void onFailure(Exception e) {
        networkError[0] = e;
      }
    };

    for (int i = 0; i < 10; i++) {
      dispatcher.execute(request, callback);
    }

    Thread.sleep(1000);
    for (NetworkDispatcher.Cancelable call : calls) {
      call.cancel();
    }

    assertTrue(networkResult[0] == null);
    assertTrue(networkError[0] == null);
  }
}