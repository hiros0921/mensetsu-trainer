package jp.lightech.mensetsu.app.llm;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import java.util.List;
import jp.lightech.mensetsu.domain.port.Analysis;
import jp.lightech.mensetsu.domain.port.Specificity;
import jp.lightech.mensetsu.domain.port.Star;

/**
 * LLM に返させる観察の形。
 *
 * <h2>なぜドメインの {@link Analysis} をそのまま使わないか</h2>
 *
 * この型には Jackson の注釈が付いている。ドメインに置くと、ドメインが JSON の
 * ライブラリに依存する。そうすると「domain の依存は JUnit だけ」という前提が崩れ、
 * ステートマシンの単体テストに JSON の準備が要るようになる。
 *
 * <p>境界で受け取る形と、中で使う形は別物にしておく。ここが変換の一箇所になる。
 *
 * <h2>【重要】ここに判断を入れない</h2>
 *
 * 入っているのは観察だけ。「浅い」「良い」といった値踏みの項目を足さないこと。
 * それはドメイン層とスコアリングの仕事（仕様書7章）。
 */
public record AnalysisJson(
    @JsonPropertyDescription("回答に出てきた技術の名前。製品名・言語名・サービス名。無ければ空配列")
        List<String> technicalTerms,
    @JsonPropertyDescription("数字が入っているか") boolean hasNumber,
    @JsonPropertyDescription("固有名詞が入っているか") boolean hasProperNoun,
    @JsonPropertyDescription("自分がやったこととして語っているか。伝聞なら false") boolean firstPerson,
    @JsonPropertyDescription("直前の質問に実質的に答えているか。中身が無ければ false")
        boolean substantive,
    @JsonPropertyDescription("これまでの発言と食い違っているか") boolean hasContradiction,
    @JsonPropertyDescription("何と食い違っているか。無ければ空文字") String contradictionWith,
    @JsonPropertyDescription("所見。20字以内") String note,
    @JsonPropertyDescription("回答の語数。英語なら空白区切りの単語数。日本語なら0") int wordCount,
    @JsonPropertyDescription("STAR構造: 状況（どういう状況だったか）が述べられているか")
        boolean starSituation,
    @JsonPropertyDescription("STAR構造: 課題（何をすべきだったか）が述べられているか")
        boolean starTask,
    @JsonPropertyDescription("STAR構造: 行動（自分が何をしたか）が述べられているか")
        boolean starAction,
    @JsonPropertyDescription("STAR構造: 結果（どうなったか）が述べられているか")
        boolean starResult) {

  /**
   * ドメインの型に移す。ここが境界。
   *
   * @param starObserved STAR を観察させたか。英語面接以外では false
   */
  public Analysis toDomain(boolean starObserved) {
    return new Analysis(
        technicalTerms == null ? List.of() : technicalTerms,
        new Specificity(hasNumber, hasProperNoun, firstPerson),
        substantive,
        hasContradiction,
        contradictionWith,
        note,
        starObserved
            ? Star.of(starSituation, starTask, starAction, starResult)
            : Star.notObserved(),
        wordCount);
  }
}
