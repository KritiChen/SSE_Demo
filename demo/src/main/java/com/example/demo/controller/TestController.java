package com.example.demo.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Stream;

@RestController
@CrossOrigin
public class TestController {

    private static final String API_URL = "https://api.moonshot.cn/v1/chat/completions";
    private static final String API_KEY = "sk-XK4vpLnZZkEJ1vqHW9ssxhpizFKQb4kjqJfUb3cgki4RhOwZ";
    private static final String MODEL = "moonshot-v1-8k";

    private final WebClient webClient = WebClient.builder()
            .baseUrl(API_URL)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();

    @GetMapping(value = "/chat-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@RequestParam String question) {
        return webClient.post()
                .bodyValue(Map.of(
                        "model", MODEL,
                        "messages", List.of(Map.of("role", "user", "content", question)),
                        "stream", true
                ))
                .retrieve()
                .bodyToFlux(String.class)
                .doOnNext(raw -> System.out.println("🌐 Raw Chunk:\n" + raw)) // 打印原始数据
                .flatMap(this::processDataChunk)
                .filter(content -> !content.isEmpty())
                .map(content -> {
                    return ServerSentEvent.builder(content).build();
                })
                .onErrorResume(e -> {
                    System.err.println("❌ Error: " + e.getMessage());
                    return Flux.just(ServerSentEvent.builder("event: error\ndata: " + e.getMessage() + "\n\n").build());
                });
    }

    private Flux<String> processDataChunk(String rawChunk) {
        Flux<String> stringFlux = Flux.fromStream(
                Arrays.stream(rawChunk.split("\n\n")) // 分割SSE事件块// 去掉"data: "前缀
                        .flatMap(this::extractContent)
        );
        return stringFlux;
    }

    private Stream<String> extractContent(String jsonData) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonData);
            JsonNode choices = root.get("choices");
            if (choices != null && choices.isArray()) {
                for (JsonNode choice : choices) {
                    JsonNode delta = choice.get("delta");
                    if (delta != null && delta.has("content")) {
                        String content = delta.get("content").asText();
                        if (!content.isEmpty()) {
                            return Stream.of(content);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ JSON解析失败: " + jsonData);
        }
        return Stream.empty();
    }
}
