package jp.lightech.mensetsu.domain.scoring;

import java.util.List;
import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.InputMethod;
import jp.lightech.mensetsu.domain.interview.InterviewMachine;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.InterviewerProfile;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.Step;
import jp.lightech.mensetsu.domain.stub.StubEngine;

/**
 * 基準を比べるための、面接を受ける側の型。
 *
 * <p>案を選ぶには、同じ相手を複数の基準で評価して並べる必要がある。そのための人物像。
 *
 * <p>【重要】実在の人物を模していない（仕様書10章）。技術名も一般的なものだけを使う。
 */
public final class Candidates {

  /**
   * 1人ぶんの面接記録を作る。
   *
   * <p>【重要】台本は往復数ぶん用意すること。
   *
   * <p>最初は6個しか書いておらず、8往復の面接で余った2回が締めの挨拶の繰り返しで
   * 埋まった。その結果「よく答えられている人」の具体性が、本人の実力ではなく
   * 台本の長さで下がっていた。基準を選ぶための材料が、材料の作り方で歪んでいた。
   *
   * @param name 人物像の名前
   * @param answers 回答の台本
   * @param elapsedMs 1回の回答にかかった時間
   * @param silenceMs 1回の回答で詰まっていた時間
   */
  public record Profile(String name, List<String> answers, int elapsedMs, int silenceMs) {

    /** スタブで面接を1回通し、終わった状態を返す。 */
    public InterviewState run() {
      InterviewMachine machine = new InterviewMachine(new StubEngine());
      Step s = machine.begin(Mode.ENGINEER, InterviewerProfile.engineerStandard());
      int i = 0;
      while (!s.state().isFinished() && i < 30) {
        String text = answers.get(Math.min(i, answers.size() - 1));
        i++;
        s = machine.submit(
            s.state(), new Answer(text, InputMethod.TEXT, elapsedMs, silenceMs, false));
      }
      return s.state();
    }
  }

  private Candidates() {}

  /** よく答えられている人。数字・固有名詞・自分の行動がそろい、3段掘られても答え切る。 */
  public static Profile strong() {
    return new Profile(
        "よく答えられている",
        List.of(
            // INTRO
            "私が PostgreSQL を選びました。3人のチームで、2か月の納期に対して"
                + "あいまい一致の実装コストが最も低いと判断したためです。",
            // PROBE ×5
            "私が MySQL と比較しました。全文検索の拡張と部分インデックスの2点で決めました。"
                + "捨てたのは、チームに3年ぶんある MySQL の運用ノウハウです。",
            "私が Redis を併用する案も検討しました。件数が月500件なので、"
                + "キャッシュ層を増やす複雑さに見合わないと判断してやめました。",
            "私が Docker で環境を固定しました。3人の手元で挙動が違う問題が2回起きたためです。",
            "私が Go を選びました。同時に走る処理が5本あり、"
                + "その制御を標準ライブラリだけで書ける点を取りました。",
            "私が SQLite も試しました。同時に3人が承認するので行ロックが要ると判断し、"
                + "2日で見切りをつけています。",
            // REVERSE
            "1点だけ伺えますか。3人のチームで、レビューは何名で回されていますか。",
            // CLOSING
            "本日はありがとうございました。"),
        45_000,
        1_000);
  }

  /** ふつうの人。具体的なときと、そうでないときが混ざる。 */
  public static Profile middling() {
    return new Profile(
        "ふつう",
        List.of(
            "私が PostgreSQL を選びました。チームで使った経験があったためです。",
            "モダンだからです。",
            "私が検討しました。ほかの選択肢も見ましたが、時間が無くて詳しくは比べていません。",
            "Docker を使いました。手元と本番で差が出ないので便利です。",
            "私が3人で進めました。細かい経緯はあまり覚えていません。",
            "そのあたりはチームが決めたと聞いています。",
            "特にありません。",
            "本日はありがとうございました。"),
        70_000,
        9_000);
  }

  /** 中身が出てこない人。掘っても答えが返らない。 */
  public static Profile weak() {
    return new Profile(
        "中身が出てこない",
        List.of(
            "React を使いました。",
            "モダンだからです。",
            "なんとなくです。",
            "みんな使っていたので。",
            "わかりません。",
            "特にありません。",
            "特にありません。",
            "ありがとうございました。"),
        90_000,
        25_000);
  }

  /**
   * 技術の話が一度も出てこない人。
   *
   * <p>【重要】この人が、案の違いをいちばん強く分ける。深さが測れないので、
   * 「配り直す」か「0点にする」かで判定が変わる。
   */
  public static Profile noTechTalk() {
    return new Profile(
        "技術の話が出てこない",
        List.of(
            "私が現場の運用を見直しました。3名の担当で、手順書を2か月かけて作り直しています。",
            "私が関係部署と20回ほど調整しました。反対が3件ありましたが、"
                + "数字を出して合意を取りました。",
            "私が判断しました。月次の作業時間が30時間から8時間になっています。",
            "私が引き継ぎの資料を作りました。後任が1週間で回せるようになりました。",
            "私が手順を5段階に整理しました。差し戻しが月12件から2件に減っています。",
            "私が2名に教えました。3週間で独り立ちしています。",
            "1点だけ。引き継ぎの期間はどれくらい見ておられますか。",
            "本日はありがとうございました。"),
        50_000,
        2_000);
  }

  public static List<Profile> all() {
    return List.of(strong(), middling(), weak(), noTechTalk());
  }
}
