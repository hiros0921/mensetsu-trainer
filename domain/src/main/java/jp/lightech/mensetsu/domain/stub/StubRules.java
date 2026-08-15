package jp.lightech.mensetsu.domain.stub;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import jp.lightech.mensetsu.domain.interview.InterviewState;

/**
 * スタブが回答を観察するときの規則。
 *
 * <p>単純で予測できることを優先している。LLM の真似をさせない。
 *
 * <p>【重要】ここに出てくる語は、すべて一般的な技術名。実在の企業名・個人名は
 * 含めない（仕様書10章）。
 */
public final class StubRules {

  /** 掘る対象として拾う技術用語。試験で狙った経路を再現するために固定してある。 */
  private static final List<String> KNOWN_TERMS =
      List.of(
          "React", "Vue", "Angular", "Java", "Spring", "Go", "Rust", "Python",
          "PostgreSQL", "MySQL", "Redis", "Docker", "Kubernetes", "AWS",
          "TypeScript", "GraphQL", "REST", "gRPC", "Kafka", "Terraform");

  private static final Pattern NUMBER = Pattern.compile("[0-9０-９]+");

  /** 自分の行動として語っているか。「私が」「担当した」など。 */
  private static final List<String> FIRST_PERSON =
      List.of("私が", "私は", "自分が", "自分で", "担当し", "設計し", "実装し", "決めま", "選びま", "I ", "my ");

  /** 伝聞。主語が自分でない。圧が上がる方向（仕様書4-2）。 */
  private static final List<String> HEARSAY =
      List.of("と聞いて", "らしい", "と言われ", "だそうで", "チームが", "上司が");

  /**
   * 中身の無い回答。仕様書4-1 の例「モダンだからです」がここに入る。
   *
   * <p>これに当たると substantive=false になり、その段は答えられなかった扱いになる。
   * 3段掘って一度も外れなければ、知識が浅いと判定される経路に入る。
   */
  private static final List<String> EMPTY_PHRASES =
      List.of("モダン", "流行", "なんとなく", "特にありません", "普通に", "よくある", "みんな使って", "わかりません");

  private static final int MIN_SUBSTANTIVE_LENGTH = 20;

  private StubRules() {}

  public static StubRules byKeyword() {
    return new StubRules();
  }

  /** 回答に出てきた技術用語を拾う。大文字小文字は無視する。 */
  public List<String> extractTerms(String text) {
    String lower = text.toLowerCase();
    List<String> found = new ArrayList<>();
    for (String t : KNOWN_TERMS) {
      if (lower.contains(t.toLowerCase())) {
        found.add(t);
      }
    }
    return found;
  }

  public boolean hasNumber(String text) {
    return NUMBER.matcher(text).find();
  }

  /** 固有名詞があるか。ここでは既知の技術名が出ていることで代用する。 */
  public boolean hasProperNoun(String text) {
    return !extractTerms(text).isEmpty();
  }

  public boolean isFirstPerson(String text) {
    if (containsAny(text, HEARSAY)) {
      return false;
    }
    return containsAny(text, FIRST_PERSON);
  }

  /**
   * 直前の問いに実質的に答えているか。
   *
   * <p>2つの規則で見る。中身の無い言い回しが入っていないこと。そして、ある程度の
   * 長さがあること。長さだけで測ると「ええと、そうですね、ええと」が通ってしまうので、
   * 言い回しの検査を先に置いている。
   */
  public boolean isSubstantive(String text) {
    if (containsAny(text, EMPTY_PHRASES)) {
      return false;
    }
    return text.strip().length() >= MIN_SUBSTANTIVE_LENGTH;
  }

  /**
   * 前の回答と矛盾しているか。
   *
   * <p>スタブでは「矛盾」と書いてあったら矛盾とみなす。試験で矛盾の経路を狙って
   * 通せるようにするためだけの規則。本物の判定は第4段階で LLM に任せる。
   *
   * <p>ここを賢くしようとしないこと。スタブで通ったのに本物で落ちる、という
   * 一番困る状態になる。
   */
  public boolean contradicts(String text, InterviewState state) {
    return state.history().size() > 1 && text.contains("矛盾");
  }

  private static boolean containsAny(String text, List<String> needles) {
    for (String n : needles) {
      if (text.contains(n)) {
        return true;
      }
    }
    return false;
  }
}
