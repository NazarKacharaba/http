package com.kn.http;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Very very simple com.kn.http.HttpClient
 *
 * @author nk
 */

public final class HttpClient {

  public enum HttpMethod {
    POST("POST"), GET("GET");

    private final String method;

    HttpMethod(String arg) {
      this.method = arg;
    }

    public String methodName() {
      return method;
    }

  }

  private int readTimeout = 1000 * 20; // default 20 sec
  private int connectTimeout = 1000 * 20; // default 20 sec

  public HttpClient() {
  }

  public void readTimeout(int timeout) {
    if (timeout <= 0) {
      throw new IllegalArgumentException("timeout <= 0");
    }
    this.readTimeout = timeout;
  }

  public void connectTimeout(int timeout) {
    if (timeout <= 0) {
      throw new IllegalArgumentException("timeout <= 0");
    }
    this.connectTimeout = timeout;
  }

  public Response execute(Request request) throws IOException {
    HttpURLConnection connection = (HttpURLConnection) request.url.openConnection();
    connection.setRequestMethod(request.method.methodName());
    connection.setReadTimeout(readTimeout);
    connection.setConnectTimeout(connectTimeout);
    copyHeaders(connection, request.headers);

    switch (request.method) {
      case POST:
        return doPost(connection, request);
      case GET:
        return doGet(connection);
      default:
        throw new UnsupportedOperationException("Currently only GET and POST is supported");
    }
  }

  private final Response doGet(HttpURLConnection connection) throws IOException {
    connection.setDoOutput(false);

    Response response = new Response();
    response.code = connection.getResponseCode();
    response.inputStream = inputStream(connection);
    response.headers = connection.getHeaderFields();

    return response;
  }

  private final Response doPost(HttpURLConnection connection, Request request) throws IOException {
    connection.setDoOutput(true);

    copy(request.stream, connection.getOutputStream());

    Response response = new Response();
    response.code = connection.getResponseCode();
    response.inputStream = inputStream(connection);
    response.headers = connection.getHeaderFields();

    return response;
  }

  //TODO find out which stream better to provide to user
  private InputStream inputStream(HttpURLConnection connection) throws IOException {
    InputStream stream;
    if (connection.getResponseCode() < 400) {
      stream = connection.getInputStream();
    } else {
      stream = connection.getErrorStream();
      if (stream == null) {
        stream = connection.getInputStream();
      }
    }

    if (!"gzip".equals(connection.getHeaderField("Content-Encoding"))) {
      return stream;
    } else {
      return new GZIPInputStream(stream);
    }
  }

  private void copyHeaders(HttpURLConnection connection, Map<String, String> headers) {
    if (headers != null && !headers.isEmpty()) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        connection.setRequestProperty(entry.getKey(), entry.getValue());
      }
    }
  }

  public final static class Request {
    URL url;
    HttpMethod method;
    InputStream stream;
    Map<String, String> headers;

    private Request() {
    }

    public static class Builder {
      private String baseUrl;
      private HttpMethod method;
      private InputStream stream;
      private StringBuilder paramQuery;
      private final Map<String, String> headers = new HashMap<>();

      public Builder url(String url) {
        this.baseUrl = url;
        return this;
      }

      public Builder header(String headerName, String value) {
        headers.put(headerName, value);
        return this;
      }

      public Builder method(HttpMethod method) {
        this.method = method;
        return this;
      }

      public Builder body(String value) {
        byte[] bytes;
        try {
          bytes = value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException(e);
        }

        stream = new ByteArrayInputStream(bytes);
        return this;
      }

      public Builder body(InputStream argStream) {
        stream = argStream;
        return this;
      }

      public Builder contentType(String value) {
        header("Content-Type", value);
        return this;
      }

      public Builder params(HashMap<String, String> params) {
        if (params == null || params.isEmpty()) return this;
        paramQuery = new StringBuilder();

        Set<Map.Entry<String, String>> entries = params.entrySet();
        for (Map.Entry<String, String> entry : entries) {
          paramQuery.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }

        int lastSymbol = paramQuery.length() - 1;
        if (paramQuery.charAt(lastSymbol) == '&') {
          paramQuery.deleteCharAt(lastSymbol);
        }
        return this;
      }

      public Request build() {
        checkCondition();

        Request request = new Request();
        URL url;
        try {
          url = new URL(baseUrl + addParams());
        } catch (MalformedURLException e) {
          throw new RuntimeException(e);
        }
        request.url = url;
        request.stream = stream;
        request.method = method;
        request.headers = headers;
        return request;
      }

      private String addParams() {
        if (paramQuery == null || paramQuery.length() == 0) {
          return "";
        }
        return "?" + paramQuery.toString();
      }

      private void checkCondition() {
        if (method != HttpMethod.POST && stream != null) {
          throw new IllegalArgumentException("Currently only POST supports body");
        }
        if (baseUrl == null) {
          throw new IllegalArgumentException("Url must be set");
        }
        if (method == null) {
          throw new IllegalArgumentException("Http method must be set");
        }
      }
    }
  }

  public static final class Response {
    private Map<String, List<String>> headers;
    private int code;
    private InputStream inputStream;

    private Response() {
    }

    public List<String> getHeaderFields(String headerName) {
      List<String> h = this.headers.get(headerName);
      return h == null ? Collections.emptyList() : h;
    }

    public String getHeaderField(String headerName) {
      List<String> headerFields = getHeaderFields(headerName);
      if (!headerFields.isEmpty()) return headerFields.get(0);
      return null;
    }

    public int code() {
      return code;
    }

    public boolean isOk() {
      return code() == 200;
    }

    public String string() {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      copy(buffer(), output);
      String charset = charset();
      if (charset == null) return output.toString();
      try {
        return output.toString(charset);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    public InputStream stream() {
      return inputStream;
    }

    public byte[] bytes() {
      ByteArrayOutputStream output = new ByteArrayOutputStream(contentLength());
      copy(buffer(), output);
      return output.toByteArray();
    }

    public BufferedInputStream buffer() {
      return new BufferedInputStream(stream());
    }

    public int contentLength() {
      List<String> strings = headers.get("Content-Length");
      try {
        return Integer.parseInt(strings.get(0));
      } catch (Exception ignored) {
      }
      return 0;
    }

    public String charset() {
      String contentType = getHeaderField("Content-Type");
      if (contentType == null || contentType.length() == 0) return null;
      int postSemi = contentType.indexOf(';') + 1;
      if (postSemi > 0 && postSemi == contentType.length()) return null;
      String[] params = contentType.substring(postSemi).split(";");
      final String charsetParam = "charset";
      for (String param : params) {
        String[] split = param.split("=");
        if (split.length != 2) continue;
        if (!charsetParam.equals(split[0])) continue;

        String charset = split[1];
        int length = charset.length();
        if (length == 0) continue;
        if (length > 2 && '"' == charset.charAt(0) && '"' == charset.charAt(length - 1)) {
          charset = charset.substring(1, length - 1);
        }
        return charset;
      }
      return null;
    }
  }

  static void copy(final InputStream input, final OutputStream output) {
    if (input == null) return;
    final byte[] buffer = new byte[1024 * 10];
    int read;
    try {
      while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
    } catch (IOException e) {
      throw new RuntimeException(e);
    } finally {
      try {
        input.close();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
