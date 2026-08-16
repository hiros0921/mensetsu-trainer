package jp.lightech.mensetsu.domain.interview;

/**
 * 面接官の設定。DB の interviewer_profiles に対応する。
 *
 * <p>性格をコードではなくデータで持つ。「圧の強いエンジニア面接官」を、
 * クラスを増やさずに足せるようにするため。
 *
 * @param code 識別子。'engineer_standard' など。
 * @param displayName 画面に出す名前。
 * @param pressureBase 圧の初期値。
 * @param probeDepth 技術用語を何段まで掘るか（仕様書4-1 は3段）。
 * @param smallTalkRatio 雑談の量。0 で一切しない。
 * @param timing 制限時間と沈黙の境目。時間を測らない面接官では null
 */
public record InterviewerProfile(
    String code,
    String displayName,
    int pressureBase,
    int probeDepth,
    int smallTalkRatio,
    TimingRules timing) {

  /** 時間を測らない面接官。 */
  public InterviewerProfile(
      String code, String displayName, int pressureBase, int probeDepth, int smallTalkRatio) {
    this(code, displayName, pressureBase, probeDepth, smallTalkRatio, null);
  }

  /** 時間を測るか。 */
  public boolean isTimed() {
    return timing != null;
  }

  public InterviewerProfile {
    if (probeDepth < 1) {
      throw new IllegalArgumentException("probeDepth は 1 以上: " + probeDepth);
    }
    if (pressureBase < PressureConfig.MIN || pressureBase > PressureConfig.MAX) {
      throw new IllegalArgumentException("pressureBase が範囲外: " + pressureBase);
    }
  }

  /** 試験用。DB を通さずにステートマシンを動かすときに使う。 */
  public static InterviewerProfile engineerStandard() {
    return new InterviewerProfile("engineer_standard", "技術面接官", 20, 3, 0);
  }

  public static InterviewerProfile pressureHard() {
    return new InterviewerProfile("pressure_hard", "圧迫面接官", 55, 3, 0);
  }

  /**
   * 英語面接官。制限時間を持つ。
   *
   * <p>【重要】ここの値は「DBが読めないときの既定」。本番では
   * interviewer_profiles から読む。諏訪が案T2に上げたくなったとき、
   * DB を1行 UPDATE すれば済むようにするため（第8段階の判断）。
   */
  public static InterviewerProfile englishStandard() {
    return new InterviewerProfile(
        "english_standard", "English Interviewer", 20, 2, 20, TimingRules.proposalT1());
  }
}
