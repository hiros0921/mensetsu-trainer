package jp.lightech.mensetsu.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ドメイン層がフレームワークから切れていることを、テストで固定する。
 *
 * <p>モジュールを分けてあるので、今この瞬間は Spring を書けない。ただし
 * build.gradle.kts に1行足せば書けるようになる。1行足すのは簡単で、
 * 「そのほうが早いから」という理由で、いつか足される。
 *
 * <p>このテストは、その1行を足した瞬間に赤くなる。仕様書8章①の要件を、
 * 人の記憶ではなくビルドに守らせるための番人。
 */
class DomainIsolationTest {

  @Test
  @DisplayName("domain のクラスパスに Spring が入っていないこと")
  void springIsNotOnClasspath() {
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("org.springframework.context.ApplicationContext"),
        "domain に Spring が入っている。モジュールの依存を確認すること");
  }

  @Test
  @DisplayName("domain のクラスパスに JDBC ドライバが入っていないこと")
  void jdbcDriverIsNotOnClasspath() {
    // 永続化もドメインの外側。ステートマシンのテストに DB を要求しない。
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("org.postgresql.Driver"),
        "domain に PostgreSQL のドライバが入っている");
  }

  @Test
  @DisplayName("domain のクラスパスに Claude の SDK が入っていないこと")
  void anthropicSdkIsNotOnClasspath() {
    // 仕様書8章②「LLM呼び出しをインターフェースで抽象化すること」。
    // InterviewerEngine と EngineObserver は domain 側にあり、その実装は app 側にある。
    // ここに SDK が入ったら、依存の向きが逆になっている。
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("com.anthropic.client.AnthropicClient"),
        "domain に Claude の SDK が入っている。実装は app 側に置くこと");
  }

  @Test
  @DisplayName("domain のクラスパスに JSON のライブラリが入っていないこと")
  void jacksonIsNotOnClasspath() {
    // 境界で受け取る形（AnalysisJson）と、中で使う形（Analysis）を分けてある理由。
    assertThrows(
        ClassNotFoundException.class,
        () -> Class.forName("com.fasterxml.jackson.databind.ObjectMapper"),
        "domain に Jackson が入っている");
  }
}
