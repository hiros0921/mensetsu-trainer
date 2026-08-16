package jp.lightech.mensetsu.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jp.lightech.mensetsu.domain.interview.InterviewState;
import jp.lightech.mensetsu.domain.interview.Mode;
import jp.lightech.mensetsu.domain.interview.Phase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 採用された基準を固定する。
 *
 * <h2>ここだけは値そのものをテストで固定する</h2>
 *
 * 他のテストでは値を固定していない。値は変わりうるものだから。
 * だがこの基準は、諏訪さんが3案を比べて選んだもの。うっかり変わってはいけない。
 *
 * <p>変えるときは、このテストを直すことになる。そのとき「本当に変えてよいか」を
 * 考える機会になる。それがこのテストの役目。
 */
class AdoptedPolicyTest {

  private final ScoringPolicy adopted = ScoringPolicy.adoptedEngineer();

  @Test
  @DisplayName("採用された重みが、諏訪さんの選んだとおりであること")
  void weightsAreAsChosen() {
    // 案Aの重み。深さを主軸に、揺らぐ一貫性は抑える。
    assertEquals(25, adopted.weights().of(Axis.SPECIFICITY));
    assertEquals(15, adopted.weights().of(Axis.CONCISENESS));
    assertEquals(15, adopted.weights().of(Axis.CONSISTENCY));
    assertEquals(35, adopted.weights().of(Axis.DEPTH));
    assertEquals(10, adopted.weights().of(Axis.SILENCE));
  }

  @Test
  @DisplayName("深さが、いちばん重い軸であること")
  void depthIsTheHeaviest() {
    // このモードの中心は「技術選定を掘られて答え切れるか」。
    int depth = adopted.weights().of(Axis.DEPTH);
    for (Axis a : Axis.values()) {
      if (a == Axis.DEPTH) {
        continue;
      }
      assertTrue(depth > adopted.weights().of(a),
          "深さ(%d)が %s(%d) 以下になっている".formatted(depth, a.label(), adopted.weights().of(a)));
    }
  }

  @Test
  @DisplayName("一貫性の重みが、深さと具体性より小さいこと")
  void consistencyIsDeliberatelyLight() {
    // 一貫性は LLM の判定で揺らぐ。揺らぐ判定の影響を、重み配分で抑えている。
    // ここを上げるときは、判定の揺らぎが減ったことを確かめてから。
    int consistency = adopted.weights().of(Axis.CONSISTENCY);
    assertTrue(consistency < adopted.weights().of(Axis.DEPTH));
    assertTrue(consistency < adopted.weights().of(Axis.SPECIFICITY));
  }

  @Test
  @DisplayName("採用された境目が、諏訪さんの選んだとおりであること")
  void thresholdsAreAsChosen() {
    assertEquals(90, adopted.thresholds().s());
    assertEquals(78, adopted.thresholds().a());
    assertEquals(62, adopted.thresholds().b());
    assertEquals(45, adopted.thresholds().c());
  }

  @Test
  @DisplayName("測れなかった軸を0点として数えること")
  void unmeasuredCountsAsZero() {
    // 技術用語を出さない限り掘られない設計なので、出さなかったのは本人の結果。
    // 配り直すと、話さないほうが有利になる。
    assertEquals(UnmeasuredHandling.ZERO, adopted.unmeasured());
  }

  @Test
  @DisplayName("技術の話が出てこない人が、配り直しより低く出ること")
  void notTalkingTechIsPenalised() {
    InterviewState state = Candidates.noTechTalk().run();
    ScoreBreakdown b = new Scorer(adopted.params()).score(state);

    int withZero = adopted.evaluate(b).total();
    int withRedistribute = adopted.with(UnmeasuredHandling.REDISTRIBUTE).evaluate(b).total();

    assertTrue(withZero < withRedistribute,
        "0点にしたのに、配り直しより高い: %d / %d".formatted(withZero, withRedistribute));
  }

  @Test
  @DisplayName("逆質問が、具体性の分母に入っていること")
  void reverseQuestionCountsTowardScore() {
    // 「特にありません」を選ぶことにコストがあることを見せる、という判断。
    // 逆質問で答えた人と答えなかった人で、素点が変わることを確かめる。
    InterviewState answered = Candidates.strong().run(); // 逆質問に中身のある返しをする
    InterviewState declined = Candidates.middling().run(); // 「特にありません」

    assertFalse(answered.exchangesIn(Phase.REVERSE).isEmpty(), "この台本には逆質問が含まれるはず");

    Scorer scorer = new Scorer(adopted.params());
    assertTrue(
        scorer.score(answered).get(Axis.SPECIFICITY).value()
            > scorer.score(declined).get(Axis.SPECIFICITY).value());
  }

  @Test
  @DisplayName("締めの挨拶は、分母に入っていないこと")
  void closingIsStillExcluded() {
    InterviewState state = Candidates.strong().run();
    int turns = state.history().size();
    String why = new Scorer(adopted.params()).score(state).get(Axis.SPECIFICITY).why();
    assertFalse(why.contains(turns + "回の回答"), "全往復を分母にしている: " + why);
  }

  @Test
  @DisplayName("採用済みのモードでは、採用された基準が返ること")
  void adoptedModesUseTheirOwnPolicy() {
    // 【重要】黙って流用してはいけない。点は出るしエラーも出ないが、
    // その数字が別のモードの基準で出たことは、誰にも分からなくなる。
    assertEquals(ScoringPolicy.adoptedEngineer(), ScoringPolicies.forMode(Mode.ENGINEER));
    assertEquals(ScoringPolicy.adoptedPressure(), ScoringPolicies.forMode(Mode.PRESSURE));
    // モードごとに違う基準であること。同じなら分けている意味が無い。
    assertNotEquals(
        ScoringPolicies.forMode(Mode.ENGINEER).version(),
        ScoringPolicies.forMode(Mode.PRESSURE).version());
  }

  @Test
  @DisplayName("英語面接に案E-2が入っていること")
  void englishUsesE2() {
    // 諏訪さんの選択（第8段階）:
    //   「② 案E-2 です。沈黙は慣れでしか直らないが、STARは準備で直るからです。
    //     練習アプリなので、直せる軸に重みを置くほうが価値が出ます」
    ScoringPolicy english = ScoringPolicies.forMode(Mode.ENGLISH);
    assertEquals("english-v1", english.version());
    assertEquals(ScoringPolicy.proposalEn2().weights(), english.weights());
    assertTrue(ScoringPolicies.isDecided(Mode.ENGLISH));

    // 簡潔さ（STAR構造）が最大の重み。直せる軸に重みを置く。
    assertEquals(35, english.weights().of(Axis.CONCISENESS));
    assertTrue(
        english.weights().of(Axis.CONCISENESS) > english.weights().of(Axis.SILENCE),
        "STARより沈黙のほうが重い");
  }

  @Test
  @DisplayName("測れなかった軸でも、落とした重みが見えること")
  void unmeasuredAxisStillShowsItsWeight() {
    // 【重要】0点として数えるなら、いくらぶん落としたかを見せる。
    // 実測で見つけた: 画面に「沈黙 ×0 0.0点（測れず）」と出ていた。
    // 勘定から外れたように見えるが、実際は25点ぶんを落としている。
    ScoringPolicy p = ScoringPolicy.adoptedPressure();
    ScoreBreakdown b =
        ScoreBreakdown.of(
            java.util.List.of(
                AxisScore.of(Axis.SPECIFICITY, 80, ""),
                AxisScore.of(Axis.CONCISENESS, 80, ""),
                AxisScore.of(Axis.CONSISTENCY, 80, ""),
                AxisScore.of(Axis.DEPTH, 80, ""),
                AxisScore.notMeasured(Axis.SILENCE, "時間が記録されていないため")));
    Score score = p.evaluate(b);
    Score.Contribution silence =
        score.contributions().stream().filter(c -> c.axis() == Axis.SILENCE).findFirst()
            .orElseThrow();

    assertFalse(silence.measured());
    assertEquals(0.0, silence.points(), 0.001, "測れなかった軸に点が入っている");
    assertEquals(25, silence.weight(), "落とした重みが画面に出ない");
  }

  @Test
  @DisplayName("強調の理由が、基準ごとに違う文であること")
  void emphasisReasonBelongsToThePolicy() {
    // 【重要】画面に固定文を持たせない。
    // 実測で見つけた: 圧迫面接向けの「重みを抑えてあります」という文が、
    // 英語面接の結果画面にもそのまま出ていた。英語面接で目立たせている簡潔さは
    // 重みが最大（35）の軸なので、画面に正反対のことが書かれていた。
    String pressure = ScoringPolicy.adoptedPressure().emphasisNote();
    String english = ScoringPolicy.adoptedEnglish().emphasisNote();

    assertFalse(pressure.isBlank(), "圧迫面接に強調の理由が無い");
    assertFalse(english.isBlank(), "英語面接に強調の理由が無い");
    assertNotEquals(pressure, english, "2つの基準が同じ文を使っている");

    // 強調している軸が最大の重みなら、「抑えてあります」とは言えない。
    for (ScoringPolicy p : java.util.List.of(
        ScoringPolicy.adoptedEngineer(),
        ScoringPolicy.adoptedPressure(),
        ScoringPolicy.adoptedEnglish())) {
      for (Axis a : Axis.values()) {
        if (!p.isEmphasised(a)) {
          continue;
        }
        int max = java.util.Arrays.stream(Axis.values()).mapToInt(p.weights()::of).max().orElse(0);
        if (p.weights().of(a) == max) {
          assertFalse(p.emphasisNote().contains("抑えて"),
              p.version() + ": 最大の重みの軸に「抑えてあります」と書いている");
        }
      }
    }
  }

  @Test
  @DisplayName("英語面接では簡潔さを内訳で目立たせること")
  void englishEmphasisesConciseness() {
    assertTrue(ScoringPolicy.adoptedEnglish().isEmphasised(Axis.CONCISENESS));
  }

  @Test
  @DisplayName("案E-1を採らなかった理由が、重みに残っていること")
  void silenceIsNotTheHeaviestAxis() {
    // 諏訪さんが案E-1（沈黙35）を却下された理由:
    //   「テキスト入力だと沈黙が発生せず、35点が自動的に満点になる。
    //     実質65点満点の勝負になって、判定が壊れます」
    //
    // 【重要】これは圧迫面接の一貫性（LLM判定で揺れる）と同じ扱い。
    // 測定が不安定な軸は、重要でも単独最大にはしない。
    ScoringPolicy english = ScoringPolicies.forMode(Mode.ENGLISH);
    int silence = english.weights().of(Axis.SILENCE);
    int max =
        java.util.Arrays.stream(Axis.values()).mapToInt(english.weights()::of).max().orElse(0);
    assertTrue(silence < max, "英語面接で沈黙が最大の重みになっている");

    // 案E-1（沈黙35・簡潔さ25）は、この条件を満たさない。却下の理由がここに残る。
    assertTrue(
        ScoringPolicy.proposalEn1().weights().of(Axis.SILENCE)
            >= ScoringPolicy.proposalEn1().weights().of(Axis.CONCISENESS),
        "案E-1の性格（沈黙重視）が変わっている");
  }

  @Test
  @DisplayName("圧迫面接では一貫性を内訳で目立たせること")
  void pressureEmphasisesConsistency() {
    // 諏訪さんの条件（第7段階）:
    //   「重みは下げますが、内訳表示では一貫性を目立たせてください。
    //     押されて話が変わったことは、点数に反映されなくても本人に伝える価値があります」
    ScoringPolicy pressure = ScoringPolicy.adoptedPressure();
    assertTrue(pressure.isEmphasised(Axis.CONSISTENCY), "一貫性が強調されていない");
    // 重みは抑えたまま。強調と重みは別のもの。
    assertEquals(25, pressure.weights().of(Axis.CONSISTENCY));
  }

  @Test
  @DisplayName("圧迫面接の一貫性の説明に、揺らぐことが残っていること")
  void pressureKeepsTheReliabilityWarning() {
    // 「この判定は揺らぎます」の注記も、そのまま残してください、という条件。
    var state = Candidates.strong().run();
    String why = new Scorer(ScoringPolicy.adoptedPressure().params())
        .score(state).get(Axis.CONSISTENCY).why();
    assertTrue(why.contains("揺らぎ"), "注記が消えている: " + why);
  }
}
