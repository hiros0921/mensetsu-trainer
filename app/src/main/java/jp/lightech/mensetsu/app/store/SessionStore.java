package jp.lightech.mensetsu.app.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.InterviewerProfile;
import jp.lightech.mensetsu.domain.interview.TimingRules;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.PhaseTransition;
import jp.lightech.mensetsu.domain.interview.Question;
import jp.lightech.mensetsu.domain.port.Analysis;
import jp.lightech.mensetsu.domain.port.EngineCall;
import jp.lightech.mensetsu.domain.scoring.AxisScore;
import jp.lightech.mensetsu.domain.scoring.Score;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 面接の記録を DB に残す。
 *
 * <h2>SQL を直接書く理由</h2>
 *
 * JSONB、部分インデックス、CHECK 制約を使っている。ORM に隠されると、
 * スキーマに書いた意図（第2段階で入れた制約）がコードから見えなくなる。
 *
 * <h2>【重要】ドメインはここを知らない</h2>
 *
 * 依存は app → domain の一方向。{@link InterviewState} は保存の仕方を知らないし、
 * 保存できるかどうかも気にしない。だからステートマシンのテストに DB が要らない。
 */
@Repository
public class SessionStore {

  private final JdbcTemplate jdbc;
  private final ObjectMapper json;

  public SessionStore(JdbcTemplate jdbc, ObjectMapper json) {
    this.jdbc = jdbc;
    this.json = json;
  }

  // ── 面接官の設定 ──

  /**
   * 面接官の設定を DB から読む。
   *
   * <h2>【重要】コードの既定値ではなく、DB を正にする（第8段階の判断）</h2>
   *
   * <blockquote>この3つの値を、設定として変更できる形にしておいてください。
   * interviewer_profiles に持たせれば済むはずです。既定はT1。慣れてきたらT2に上げる、
   * という遊び方ができます。ハードコードしないこと、それだけです。</blockquote>
   *
   * <p>だから制限時間を変えたいときは、SQL を1行 UPDATE すれば済む。コードは触らない。
   *
   * <pre>
   *   UPDATE interviewer_profiles
   *      SET answer_limit_ms = 60000, silence_cutoff_ms = 5000, grace_ms = 2000
   *    WHERE code = 'english_standard';
   * </pre>
   */
  public java.util.Optional<InterviewerProfile> findProfile(String code) {
    List<Map<String, Object>> rows =
        jdbc.queryForList(
            """
            SELECT code, display_name, pressure_base, probe_depth, small_talk_ratio,
                   answer_limit_ms, silence_cutoff_ms, grace_ms
              FROM interviewer_profiles WHERE code = ?
            """,
            code);
    if (rows.isEmpty()) {
      return java.util.Optional.empty();
    }
    Map<String, Object> r = rows.get(0);
    Object limit = r.get("answer_limit_ms");
    // 3つは揃っているか、揃っていないかのどちらか（DB の CHECK 制約で保証）。
    TimingRules timing =
        limit == null
            ? null
            : new TimingRules(
                ((Number) limit).longValue(),
                ((Number) r.get("silence_cutoff_ms")).longValue(),
                ((Number) r.get("grace_ms")).longValue());
    return java.util.Optional.of(
        new InterviewerProfile(
            (String) r.get("code"),
            (String) r.get("display_name"),
            ((Number) r.get("pressure_base")).intValue(),
            ((Number) r.get("probe_depth")).intValue(),
            ((Number) r.get("small_talk_ratio")).intValue(),
            timing));
  }

  // ── セッション ──

  /**
   * 面接を1つ始める。外に見せる識別子（UUID）と、内部の id を返す。
   *
   * <p>題名は持たない。第2段階のスキーマに列を作らなかったので、SQL 側も持たせない。
   * 履歴の見出しはモードと日時から作れる。使わない列を足す理由が無い。
   */
  @Transactional
  public Created createSession(Mode mode, String profileCode, String engineKind) {
    Long profileId =
        jdbc.queryForObject(
            "SELECT id FROM interviewer_profiles WHERE code = ?", Long.class, profileCode);

    Map<String, Object> row =
        jdbc.queryForMap(
            """
            INSERT INTO sessions (mode, interviewer_profile_id, engine_kind, pressure, current_phase)
            VALUES (?, ?, ?, (SELECT pressure_base FROM interviewer_profiles WHERE id = ?), 'INTRO')
            RETURNING id, public_id
            """,
            mode.name(), profileId, engineKind, profileId);

    long id = ((Number) row.get("id")).longValue();
    UUID publicId = (UUID) row.get("public_id");

    // 最初のフェーズを記録する。session_phases は「いつ、どこに、なぜ」の履歴。
    jdbc.update(
        """
        INSERT INTO session_phases (session_id, phase, seq, entered_reason, entered_detail)
        VALUES (?, 'INTRO', 1, 'START', '面接を開始した')
        """,
        id);
    return new Created(id, publicId);
  }

  public record Created(long id, UUID publicId) {}

  /** 進行中の状態を書き戻す。フェーズと圧は画面にも出るので、DB を正にしておく。 */
  public void updateProgress(long sessionId, InterviewState state) {
    jdbc.update(
        """
        UPDATE sessions
           SET current_phase = ?, pressure = ?, last_seen_at = now()
         WHERE id = ?
        """,
        state.phase().name(), state.pressure(), sessionId);
  }

  /**
   * フェーズが移ったので、履歴に1行足す。
   *
   * <p>仕様書9章「session_phases に遷移理由を残すこと」。あとから
   * 「なぜこの評価になったか」を追えるようにするため。
   */
  public void recordTransition(long sessionId, PhaseTransition transition, int roundCount) {
    Integer seq =
        jdbc.queryForObject(
            "SELECT coalesce(max(seq), 0) + 1 FROM session_phases WHERE session_id = ?",
            Integer.class, sessionId);
    // 直前のフェーズを閉じる。
    jdbc.update(
        """
        UPDATE session_phases SET exited_at = now(), round_count = ?
         WHERE session_id = ? AND exited_at IS NULL
        """,
        roundCount, sessionId);
    jdbc.update(
        """
        INSERT INTO session_phases (session_id, phase, seq, entered_reason, entered_detail)
        VALUES (?, ?, ?, ?, ?)
        """,
        sessionId, transition.to().name(), seq,
        transition.reason().name(), transition.detail());
  }

  /** 面接が終わった。 */
  public void finish(long sessionId) {
    jdbc.update(
        """
        UPDATE sessions
           SET status = 'COMPLETED', current_phase = 'RESULT', ended_at = now(), last_seen_at = now()
         WHERE id = ?
        """,
        sessionId);
    jdbc.update(
        "UPDATE session_phases SET exited_at = now() WHERE session_id = ? AND exited_at IS NULL",
        sessionId);
  }

  /**
   * RESULT に着かずに終わった。
   *
   * <p>【重要】中断は完了と分けて記録する（第1段階 Q4）。中断したセッションが履歴や
   * スコアの集計に混ざると、平均点が意味を失う。「答えるのをやめた面接」は、落ちた面接とは違う。
   */
  public void abandon(long sessionId) {
    jdbc.update(
        """
        UPDATE sessions SET status = 'ABANDONED', ended_at = now()
         WHERE id = ? AND status = 'RUNNING'
        """,
        sessionId);
  }

  // ── 往復 ──

  /** 面接官が質問を出した。回答はまだ。 */
  public long recordQuestion(long sessionId, int turnNo, Question question, String phase) {
    Long phaseId =
        jdbc.queryForObject(
            """
            SELECT id FROM session_phases
             WHERE session_id = ? AND exited_at IS NULL
             ORDER BY seq DESC LIMIT 1
            """,
            Long.class, sessionId);
    return jdbc.queryForObject(
        """
        INSERT INTO turns (session_id, session_phase_id, phase, turn_no, question_text, question_kind)
        VALUES (?, ?, ?, ?, ?, ?)
        RETURNING id
        """,
        Long.class,
        sessionId, phaseId, phase, turnNo, question.text(), question.kind().name());
  }

  /**
   * 回答が返ってきた。
   *
   * <p>入力方式（音声／テキスト）はここに残すだけで、進行の判断には使わない（仕様書8章④）。
   */
  public void recordAnswer(long turnId, Answer answer) {
    jdbc.update(
        """
        UPDATE turns
           SET answer_text = ?, input_method = ?, elapsed_ms = ?, silence_ms = ?,
               timed_out = ?, answered_at = now()
         WHERE id = ?
        """,
        answer.text(), answer.input().name(), answer.elapsedMs(), answer.silenceMs(),
        answer.timedOut(), turnId);
  }

  /** 回答の観察。engine_kind を残すのは、スタブで作ったデータと本物を区別するため。 */
  public void recordAnalysis(long turnId, Analysis analysis, String engineKind, String model) {
    jdbc.update(
        """
        INSERT INTO answer_analyses (turn_id, analysis, engine_kind, model)
        VALUES (?, ?::jsonb, ?, ?)
        """,
        turnId, toJson(Map.of(
            "technicalTerms", analysis.technicalTerms(),
            "specificity", Map.of(
                "hasNumber", analysis.specificity().hasNumber(),
                "hasProperNoun", analysis.specificity().hasProperNoun(),
                "firstPerson", analysis.specificity().firstPerson()),
            "substantive", analysis.substantive(),
            "hasContradiction", analysis.hasContradiction(),
            "contradictionWith", analysis.contradictionWith(),
            "note", analysis.note())),
        engineKind, model);
  }

  // ── LLM の呼び出し ──

  /** 応答時間の記録（第1段階 Q5）。目標を超えたものは、部分インデックスで拾える。 */
  public void recordEngineCall(long sessionId, Long turnId, EngineCall call) {
    jdbc.update(
        """
        INSERT INTO engine_calls
          (session_id, turn_id, purpose, engine_kind, model, first_token_ms, total_ms, ok, error_note)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        sessionId, turnId, call.purpose(), call.engineKind(), call.model(),
        (int) call.firstTokenMs(), (int) call.totalMs(), call.ok(), call.errorNote());
  }

  // ── 評価 ──

  /**
   * 判定を保存する。
   *
   * <p>【重要】threshold_version を必ず入れる。基準を変えたときに、
   * 過去のスコアがどの基準で出たものかを追えるようにするため。
   */
  public void saveScore(long sessionId, Score score) {
    jdbc.update(
        """
        INSERT INTO scores (session_id, grade, total, breakdown, threshold_version)
        VALUES (?, ?, ?, ?::jsonb, ?)
        ON CONFLICT (session_id) DO UPDATE
          SET grade = excluded.grade, total = excluded.total,
              breakdown = excluded.breakdown, threshold_version = excluded.threshold_version
        """,
        sessionId, score.grade().name(), score.total(), toJson(breakdownJson(score)),
        score.thresholdVersion());
  }

  /**
   * 内訳を JSON にする。
   *
   * <p>点数だけでなく <b>why も一緒に残す</b>。仕様書7章「この内訳表示が、アプリの価値の中心」。
   * 説明を残さないと、あとから履歴を開いたときに数字しか出せない。
   */
  private List<Map<String, Object>> breakdownJson(Score score) {
    return score.contributions().stream()
        .map(c -> {
          AxisScore raw = score.breakdown().get(c.axis());
          return Map.<String, Object>of(
              "axis", c.axis().name(),
              "label", c.axis().label(),
              "raw", c.raw(),
              "weight", c.weight(),
              "points", Math.round(c.points() * 10) / 10.0,
              "measured", c.measured(),
              "why", raw.why());
        })
        .toList();
  }

  // ── 読み出し ──

  /** 完了した面接だけを新しい順に。中断は混ぜない（ビューが弾く）。 */
  public List<Map<String, Object>> completedHistory(int limit) {
    return jdbc.queryForList(
        """
        SELECT s.public_id, s.mode, s.started_at, s.ended_at,
               sc.grade, sc.total, sc.threshold_version, sc.breakdown
          FROM completed_sessions s
          LEFT JOIN scores sc ON sc.session_id = s.id
         ORDER BY s.started_at DESC
         LIMIT ?
        """,
        limit);
  }

  public java.util.Optional<Long> findIdByPublicId(UUID publicId) {
    List<Long> ids =
        jdbc.queryForList("SELECT id FROM sessions WHERE public_id = ?", Long.class, publicId);
    return ids.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(ids.get(0));
  }

  private String toJson(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      // ここで落ちると面接が止まる。JSON にできない値を入れた側のバグなので、
      // 握りつぶさずに投げる。ただし中身は出さない（回答本文が混ざりうる）。
      throw new IllegalStateException("記録を JSON にできませんでした: " + e.getOriginalMessage(), e);
    }
  }
}
