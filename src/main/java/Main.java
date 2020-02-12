import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {

    private static final OkHttpClient httpClient = new OkHttpClient();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String URL = "http://localhost";

    public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    public static void main(String[] args) {
        try {
            AtomicInteger count = new AtomicInteger(0);
            Files.readAllLines(new File("/Users/jackhopkins/IdeaProjects/wallarm/data/anomalous.txt").toPath()).
                    stream().

                    map(line -> {
                        try {
                            return objectMapper.readTree(line);
                        } catch (JsonProcessingException e) {
                            e.printStackTrace();
                            return JsonNodeFactory.instance.objectNode();
                        }
                    }).

                    forEach(line -> {
                        try {
                            send(line, count.incrementAndGet());
                        } catch (IOException e) {
                            //e.printStackTrace();
                        }
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Request.Builder setMethod(Request.Builder builder, String method, Optional<JsonNode> bodyJson) {
        if (method.equals("GET")) return builder.get();
        if (method.equals("POST")) {
            String bodyString = bodyJson.get().toString();
            RequestBody body = RequestBody.create(JSON, bodyString);
            return builder.post(body);
        }
        if (method.equals("PUT")) return builder.get();
        if (method.equals("DELETE")) return builder.delete();
        if (method.equals("PATCH")) {
            String bodyString = bodyJson.get().toString();
            RequestBody body = RequestBody.create(JSON, bodyString);
            return builder.patch(body);
        }
        return builder;
    }
    private static Request.Builder setPathAndQuery(Request.Builder builder, String path, JsonNode query) {
        StringBuilder queryStringBuilder = new StringBuilder();
        query.iterator().forEachRemaining(string -> {
            queryStringBuilder.append(string.get(0) + "&");
        });
        String queryString = queryStringBuilder.toString();
        if (queryString.length() != 0) {
            queryString = "?" + queryString.substring(0, queryString.length() - 2);
        }
        return builder.url(URL + path + queryString);
    }
    private static Request.Builder setHeaders(Request.Builder builder, ObjectNode headers, String method) {
       if (headers.has("Content-Type")) {
           headers.remove("Content-Type");
           ArrayNode typeHeader = JsonNodeFactory.instance.arrayNode();
           typeHeader.add("application/json");
           headers.put("Content-Type", typeHeader);
       }
        headers.fieldNames().forEachRemaining(string -> {
            if (string.equals("Content-Type")) {
            //    if (method.equals("POST")) {
                    builder.removeHeader(string);
                    builder.addHeader(string, "application/json");
             //   }else{
             //       builder.addHeader(string,headers.get(string).get(0).textValue());
            //    }
            }else {
                builder.addHeader(string, headers.get(string).get(0).textValue());
            }
        });
        return builder;
    }
    private static void send(JsonNode csicRequest, int count) throws IOException {
        Request.Builder builder = new Request.Builder();
        //if (!csicRequest.get("method").asText().equals("POST")) return;
        builder = setMethod(builder, csicRequest.get("method").asText(), Optional.ofNullable(csicRequest.get("body")));
        builder = setPathAndQuery(builder, csicRequest.get("path").asText()+"-"+count, csicRequest.get("queryString"));
        builder = setHeaders(builder, (ObjectNode)csicRequest.get("headers"), csicRequest.get("method").asText());

        try (Response response = httpClient.newCall(builder.build()).execute()) {

            if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);

            // Get response body
            System.out.println(response.body().string());
        }

    }
}
