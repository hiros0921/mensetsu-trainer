package jp.lightech.mensetsu.domain.scoring;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 軸ごとの重み。合計は必ず100。
 *
 * <h2>【重要】ここに既定値を置かないこと</h2>
 *
 * 「とりあえずの値」を静的フィールドで持つと、それが既定として使われ続ける。
 * 提案は {@link ScoringPolicy} 側に、案として名前を付けて置く。
 * このクラスは入れ物と検査だけを持つ。
 */
public record Weights(Map<Axis, Integer> byAxis) {

  public Weights {
    Map<Axis, Integer> copy = new EnumMap<>(Axis.class);
    if (byAxis != null) {
      copy.putAll(byAxis);
    }
    int sum = 0;
    for (Axis a : Axis.values()) {
      Integer w = copy.get(a);
      if (w == null) {
        throw new IllegalArgumentException("重みが指定されていない軸: " + a);
      }
      if (w < 0) {
        throw new IllegalArgumentException(a + " の重みが負: " + w);
      }
      sum += w;
    }
    if (sum != 100) {
      // 合計が100でないと、点数が100点満点にならない。
      // 「合計95で運用していた」のような事故は、あとから気づけない。
      throw new IllegalArgumentException("重みの合計が100ではない: " + sum);
    }
    byAxis = Map.copyOf(copy);
  }

  public static Weights of(int specificity, int conciseness, int consistency, int depth, int silence) {
    Map<Axis, Integer> m = new EnumMap<>(Axis.class);
    m.put(Axis.SPECIFICITY, specificity);
    m.put(Axis.CONCISENESS, conciseness);
    m.put(Axis.CONSISTENCY, consistency);
    m.put(Axis.DEPTH, depth);
    m.put(Axis.SILENCE, silence);
    return new Weights(m);
  }

  public int of(Axis axis) {
    return byAxis.get(axis);
  }

  /** 測れた軸だけの重みの合計。配り直しに使う。 */
  public int sumOf(List<Axis> axes) {
    return axes.stream().mapToInt(this::of).sum();
  }

  /** 画面に出す用。「具体性25 / 簡潔さ20 / …」 */
  public String describe() {
    StringBuilder b = new StringBuilder();
    for (Axis a : Axis.values()) {
      if (b.length() > 0) {
        b.append(" / ");
      }
      b.append(a.label()).append(of(a));
    }
    return b.toString();
  }
}
