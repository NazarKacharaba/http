package com.kn.http;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
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
 * Very very simple HttpClient
 *
 * @author nk
 */

public final class HttpClient {

  public enum HttpMethod {
    POST("POST"), GET("GET"), PUT("PUT"), PATCH("PATCH");

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

  public Call call(final Request request) {

    return new Call() {
      public HttpURLConnection connection;

      @Override public Response execute() throws IOException {
        connection = (HttpURLConnection) request.url.openConnection();
        connection.setRequestMethod(request.method.methodName());
        connection.setReadTimeout(readTimeout);
        connection.setConnectTimeout(connectTimeout);
        copyHeaders(connection, request.headers);

        Response response = null;

        switch (request.method) {
          case PUT:
          case PATCH:
          case POST:
            response = doPost(connection, request, request.method.method);
            break;
          case GET:
            response = doGet(connection);
            break;
        }

        return response;
      }

      @Override public boolean isExecuted() {
        return connection != null;
      }

      @Override public void cancel() {
        if (connection == null) {
          throw new IllegalStateException("http call has not been executed");
        }
        // TODO what should we do to in order to say that call is really cancelled?
        // or another question, is it possible to cancel already started connection in a proper way?
        // if we are waiting for server to establish connection, can we cancel that?
        // seems that HttpURLConnection does not have such functionality
        connection.disconnect();
      }
    };
  }

  private final Response doGet(HttpURLConnection connection) throws IOException {
    connection.setDoOutput(false);

    InputStream inputStream = null;
    try {
      Response response = new Response();
      response.code = connection.getResponseCode();
      response.inputStream = inputStream = inputStream(connection);
      response.headers = connection.getHeaderFields();

      return response;
    } catch (IOException exception) {
      closeQuietly(inputStream);
      connection.disconnect();
      throw exception;
    }
  }

  private final Response doPost(HttpURLConnection connection, Request request, String method)
      throws IOException {
    connection.setDoOutput(request.stream != null);
    connection.setRequestMethod(method);

    OutputStream outputStream = null;
    InputStream inputStream = null;

    try {
      outputStream = connection.getOutputStream();
      copy(request.stream, outputStream);

      Response response = new Response();
      response.code = connection.getResponseCode();
      response.inputStream = inputStream = inputStream(connection);
      response.headers = connection.getHeaderFields();

      return response;
    } catch (IOException exception) {
      closeQuietly(outputStream);
      closeQuietly(inputStream);
      connection.disconnect();
      throw exception;
    }
  }

  //TODO find out which stream is better to provide to user
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

    if ("gzip".equals(connection.getHeaderField("Content-Encoding"))) {
      return new GZIPInputStream(stream);
    } else {
      return stream;
    }
  }

  private void copyHeaders(HttpURLConnection connection, Map<String, String> headers) {
    if (headers != null && !headers.isEmpty()) {
      for (Map.Entry<String, String> entry : headers.entrySet()) {
        connection.setRequestProperty(entry.getKey(), entry.getValue());
      }
    }
  }

  public interface Call {
    Response execute() throws IOException;

    boolean isExecuted();

    void cancel();
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

      public Builder() {
        this.method = HttpMethod.GET;
      }

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
        byte[] bytes = null;
        try {
          bytes = value.getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
          rethrow(e);
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

      public Builder params(Map<String, String> params) {
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
        checkArgs();

        Request request = new Request();
        URL url = null;
        try {
          url = new URL(baseUrl + addParams());
        } catch (MalformedURLException e) {
          rethrow(e);
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

      private void checkArgs() {
        if (method == HttpMethod.GET && stream != null) {
          throw new IllegalStateException("GET method can not have body");
        }
        if (baseUrl == null) {
          throw new IllegalStateException("Url must be set");
        }
        if (method == null) {
          throw new IllegalStateException("Http method must be set");
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

    public List<String> headers(String headerName) {
      List<String> list = headers.get(headerName);
      return list != null ? list : Collections.<String>emptyList();
    }

    public String header(String headerName) {
      List<String> headerFields = headers(headerName);
      if (!headerFields.isEmpty()) return headerFields.get(0);
      return null;
    }

    public int code() {
      return code;
    }

    public boolean isOk() {
      return code == 200;
    }

    public String string() {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      BufferedInputStream input = buffer();
      try {
        copy(input, output);
        String charset = charset();
        return charset == null ? output.toString() : output.toString(charset);
      } catch (IOException e) {
        rethrow(e);
      } finally {
        closeQuietly(input);
        closeQuietly(output);
      }
      return null;
    }

    public InputStream stream() {
      return inputStream;
    }

    public byte[] bytes() {
      ByteArrayOutputStream output = null;
      BufferedInputStream input = null;
      try {
        output = new ByteArrayOutputStream(contentLength());
        input = buffer();
        copy(input, output);
        return output.toByteArray();
      } finally {
        closeQuietly(input);
        closeQuietly(output);
      }
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
      String contentType = header("Content-Type");
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
      rethrow(e);
    } finally {
      closeQuietly(input);
    }
  }

  static void closeQuietly(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException ignored) {
      }
    }
  }

  static void rethrow(Throwable throwable) {
    HttpClient.<RuntimeException>sneakyThrow0(throwable);
  }

  static <T extends Throwable> void sneakyThrow0(Throwable throwable) throws T {
    throw (T) throwable;
  }
}