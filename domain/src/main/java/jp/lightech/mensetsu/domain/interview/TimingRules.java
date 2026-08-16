package jp.lightech.mensetsu.domain.interview;

/**
 * 制限時間と沈黙の境目（仕様書4-3・5章）。
 *
 * <h2>【重要】これはサーバーが持つ</h2>
 *
 * 仕様書5章161行「沈黙の閾値はサーバー側で管理すること。クライアントに判断させない」。
 *
 * <p>クライアントに判定させると、こう壊れる。ブラウザのタブが裏に回るとタイマーが止まる。
 * 端末の時計がずれていれば境目もずれる。そして何より、画面側を書き換えれば制限時間を
 * 無効にできてしまう。練習アプリなので不正の心配は小さいが、
 * <b>「どの端末で受けても同じ面接になる」</b>ことのほうが大事。
 *
 * <p>だからクライアントが送るのは「今、入力があった」という事実だけ。
 * それを何秒の沈黙とみなすかは、こちらが決める。
 *
 * <h2>【重要】ここの数値は案です</h2>
 *
 * 圧の設定やスコアの重みと同じで、面接の難しさそのものを決める。第8段階で案を出し、
 * 諏訪が選ぶ。
 *
 * @param answerLimitMs 1問あたりの回答制限時間。これを過ぎたら打ち切る
 * @param silenceCutoffMs 入力（発話）が止まってから、回答終了とみなすまでの時間
 * @param graceMs 質問を出してから最初の入力までの猶予。ここは沈黙に数えない
 */
public record TimingRules(long answerLimitMs, long silenceCutoffMs, long graceMs) {

  public TimingRules {
    if (answerLimitMs <= 0 || silenceCutoffMs <= 0 || graceMs < 0) {
      throw new IllegalArgumentException(
          "時間が不正: 制限%d 沈黙%d 猶予%d".formatted(answerLimitMs, silenceCutoffMs, graceMs));
    }
    if (silenceCutoffMs >= answerLimitMs) {
      // 沈黙の打ち切りが制限時間より長いと、沈黙では一度も打ち切られない。
      // 制限時間のほうが先に来るので、沈黙の検出が死んでいることに気づけない。
      throw new IllegalArgumentException(
          "沈黙の打ち切り(%d)が制限時間(%d)以上。沈黙で打ち切られることが無くなる"
              .formatted(silenceCutoffMs, answerLimitMs));
    }
    if (graceMs >= silenceCutoffMs) {
      // 猶予が沈黙の打ち切りより長いと、考え始める前に打ち切られない代わりに、
      // 猶予中の沈黙が一切数えられなくなる。
      throw new IllegalArgumentException(
          "猶予(%d)が沈黙の打ち切り(%d)以上".formatted(graceMs, silenceCutoffMs));
    }
  }

  /**
   * 時間を測らないモード。
   *
   * <p>エンジニア面接と圧迫面接では、制限時間を設けない。仕様書4-3は「制限時間と沈黙の再現」を
   * 英語面接モードの主眼としており、他のモードでは求めていない。
   *
   * <p>時間を測らないので、沈黙の軸は「測れなかった」になる。0点ではない。
   */
  public static TimingRules none() {
    return null;
  }

  // ══════════════════════════════════════════════════════════════════
  //  英語面接モードの【案】（第8段階）。採用されていません。
  // ══════════════════════════════════════════════════════════════════

  /**
   * 案T1「実際のAI面接に近い」。
   *
   * <p>制限90秒、沈黙8秒で打ち切り。市販のAI面接ツールでよくある設定に近い。
   * 8秒の沈黙は、英語で言葉に詰まったときに現実的に起こる長さ。
   */
  public static TimingRules proposalT1() {
    return new TimingRules(90_000, 8_000, 3_000);
  }

  /**
   * 案T2「厳しめ」。
   *
   * <p>制限60秒、沈黙5秒。短く簡潔に答える練習に振る。詰まると即座に打ち切られるので、
   * 「考えてから話す」ではなく「話しながら考える」練習になる。
   *
   * <p>英語に慣れていない段階では、ほとんど打ち切られる可能性があります。
   */
  public static TimingRules proposalT2() {
    return new TimingRules(60_000, 5_000, 2_000);
  }

  /**
   * 案T3「緩め」。
   *
   * <p>制限120秒、沈黙12秒。まず最後まで話し切る練習。
   * 打ち切られにくいぶん、制限時間の緊張感は薄くなります。
   */
  public static TimingRules proposalT3() {
    return new TimingRules(120_000, 12_000, 5_000);
  }

  public String describe() {
    return "制限 %.0f秒 ／ 沈黙 %.0f秒で打ち切り ／ 猶予 %.0f秒"
        .formatted(answerLimitMs / 1000.0, silenceCutoffMs / 1000.0, graceMs / 1000.0);
  }
}
