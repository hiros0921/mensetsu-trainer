package jp.lightech.mensetsu.domain.scoring;

import java.util.ArrayList;
import java.util.List;
import jp.lightech.mensetsu.domain.interview.Exchange;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.Outcome;
import jp.lightech.mensetsu.domain.interview.TermResult;
import jp.lightech.mensetsu.domain.port.Specificity;

/**
 * 面接の記録から、5軸の素点を出す。
 *
 * <h2>【重要】ここは測るだけ。値踏みしない</h2>
 *
 * 出すのは「8回中3回に数字が入っていた → 37点」まで。その37点を良いと見るか悪いと見るかは
 * {@link ScoringPolicy} の仕事で、その基準は諏訪さんが決める（仕様書7章）。
 *
 * <p>この分離があるので、基準を変えたときに面接をやり直さずに再評価できる。
 *
 * <h2>数え方を単純に保つこと</h2>
 *
 * どの軸も「割合を100倍する」だけにしてある。式を凝ると、点数の意味が説明できなくなる。
 * 説明できない点数は、練習の材料にならない（仕様書7章「内訳表示が価値の中心」）。
 */
public final class Scorer {

  private final AxisParams params;

  public Scorer(AxisParams params) {
    this.params = params;
  }

  public ScoreBreakdown score(InterviewState state) {
    Outcome outcome = state.result().orElseThrow(() -> new IllegalStateException("まだ終わっていない面接"));
    List<Exchange> answered = state.history().stream().filter(e -> !e.answer().isSilent()).toList();
    List<Exchange> substantive = answered.stream().filter(Scorer::asksAboutWork).toList();

    List<AxisScore> list = new ArrayList<>();
    list.add(specificity(substantive));
    list.add(conciseness(substantive));
    list.add(consistency(answered));
    list.add(depth(state, outcome));
    list.add(silence(state, outcome));
    return ScoreBreakdown.of(list);
  }

  /**
   * 仕事の中身を問われた場面か。
   *
   * <h2>【重要】締めの挨拶を採点の分母に入れないこと</h2>
   *
   * 最初はここを分けておらず、全8往復を具体性の分母にしていた。実測すると、中身のある
   * 回答4回はすべて満点（12/12）なのに、残り4回が「特にありません」と
   * 「本日はありがとうございました」で、合計 12/24 = 50点になった。
   *
   * <p>締めの挨拶に数字や固有名詞が入るはずがない。入れられない場面を分母に入れると、
   * 誰でも同じだけ点が下がる。全員が同じだけ下がる減点は、誰の練習の材料にもならない。
   *
   * <h2>逆質問（REVERSE）は分母に入れる — 第5段階で確定</h2>
   *
   * 「特にありません」を逃げとみなさない立場もあるが、採らなかった。諏訪さんの判断:
   *
   * <blockquote>逆質問は面接で最も評価される場面の一つで、しかも準備でどうにでもなる部分。
   * 練習アプリなら、その選択にコストがあることを見せるべき。</blockquote>
   *
   * <p>だから CLOSING だけを外す。挨拶に数字は入らないが、逆質問には準備が効く。
   */
  private static boolean asksAboutWork(Exchange e) {
    return e.phase() != jp.lightech.mensetsu.domain.interview.Phase.CLOSING;
  }

  // ── 具体性 ──

  /**
   * 数字・固有名詞・自分の行動。3つのうちいくつ揃っていたかを数える。
   *
   * <p>3つに重みを付けない。付けるならそれも諏訪さんの判断になり、決めるべき数値が増える。
   * 単純に数えたほうが「3つのうち何個」と説明できる。
   */
  private AxisScore specificity(List<Exchange> answered) {
    if (answered.isEmpty()) {
      return AxisScore.notMeasured(Axis.SPECIFICITY, "答えた回答がありません");
    }
    int hit = 0;
    int withNumber = 0;
    for (Exchange e : answered) {
      Specificity s = e.analysis().specificity();
      if (s.hasNumber()) {
        hit++;
        withNumber++;
      }
      if (s.hasProperNoun()) {
        hit++;
      }
      if (s.firstPerson()) {
        hit++;
      }
    }
    int total = answered.size() * 3;
    int value = Math.round(hit * 100f / total);
    return AxisScore.of(
        Axis.SPECIFICITY,
        value,
        "%d回の回答で、数字・固有名詞・自分の行動が %d/%d 個。数字が入っていたのは %d回"
            .formatted(answered.size(), hit, total, withNumber));
  }

  // ── 簡潔さ ──

  /**
   * 短すぎず、長すぎない回答の割合。
   *
   * <p>【重要】短いほど良いのではない。「特にありません」は最も短いが、最も答えていない。
   * だから帯の下限も見る。上限だけを見る作りにすると、黙るほど点が上がる。
   */
  private AxisScore conciseness(List<Exchange> answered) {
    if (answered.isEmpty()) {
      return AxisScore.notMeasured(Axis.CONCISENESS, "答えた回答がありません");
    }
    int inBand = 0;
    int tooShort = 0;
    int tooLong = 0;
    for (Exchange e : answered) {
      int len = e.answer().text().strip().length();
      if (len < params.conciseMinChars()) {
        tooShort++;
      } else if (len > params.conciseMaxChars()) {
        tooLong++;
      } else {
        inBand++;
      }
    }
    int value = Math.round(inBand * 100f / answered.size());
    return AxisScore.of(
        Axis.CONCISENESS,
        value,
        "%d回中 %d回が %d〜%d字に収まっていました（短すぎ %d回・長すぎ %d回）"
            .formatted(
                answered.size(), inBand,
                params.conciseMinChars(), params.conciseMaxChars(), tooShort, tooLong));
  }

  // ── 一貫性 ──

  /**
   * 前の発言と食い違わなかった割合。
   *
   * <p>【重要】この軸だけ、他より当てにならない。矛盾の判定は LLM がしており、揺らぐ。
   * 第1段階の懸念③に書いたとおり。重みを決めるときに、そのことを踏まえていただきたい。
   *
   * <p>矛盾を突く機会そのものが無い面接（1〜2往復で終わったなど）では測れない。
   */
  private AxisScore consistency(List<Exchange> answered) {
    // 1つ目の回答は、比べる相手がいないので矛盾のしようがない。
    List<Exchange> comparable = answered.size() <= 1 ? List.of() : answered.subList(1, answered.size());
    if (comparable.isEmpty()) {
      return AxisScore.notMeasured(Axis.CONSISTENCY, "比べる相手のある回答が1回もありません");
    }
    long contradictions = comparable.stream().filter(e -> e.analysis().hasContradiction()).count();
    int value = Math.round((comparable.size() - contradictions) * 100f / comparable.size());
    String detail =
        contradictions == 0
            ? "%d回の回答に、前の発言との食い違いはありませんでした".formatted(comparable.size())
            : "%d回中 %d回に、前の発言との食い違いがありました".formatted(comparable.size(), contradictions);
    return AxisScore.of(Axis.CONSISTENCY, value, detail + "（この判定は揺らぎます）");
  }

  // ── 深さ ──

  /**
   * 掘られて何段まで答えられたか。
   *
   * <h2>【重要】掘る対象が無かった面接は「測れなかった」にする</h2>
   *
   * 技術用語を一度も口にしなければ、掘られようがない。これを0点にすると
   * 「深く聞かれて答えられなかった人」と「そもそも聞かれなかった人」が同じ点になる。
   * まったく別のことを意味するので、分ける。
   */
  private AxisScore depth(InterviewState state, Outcome outcome) {
    List<TermResult> terms = outcome.terms();
    var probe = state.probe();
    // 【重要】掘りかけで終わった用語も数える。
    //
    // 実測で見つけた。PostgreSQL を3段、MySQL を2段掘られた面接で、深さが
    // 「1件の技術について 3段中 1段」と出た。MySQL は PROBE の上限（5往復）で
    // 打ち切られ、掘り終えていないので finished に入らない。
    //
    // 2段答えた事実が、どこにも残らないまま捨てられていた。上限で切れるかどうかは
    // 面接の長さの都合で、本人の実力ではない。
    //
    // 分母は掘り終えたものが maxDepth、掘りかけは実際に投げた段数。
    // こうすると「答え切れなかった」の減点は残しつつ、答えた事実も拾える。
    boolean digging = probe.hasCurrent() && probe.askedDepth() > 0;

    if (terms.isEmpty() && !digging) {
      return AxisScore.notMeasured(
          Axis.DEPTH, "掘る対象になる技術の話が出なかったため、測れませんでした");
    }

    int answered = terms.stream().mapToInt(TermResult::answeredDepth).sum();
    int asked = terms.stream().mapToInt(TermResult::maxDepth).sum();
    int termCount = terms.size();
    String pending = "";
    if (digging) {
      answered += probe.answeredDepth();
      asked += probe.askedDepth();
      termCount++;
      pending =
          "。うち「%s」は %d段目で打ち切り（%d段まで答えた）"
              .formatted(probe.currentTerm(), probe.askedDepth(), probe.answeredDepth());
    }

    int value = Math.round(answered * 100f / asked);
    long failed = terms.stream().filter(TermResult::failed).count();
    return AxisScore.of(
        Axis.DEPTH,
        value,
        "%d件の技術について %d段中 %d段まで答えられました（答え切れなかったもの %d件）%s"
            .formatted(termCount, asked, answered, failed, pending));
  }

  // ── 沈黙 ──

  /**
   * 詰まった回数と時間。
   *
   * <h2>【重要】時間が記録されていない面接では測れない</h2>
   *
   * 台本で回した検証や、時間を取らない画面から来たデータでは、沈黙の時間が0で埋まる。
   * それを「一度も詰まらなかった」と読むと、満点が付いてしまう。
   * 制限時間のあるモード（英語面接）以外では、そもそも測っていない可能性がある。
   */
  private AxisScore silence(InterviewState state, Outcome outcome) {
    int turns = state.history().size();
    if (turns == 0) {
      return AxisScore.notMeasured(Axis.SILENCE, "やりとりがありません");
    }
    boolean anyTiming = state.history().stream().anyMatch(e -> e.answer().elapsedMs() > 0);
    if (!anyTiming && outcome.silentAnswers() == 0) {
      return AxisScore.notMeasured(
          Axis.SILENCE, "時間が記録されていないため、測れませんでした");
    }

    // 無言そのものと、詰まっていた時間の2つを見る。
    long overTolerance =
        state.history().stream()
            .filter(e -> e.answer().silenceMs() > params.silenceToleranceMs())
            .count();
    long bad = outcome.silentAnswers() + overTolerance;
    int value = Math.round(Math.max(0, turns - bad) * 100f / turns);
    return AxisScore.of(
        Axis.SILENCE,
        value,
        "%d回中、何も言えなかったのが %d回、%.1f秒を超えて詰まったのが %d回"
            .formatted(turns, outcome.silentAnswers(),
                params.silenceToleranceMs() / 1000.0, overTolerance));
  }
}
