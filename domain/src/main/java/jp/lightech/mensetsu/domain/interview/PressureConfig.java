package jp.lightech.mensetsu.domain.interview;

/**
 * 圧の増減幅と、強制遷移の境目（仕様書4-2）。
 *
 * <h2>【重要】ここの数値は暫定です</h2>
 *
 * 仕様書7章は「スコアリングの重み・5段階の閾値を、AIが独自に決定しないこと」と
 * している。圧のパラメータはスコアではないが、面接の進行を決めてしまう以上、
 * 同じ扱いにすべきだと判断した。
 *
 * <p>ここに入っている値は、第3段階でステートマシンを動かすために必要だから
 * 置いてあるだけ。第7段階（圧迫面接モード）で、根拠を添えた案を複数出す。
 * そのときに諏訪が決める。
 *
 * <p>値をどこにも埋め込んでいないので、差し替えは このクラスの生成だけで済む。
 *
 * <h2>暫定値の考え方（第7段階の議論の出発点として）</h2>
 *
 * 上げ幅を下げ幅より大きくしてある。実際の圧迫面接は、一度上がった空気が
 * 一言で元に戻ることはない。取り返すには何度か続ける必要がある、という非対称を
 * 入れてある。
 *
 * @param riseVague 具体性が1つも無い回答で上がる幅。
 * @param riseNoFirstPerson 自分の行動として語っていないときに上がる幅。
 * @param riseSilent 無言・打ち切りで上がる幅。
 * @param dropNumber 数字があるときに下がる幅。
 * @param dropProperNoun 固有名詞があるときに下がる幅。
 * @param dropFirstPerson 自分の行動として語っているときに下がる幅。
 * @param forceAt この値に達したら PRESSURE フェーズへ強制遷移する。
 * @param breakAt この値に達したら押し切られたとみなす（負け）。
 */
public record PressureConfig(
    int riseVague,
    int riseNoFirstPerson,
    int riseSilent,
    int dropNumber,
    int dropProperNoun,
    int dropFirstPerson,
    int forceAt,
    int breakAt) {

  public static final int MIN = 0;
  public static final int MAX = 100;

  /** 暫定値。第7段階で決め直す。 */
  public static PressureConfig provisional() {
    return new PressureConfig(12, 8, 22, 6, 4, 6, 75, 95);
  }

  public PressureConfig {
    if (forceAt >= breakAt) {
      // 強制遷移より先に負けが来ると、PRESSURE フェーズに一度も入らずに終わる。
      throw new IllegalArgumentException("forceAt は breakAt より小さいこと: " + forceAt + " / " + breakAt);
    }
    // 【重要】無言は、どんなに曖昧な回答よりも重くする。
    //
    // 最初の暫定値（riseSilent=15）で、これが破れていた。曖昧な回答は
    // riseVague(12) と riseNoFirstPerson(8) の両方が乗って +20 になり、
    // 無言の +15 を上回っていた。つまり「なんとなくです」と言うより、
    // 黙っているほうが圧が上がらない。黙るほうが得になる設定になっていた。
    //
    // 幅の値は第7段階で決め直すが、この大小関係だけは、どの案でも保つこと。
    // だから値ではなく関係のほうを、ここで検査にしている。
    int worstAnswer = riseVague + riseNoFirstPerson;
    if (riseSilent < worstAnswer) {
      throw new IllegalArgumentException(
          "無言(%d)が、最も曖昧な回答(%d)より軽い。黙るほうが得になる".formatted(riseSilent, worstAnswer));
    }
  }

  /** 範囲に収める。0 を下回らず、100 を超えない。 */
  public int clamp(int value) {
    return Math.max(MIN, Math.min(MAX, value));
  }
}
