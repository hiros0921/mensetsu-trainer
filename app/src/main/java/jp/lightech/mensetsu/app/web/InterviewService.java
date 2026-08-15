package jp.lightech.mensetsu.app.web;

import com.anthropic.client.AnthropicClient;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import jp.lightech.mensetsu.app.llm.ClaudeEngine;
import jp.lightech.mensetsu.app.llm.LlmSettings;
import jp.lightech.mensetsu.app.store.SessionStore;
import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.CannedLines;
import jp.lightech.mensetsu.domain.interview.Expression;
import jp.lightech.mensetsu.domain.interview.ExpressionRules;
import jp.lightech.mensetsu.domain.interview.InterviewMachine;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.InterviewerProfile;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.Phase;
import jp.lightech.mensetsu.domain.interview.PressureConfigs;
import jp.lightech.mensetsu.domain.interview.PressureModel;
import jp.lightech.mensetsu.domain.interview.Question;
import jp.lightech.mensetsu.domain.interview.Step;
import jp.lightech.mensetsu.domain.port.EngineCall;
import jp.lightech.mensetsu.domain.port.EngineObserver;
import jp.lightech.mensetsu.domain.port.InterviewerEngine;
import jp.lightech.mensetsu.domain.scoring.Score;
import jp.lightech.mensetsu.domain.scoring.Scorer;
import jp.lightech.mensetsu.domain.scoring.ScoringPolicies;
import jp.lightech.mensetsu.domain.scoring.ScoringPolicy;
import jp.lightech.mensetsu.domain.stub.StubEngine;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 面接を1回、端から端まで動かす。
 *
 * <h2>ここが繋いでいるもの</h2>
 *
 * <pre>
 *   ステートマシン（domain）    進行の判断
 *   面接官（LLM またはスタブ）   発言と観察
 *   スコアリング（domain）       素点と判定
 *   永続化（app/store）          記録
 *   WebSocket                    画面への配信
 * </pre>
 *
 * <h2>状態の置き場所（第1段階 Q4）</h2>
 *
 * 進行中の {@link InterviewState} は、このクラスがメモリに持つ。DB には記録が残る。
 * 切断からの復帰は作らない方針だが、<b>記録は残り続ける</b>ので、後から足せる。
 *
 * <p>切断されたセッションは「中断」として記録する。集計に混ざらないようにするため。
 */
@Service
public class InterviewService {

  private final SessionStore store;
  private final AnthropicClient claude;
  private final LlmSettings llmSettings;
  private final boolean llmAvailable;

  /**
   * 表情の境目。第7段階の案E1。まだ採用されていない。
   *
   * <p>圧の設定を決めてから、こちらを調整するのが順番。圧の幅が変わると
   * 表情の出方も変わる。
   */
  private static final ExpressionRules EXPRESSION_RULES = ExpressionRules.proposalE1();

  /** 進行中の面接。キーは外に見せる識別子。 */
  private final Map<UUID, Live> live = new ConcurrentHashMap<>();

  public InterviewService(
      SessionStore store,
      @org.springframework.beans.factory.annotation.Autowired(required = false)
          AnthropicClient claude,
      @Value("${mensetsu.llm.model:claude-opus-5}") String model,
      @Value("${mensetsu.llm.analysis-model:}") String analysisModel,
      @Value("${mensetsu.llm.first-token-target-ms:3000}") long targetMs) {
    this.store = store;
    this.claude = claude;
    this.llmSettings = LlmSettings.fastDefault(model, analysisModel, targetMs);
    this.llmAvailable = claude != null;
  }

  /** 進行中の1面接。 */
  public static final class Live {
    final long dbId;
    final UUID publicId;
    final InterviewMachine machine;
    final InterviewerEngine engine;
    final ScoringPolicy policy;
    InterviewState state;
    long pendingTurnId;
    int turnNo;
    String lastFiller = "";

    Live(long dbId, UUID publicId, InterviewMachine machine, InterviewerEngine engine,
        ScoringPolicy policy) {
      this.dbId = dbId;
      this.publicId = publicId;
      this.machine = machine;
      this.engine = engine;
      this.policy = policy;
    }

    public UUID publicId() {
      return publicId;
    }

    public InterviewState state() {
      return state;
    }

    public ScoringPolicy policy() {
      return policy;
    }
  }

  /**
   * 面接を始める。最初の質問まで作って返す。
   *
   * @param sink 生成中の文字と、呼び出しの記録を受け取る口
   */
  public Live begin(Mode mode, Sink sink) {
    // 【重要】基準が決まっていないモードでは、ここで止まる。
    // 別モードの基準を黙って流用しない（ScoringPolicies を参照）。
    ScoringPolicy policy = ScoringPolicies.forMode(mode);

    InterviewerProfile profile = profileFor(mode);
    String engineKind = llmAvailable ? "CLAUDE" : "STUB";
    SessionStore.Created created =
        store.createSession(mode, profile.code(), engineKind);

    InterviewerEngine engine = engineFor(created.id(), sink);
    // 【重要】モードの圧設定を使う。既定のままだと第3段階の暫定値で動く。
    InterviewMachine machine =
        new InterviewMachine(engine, new PressureModel(PressureConfigs.forMode(mode)));
    Live session = new Live(created.id(), created.publicId(), machine, engine, policy);
    live.put(created.publicId(), session);

    Step step = session.machine.begin(mode, profile);
    session.state = step.state();
    afterQuestion(session);
    return session;
  }

  /**
   * 回答を1つ受け取って進める。
   *
   * <p>相槌は呼ぶ側（{@link InterviewWebSocketHandler}）が先に流す。ここは本物を作る側。
   */
  public Step submit(Live session, Answer answer) {
    store.recordAnswer(session.pendingTurnId, answer);

    Phase before = session.state.phase();
    Step step = session.machine.submit(session.state, answer);
    session.state = step.state();

    // 観察を記録する。直前の往復に紐づく。
    session.state.history().stream()
        .reduce((a, b) -> b)
        .ifPresent(last -> store.recordAnalysis(
            session.pendingTurnId, last.analysis(), session.engine.kind(), modelOf(session)));

    if (step.transition().to() != before) {
      // 【重要】閉じるフェーズの往復数を渡すこと。
      //
      // 最初は session.state.phaseRound() を渡していた。遷移後の状態なので
      // 0 にリセットされており、round_count が全レコード 0 で埋まっていた。
      // エラーは出ない。DB を見て初めて気づいた。
      //
      // 履歴から数えれば、遷移の前後に依存しない。
      int roundsInExitedPhase = session.state.exchangesIn(before).size();
      store.recordTransition(session.dbId, step.transition(), roundsInExitedPhase);
    }
    store.updateProgress(session.dbId, session.state);

    if (session.state.isFinished()) {
      store.finish(session.dbId);
    } else {
      afterQuestion(session);
    }
    return step;
  }

  /** 面接が終わったので、素点を出して判定する。 */
  public Score scoreAndSave(Live session) {
    Score score = session.policy.evaluate(new Scorer(session.policy.params()).score(session.state));
    store.saveScore(session.dbId, score);
    return score;
  }

  /**
   * 今の表情（仕様書6章）。
   *
   * <p>圧だけでなく、直前の回答に中身があったかも見る。圧だけで切り替えると、
   * エンジニア面接では圧がほとんど動かないので表情が固まったままになる。
   */
  public Expression expression(Live session) {
    var last = session.state.history().stream().reduce((a, b) -> b);
    if (last.isEmpty()) {
      return EXPRESSION_RULES.atStart(session.state.pressure());
    }
    var a = last.get().analysis();
    return EXPRESSION_RULES.pick(
        session.state.pressure(),
        a.substantive(),
        !a.technicalTerms().isEmpty(),
        substantiveStreak(session));
  }

  /**
   * 中身のある回答が何回続いているか。
   *
   * <p>「好意的」は流れに対する反応なので、直前の1回だけでは決められない。
   */
  private static int substantiveStreak(Live session) {
    var history = session.state.history();
    int streak = 0;
    for (int i = history.size() - 1; i >= 0; i--) {
      if (!history.get(i).analysis().substantive()) {
        break;
      }
      streak++;
    }
    return streak;
  }

  /** その場面で言う相槌。LLM を呼ばないので待ち時間ゼロ（第1段階 Q5）。 */
  public String filler(Live session, boolean answerLooksSubstantive) {
    String line =
        CannedLines.pick(
            session.state.phase(), session.state.pressure(), answerLooksSubstantive,
            session.lastFiller);
    session.lastFiller = line;
    return line;
  }

  /**
   * 画面が閉じられた。
   *
   * <p>【重要】RESULT に着いていなければ「中断」として記録する（第1段階 Q4）。
   * 完了と分けないと、履歴やスコアの集計に混ざる。
   */
  public void disconnect(UUID publicId) {
    Live session = live.remove(publicId);
    if (session != null && !session.state.isFinished()) {
      store.abandon(session.dbId);
    }
  }

  public Live find(UUID publicId) {
    return live.get(publicId);
  }

  public boolean usingLlm() {
    return llmAvailable;
  }

  // ── 内側 ──

  private void afterQuestion(Live session) {
    Question q = session.state.pendingQuestion().orElseThrow();
    session.turnNo++;
    session.pendingTurnId =
        store.recordQuestion(session.dbId, session.turnNo, q, session.state.phase().name());
  }

  private InterviewerEngine engineFor(long dbId, Sink sink) {
    EngineObserver observer =
        new EngineObserver() {
          @Override
          public void onDelta(String purpose, String delta) {
            if (EngineCall.NEXT_QUESTION.equals(purpose)) {
              sink.delta(delta);
            }
          }

          @Override
          public void onCall(EngineCall call) {
            store.recordEngineCall(dbId, null, call);
            sink.call(call);
          }
        };
    return llmAvailable
        ? new ClaudeEngine(claude, llmSettings, observer)
        : new StubEngine();
  }

  private String modelOf(Live session) {
    return session.engine instanceof ClaudeEngine c ? c.settings().effectiveAnalysisModel() : "";
  }

  private static InterviewerProfile profileFor(Mode mode) {
    return switch (mode) {
      case ENGINEER -> InterviewerProfile.engineerStandard();
      case PRESSURE -> InterviewerProfile.pressureHard();
      case ENGLISH -> InterviewerProfile.englishStandard();
    };
  }

  /** 生成中の文字と、呼び出しの記録を受け取る口。画面へ流すために使う。 */
  public interface Sink {
    void delta(String text);

    void call(EngineCall call);

    Sink NONE =
        new Sink() {
          @Override
          public void delta(String text) {}

          @Override
          public void call(EngineCall call) {}
        };
  }
}
