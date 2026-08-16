package jp.lightech.mensetsu.domain.scoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import jp.lightech.mensetsu.domain.port.Star;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** STAR構造の観察と、英語面接での「簡潔さ」の測り方。 */
class StarScoringTest {

  @Test
  @DisplayName("観察していない STAR を0点として数えないこと")
  void notObservedIsNotZero() {
    // 【重要】「観察していない」と「無かった」は違う。
    // 英語面接以外では STAR を聞かないので、そこで0点にすると全モードが減点される。
    Star notObserved = Star.notObserved();
    assertFalse(notObserved.observed());
    assertEquals(0, notObserved.count());
    assertEquals("", notObserved.missing(), "観察していないのに欠けているものを並べている");
  }

  @Test
  @DisplayName("欠けている要素を並べること")
  void listsMissingElements() {
    // 数字だけ出しても、次に何を足せばよいか分からない。
    assertEquals("結果", Star.of(true, true, true, false).missing());
    assertEquals("課題・結果", Star.of(true, false, true, false).missing());
    assertEquals("", Star.of(true, true, true, true).missing());
    assertEquals("状況・課題・行動・結果", Star.of(false, false, false, false).missing());
  }

  @Test
  @DisplayName("揃っている数を数えること")
  void counts() {
    assertEquals(4, Star.of(true, true, true, true).count());
    assertEquals(2, Star.of(true, false, true, false).count());
    assertEquals(0, Star.of(false, false, false, false).count());
  }

  @Test
  @DisplayName("英語面接では、語数とSTARで簡潔さを測ること")
  void englishUsesWordsAndStar() {
    AxisParams params = AxisParams.words(40, 150, 4000);
    assertTrue(params.useWordsAndStar());

    // 語数が帯に入り、STAR も4つ揃っている → 満点
    var full = EnglishRuns.run(List.of(new EnglishRuns.Turn(80, Star.of(true, true, true, true))));
    assertEquals(100, new Scorer(params).score(full).get(Axis.CONCISENESS).value());

    // 語数は帯に入るが STAR が0 → 語数100・STAR0 の平均で50
    var noStar = EnglishRuns.run(List.of(new EnglishRuns.Turn(80, Star.of(false, false, false, false))));
    assertEquals(50, new Scorer(params).score(noStar).get(Axis.CONCISENESS).value());
  }

  @Test
  @DisplayName("語数が帯から外れたら下がること")
  void wordCountMatters() {
    AxisParams params = AxisParams.words(40, 150, 4000);
    Star perfect = Star.of(true, true, true, true);

    var tooShort = EnglishRuns.run(List.of(new EnglishRuns.Turn(10, perfect)));
    var tooLong = EnglishRuns.run(List.of(new EnglishRuns.Turn(400, perfect)));
    var justRight = EnglishRuns.run(List.of(new EnglishRuns.Turn(80, perfect)));

    Scorer scorer = new Scorer(params);
    assertTrue(scorer.score(tooShort).get(Axis.CONCISENESS).value()
        < scorer.score(justRight).get(Axis.CONCISENESS).value());
    assertTrue(scorer.score(tooLong).get(Axis.CONCISENESS).value()
        < scorer.score(justRight).get(Axis.CONCISENESS).value());
  }

  @Test
  @DisplayName("いちばん欠けていた要素を説明に出すこと")
  void explainsWhatWasMissing() {
    AxisParams params = AxisParams.words(40, 150, 4000);
    // 結果だけがいつも抜けている。
    var run = EnglishRuns.run(List.of(
        new EnglishRuns.Turn(80, Star.of(true, true, true, false)),
        new EnglishRuns.Turn(80, Star.of(true, true, true, false)),
        new EnglishRuns.Turn(80, Star.of(true, true, true, false))));
    String why = new Scorer(params).score(run).get(Axis.CONCISENESS).why();
    assertTrue(why.contains("結果"), "欠けている要素が説明に出ていない: " + why);
    assertFalse(why.contains("状況"), "揃っていた要素が欠けている扱いになっている: " + why);
  }

  @Test
  @DisplayName("STAR を観察していなければ、語数だけで測ること")
  void fallsBackToWordsAlone() {
    AxisParams params = AxisParams.words(40, 150, 4000);
    var run = EnglishRuns.run(List.of(new EnglishRuns.Turn(80, Star.notObserved())));
    var score = new Scorer(params).score(run).get(Axis.CONCISENESS);
    assertEquals(100, score.value(), "STAR を0点として引いてしまっている");
    assertTrue(score.why().contains("観察されていません"), score.why());
  }

  @Test
  @DisplayName("日本語のモードでは、文字数で測ること")
  void japaneseUsesCharacters() {
    AxisParams params = new AxisParams(40, 200, 5000);
    assertFalse(params.useWordsAndStar());
    String why = new Scorer(params)
        .score(Candidates.strong().run()).get(Axis.CONCISENESS).why();
    assertTrue(why.contains("字"), "文字数で測っていない: " + why);
    assertFalse(why.contains("STAR"), "日本語なのに STAR を見ている: " + why);
  }

  @Test
  @DisplayName("英語面接の3案が、いずれも壊れていないこと")
  void englishProposalsAreValid() {
    for (ScoringPolicy p : ScoringPolicy.englishProposals()) {
      assertTrue(p.params().useWordsAndStar(), p.label() + " が語数モードになっていない");
      // 5段階すべてに到達できること
      for (Grade g : Grade.values()) {
        boolean reachable = false;
        for (int total = 0; total <= 100; total++) {
          if (p.thresholds().gradeOf(total) == g) {
            reachable = true;
            break;
          }
        }
        assertTrue(reachable, p.label() + " で " + g + " に到達できない");
      }
    }
  }
}
