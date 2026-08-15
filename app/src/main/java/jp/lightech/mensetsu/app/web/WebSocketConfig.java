package jp.lightech.mensetsu.app.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * WebSocket の口を1つ開ける。
 *
 * <p>認証は作り込まない（仕様書11章「ローカルで動けば足りる」）。
 * ただし、どのセッションかは接続ごとに持つので、他人の面接には触れない。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final InterviewService service;
  private final ObjectMapper json;

  public WebSocketConfig(InterviewService service, ObjectMapper json) {
    this.service = service;
    this.json = json;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry
        .addHandler(new InterviewWebSocketHandler(service, json), "/ws/interview")
        // ローカルで動かすだけなので、どこからでも繋げてよい。
        // 本番デプロイは対象外（仕様書1章）。
        .setAllowedOrigins("*");
  }
}
