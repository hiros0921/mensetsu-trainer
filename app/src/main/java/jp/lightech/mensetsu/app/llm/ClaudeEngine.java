package jp.lightech.mensetsu.app.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.anthropic.models.messages.ThinkingConfigParam;
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

  /**
   * 観察に使うモデルが深さ（effort）を受け付けるか。
   *
   * <p>最初は「受け付ける」とみなして投げ、断られたら false にして以後付けない。
   * モデルごとの対応表をコードに持たないための仕掛け。
   */
  private volatile boolean analysisEffortSupported = true;

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
      // 【重要】ここにも思考の設定を入れる。
      //
      // 最初は入れ忘れていた。生成側だけ「思考なし・深さ low」にして、観察側は既定のまま
      // 走っていた。実測（8件）: 観察が 3200〜6313ms、生成が 739〜1692ms。
      // 観察のほうが4〜8倍遅く、利用者の待ち時間の大半を占めていた。
      //
      // 観察は「数字があるか」「固有名詞があるか」を見るだけの作業で、深い推論は要らない。
      // 深さを上げても正しくならない種類の仕事に、時間を払っていた。
      //
      // 出力の上限も上げる。600 では日本語の JSON が途中で切れて、
      // 解析に失敗した（実測で1件）。切れた JSON は「観察できなかった」ではなく
      // 「壊れた応答」なので、原因が分かりにくい失敗になる。
      // 【重要】深さと形式は、同じ outputConfig に両方入れる。
      //
      // ここも一度間違えた。applyThinking() で深さを入れたあとに
      // outputConfig(AnalysisJson.class) を呼ぶと、後者が outputConfig ごと
      // 差し替えるので、深さの指定が消える。消えても API は通るし、結果も正しい。
      // 遅くなるだけなので、測らないと気づけない。
      //
      // StructuredOutputConfig は effort と format の両方を持てる。こちらを使う。
      AnalysisJson json = callAnalyze(state, answer, analysisEffortSupported);

      if (json == null) {
        record(EngineCall.ANALYZE_ANSWER, -1, started, false, "観察の応答が空でした");
        return Analysis.empty("観察できませんでした");
      }
      long ms = millisSince(started);
      record(EngineCall.ANALYZE_ANSWER, ms, started, true, "");
      return json.toDomain();

    } catch (RuntimeException e) {
      // 【重要】モデルごとの対応表をコードに埋め込まない。
      //
      // 実測で踏んだ: claude-haiku-4-5 は effort を受け付けず、8件すべてが 400 で落ちた。
      // 「このモデルは effort に対応」という一覧をコードに書くと、モデルが増えるたびに
      // 直しに来ることになり、直し忘れると静かに壊れる。
      //
      // 断られたら、その1回だけ深さを外して投げ直し、以後このセッションでは付けない。
      // 対応表は API に聞く。こちらは覚えるだけ。
      if (analysisEffortSupported && mentionsEffortUnsupported(e)) {
        analysisEffortSupported = false;
        try {
          AnalysisJson json = callAnalyze(state, answer, false);
          if (json != null) {
            record(EngineCall.ANALYZE_ANSWER, millisSince(started), started, true,
                "深さの指定を外して再試行");
            return json.toDomain();
          }
        } catch (RuntimeException retry) {
          record(EngineCall.ANALYZE_ANSWER, -1, started, false, describe(retry));
          return Analysis.empty("観察に失敗しました");
        }
      }
      record(EngineCall.ANALYZE_ANSWER, -1, started, false, describe(e));
      // 【重要】ここで適当な観察を作らない。
      // 嘘の観察を返すと、圧の計算と段数のカウントが狂う。
      // 「観察できなかった」を正直に返すほうが、あとから追える。
      return Analysis.empty("観察に失敗しました");
    }
  }

  /** 観察を1回投げる。深さを付けるかどうかだけが違う。 */
  private AnalysisJson callAnalyze(InterviewState state, Answer answer, boolean withEffort) {
    var format = StructuredOutputConfig.<AnalysisJson>builder().format(AnalysisJson.class);
    if (withEffort) {
      format.effort(effortOf(settings.effort()));
    }
    StructuredMessageCreateParams<AnalysisJson> params =
        MessageCreateParams.builder()
            .model(settings.effectiveAnalysisModel())
            .maxTokens(1200)
            .system("あなたは面接の回答を観察する係です。評価や判定はしません。")
            .addUserMessage(Prompts.analyze(state, answer.text()))
            .thinking(thinkingConfig())
            .outputConfig(format.build())
            .build();

    return client.messages().create(params).content().stream()
        .flatMap(cb -> cb.text().stream())
        .map(t -> t.text())
        .findFirst()
        .orElse(null);
  }

  /** 深さを受け付けないと断られたか。文言で判定するのは弱いので、型と文言の両方を見る。 */
  private static boolean mentionsEffortUnsupported(RuntimeException e) {
    String m = e.getMessage();
    return m != null && m.contains("effort") && m.contains("does not support");
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
    b.thinking(thinkingConfig());
    b.outputConfig(OutputConfig.builder().effort(effortOf(settings.effort())).build());
  }

  private ThinkingConfigParam thinkingConfig() {
    if (LlmSettings.THINKING_OFF.equals(settings.thinkingMode())) {
      // 【重要】思考を切れるのは effort が high 以下のときだけ。
      // xhigh / max と組み合わせると API が 400 を返す。
      return ThinkingConfigParam.ofDisabled(ThinkingConfigDisabled.builder().build());
    }
    return ThinkingConfigParam.ofAdaptive(ThinkingConfigAdaptive.builder().build());
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
            EngineCall.ANALYZE_ANSWER.equals(purpose)
                ? settings.effectiveAnalysisModel()
                : settings.model(),
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
