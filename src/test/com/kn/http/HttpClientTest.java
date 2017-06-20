package com.kn.http;

import com.kn.http.HttpClient.Request;
import org.junit.Before;
import org.junit.Test;

import static com.kn.http.HttpClient.*;
import static org.junit.Assert.fail;

/**
 * @author nk
 */
public class HttpClientTest {
  HttpClient client;

  @Before
  public void setUp() throws Exception {
    client = new HttpClient();
  }

  @Test()
  public void httpMethodIsNotSet() {
    try {
      new Request.Builder().url("http://google.com").build();
      fail("Expected " + IllegalStateException.class.getName() + ", http method is not set");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void urlIsNotSet() throws Exception {
    try {
      new Request.Builder().method(HttpMethod.GET).build();
      fail("Expected " + IllegalStateException.class.getName() + ", url is not set");
    } catch (IllegalStateException expected) {
    }
  }

  @Test
  public void bodyIsSetForGet() throws Exception {
    try {
      new Request.Builder().method(HttpMethod.GET)
          .url("http://google.com")
          .body("{}")
          .build();
      fail("Expected " + IllegalStateException.class.getName() + ", body is set for GET method");
    } catch (IllegalStateException expected) {
    }
  }
}
