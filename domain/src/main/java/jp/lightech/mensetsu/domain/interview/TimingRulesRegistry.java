package jp.lightech.mensetsu.domain.interview;

/**
 * 制限時間をどこから取るか。
 *
 * <h2>【重要】ハードコードしない（第8段階・諏訪さんの判断）</h2>
 *
 * <blockquote>この3つの値を、設定として変更できる形にしておいてください。
 * interviewer_profiles に持たせれば済むはずです。既定はT1。
 * 慣れてきたらT2に上げる、という遊び方ができます。ハードコードしないこと、それだけです。</blockquote>
 *
 * <p>だから値の持ち主は {@link InterviewerProfile}。このクラスは、
 * 「そこから取る」ということだけを表している。
 *
 * <h2>時間を測るのは英語面接だけ</h2>
 *
 * 仕様書4-3は「制限時間と沈黙の再現」を英語面接モードの主眼としており、他のモードでは
 * 求めていない。エンジニア面接で90秒の制限をかけると、技術選定の説明が途中で切れる。
 *
 * <p>ただし<b>測るのは全モードでする</b>。打ち切らないだけ。
 * 実測で見つけた: 英語面接以外で時計を持たせなかったところ、沈黙の軸が
 * 「測れなかった」になり、0点として数えられた（諏訪さんの判断②）。
 * 圧迫面接は沈黙が重み25なので、満点が75点になっていた。
 * 基準を選んだときの材料では沈黙は測れていたので、前提が食い違う。
 */
public final class TimingRulesRegistry {

  private TimingRulesRegistry() {}

  /**
   * その面接官の制限時間。時間を測らない面接官では null。
   *
   * <p>【重要】モードではなく面接官から取る。同じ英語面接でも、
   * 「厳しい面接官」を後から足せるようにするため。
   */
  public static TimingRules forProfile(InterviewerProfile profile) {
    return profile == null ? null : profile.timing();
  }

  /**
   * 打ち切らないモードで、測るためだけに使う設定。
   *
   * <p>使うのは猶予（3秒）だけ。制限時間と沈黙の境目は、打ち切らないので効かない。
   *
   * <p>猶予を英語面接と同じ3秒にしたのは私の判断です。モードごとに変えると、
   * 「同じ5秒の間」がモードによって沈黙になったりならなかったりする。
   * 数値を変えたい場合は言ってください。
   */
  public static TimingRules measurementOnly() {
    return adoptedEnglishDefault();
  }

  /** 採用された既定値（案T1）。DB が読めないときの落とし所。 */
  public static TimingRules adoptedEnglishDefault() {
    return TimingRules.proposalT1();
  }
}
