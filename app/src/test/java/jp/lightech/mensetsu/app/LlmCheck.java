package jp.lightech.mensetsu.app;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import java.util.ArrayList;
import java.util.List;
import jp.lightech.mensetsu.app.llm.ClaudeEngine;
import jp.lightech.mensetsu.app.llm.LlmSettings;
import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.CannedLines;
import jp.lightech.mensetsu.domain.interview.InterviewMachine;
import jp.lightech.mensetsu.domain.interview.InterviewerProfile;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.Outcome;
import jp.lightech.mensetsu.domain.interview.Step;
import jp.lightech.mensetsu.domain.interview.TermResult;
import jp.lightech.mensetsu.domain.port.EngineCall;
import jp.lightech.mensetsu.domain.port.EngineObserver;

/**
 * 本物の Claude API を相手に面接を1回通し、応答時間を測る。
 *
 * <pre>
 *   ./gradlew :app:llmcheck
 *   ./gradlew :app:llmcheck --args="ENGINEER adaptive"
 * </pre>
 *
 * <h2>何を確かめるためのものか</h2>
 *
 * 第1段階 Q5 で決めた目標値「深掘りは3秒以内に表示が始まること」を、実測で確かめる。
 * 測るのは <b>最初の文字が出るまで</b>（firstTokenMs）。全部出来上がるまでの時間は
 * 体感に効かないので、そちらを目標にしない。
 *
 * <p>あわせて、相槌を先に返す設計が効いているかも見る。相槌は LLM を呼ばないので
 * 0ms で出る。その裏で深掘りを生成している間が「間」に見えるかどうか。
 *
 * <p>【重要】これは課金される。実行するとキーが使われる。
 */
public final class LlmCheck {

  /** 面接に答える側の台本。人が居ないので固定する。 */
  private static final List<String> SCRIPT =
      List.of(
          "諏訪と申します。物流とECの現場で12年、システムの開発と運用を担当してきました。"
              + "直近では会計事務所向けの帳票照合システムを、C++とGoとRubyで作っています。",
          "私が PostgreSQL を選びました。取引先名のあいまい一致に pg_trgm を使いたかったのと、"
              + "3人のチームで運用経験があったためです。",
          "MySQL と比較しました。全文検索の拡張が使えることと、部分インデックスが書けることで"
              + "PostgreSQL にしました。捨てたのは、チームの MySQL の運用ノウハウです。",
          "使わない判断も検討しました。件数が月500件程度なら SQLite で足りると考えましたが、"
              + "同時に複数人が承認作業をするので、行ロックが要ると判断してやめました。",
          "特にありません。",
          "本日はありがとうございました。");

  public static void main(String[] args) {
    Mode mode = args.length > 0 ? Mode.valueOf(args[0]) : Mode.ENGINEER;
    String thinking = args.length > 1 ? args[1] : LlmSettings.THINKING_OFF;
    String model = System.getenv().getOrDefault("MENSETSU_LLM_MODEL", "claude-opus-5");
    // 観察は分類作業なので、別のモデルにできる。空なら発言と同じものを使う。
    String analysisModel = System.getenv().getOrDefault("MENSETSU_ANALYSIS_MODEL", "");
    long target =
        Long.parseLong(System.getenv().getOrDefault("MENSETSU_FIRST_TOKEN_TARGET_MS", "3000"));

    if (blank(System.getenv("ANTHROPIC_API_KEY"))) {
      System.err.println("ANTHROPIC_API_KEY がありません。.env に設定してください。");
      System.exit(2);
    }

    LlmSettings settings = new LlmSettings(model, analysisModel, thinking, "low", target, 400);
    System.out.printf(
        "発言 %s ／ 観察 %s ／ 思考 %s ／ 深さ %s ／ 目標 %dms%n%n",
        settings.model(), settings.effectiveAnalysisModel(),
        settings.thinkingMode(), settings.effort(), target);

    AnthropicClient client = AnthropicOkHttpClient.fromEnv();
    Recorder recorder = new Recorder(target, thinking);
    ClaudeEngine engine = new ClaudeEngine(client, settings, recorder);
    InterviewMachine machine = new InterviewMachine(engine);

    long wall = System.nanoTime();
    Step step = machine.begin(mode, profileFor(mode));
    show(step, recorder);

    String lastFiller = "";
    int i = 0;
    while (!step.state().isFinished() && i < SCRIPT.size() + 4) {
      String answer = SCRIPT.get(Math.min(i, SCRIPT.size() - 1));
      i++;
      System.out.printf("%n  あなた: %s%n", shorten(answer));

      // ① 相槌を即座に返す。LLM を呼ばないので待ち時間ゼロ。
      long fillerAt = System.nanoTime();
      String filler =
          CannedLines.pick(mode, step.state().phase(), step.state().pressure(), true, lastFiller);
      lastFiller = filler;
      System.out.printf("  面接官: %s   [%dms・定型／LLMなし]%n", filler, ms(fillerAt));

      // ② その裏で本物を作る。ここが待ち時間になるが、①のぶん「間」に見える。
      recorder.clearTurn();
      step = machine.submit(step.state(), Answer.of(answer));
      if (!step.state().isFinished()) {
        show(step, recorder);
      } else {
        System.out.println("  ---- 面接終了");
      }
    }

    summarize(step.state().result().orElse(null), recorder, target, ms(wall));
  }

  /** 面接官の発言と、その1往復ぶんの計測を出す。 */
  private static void show(Step step, Recorder recorder) {
    var state = step.state();
    var q = state.pendingQuestion().orElseThrow();
    String dig = q.depth() > 0 ? "  <%s %d段目>".formatted(q.topic(), q.depth()) : "";
    System.out.printf("  面接官: %s%s%n", q.text(), dig);

    for (EngineCall c : recorder.turnCalls()) {
      System.out.printf(
          "          [%s %s／初文字 %dms・完了 %dms%s]%n",
          c.purpose(),
          c.ok() ? "成功" : "失敗: " + c.errorNote(),
          c.firstTokenMs(),
          c.totalMs(),
          c.slowerThan(recorder.target) ? "  ★目標超え" : "");
    }
    System.out.printf("          [%s 圧%d]%n", state.phase(), state.pressure());
  }

  private static void summarize(Outcome outcome, Recorder r, long target, long wallMs) {
    System.out.println();
    System.out.println("=".repeat(72));
    System.out.printf("  実測（思考 %s ／ 深さ low）%n", r.thinking);
    System.out.println("=".repeat(72));

    report(r, EngineCall.NEXT_QUESTION, "深掘りの生成", target);
    report(r, EngineCall.ANALYZE_ANSWER, "回答の観察", target);

    long failed = r.all.stream().filter(c -> !c.ok()).count();
    System.out.printf("  失敗した呼び出し: %d 件%n", failed);
    // 同じ原因で16回落ちると、同じ行が16回出て読めない。まとめて数える。
    r.all.stream()
        .filter(c -> !c.ok())
        .collect(java.util.stream.Collectors.groupingBy(
            EngineCall::errorNote, java.util.LinkedHashMap::new, java.util.stream.Collectors.counting()))
        .forEach((note, count) -> System.out.printf("      %d件: %s%n", count, note));
    System.out.printf("  面接1回の所要: %.1f 秒%n", wallMs / 1000.0);

    if (outcome != null) {
      System.out.printf(
          "  往復 %d ／ 到達段 %d ／ 答え切れず %d件%n",
          outcome.turnCount(), outcome.deepestAnswered(), outcome.failedTerms());
      for (TermResult t : outcome.terms()) {
        System.out.printf("      %-14s %d段投げて %d段答えた%s%n",
            t.term(), t.askedDepth(), t.answeredDepth(), t.failed() ? "  ← 答え切れず" : "");
      }
    }
  }

  private static void report(Recorder r, String purpose, String label, long target) {
    List<EngineCall> calls = r.all.stream().filter(c -> c.purpose().equals(purpose)).toList();
    if (calls.isEmpty()) {
      System.out.printf("  %s: 呼び出しなし%n", label);
      return;
    }
    var first = calls.stream().filter(EngineCall::ok).mapToLong(EngineCall::firstTokenMs);
    long[] v = first.sorted().toArray();
    if (v.length == 0) {
      System.out.printf("  %s: 成功した呼び出しなし（%d件すべて失敗）%n", label, calls.size());
      return;
    }
    long over = calls.stream().filter(c -> c.slowerThan(target)).count();
    System.out.printf(
        "  %s（%d件）: 初文字 最小 %dms ／ 中央 %dms ／ 最大 %dms ／ 目標超え %d件%n",
        label, calls.size(), v[0], v[v.length / 2], v[v.length - 1], over);
    System.out.printf("      使ったモデル: %s%n", calls.get(0).model());
  }

  // ── 記録する側 ──

  /**
   * 呼び出しの記録を貯める。
   *
   * <p>本番では DB の engine_calls に入れる。ここでは画面に出すだけ。
   * {@link EngineObserver} で分けてあるので、貯め先を差し替えるだけで済む。
   */
  private static final class Recorder implements EngineObserver {
    final List<EngineCall> all = new ArrayList<>();
    private final List<EngineCall> thisTurn = new ArrayList<>();
    final long target;
    final String thinking;
    String model = "";

    Recorder(long target, String thinking) {
      this.target = target;
      this.thinking = thinking;
    }

    @Override
    public void onCall(EngineCall call) {
      all.add(call);
      thisTurn.add(call);
      model = call.model();
    }

    void clearTurn() {
      thisTurn.clear();
    }

    List<EngineCall> turnCalls() {
      return List.copyOf(thisTurn);
    }
  }

  private static InterviewerProfile profileFor(Mode mode) {
    return switch (mode) {
      case ENGINEER -> InterviewerProfile.engineerStandard();
      case PRESSURE -> InterviewerProfile.pressureHard();
      case ENGLISH -> InterviewerProfile.englishStandard();
    };
  }

  private static long ms(long startedNanos) {
    return (System.nanoTime() - startedNanos) / 1_000_000L;
  }

  private static String shorten(String s) {
    return s.length() <= 52 ? s : s.substring(0, 52) + "…";
  }

  private static boolean blank(String s) {
    return s == null || s.isBlank();
  }

  private LlmCheck() {}
}
