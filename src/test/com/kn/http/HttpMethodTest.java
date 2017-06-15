package com.kn.http;

import java.io.IOException;

import static com.kn.http.HttpClient.*;

/**
 * @author nk
 */
class HttpMethodTest {

  public static void main(String... args) throws IOException {
    HttpClient client = new HttpClient();
    Request request = new Request.Builder()
        .method(HttpMethod.GET)
        .url("https://api.github.com/repos/google/dagger/contributors")
        .build();

    Response response = client.execute(request);
    if (response.isOk()) {
      System.out.println("Response " + response.string());
    } else {
      System.out.println("Request failed " + response.string());
    }
  }
}