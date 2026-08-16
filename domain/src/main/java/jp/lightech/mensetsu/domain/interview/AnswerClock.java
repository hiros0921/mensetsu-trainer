package jp.lightech.mensetsu.domain.interview;

/**
 * 1問ぶんの時間を測る（仕様書4-3・5章）。
 *
 * <h2>【重要】判定はここでする。クライアントにさせない</h2>
 *
 * クライアントが送ってくるのは「今、入力があった」という事実だけ。
 * それを何秒の沈黙とみなすか、打ち切るかどうかは、この時計が決める。
 *
 * <p>クライアントに判定させると、こう壊れる。ブラウザのタブが裏に回るとタイマーが止まる。
 * 端末の時計がずれていれば境目もずれる。画面側を書き換えれば制限時間を無効にできる。
 * 練習アプリなので不正の心配は小さいが、<b>「どの端末で受けても同じ面接になる」</b>ことが崩れる。
 *
 * <h2>時計そのものは渡してもらう</h2>
 *
 * {@code System.currentTimeMillis()} を直接呼ばない。呼ぶと、90秒の制限時間を試すのに
 * 90秒待つことになる。呼ぶ側から「今の時刻」を渡してもらえば、試験では好きな時刻を渡せる。
 *
 * <p>すべて不変。状態を書き換えず、新しい値を返す。
 *
 * <h2>測ることと、打ち切ることは別</h2>
 *
 * 時間を測るのはどのモードでもする。打ち切るのは英語面接だけ（仕様書4-3）。
 *
 * <p>【重要】ここを1つにまとめて「英語面接以外は時計を持たない」としたら、
 * 沈黙の軸が全モードで「測れなかった」になった。測れない軸は0点として数えるので
 * （諏訪さんの判断②「0点にする」）、圧迫面接は沈黙の25点を必ず落とし、
 * 満点が75点になっていた。基準を選んだときの前提と食い違う。
 * 打ち切らないことと、測らないことは、別のこと。
 *
 * @param rules 制限時間と沈黙の境目。猶予の長さは測るためにも使う
 * @param askedAtMs 質問を出した時刻
 * @param lastInputAtMs 最後に入力があった時刻。まだ何も入力が無ければ askedAtMs と同じ
 * @param anyInput 一度でも入力があったか
 * @param enforcing 打ち切るか。false なら測るだけ
 */
public record AnswerClock(
    TimingRules rules,
    long askedAtMs,
    long lastInputAtMs,
    boolean anyInput,
    boolean enforcing) {

  /** 質問を出した。ここから測り、制限時間で打ち切る。 */
  public static AnswerClock started(TimingRules rules, long nowMs) {
    return new AnswerClock(rules, nowMs, nowMs, false, true);
  }

  /** 測るだけ。打ち切らない。時間制限を設けないモードで使う。 */
  public static AnswerClock measuring(TimingRules rules, long nowMs) {
    return new AnswerClock(rules, nowMs, nowMs, false, false);
  }

  /** 入力があった。沈黙の起点を更新する。 */
  public AnswerClock onInput(long nowMs) {
    return new AnswerClock(rules, askedAtMs, nowMs, true, enforcing);
  }

  /** 質問を出してからの経過。 */
  public long elapsedMs(long nowMs) {
    return Math.max(0, nowMs - askedAtMs);
  }

  /**
   * 詰まっていた時間。
   *
   * <p>【重要】最初の入力までの猶予は数えない。質問を聞いて考え始めるまでの数秒は、
   * 詰まったのではなく、ふつうの間。ここを数えると全員が減点される。
   */
  public long silenceMs(long nowMs) {
    long since = Math.max(0, nowMs - lastInputAtMs);
    if (!anyInput) {
      since -= rules.graceMs();
    }
    return Math.max(0, since);
  }

  /** 制限時間を超えたか。 */
  public boolean overTimeLimit(long nowMs) {
    return elapsedMs(nowMs) >= rules.answerLimitMs();
  }

  /**
   * 沈黙で打ち切るか。
   *
   * <p>【重要】一度も入力が無い状態でも打ち切る。無言のまま固まったときに、
   * 制限時間いっぱい待たせない。猶予のぶんは差し引いてある。
   */
  public boolean overSilence(long nowMs) {
    return silenceMs(nowMs) >= rules.silenceCutoffMs();
  }

  /** 打ち切るべきか。理由も返す。測るだけの時計は、決して打ち切らない。 */
  public Cutoff cutoff(long nowMs) {
    if (!enforcing) {
      return new Cutoff(false, "");
    }
    if (overTimeLimit(nowMs)) {
      return new Cutoff(true, "制限時間（%.0f秒）に達しました".formatted(rules.answerLimitMs() / 1000.0));
    }
    if (overSilence(nowMs)) {
      return new Cutoff(
          true,
          anyInput
              ? "%.0f秒のあいだ入力が止まったので、回答を終了とみなしました"
                  .formatted(rules.silenceCutoffMs() / 1000.0)
              : "何も入力されないまま %.0f秒が過ぎました".formatted(rules.silenceCutoffMs() / 1000.0));
    }
    return new Cutoff(false, "");
  }

  /**
   * 残り時間。画面のタイマー表示に使う。
   *
   * <p>測るだけの時計では -1。画面はこれを見てタイマーを出さない。
   */
  public long remainingMs(long nowMs) {
    return enforcing ? Math.max(0, rules.answerLimitMs() - elapsedMs(nowMs)) : -1;
  }

  /** 打ち切りの判断。 */
  public record Cutoff(boolean shouldCut, String reason) {}
}
