package jp.lightech.mensetsu.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.lightech.mensetsu.domain.interview.Answer;
import jp.lightech.mensetsu.domain.interview.InputMethod;
import jp.lightech.mensetsu.domain.interview.InterviewMachine;
import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.InterviewerProfile;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.Step;
import jp.lightech.mensetsu.domain.stub.StubEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * PROBE の上限で掘りかけになった用語を、深さに含めること。
 *
 * <h2>なぜこのテストがあるか</h2>
 *
 * 本物の API で面接を通したときに見つけた。PostgreSQL を3段、MySQL を2段掘られた面接で、
 * 深さが「1件の技術について 3段中 1段」と出た。MySQL は上限で打ち切られ、
 * 掘り終えていないので記録に入らなかった。
 *
 * <p>2段答えた事実が、どこにも残らないまま捨てられていた。上限で切れるかどうかは
 * 面接の長さの都合で、本人の実力ではない。
 */
class DepthCutoffTest {

  /** 毎回新しい技術名を出す人。掘り終える前に上限が来る。 */
  private InterviewState runWithManyTerms() {
    List<String> answers =
        List.of(
            "私が PostgreSQL を選びました。3人のチームで2か月の納期に対して最適だと判断しました。",
            "私が MySQL と比較しました。全文検索の拡張が決め手で、3年ぶんのノウハウを捨てました。",
            "私が Redis も検討しました。月500件ならキャッシュ層は要らないと2日で判断しました。",
            "私が Docker で環境を固定しました。3人の手元で挙動が違う問題が2回起きたためです。",
            "私が Go を選びました。同時に走る処理が5本あり、標準ライブラリで書ける点を取りました。",
            "私が Kafka も見ました。月500件では過剰だと3日で結論を出しています。",
            "1点だけ伺えますか。レビューは何名で回されていますか。",
            "本日はありがとうございました。");

    InterviewMachine machine = new InterviewMachine(new StubEngine());
    Step s = machine.begin(Mode.ENGINEER, InterviewerProfile.engineerStandard());
    int i = 0;
    while (!s.state().isFinished() && i < 20) {
      String t = answers.get(Math.min(i, answers.size() - 1));
      i++;
      s = machine.submit(s.state(), new Answer(t, InputMethod.TEXT, 40_000, 1_000, false));
    }
    return s.state();
  }

  @Test
  @DisplayName("掘りかけの用語も、深さの分母と分子に入ること")
  void unfinishedTermCounts() {
    InterviewState state = runWithManyTerms();
    var probe = state.probe();
    // この台本では、上限で打ち切られた用語が残っているはず。
    assertTrue(probe.hasCurrent() && probe.askedDepth() > 0,
        "この台本では掘りかけが残るはず。残っていないなら台本を見直すこと");

    AxisScore depth =
        new Scorer(ScoringPolicy.adoptedEngineer().params()).score(state).get(Axis.DEPTH);

    assertTrue(depth.measured(), "掘られているのに測れなかったことになっている");
    assertTrue(depth.why().contains("打ち切り"),
        "打ち切りになった用語が説明に出ていない: " + depth.why());
    assertTrue(depth.why().contains(probe.currentTerm()),
        "打ち切りになった用語名が出ていない: " + depth.why());
  }

  @Test
  @DisplayName("掘りかけしか無くても、深さが測れること")
  void onlyUnfinishedStillMeasures() {
    // 1つ目の用語を掘っている最中に面接が終わる場合。
    // 「掘られたのに測れなかった」になると、答えた事実が消える。
    List<String> answers =
        List.of(
            "私が PostgreSQL を選びました。3人のチームで2か月の納期に対して最適でした。",
            "特にありません。",
            "特にありません。",
            "特にありません。",
            "特にありません。",
            "特にありません。",
            "特にありません。",
            "ありがとうございました。");

    InterviewMachine machine = new InterviewMachine(new StubEngine());
    Step s = machine.begin(Mode.ENGINEER, InterviewerProfile.engineerStandard());
    int i = 0;
    while (!s.state().isFinished() && i < 20) {
      s = machine.submit(
          s.state(),
          new Answer(answers.get(Math.min(i++, answers.size() - 1)),
              InputMethod.TEXT, 40_000, 1_000, false));
    }

    AxisScore depth =
        new Scorer(ScoringPolicy.adoptedEngineer().params()).score(s.state()).get(Axis.DEPTH);
    assertTrue(depth.measured(), "掘られたのに測れなかったことになっている: " + depth.why());
  }
}
