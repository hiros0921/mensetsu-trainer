package jp.lightech.mensetsu.domain.interview;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 何を何段目まで掘っているか（仕様書4-1）。
 *
 * <h2>ここがアプリ側にある理由</h2>
 *
 * 仕様書4-1は「掘る対象（技術用語）の抽出は、LLMに任せてよい。ただし『何段目か』の
 * カウントはアプリ側で持つこと」と指示している。
 *
 * <p>LLM に段数を数えさせると、会話が長くなるほど数え間違える。そして間違えても
 * それらしい文章が返ってくるので、間違いに気づけない。数えるだけの仕事に、
 * 揺らぐ道具を使う理由が無い。
 *
 * <p>すべて不変。状態を書き換えず、新しい値を返す。ステートマシンを純粋に保つため。
 */
public record ProbeState(
    int maxDepth,
    String currentTerm,
    int askedDepth,
    int answeredDepth,
    List<String> pending,
    Set<String> seen,
    List<TermResult> finished) {

  public ProbeState {
    pending = pending == null ? List.of() : List.copyOf(pending);
    seen = seen == null ? Set.of() : Set.copyOf(seen);
    finished = finished == null ? List.of() : List.copyOf(finished);
    currentTerm = currentTerm == null ? "" : currentTerm;
  }

  public static ProbeState start(int maxDepth) {
    if (maxDepth < 1) {
      throw new IllegalArgumentException("maxDepth は 1 以上: " + maxDepth);
    }
    return new ProbeState(maxDepth, "", 0, 0, List.of(), Set.of(), List.of());
  }

  /** 今、掘っている最中の用語があるか。 */
  public boolean hasCurrent() {
    return !currentTerm.isEmpty();
  }

  /** 掘る対象が尽きたか。仕様書の TOPIC_EXHAUSTED の判定に使う。 */
  public boolean isExhausted() {
    return !hasCurrent() && pending.isEmpty();
  }

  /** 答え切れなかった用語がいくつあるか。 */
  public long failedCount() {
    return finished.stream().filter(TermResult::failed).count();
  }

  /** 到達したいちばん深い段。第5段階の「深さ」の軸で使う。 */
  public int deepestAnswered() {
    return finished.stream().mapToInt(TermResult::answeredDepth).max().orElse(answeredDepth);
  }

  /**
   * 回答に出てきた用語を、掘る対象に加える。
   *
   * <p>一度扱った用語は入れない。同じ用語を何度も掘ると、そこから抜けられなくなる。
   */
  public ProbeState offer(List<String> terms) {
    if (terms == null || terms.isEmpty()) {
      return this;
    }
    List<String> next = new ArrayList<>(pending);
    Set<String> nextSeen = new LinkedHashSet<>(seen);
    for (String t : terms) {
      if (t == null || t.isBlank()) {
        continue;
      }
      String term = t.trim();
      if (nextSeen.contains(term) || term.equals(currentTerm)) {
        continue;
      }
      nextSeen.add(term);
      next.add(term);
    }
    return new ProbeState(maxDepth, currentTerm, askedDepth, answeredDepth, next, nextSeen, finished);
  }

  /**
   * 次の用語に移る。掘る対象が無ければ、そのまま返す。
   *
   * <p>掘り終えていない用語があるまま呼んではいけない。呼び出し側が
   * {@link #hasCurrent()} を確かめること。
   */
  public ProbeState takeNext() {
    if (hasCurrent() || pending.isEmpty()) {
      return this;
    }
    List<String> rest = new ArrayList<>(pending);
    String term = rest.remove(0);
    return new ProbeState(maxDepth, term, 0, 0, rest, seen, finished);
  }

  /** 1問投げた。段数を1つ進める。 */
  public ProbeState asked() {
    if (!hasCurrent()) {
      return this;
    }
    return new ProbeState(
        maxDepth, currentTerm, askedDepth + 1, answeredDepth, pending, seen, finished);
  }

  /**
   * 回答を受けて、段数の記録を更新する。
   *
   * <p>実質的に答えられていれば、到達段をそこまで伸ばす。規定の段数まで投げ終えて
   * いたら、その用語を掘り終えたことにする。
   *
   * @param substantive 直前の問いに実質的に答えているか
   */
  public ProbeState recordAnswer(boolean substantive) {
    if (!hasCurrent()) {
      return this;
    }
    int answered = substantive ? Math.max(answeredDepth, askedDepth) : answeredDepth;

    if (askedDepth < maxDepth) {
      // まだ掘る余地がある。答えられていても、いなくても、次の段へ。
      // 答えられていれば「もう一段深く」、答えられていなければ「そこを突く」。
      // どちらも実際の面接で起きること。
      return new ProbeState(maxDepth, currentTerm, askedDepth, answered, pending, seen, finished);
    }

    // 規定の段数まで投げ終えた。この用語は終わり。
    List<TermResult> done = new ArrayList<>(finished);
    done.add(new TermResult(currentTerm, askedDepth, answered, maxDepth));
    return new ProbeState(maxDepth, "", 0, 0, pending, seen, done);
  }
}
