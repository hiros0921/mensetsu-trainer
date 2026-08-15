package jp.lightech.mensetsu.domain.interview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 段数を数える部分だけを、面接から切り離して確かめる。 */
class ProbeStateTest {

  @Test
  @DisplayName("同じ用語を二度は掘らないこと")
  void doesNotRepeatTerms() {
    // 同じ用語を何度も掘ると、そこから抜けられなくなる。
    ProbeState p = ProbeState.start(3).offer(List.of("React", "React", "Java"));
    assertEquals(List.of("React", "Java"), p.pending());

    p = p.takeNext(); // React を掘り始める
    p = p.offer(List.of("React", "Go"));
    assertEquals(List.of("Java", "Go"), p.pending(), "掘っている最中の用語が積み直されている");
  }

  @Test
  @DisplayName("規定の段数に達するまで、用語は終わらないこと")
  void termFinishesOnlyAtMaxDepth() {
    ProbeState p = ProbeState.start(3).offer(List.of("Go")).takeNext();

    p = p.asked().recordAnswer(true); // 1段目
    assertTrue(p.hasCurrent(), "1段目で終わってしまった");
    p = p.asked().recordAnswer(true); // 2段目
    assertTrue(p.hasCurrent(), "2段目で終わってしまった");
    p = p.asked().recordAnswer(true); // 3段目
    assertFalse(p.hasCurrent(), "3段目でも終わっていない");

    assertEquals(1, p.finished().size());
    assertEquals(3, p.finished().get(0).answeredDepth());
  }

  @Test
  @DisplayName("途中で詰まっても、あとで答え直せば到達段が伸びること")
  void recoversAfterStumbling() {
    // 1段目で詰まっても、2段目で答え直せたなら、そこまでは分かっている。
    ProbeState p = ProbeState.start(3).offer(List.of("Redis")).takeNext();
    p = p.asked().recordAnswer(false); // 1段目：詰まった
    p = p.asked().recordAnswer(true); // 2段目：答えた
    p = p.asked().recordAnswer(true); // 3段目：答えた

    TermResult r = p.finished().get(0);
    assertEquals(3, r.answeredDepth());
    assertFalse(r.failed(), "最後まで答えられたのに失敗扱いになっている");
  }

  @Test
  @DisplayName("最後の段で答えられなければ失敗になること")
  void failsIfLastDepthUnanswered() {
    ProbeState p = ProbeState.start(3).offer(List.of("Kafka")).takeNext();
    p = p.asked().recordAnswer(true);
    p = p.asked().recordAnswer(true);
    p = p.asked().recordAnswer(false); // 3段目で落ちた

    TermResult r = p.finished().get(0);
    assertEquals(2, r.answeredDepth());
    assertTrue(r.failed());
    assertEquals(1, p.failedCount());
  }

  @Test
  @DisplayName("面接官ごとに段数を変えられること")
  void respectsProfileDepth() {
    // 英語面接の面接官は2段（InterviewerProfile.englishStandard）。
    ProbeState p = ProbeState.start(2).offer(List.of("AWS")).takeNext();
    p = p.asked().recordAnswer(true);
    assertTrue(p.hasCurrent());
    p = p.asked().recordAnswer(true);
    assertFalse(p.hasCurrent(), "2段で終わるはずが終わっていない");
    assertEquals(2, p.finished().get(0).maxDepth());
  }

  @Test
  @DisplayName("空文字や空白の用語を拾わないこと")
  void ignoresBlankTerms() {
    ProbeState p = ProbeState.start(3).offer(java.util.Arrays.asList("", "  ", null, "Rust"));
    assertEquals(List.of("Rust"), p.pending());
  }
}
