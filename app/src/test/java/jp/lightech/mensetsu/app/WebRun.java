package jp.lightech.mensetsu.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 起動中のアプリに WebSocket でつないで、面接を最後まで通す。
 *
 * <pre>
 *   ./gradlew :app:bootRun                        （別の端末で起動しておく）
 *   ./gradlew :app:webrun                         英語・音声入力
 *   ./gradlew :app:webrun --args="ENGLISH TEXT"   英語・テキスト入力
 *   ./gradlew :app:webrun --args="ENGINEER"       エンジニア面接
 * </pre>
 *
 * <h2>なぜブラウザではなくこれで確かめるか</h2>
 *
 * 確かめたいのは、サーバーが時間をどう測るか。制限時間・沈黙の打ち切り・
 * 入力方式の記録は、すべてサーバー側の判断になっている（仕様書5章）。
 * 画面を手で操作すると、90秒の制限時間を待つあいだ何も分からないし、
 * 「8秒黙る」を毎回同じ長さで再現できない。ここでは黙る時間を指定して流す。
 *
 * <h2>この通しで見ていること</h2>
 *
 * <ul>
 *   <li>制限時間が interviewer_profiles から来ていること（案T1・90/8/3秒）
 *   <li>黙ったままだと、猶予3秒＋沈黙8秒で打ち切られること
 *   <li>相槌が英語であること（第8段階で直したところ）
 *   <li>結果に入力方式が出ること、沈黙欄に注記が付くこと（案E-2の条件）
 * </ul>
 *
 * <p>【重要】これは課金される。Claude API を実際に呼ぶ。
 */
public final class WebRun {

  private static final ObjectMapper JSON = new ObjectMapper();

  /**
   * 台本。STAR で答えるつもりの回答を並べる。null は「何も言わずに黙る」。
   *
   * <p>【重要】固定の台本だけでは、深さの軸が一度も動かない。
   * 深掘りの質問は毎回ちがう用語について作られるので、台本の側が噛み合わない。
   * 一度これで「4段中0段」を見て、採点の不具合かと思った。実際は分析が正しく
   * 「Java選定理由でなくバッチ改善の話」と見抜いていた。
   * 掘られている用語は question の topic に入っているので、そこから組み立てる。
   */
  private static final String[] SCRIPT = {
    "Sure. I'm a backend engineer with about eight years of experience, mostly in Java."
        + " Recently I built an order matching service with Spring Boot and PostgreSQL.",
    "The situation was that our nightly batch took four hours and often missed the SLA."
        + " My task was to bring it under one hour. I profiled it, found the N+1 queries,"
        + " and replaced them with a single join plus batched inserts."
        + " As a result it finished in about twenty minutes.",
    null, // ここで黙る。打ち切りが動くか見る
    "We used optimistic locking with a version column. When two updates collided,"
        + " the second one retried. I chose that over pessimistic locking because"
        + " conflicts were rare and I did not want to hold row locks across the batch.",
    "Yes. I would like to know how the team decides what goes into the platform team"
        + " versus each product team.",
    "Thank you for your time today.",
    "Thanks.",
    "Thank you.",
  };

  public static void main(String[] args) throws Exception {
    // 入力方式を指定できるようにしてある。
    // 【重要】テキストで受けたときに沈黙の注記が出るかは、テキストで通さないと分からない。
    // 音声だけで確かめて「出るはず」と書くと、そこが試されないまま残る。
    String mode = args.length > 0 ? args[0].toUpperCase() : "ENGLISH";
    String input = args.length > 1 ? args[1].toUpperCase() : "VOICE";
    String url = args.length > 2 ? args[2] : "ws://localhost:8080/ws/interview";
    // 時間を測るのは英語面接だけ。ほかのモードでは「黙る」を再現しても打ち切られない。
    boolean timed = "ENGLISH".equals(mode);

    Client client = new Client();
    WebSocket ws =
        HttpClient.newHttpClient()
            .newWebSocketBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .buildAsync(URI.create(url), client)
            .join();

    System.out.printf("── %s を %s で通します ──%n%n", mode, input);
    send(ws, JSON.writeValueAsString(java.util.Map.of("type", "start", "mode", mode)));

    for (String line : SCRIPT) {
      if (!client.awaitQuestion(120)) {
        break; // 結果に着いたか、落ちた
      }
      if (line == null) {
        if (!timed || !"VOICE".equals(input)) {
          // テキストでは「黙る」を再現しない。打ち切りは音声の通しで確かめてある。
          send(ws, JSON.writeValueAsString(
              java.util.Map.of("type", "answer", "text", "Sorry, let me think.", "input", input)));
          continue;
        }
        System.out.println("   ……黙ります（入力を一切送らない）");
        // 猶予3秒＋沈黙8秒。打ち切りが来るまで待つ。
        boolean cut = client.awaitCutoff(20);
        System.out.println(cut ? "   → 打ち切られました" : "   → 打ち切られませんでした（想定外）");
        // 打ち切られたら、画面と同じように「今ある文字」を送る。今回は空。
        send(ws, "{\"type\":\"answer\",\"text\":\"\",\"input\":\"VOICE\",\"timedOut\":true}");
        continue;
      }
      // 深掘りには、掘られている用語について答える。台本のままだと噛み合わない。
      String reply = client.probing() ? probeAnswer(client.topic(), client.depth()) : line;

      // 話しているあいだ、入力があったことを送り続ける（画面の onresult と同じ）。
      speak(ws, reply);
      send(ws, JSON.writeValueAsString(
          java.util.Map.of("type", "answer", "text", reply, "input", input)));
    }

    client.awaitResult(180);
    ws.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    client.report();
  }

  /**
   * 掘られている用語に噛み合う回答を作る。
   *
   * <p>段が深くなるほど「なぜ選んだか」「何を捨てたか」に踏み込む形にしてある。
   * 語数は 40〜150語（案E-2 の帯）に収まる長さ。
   */
  private static String probeAnswer(String topic, int depth) {
    String t = topic == null || topic.isBlank() ? "that stack" : topic;
    return switch (depth) {
      case 1 -> ("We chose %s because the team already ran it in production, so the on-call"
              + " cost was known. The situation was a rewrite with four engineers and three"
              + " months. My task was to pick something we could operate, not just write."
              + " I compared it against two alternatives and %s won on tooling and hiring."
              + " As a result we shipped on time and had no production incident in the first"
              + " quarter.")
          .formatted(t, t);
      case 2 -> ("The main alternative was Go, and I did seriously consider it. What we gave up"
              + " by choosing %s was raw startup time and memory footprint, which matters for"
              + " short-lived jobs. We measured it: cold start was about 900 milliseconds"
              + " versus 30. We accepted that because our services are long-running, and the"
              + " ecosystem we needed for the batch layer was mature on our side.")
          .formatted(t);
      default -> ("Where %s hurt us was the reflective startup path. We saw it when the pod"
              + " restarted under load and the readiness probe timed out. I moved the heavy"
              + " initialisation behind a warm-up endpoint and raised the probe delay."
              + " If I did it again I would look at ahead-of-time compilation first, because"
              + " that is the part I could not fix by configuration alone.")
          .formatted(t);
    };
  }

  /** 話している最中を再現する。1〜2秒おきに「入力があった」だけを送る。 */
  private static void speak(WebSocket ws, String line) throws Exception {
    int chunks = Math.min(6, Math.max(2, line.length() / 40));
    for (int i = 0; i < chunks; i++) {
      send(ws, "{\"type\":\"input\"}");
      Thread.sleep(1_200);
    }
  }

  private static void send(WebSocket ws, String payload) {
    ws.sendText(payload, true).join();
  }

  /** 受け取ったものを記録しつつ、待ち合わせを提供する。 */
  private static final class Client implements WebSocket.Listener {

    private final StringBuilder buffer = new StringBuilder();
    private final List<String> fillers = new ArrayList<>();
    private final List<String> cutoffs = new ArrayList<>();
    private JsonNode result;
    private long firstLimitMs = -1;
    private volatile String topic = "";
    private volatile int depth = 0;
    private CountDownLatch question = new CountDownLatch(1);
    private CountDownLatch cutoff = new CountDownLatch(1);
    private final CountDownLatch finished = new CountDownLatch(1);

    boolean awaitQuestion(int seconds) throws InterruptedException {
      boolean got = question.await(seconds, TimeUnit.SECONDS);
      question = new CountDownLatch(1);
      cutoff = new CountDownLatch(1);
      return got && result == null;
    }

    boolean probing() {
      return depth > 0;
    }

    String topic() {
      return topic;
    }

    int depth() {
      return depth;
    }

    boolean awaitCutoff(int seconds) throws InterruptedException {
      return cutoff.await(seconds, TimeUnit.SECONDS);
    }

    void awaitResult(int seconds) throws InterruptedException {
      finished.await(seconds, TimeUnit.SECONDS);
    }

    @Override
    public void onOpen(WebSocket ws) {
      ws.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
      buffer.append(data);
      if (last) {
        handle(buffer.toString());
        buffer.setLength(0);
      }
      ws.request(1);
      return CompletableFuture.completedFuture(null);
    }

    private void handle(String text) {
      try {
        JsonNode n = JSON.readTree(text);
        switch (n.path("type").asText()) {
          case "started" ->
              System.out.printf(
                  "開始: engine=%s policy=%s 仮の基準=%s%n%n",
                  n.path("engine").asText(),
                  n.path("policy").asText(),
                  n.path("provisional").asBoolean());
          case "question" -> {
            topic = n.path("topic").asText("");
            depth = n.path("depth").asInt(0);
            long limit = n.path("limitMs").asLong(-1);
            if (firstLimitMs < 0) {
              firstLimitMs = limit;
            }
            System.out.printf(
                "%n[%s/%d] %s%n   （表情:%s 制限:%.1f秒）%n",
                n.path("phase").asText(),
                n.path("turnNo").asInt(),
                n.path("text").asText(),
                n.path("faceLabel").asText(),
                limit / 1000.0);
            question.countDown();
          }
          case "filler" -> {
            String f = n.path("text").asText();
            fillers.add(f);
            System.out.println("   相槌: " + f);
          }
          case "cutoff" -> {
            cutoffs.add(n.path("reason").asText());
            System.out.println("   打ち切り: " + n.path("reason").asText());
            cutoff.countDown();
          }
          case "timing" ->
              System.out.printf(
                  "   [%s 初文字 %dms / 全体 %dms]%n",
                  n.path("purpose").asText(),
                  n.path("firstTokenMs").asLong(),
                  n.path("totalMs").asLong());
          case "result" -> {
            result = n;
            question.countDown();
            finished.countDown();
          }
          case "error", "unavailable" ->
              System.out.println("   !! " + n.path("message").asText());
          default -> { /* tick と delta は量が多いので出さない */ }
        }
      } catch (Exception e) {
        System.out.println("   受信を解釈できませんでした: " + e.getMessage());
      }
    }

    void report() {
      System.out.println("\n════════ 結果 ════════");
      if (result == null) {
        System.out.println("結果に到達しませんでした");
        return;
      }
      System.out.printf(
          "判定 %s（%s） 合計 %d点  基準 %s%n",
          result.path("grade").asText(),
          result.path("meaning").asText(),
          result.path("total").asInt(),
          result.path("version").asText());
      System.out.println("入力方式: " + result.path("inputMethod").asText("（出ていない）"));
      System.out.println();
      for (JsonNode a : result.path("axes")) {
        System.out.printf(
            "  %-6s 素点%3d × 重み%2d = %5.1f点%s%s%n    %s%n",
            a.path("label").asText(),
            a.path("raw").asInt(),
            a.path("weight").asInt(),
            a.path("points").asDouble(),
            a.path("measured").asBoolean() ? "" : "（測れず）",
            a.path("emphasised").asBoolean() ? "  ★内訳で強調" : "",
            a.path("why").asText());
      }
      System.out.println("\n──── 確認 ────");
      if (firstLimitMs < 0) {
        // 時間を測らないモード。ここが正の値になっていたら、設定が漏れている。
        System.out.println("制限時間: 出していない（時間を測らない面接官）");
        System.out.println("打ち切り: " + (cutoffs.isEmpty() ? "なし（想定どおり）" : "!! " + cutoffs));
        return;
      }
      System.out.printf(
          "制限時間が profiles から来たか: %s（%.0f秒）%n",
          firstLimitMs > 80_000 && firstLimitMs <= 90_000 ? "はい" : "いいえ",
          firstLimitMs / 1000.0);
      System.out.println("打ち切りが動いたか: " + (cutoffs.isEmpty() ? "いいえ" : "はい " + cutoffs));
      boolean japanese = fillers.stream().anyMatch(f -> f.codePoints().anyMatch(c -> c > 0x2E80));
      System.out.println("相槌がすべて英語か: " + (japanese ? "いいえ " + fillers : "はい"));
    }
  }
}
