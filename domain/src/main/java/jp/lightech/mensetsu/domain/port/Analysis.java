package jp.lightech.mensetsu.domain.port;

import java.util.List;
import java.util.Objects;

/**
 * 回答1つの分析結果。LLM（またはスタブ）が返す。
 *
 * <p>【重要】ここに入るのは「観察」であって「判断」ではない。
 *
 * <p>仕様書3章と4-1の要件を守るための線引き。LLM に自由記述で評価させて、それを
 * 後からパースする作りにすると壊れる。返してほしい形を先に決めて、その形しか
 * 受け取らない。そして、この観察をどう扱うか——次に進むのか、まだ掘るのか、圧を
 * 上げるのか——は、ドメイン層が決める。LLM に遷移を判断させない。
 *
 * @param technicalTerms 回答に出てきた技術用語。掘る対象。抽出は LLM に任せてよいが、
 *     何段目かのカウントはアプリ側が持つ（仕様書4-1）。
 * @param specificity 具体性の観察。
 * @param substantive 直前の問いに、実質的に答えているか。「モダンだからです」は false。
 *     これが false のまま規定の段数に達したら、知識が浅いと判定する。
 * @param hasContradiction 前の回答と矛盾しているか。PRESSURE で突く材料。
 * @param contradictionWith 何と矛盾しているか。無ければ空。
 * @param note 所見。画面には出さない。動作確認とデバッグ用。
 */
public record Analysis(
    List<String> technicalTerms,
    Specificity specificity,
    boolean substantive,
    boolean hasContradiction,
    String contradictionWith,
    String note) {

  public Analysis {
    // 防御的にコピーする。呼び出し側が後から書き換えると、
    // 記録として残した分析が静かに変わる。
    technicalTerms = technicalTerms == null ? List.of() : List.copyOf(technicalTerms);
    Objects.requireNonNull(specificity, "specificity");
    contradictionWith = contradictionWith == null ? "" : contradictionWith;
    note = note == null ? "" : note;
  }

  /** 何も観察できなかった回答。無言や、打ち切られた回答に使う。 */
  public static Analysis empty(String note) {
    return new Analysis(List.of(), Specificity.none(), false, false, "", note);
  }
}
