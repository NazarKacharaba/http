package com.kn.http;

import com.kn.http.NetworkDispatcher.Callback;
import java.io.IOException;

import static com.kn.http.HttpClient.*;

/**
 * @author nk
 */
class HttpMethodTest {

  public static void main(String... args) throws IOException {
    pureHttpClient();
    networkDispatcher();
    delayTest();
    redirectTest();
  }

  static void pureHttpClient() throws IOException {
    HttpClient client = new HttpClient();
    Request request = new Request.Builder()
        .method(HttpMethod.GET)
        .url("https://api.github.com/repos/zikil/http/contributors")
        .build();

    Response response = client.execute(request);
    if (response.isOk()) {
      System.out.println("Response " + response.string());
    } else {
      System.out.println("Request failed " + response.string());
    }
  }

  static void networkDispatcher() {
    HttpClient client = new HttpClient();
    NetworkDispatcher dispatcher = new NetworkDispatcher(client);

    Request request = new Request.Builder()
        .method(HttpMethod.GET)
        .url("https://api.github.com/repos/square/okio/contributors")
        .build();
    
    // TODO thread pool can not take many concurrent tasks
    for (int i = 0; i < 10; i++) {
      dispatcher.execute(request, new Callback<Response>() {
        @Override public void onSuccess(Response response) {
          if (response.isOk()) {
            System.out.println("Response " + response.string());
          } else {
            System.out.println("Request failed " + response.string());
          }
          System.out.println("thread is " + Thread.currentThread().getName());
        }

        @Override public void onFailure(Exception e) {
          e.printStackTrace();
        }
      });
    }

    // if need
    //call.cancel();
  }

  static void delayTest() throws IOException {
    HttpClient client = new HttpClient();
    Request request = new Request.Builder()
        .method(HttpMethod.GET)
        .url("http://httpbin.org/delay/3")
        .build();

    Response response = client.execute(request);
    if (response.isOk()) {
      System.out.println("Response " + response.string());
    } else {
      System.out.println("Request failed " + response.string());
    }
  }


  static void redirectTest() throws IOException {
    HttpClient client = new HttpClient();
    Request request = new Request.Builder()
        .method(HttpMethod.GET)
        .url("http://httpbin.org/redirect/6")
        .build();

    Response response = client.execute(request);
    if (response.isOk()) {
      System.out.println("Response " + response.string());
    } else {
      System.out.println("Request failed " + response.string());
    }
  }
}