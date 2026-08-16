package jp.lightech.mensetsu.domain.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * 素点を判定に変える基準。
 *
 * <h2>【重要】ここに入っている数値は、すべて「案」です</h2>
 *
 * 仕様書7章「スコアリングの重み・5段階の閾値を、AIが独自に決定しないこと。第5段階で、
 * 基準案を複数出して提案すること。実際の採用は諏訪が判断します」。
 *
 * <p>理由も仕様書に書かれている。発注者はSES事業でエンジニアの面接に同席し、実際に落ちる場面を
 * 見てきた経験がある。この基準は一次情報から決める、と。
 *
 * <p>だから下の {@code proposalA/B/C} は、<b>採用されていない案</b>です。名前に proposal と
 * 付けてあるのは、そのため。決まったら {@code adopted()} を1つ足して、そちらを使う。
 *
 * <h2>この形にした理由</h2>
 *
 * 基準を「値の組」として1つのオブジェクトにしてある。だから同じ面接の記録を、複数の基準で
 * 評価し直して比べられる。第5段階で案を選ぶとき、この比較ができないと決めようがない。
 *
 * @param version どの基準か。scores.threshold_version に入る
 * @param label 人が読む名前
 * @param note この案がどういう立場を取っているか
 * @param weights 軸ごとの重み
 * @param thresholds 5段階の境目
 * @param params 素点を出すときの境目
 * @param unmeasured 測れなかった軸の扱い
 * @param emphasised 内訳表示で目立たせる軸。重みとは別の概念（下を参照）
 */
public record ScoringPolicy(
    String version,
    String label,
    String note,
    Weights weights,
    GradeThresholds thresholds,
    AxisParams params,
    UnmeasuredHandling unmeasured,
    java.util.Set<Axis> emphasised) {

  /**
   * 重みと強調は別のもの。
   *
   * <h2>なぜ分けるか</h2>
   *
   * 重みは「合計点にどれだけ効かせるか」。強調は「本人に見せるか」。
   * この2つは、ふつう同じ方向を向くが、必ずしも一致しない。
   *
   * <p>圧迫面接の一貫性がその例。押されて話が変わったことは、本人に伝える価値がある。
   * しかし判定が揺らぐので、点数に大きく効かせると実力ではなく運で判定が動く。
   *
   * <p>諏訪さんの判断（第7段階）:
   *
   * <blockquote>「その軸が重要か」と「その軸に重みを置けるか」は、別の問題。
   * 重要だからこそ、測れるようになるまで重みは下げる。ただし内訳表示では目立たせる。
   * 点数に反映されなくても、本人に伝える価値がある。</blockquote>
   */
  public ScoringPolicy {
    emphasised = emphasised == null ? java.util.Set.of() : java.util.Set.copyOf(emphasised);
  }

  /** 内訳表示で目立たせる軸か。 */
  public boolean isEmphasised(Axis axis) {
    return emphasised.contains(axis);
  }

  /**
   * 素点から判定を出す。
   *
   * <p>合計点は「素点 × 重み ÷ 100」の和。単純にしてあるのは、
   * 「なぜこの点になったか」を画面で説明できるようにするため。
   */
  public Score evaluate(ScoreBreakdown breakdown) {
    List<Axis> measured = breakdown.measuredAxes();

    // 測れた軸が1つも無い面接。1〜2往復で切れた記録などで起こりうる。
    if (measured.isEmpty()) {
      List<Score.Contribution> none = new ArrayList<>();
      for (Axis a : Axis.values()) {
        none.add(new Score.Contribution(a, 0, 0, 0.0, false));
      }
      return new Score(Grade.D, 0, breakdown, version, none);
    }

    // 重みを配り直すか、0点として数えるか。ここが判断の分かれ目。
    int denominator =
        unmeasured == UnmeasuredHandling.REDISTRIBUTE ? weights.sumOf(measured) : 100;

    List<Score.Contribution> contributions = new ArrayList<>();
    double total = 0;
    for (Axis a : Axis.values()) {
      AxisScore s = breakdown.get(a);
      if (!s.measured()) {
        contributions.add(new Score.Contribution(a, 0, 0, 0.0, false));
        continue;
      }
      // 配り直すときは、測れた軸の重みを合計100になるよう引き伸ばす。
      double effectiveWeight = weights.of(a) * 100.0 / denominator;
      double points = s.value() * effectiveWeight / 100.0;
      total += points;
      contributions.add(
          new Score.Contribution(a, s.value(), (int) Math.round(effectiveWeight), points, true));
    }

    int rounded = (int) Math.round(Math.min(100, Math.max(0, total)));
    return new Score(thresholds.gradeOf(rounded), rounded, breakdown, version, contributions);
  }

  public String describe() {
    String band =
        params.useWordsAndStar()
            ? "簡潔さ %d〜%d語 ＋ STAR構造".formatted(params.wordMin(), params.wordMax())
            : "簡潔さ %d〜%d字".formatted(params.conciseMinChars(), params.conciseMaxChars());
    String emph = emphasised.isEmpty()
        ? "なし"
        : emphasised.stream().map(Axis::label).sorted().reduce((a, b) -> a + "・" + b).orElse("");
    return """
        %s（%s）
          立場  : %s
          重み  : %s
          境目  : %s
          帯    : %s ／ 沈黙の許容 %.1f秒
          測定なし: %s
          内訳で強調: %s"""
        .formatted(
            label, version, note,
            weights.describe(), thresholds.describe(),
            band, params.silenceToleranceMs() / 1000.0,
            unmeasured.label(), emph);
  }

  // ══════════════════════════════════════════════════════════════════
  //  採用された基準
  // ══════════════════════════════════════════════════════════════════

  /**
   * エンジニア面接モードの基準。<b>採用済み</b>。
   *
   * <h2>誰が何を決めたか</h2>
   *
   * 第5段階で3案を出し、諏訪さんが選ばれた。案Aの重み・境目・帯に、測れなかった軸の扱いだけを
   * 「0点」に差し替えたもの。選定の理由は、諏訪さんの言葉で記録しておく。
   *
   * <ul>
   *   <li><b>深さを主軸に置く</b> — このモードの中心は、技術選定を掘られて答え切れるか。
   *   <li><b>判定が散らばる</b> — 実測で S / C / D / B の4段階に分かれた。案B・Cは2人がBに
   *       固まり、練習アプリとしては情報量が落ちる。
   *   <li><b>いちばん当てにならない軸に、大きな重みを置かない</b> — 一貫性は LLM の判定で
   *       揺らぐ。案Aはそこが15で、案B・Cの20より小さい。揺らぐ判定の影響を重み配分で
   *       抑えている。
   * </ul>
   *
   * <h2>測れなかった軸を0点にする理由</h2>
   *
   * エンジニア面接で技術の話が出てこないのは、失敗そのもの。「聞かれなかった」のではない。
   * 技術用語を出さない限り掘られない設計なので、出さなかったのは本人の結果。
   * 配り直すと、話さないほうが有利になる。
   *
   * <h2>【重要】これはエンジニア面接モードだけの基準です</h2>
   *
   * 圧迫面接は一貫性が主軸（矛盾を突かれるので）、英語面接は簡潔さと沈黙が主軸
   * （STAR構造と制限時間）。モードごとに重みが違って当然。
   *
   * <p>他モードの基準は第7・第8段階で案を出し、そこで決める。それまでは
   * {@link jp.lightech.mensetsu.domain.scoring.ScoringPolicies#forMode} が
   * 「まだ決まっていない」と言って止まる。他モードの点を、この基準で黙って出さないため。
   */
  public static ScoringPolicy adoptedEngineer() {
    return new ScoringPolicy(
        "engineer-v1",
        "エンジニア面接（採用）",
        "深さを主軸に。揺らぐ一貫性の重みは抑える。測れなかった軸は0点",
        Weights.of(25, 15, 15, 35, 10),
        new GradeThresholds(90, 78, 62, 45),
        new AxisParams(40, 200, 5000),
        UnmeasuredHandling.ZERO,
        java.util.Set.of());
  }

  /**
   * 圧迫面接モードの基準。<b>採用済み</b>（第7段階）。
   *
   * <p>案R3の重み・境目・帯に、内訳表示での一貫性の強調を足したもの。
   *
   * <h2>なぜ一貫性の重みを25に下げたか</h2>
   *
   * 諏訪さんは第5段階で「圧迫面接は一貫性が主軸」と述べられた。その見立ては変わっていない。
   * 変わったのは、重みで表現するかどうか。
   *
   * <blockquote>「その軸が重要か」と「その軸に重みを置けるか」は、別の問題。
   * 一貫性の判定は、同じ台本で71点と100点に分かれる。これは軸が悪いのではなく、
   * 測定が不安定ということ。不安定な測定に大きな重みを置くと、実力ではなく運で判定が動く。
   * 重要だからこそ、測れるようになるまで重みは下げる。</blockquote>
   *
   * <p>実測: 一貫性が1回ぶれると合計点が動く幅は、重み40なら11.6点、重み25なら7.3点。
   * 11.6点は B と C の境目（58点）をまたぐ。
   *
   * <h2>ただし内訳では目立たせる</h2>
   *
   * 押されて話が変わったことは、点数に反映されなくても本人に伝える価値がある。
   * だから {@code emphasised} に一貫性を入れてある。「この判定は揺らぎます」の注記も残す。
   *
   * <h2>判定の散らばり（実測・圧の設定は案P2）</h2>
   *
   * <pre>
   *   圧に耐える          耐え切った    S 92点
   *   押されると崩れる      押し切られた   C 54点
   *   最初から中身が無い     押し切られた   D 28点
   * </pre>
   */
  public static ScoringPolicy adoptedPressure() {
    return new ScoringPolicy(
        "pressure-v1",
        "圧迫面接（採用）",
        "詰まらずに答え続けられるか。一貫性は重要だが測定が不安定なので、重みは抑えて表示で見せる",
        Weights.of(25, 10, 25, 15, 25),
        new GradeThresholds(88, 74, 58, 42),
        new AxisParams(30, 200, 6000),
        UnmeasuredHandling.ZERO,
        java.util.Set.of(Axis.CONSISTENCY));
  }

  // ══════════════════════════════════════════════════════════════════
  //  以下は【案】です。
  //  第5段階の選定に使ったもの。記録として残す。
  // ══════════════════════════════════════════════════════════════════

  /**
   * 案A「技術面接の目線」。
   *
   * <p>深さを最も重く見る。3段掘って答え切れるかが、この面接の主眼だという立場。
   * 境目も厳しめで、A以上は簡単には出ない。
   *
   * <p>この案を選ぶと、技術の話が出てこない面接は「測れなかった」として扱われ、
   * 残りの軸だけで評価される。
   */
  public static ScoringPolicy proposalA() {
    return new ScoringPolicy(
        "proposal-a",
        "案A・技術面接の目線",
        "深さを最重視。3段掘って答え切れるかが主眼。境目は厳しめ",
        Weights.of(25, 15, 15, 35, 10),
        new GradeThresholds(90, 78, 62, 45),
        new AxisParams(40, 200, 5000),
        UnmeasuredHandling.REDISTRIBUTE,
        java.util.Set.of());
  }

  /**
   * 案B「均等・標準」。
   *
   * <p>どの軸も同じくらい大事だという立場。判定の分布がいちばん広がりやすい。
   * 迷ったときの出発点として置いてある。
   */
  public static ScoringPolicy proposalB() {
    return new ScoringPolicy(
        "proposal-b",
        "案B・均等",
        "5軸をほぼ均等に見る。判定が偏りにくい",
        Weights.of(25, 20, 20, 25, 10),
        new GradeThresholds(85, 70, 55, 40),
        new AxisParams(40, 250, 8000),
        UnmeasuredHandling.REDISTRIBUTE,
        java.util.Set.of());
  }

  /**
   * 案C「伝わり方の目線」。
   *
   * <p>知識より、伝え方を重く見る立場。具体性と簡潔さを厚くする。
   * 境目も甘めで、練習として続けやすい。
   *
   * <p>測れなかった軸を0点にする。技術の話を自分から出せなかったことも結果のうち、という立場。
   * 案A・Bとここが違う。
   */
  public static ScoringPolicy proposalC() {
    return new ScoringPolicy(
        "proposal-c",
        "案C・伝わり方の目線",
        "具体性と簡潔さを重視。境目は甘め。測れなかった軸は0点",
        Weights.of(30, 25, 20, 15, 10),
        new GradeThresholds(80, 65, 48, 32),
        new AxisParams(30, 300, 10000),
        UnmeasuredHandling.ZERO,
        java.util.Set.of());
  }

  /** 3案すべて。比較して選ぶために使う。 */
  public static List<ScoringPolicy> proposals() {
    return List.of(proposalA(), proposalB(), proposalC());
  }

  // ══════════════════════════════════════════════════════════════════
  //  圧迫面接モードの基準【案】（第7段階）
  //  採用されていません。
  // ══════════════════════════════════════════════════════════════════

  /**
   * 案R1「一貫性が主軸」。
   *
   * <p>圧迫面接で突かれるのは、前の発言との食い違い。だから一貫性を最も重く見る、という立場。
   * 諏訪さんが第5段階で示された見立てをそのまま重みにしたもの。
   *
   * <p><b>ただし懸念があります。</b> 一貫性の判定は LLM がしており、揺らぐ。
   * エンジニア面接では、その揺らぎを重み15に抑えることで影響を小さくしました。
   * この案は逆に、いちばん当てにならない軸に最大の重みを置くことになります。
   *
   * <p>実測の例（第6段階）: 同じ台本を2回流して「食い違い2回」と「食い違い0回」に分かれ、
   * 一貫性が 71点と100点で29点動きました。重み40なら、合計点が11.6点動きます。
   */
  public static ScoringPolicy proposalR1() {
    return new ScoringPolicy(
        "pressure-r1",
        "案R1・一貫性が主軸",
        "食い違いを突かれる面接。一貫性を最重視。ただし判定が揺らぐ軸でもある",
        Weights.of(20, 10, 40, 15, 15),
        new GradeThresholds(88, 74, 58, 42),
        new AxisParams(30, 200, 6000),
        UnmeasuredHandling.ZERO,
        java.util.Set.of());
  }

  /**
   * 案R2「一貫性と具体性を並べる」。
   *
   * <p>圧に押されると、回答から数字と主語が消える。「チームが決めました」「たしか〜だったと」。
   * 突かれるのは食い違いだけでなく、具体性が落ちることそのものだ、という立場。
   *
   * <p>一貫性の重みを案R1より下げてあるので、判定の揺らぎの影響も小さくなります。
   */
  public static ScoringPolicy proposalR2() {
    return new ScoringPolicy(
        "pressure-r2",
        "案R2・一貫性と具体性を並べる",
        "圧に押されると数字と主語が消える。食い違いと具体性の両方を見る",
        Weights.of(30, 10, 30, 15, 15),
        new GradeThresholds(88, 74, 58, 42),
        new AxisParams(30, 200, 6000),
        UnmeasuredHandling.ZERO,
        java.util.Set.of());
  }

  /**
   * 案R3「崩れないことを見る」。
   *
   * <p>圧迫面接で試されるのは、詰まらずに答え続けられるかだ、という立場。
   * 沈黙の重みを大きく取る。
   *
   * <p>沈黙は時間の記録が要ります。画面から入力すれば取れますが、
   * 台本で回した検証では0で埋まり「測れなかった」になります。
   * この案を選ぶ場合、測れない面接では重みの25が丸ごと0点になります（扱いは ZERO）。
   */
  public static ScoringPolicy proposalR3() {
    return new ScoringPolicy(
        "pressure-r3",
        "案R3・崩れないことを見る",
        "詰まらずに答え続けられるか。沈黙を重く見る",
        Weights.of(25, 10, 25, 15, 25),
        new GradeThresholds(88, 74, 58, 42),
        new AxisParams(30, 200, 6000),
        UnmeasuredHandling.ZERO,
        java.util.Set.of());
  }

  /** 圧迫面接モードの3案。 */
  public static List<ScoringPolicy> pressureProposals() {
    return List.of(proposalR1(), proposalR2(), proposalR3());
  }

  // ══════════════════════════════════════════════════════════════════
  //  英語面接モードの基準【案】（第8段階）
  //  採用されていません。
  // ══════════════════════════════════════════════════════════════════

  /**
   * 案E-1「時間内に言い切る」。
   *
   * <p>仕様書4-3「制限時間と沈黙の再現が主眼」を、そのまま重みにしたもの。
   * 沈黙を最も重く見る。詰まらずに話し切れるかが英語面接の中心だという立場。
   *
   * <p><b>気をつける点</b>: 沈黙は時間の記録が要ります。音声入力を使わず、
   * テキストで落ち着いて書くと沈黙は発生しにくく、この軸が満点に張り付きます。
   */
  public static ScoringPolicy proposalEn1() {
    return new ScoringPolicy(
        "english-e1",
        "案E-1・時間内に言い切る",
        "沈黙を最重視。詰まらずに話し切れるかが中心",
        Weights.of(15, 25, 10, 15, 35),
        new GradeThresholds(86, 72, 56, 40),
        AxisParams.words(40, 150, 4000),
        UnmeasuredHandling.ZERO,
        java.util.Set.of());
  }

  /**
   * 案E-2「構造立てて答える」。
   *
   * <p>簡潔さ（語数＋STAR構造）を最も重く見る。時間内に収まっても、
   * 状況・課題・行動・結果が抜けていれば伝わらない、という立場。
   *
   * <p>英語面接の練習として、いちばん持ち帰るものが多いのはこの案だと思います。
   * STAR は準備で身に付くので、練習の効果が出やすい部分です。
   */
  public static ScoringPolicy proposalEn2() {
    return new ScoringPolicy(
        "english-e2",
        "案E-2・構造立てて答える",
        "語数とSTAR構造を最重視。伝わる形になっているかが中心",
        Weights.of(20, 35, 10, 10, 25),
        new GradeThresholds(86, 72, 56, 40),
        AxisParams.words(40, 150, 4000),
        UnmeasuredHandling.ZERO,
        // STAR構造は準備で身に付く部分なので、内訳で目立たせて次に何を足すかを伝える。
        java.util.Set.of(Axis.CONCISENESS));
  }

  /**
   * 案E-3「バランス」。
   *
   * <p>簡潔さと沈黙を同じくらいに置く。どちらかに寄せる根拠が薄いなら、
   * ここから始めて実際に受けてみるのが早い、という立場。
   */
  public static ScoringPolicy proposalEn3() {
    return new ScoringPolicy(
        "english-e3",
        "案E-3・バランス",
        "簡潔さと沈黙を同じくらいに。具体性も残す",
        Weights.of(20, 25, 10, 15, 30),
        new GradeThresholds(86, 72, 56, 40),
        AxisParams.words(40, 150, 4000),
        UnmeasuredHandling.ZERO,
        java.util.Set.of());
  }

  /** 英語面接モードの3案。 */
  public static List<ScoringPolicy> englishProposals() {
    return List.of(proposalEn1(), proposalEn2(), proposalEn3());
  }

  /**
   * 測れなかった軸の扱いだけを差し替えた同じ案。
   *
   * <h2>なぜこれが要るか</h2>
   *
   * 案A・Bは「配り直す」、案Cは「0点」にしてある。しかし案Cは重みも境目も違うので、
   * 判定の差がどちらから来たのか分からない。<b>2つの判断が混ざっている。</b>
   *
   * <p>混ざったまま「案Cは点が低い」と見ると、重みのせいなのか扱いのせいなのかを
   * 取り違える。同じ案で扱いだけ変えたものを並べて、初めて切り分けられる。
   */
  public ScoringPolicy with(UnmeasuredHandling handling) {
    if (handling == unmeasured) {
      return this;
    }
    return new ScoringPolicy(
        version + "-" + handling.name().toLowerCase(),
        label + "（" + handling.label() + "）",
        note,
        weights,
        thresholds,
        params,
        handling,
        emphasised);
  }
}
