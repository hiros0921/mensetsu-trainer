package jp.lightech.mensetsu.app.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ThinkingConfigDisabled;
import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.Question;
import jp.lightech.mensetsu.domain.port.Analysis;
import jp.lightech.mensetsu.domain.port.EngineCall;
import jp.lightech.mensetsu.domain.port.EngineObserver;
import jp.lightech.mensetsu.domain.port.InterviewerEngine;

/**
 * Claude API を使う面接官（仕様書8章②の実装側）。
 *
 * <h2>この実装が守っていること</h2>
 *
 * <ul>
 *   <li><b>例外を投げない。</b> 面接の途中で落ちるのは体験として最悪。作れなければ相槌でつなぐ。
 *   <li><b>遷移を判断しない。</b> 返すのは発言と観察だけ。次にどこへ行くかはステートマシンが決める。
 *   <li><b>最初の文字までを測る。</b> ストリーミングなので、利用者が待つのはそこまで。
 * </ul>
 *
 * <h2>失敗したときの振る舞い</h2>
 *
 * LLM の呼び出しは失敗する。レート制限、タイムアウト、応答が壊れている、キーが無効。
 * どれも面接の最中に起こりうる。
 *
 * <p>そのとき、この実装は <b>相槌を返して面接を続ける</b>。深掘りが1回できなくても面接は
 * 成立するが、例外で落ちたら成立しない。失敗したことは {@link EngineObserver} 経由で
 * 記録に残るので、あとから engine_calls を見れば分かる。
 *
 * <p>観察（analyzeAnswer）が失敗したときは、空の観察を返す。ここで嘘の観察を作ると、
 * 圧の計算や段数のカウントが狂う。「観察できなかった」を正直に返すほうがよい。
 */
public final class ClaudeEngine implements InterviewerEngine {

  private final AnthropicClient client;
  private final LlmSettings settings;
  private final EngineObserver observer;

  /** 直前に使った相槌。同じものを続けて出さないために覚えておく。 */
  private String lastFiller = "";

  public ClaudeEngine(AnthropicClient client, LlmSettings settings, EngineObserver observer) {
    this.client = client;
    this.settings = settings;
    this.observer = observer == null ? EngineObserver.NONE : observer;
  }

  @Override
  public String kind() {
    return "CLAUDE";
  }

  public LlmSettings settings() {
    return settings;
  }

  // ── 発言を作る ──

  @Override
  public Question nextQuestion(InterviewState state) {
    long started = System.nanoTime();
    StringBuilder text = new StringBuilder();
    long firstTokenMs = -1;

    try (StreamResponse<RawMessageStreamEvent> stream =
        client.messages().createStreaming(questionParams(state))) {

      for (RawMessageStreamEvent event : (Iterable<RawMessageStreamEvent>) stream.stream()::iterator) {
        String delta = textDelta(event);
        if (delta.isEmpty()) {
          continue;
        }
        if (firstTokenMs < 0) {
          firstTokenMs = millisSince(started);
        }
        text.append(delta);
        observer.onDelta(EngineCall.NEXT_QUESTION, delta);
      }
    } catch (RuntimeException e) {
      record(EngineCall.NEXT_QUESTION, firstTokenMs, started, false, describe(e));
      return fallbackFiller(state);
    }

    String cleaned = clean(text.toString());
    if (cleaned.isBlank()) {
      // 通信は成功したが中身が空。応答の形が変わったときに起きる。
      record(EngineCall.NEXT_QUESTION, firstTokenMs, started, false, "応答が空でした");
      return fallbackFiller(state);
    }

    record(EngineCall.NEXT_QUESTION, firstTokenMs, started, true, "");
    return toQuestion(state, cleaned);
  }

  private MessageCreateParams questionParams(InterviewState state) {
    MessageCreateParams.Builder b =
        MessageCreateParams.builder()
            .model(settings.model())
            .maxTokens(settings.maxTokens())
            .system(Prompts.system(state))
            .addUserMessage(Prompts.nextQuestion(state));
    applyThinking(b);
    return b.build();
  }

  /** 掘っている最中なら、何を何段目で掘っているかを発言に持たせる。 */
  private Question toQuestion(InterviewState state, String text) {
    var probe = state.probe();
    if (state.phase() == jp.lightech.mensetsu.domain.interview.Phase.PROBE && probe.hasCurrent()) {
      return Question.probe(text, probe.currentTerm(), probe.askedDepth());
    }
    return Question.generated(text);
  }

  // ── 回答を観察する ──

  @Override
  public Analysis analyzeAnswer(Answer answer, InterviewState state) {
    if (answer.isSilent()) {
      // LLM を呼ぶまでもない。何も言っていないので観察のしようがない。
      return Analysis.empty("無言または打ち切り");
    }

    long started = System.nanoTime();
    try {
      StructuredMessageCreateParams<AnalysisJson> params =
          MessageCreateParams.builder()
              .model(settings.model())
              .maxTokens(600)
              .system("あなたは面接の回答を観察する係です。評価や判定はしません。")
              .addUserMessage(Prompts.analyze(state, answer.text()))
              .outputConfig(AnalysisJson.class)
              .build();

      AnalysisJson json =
          client.messages().create(params).content().stream()
              .flatMap(cb -> cb.text().stream())
              .map(t -> t.text())
              .findFirst()
              .orElse(null);

      if (json == null) {
        record(EngineCall.ANALYZE_ANSWER, -1, started, false, "観察の応答が空でした");
        return Analysis.empty("観察できませんでした");
      }
      long ms = millisSince(started);
      record(EngineCall.ANALYZE_ANSWER, ms, started, true, "");
      return json.toDomain();

    } catch (RuntimeException e) {
      record(EngineCall.ANALYZE_ANSWER, -1, started, false, describe(e));
      // 【重要】ここで適当な観察を作らない。
      // 嘘の観察を返すと、圧の計算と段数のカウントが狂う。
      // 「観察できなかった」を正直に返すほうが、あとから追える。
      return Analysis.empty("観察に失敗しました");
    }
  }

  // ── 失敗したときのつなぎ ──

  /**
   * 深掘りが作れなかったので、相槌でつなぐ。
   *
   * <p>面接は続く。作れなかったことは記録に残っているので、あとから engine_calls を見れば
   * 「この回は LLM が落ちていた」と分かる。
   */
  private Question fallbackFiller(InterviewState state) {
    String line =
        jp.lightech.mensetsu.domain.interview.CannedLines.pick(
            state.phase(), state.pressure(), true, lastFiller);
    lastFiller = line;
    return Question.canned(line);
  }

  // ── 細かい道具 ──

  private void applyThinking(MessageCreateParams.Builder b) {
    if (LlmSettings.THINKING_OFF.equals(settings.thinkingMode())) {
      // 【重要】思考を切れるのは effort が high 以下のときだけ。
      // xhigh / max と組み合わせると API が 400 を返す。
      b.thinking(ThinkingConfigDisabled.builder().build());
    } else {
      b.thinking(ThinkingConfigAdaptive.builder().build());
    }
    b.outputConfig(OutputConfig.builder().effort(effortOf(settings.effort())).build());
  }

  private static OutputConfig.Effort effortOf(String name) {
    return switch (name == null ? "low" : name.toLowerCase()) {
      case "medium" -> OutputConfig.Effort.MEDIUM;
      case "high" -> OutputConfig.Effort.HIGH;
      default -> OutputConfig.Effort.LOW;
    };
  }

  /** ストリームの1件から、文字の断片だけを取り出す。思考ブロックは無視する。 */
  private static String textDelta(RawMessageStreamEvent event) {
    return event
        .contentBlockDelta()
        .flatMap(d -> d.delta().text())
        .map(t -> t.text())
        .orElse("");
  }

  /**
   * 生成物の後始末。
   *
   * <p>【重要】思考を切ると、内部用のタグが出力に混ざることがある。プロンプトで抑えてあるが、
   * 抑えきれなかったぶんをここで落とす。プロンプトだけに頼らない。
   */
  static String clean(String raw) {
    String s = raw.replaceAll("(?s)<thinking>.*?</thinking>", "");
    s = s.replaceAll("(?s)<[^>]{1,40}>", ""); // 短いタグだけ。文章中の不等号を壊さないため
    s = s.strip();
    // 面接官の発言だけが欲しいので、囲みが付いていたら外す。
    if (s.length() >= 2
        && ((s.startsWith("「") && s.endsWith("」")) || (s.startsWith("\"") && s.endsWith("\"")))) {
      s = s.substring(1, s.length() - 1).strip();
    }
    return s;
  }

  private void record(String purpose, long firstTokenMs, long started, boolean ok, String note) {
    long total = millisSince(started);
    observer.onCall(
        new EngineCall(
            purpose,
            kind(),
            settings.model(),
            firstTokenMs < 0 ? total : firstTokenMs,
            total,
            ok,
            note));
  }

  private static long millisSince(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000L;
  }

  /** 人が読む失敗の理由。キーの中身が混ざらないよう、型と短い文言だけにする。 */
  private static String describe(RuntimeException e) {
    String msg = e.getMessage() == null ? "" : e.getMessage();
    if (msg.length() > 200) {
      msg = msg.substring(0, 200) + "…";
    }
    return e.getClass().getSimpleName() + ": " + msg;
  }
}
