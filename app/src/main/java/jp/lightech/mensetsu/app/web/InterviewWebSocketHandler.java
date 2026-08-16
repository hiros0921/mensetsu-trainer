package jp.lightech.mensetsu.app.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.InputMethod;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.Question;
import jp.lightech.mensetsu.domain.interview.Step;
import jp.lightech.mensetsu.domain.port.EngineCall;
import jp.lightech.mensetsu.domain.scoring.AxisScore;
import jp.lightech.mensetsu.domain.scoring.Score;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * 画面とのやりとり。
 *
 * <h2>WebSocket にする理由（仕様書8章③）</h2>
 *
 * タイマーによる打ち切りをサーバー側で制御するため。第8段階（英語面接）で効いてくる。
 * ここではまだ打ち切りを使わないが、経路は先に作っておく。
 *
 * <h2>【重要】画面からフェーズ遷移を指示できないこと</h2>
 *
 * 受け取るのは「面接を始める」と「回答」だけ。次にどのフェーズへ行くかを指示する手段は無い。
 * 仕様書3章「フェーズはサーバー側で管理する。クライアントから遷移を指示させない」。
 *
 * <h2>相槌を先に流す（第1段階 Q5）</h2>
 *
 * 回答を受け取ったら、まず相槌を送る。LLM を呼ばないので0msで届く。
 * その裏で深掘りを作り、生成中の文字を流す。待ち時間が「間」に見える。
 */
public class InterviewWebSocketHandler extends TextWebSocketHandler {

  private final InterviewService service;
  private final ObjectMapper json;

  /**
   * 面接1回ぶんの生成を回すスレッド。
   *
   * <p>WebSocket の受信スレッドで LLM を待つと、その間ほかの受信を処理できない。
   * 面接は1人1セッションなので、待たせても本人だけだが、
   * 打ち切りのタイマー（第8段階）を同じスレッドで動かせなくなる。
   */
  private final ExecutorService worker = Executors.newCachedThreadPool();

  /**
   * 制限時間と沈黙を見張る時計（仕様書4-3・5章）。
   *
   * <p>【重要】判定はサーバー側でする。クライアントに任せない。
   * クライアントが送るのは「入力があった」という事実だけで、
   * 何秒の沈黙とみなすか、打ち切るかどうかはここが決める。
   *
   * <p>0.5秒ごとに見に行く。細かくしても体感は変わらず、粗くすると打ち切りが遅れて見える。
   */
  private final ScheduledExecutorService ticker = Executors.newSingleThreadScheduledExecutor();

  public InterviewWebSocketHandler(InterviewService service, ObjectMapper json) {
    this.service = service;
    this.json = json;
  }

  @Override
  protected void handleTextMessage(WebSocketSession ws, TextMessage message) throws Exception {
    JsonNode in = json.readTree(message.getPayload());
    String type = in.path("type").asText("");

    switch (type) {
      case "start" -> worker.submit(() -> start(ws, in));
      case "answer" -> worker.submit(() -> answer(ws, in));
      // 入力があった、という事実だけ。何秒の沈黙かはサーバーが決める。
      case "input" -> onInput(ws);
      default -> send(ws, Map.of("type", "error", "message", "知らない指示です: " + type));
    }
  }

  private void onInput(WebSocketSession ws) {
    InterviewService.Live session = sessionOf(ws);
    if (session != null) {
      service.onInput(session);
    }
  }

  private InterviewService.Live sessionOf(WebSocketSession ws) {
    Object id = ws.getAttributes().get("publicId");
    return id instanceof UUID publicId ? service.find(publicId) : null;
  }

  /**
   * 制限時間と沈黙を見張る。
   *
   * <p>打ち切るときは、クライアントに「今ある文字を送れ」と伝える。
   * 文字はブラウザにしか無いので、こちらからは取れない。指示だけを出す。
   */
  private void watch(WebSocketSession ws, InterviewService.Live session, int turnNo) {
    ticker.schedule(() -> {
      if (!ws.isOpen()
          || session.state().isFinished()
          || session.turnNoNow() != turnNo
          // 回答を受け取り済み。次の問が出るまで、打ち切る相手がいない。
          || !service.isTiming(session)) {
        return; // 役目を終えた
      }
      var cut = service.shouldCutOff(session);
      if (cut.shouldCut()) {
        send(ws, Map.of("type", "cutoff", "reason", cut.reason()));
        return;
      }
      long remaining = service.remainingMs(session);
      if (remaining >= 0) {
        send(ws, Map.of("type", "tick", "remainingMs", remaining,
            "silenceMs", service.silenceMs(session)));
      }
      watch(ws, session, turnNo);
    }, 500, TimeUnit.MILLISECONDS);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
    // 【重要】RESULT に着いていなければ「中断」として記録される（第1段階 Q4）。
    Object id = ws.getAttributes().get("publicId");
    if (id instanceof UUID publicId) {
      service.disconnect(publicId);
    }
  }

  // ── 面接を始める ──

  private void start(WebSocketSession ws, JsonNode in) {
    try {
      Mode mode = Mode.valueOf(in.path("mode").asText("ENGINEER"));
      InterviewService.Live session = service.begin(mode, sinkFor(ws));
      ws.getAttributes().put("publicId", session.publicId());

      send(ws, Map.of(
          "type", "started",
          "sessionId", session.publicId().toString(),
          "mode", mode.name(),
          "engine", service.usingLlm() ? "CLAUDE" : "STUB",
          // 基準が仮のモードでは、画面にそう出す。動くことと決まっていることは違う。
          "provisional",
          !jp.lightech.mensetsu.domain.scoring.ScoringPolicies.isDecided(mode),
          "policy", session.policy().label()));
      sendQuestion(ws, session);

    } catch (IllegalStateException e) {
      // 基準が決まっていないモード。画面に理由をそのまま出す。
      send(ws, Map.of("type", "unavailable", "message", e.getMessage()));
    } catch (Exception e) {
      send(ws, Map.of("type", "error", "message", "面接を開始できませんでした: " + e.getMessage()));
    }
  }

  // ── 回答を受け取る ──

  private void answer(WebSocketSession ws, JsonNode in) {
    UUID publicId = (UUID) ws.getAttributes().get("publicId");
    InterviewService.Live session = publicId == null ? null : service.find(publicId);
    if (session == null) {
      send(ws, Map.of("type", "error", "message", "面接が始まっていません"));
      return;
    }

    String text = in.path("text").asText("");
    InputMethod input =
        "VOICE".equals(in.path("input").asText("TEXT")) ? InputMethod.VOICE : InputMethod.TEXT;
    // 【重要】時間はサーバーが測った値を使う。クライアントが送ってきた値は使わない。
    // 端末の時計がずれていても、タブが裏に回っても、同じ面接になるようにするため。
    // 時間を測らないモードでは0が入る。
    Answer answer =
        new Answer(
            text,
            input,
            (int) service.elapsedMs(session),
            (int) service.silenceMs(session),
            in.path("timedOut").asBoolean(false));

    // 【重要】時間を読んだあとで計測を止める。順番が逆だと0秒で記録される。
    // 止めておかないと、生成待ちのあいだに打ち切りが飛び、同じ問へ二度回答が来る。
    service.stopClock(session);

    try {
      // ① 相槌を即座に返す。LLM を呼ばないので待ち時間ゼロ。
      //    中身があるかは、この時点ではまだ観察していない。長さで当たりを付ける。
      //    実際の面接官も、聞き終わる前に「なるほど」と言っている。
      send(ws, Map.of("type", "filler", "text", service.filler(session, text.length() >= 20)));

      // ② その裏で本物を作る。生成中の文字は sink から流れる。
      Step step = service.submit(session, answer);

      if (step.state().isFinished()) {
        Score score = service.scoreAndSave(session);
        Map<String, Object> out = new LinkedHashMap<>(result(score, session));
        // 【重要】どちらの入力方式で受けたかを結果に出す（第8段階の指示）。
        // 沈黙の軸は入力方式で意味が変わる。測っていない条件を隠さない。
        step.state().result().ifPresent(o -> out.put("inputMethod", o.inputMethodSummary()));
        send(ws, out);
      } else {
        sendQuestion(ws, session);
      }
    } catch (Exception e) {
      send(ws, Map.of("type", "error", "message", "処理できませんでした: " + e.getMessage()));
    }
  }

  // ── 送る ──

  private void sendQuestion(WebSocketSession ws, InterviewService.Live session) {
    var state = session.state();
    Question q = state.pendingQuestion().orElseThrow();
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("type", "question");
    m.put("text", q.text());
    m.put("kind", q.kind().name());
    m.put("topic", q.topic());
    m.put("depth", q.depth());
    m.put("phase", state.phase().name());
    m.put("pressure", state.pressure());
    // 表情（仕様書6章）。画面は key から画像のファイル名を組み立てる。
    var face = service.expression(session);
    m.put("face", face.fileKey());
    m.put("faceLabel", face.label());
    m.put("turnNo", state.turnNo() + 1);
    // 制限時間。時間を測らないモードでは -1 で、画面はタイマーを出さない。
    m.put("limitMs", service.remainingMs(session));
    send(ws, m);
    if (service.remainingMs(session) >= 0) {
      watch(ws, session, session.state().turnNo() + 1);
    }
  }

  /**
   * 判定と内訳を送る。
   *
   * <p>仕様書7章「この内訳表示が、アプリの価値の中心です。判定だけなら、既存のチャットで
   * 足ります」。だから why を必ず含める。
   */
  private Map<String, Object> result(Score score, InterviewService.Live session) {
    List<Map<String, Object>> axes = new ArrayList<>();
    for (Score.Contribution c : score.contributions()) {
      AxisScore raw = score.breakdown().get(c.axis());
      Map<String, Object> m = new LinkedHashMap<>();
      m.put("label", c.axis().label());
      m.put("raw", c.raw());
      m.put("weight", c.weight());
      m.put("points", Math.round(c.points() * 10) / 10.0);
      m.put("measured", c.measured());
      m.put("why", raw.why());
      // 【重要】重みと強調は別のもの。
      // 圧迫面接の一貫性は、重みを25に抑えたうえで表示では目立たせる。
      // 押されて話が変わったことは、点数に反映されなくても本人に伝える価値がある。
      m.put("emphasised", session.policy().isEmphasised(c.axis()));
      axes.add(m);
    }
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("type", "result");
    out.put("grade", score.grade().name());
    out.put("meaning", score.grade().meaning());
    out.put("total", score.total());
    out.put("version", score.thresholdVersion());
    out.put("axes", axes);
    // 強調の理由は基準ごとに違う。画面に固定文を持たせない。
    out.put("emphasisNote", session.policy().emphasisNote());
    score.biggestGap().ifPresent(g -> {
      out.put("nextFocus", g.axis().label());
      out.put("nextFocusGain", Math.round((100 - g.raw()) * g.weight() / 10.0) / 10.0);
    });
    return out;
  }

  /** 生成中の文字と、呼び出しの記録を画面へ流す。 */
  private InterviewService.Sink sinkFor(WebSocketSession ws) {
    return new InterviewService.Sink() {
      @Override
      public void delta(String text) {
        send(ws, Map.of("type", "delta", "text", text));
      }

      @Override
      public void call(EngineCall call) {
        // 応答時間を画面にも出す。遅いときに「何が遅いか」が本人に見えるほうがよい。
        send(ws, Map.of(
            "type", "timing",
            "purpose", call.purpose(),
            "firstTokenMs", call.firstTokenMs(),
            "totalMs", call.totalMs(),
            "ok", call.ok(),
            "note", call.errorNote()));
      }
    };
  }

  private void send(WebSocketSession ws, Map<String, ?> payload) {
    try {
      if (!ws.isOpen()) {
        return;
      }
      // 同じセッションへの送信は1本ずつ。並行して書くと WebSocket が壊れる。
      synchronized (ws) {
        ws.sendMessage(new TextMessage(json.writeValueAsString(payload)));
      }
    } catch (Exception e) {
      // 送れないのは、画面が閉じたとき。面接側は afterConnectionClosed で片付く。
    }
  }
}
