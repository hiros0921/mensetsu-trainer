// ドメイン層。面接の進行と評価そのもの。
//
// 【重要】ここに依存を足すときは、必ず立ち止まること。
//
// このモジュールの価値は「何にも依存していないこと」にある。
// Spring も、DB のドライバも、HTTP のクライアントも、JSON のライブラリも入れない。
// 入れた瞬間に、単体テストに準備が要るようになり、
// 「フレームワークなしでステートマシンが動く」という主張が崩れる。
//
// 依存してよいのは、テスト用の JUnit だけ。

dependencies {
  testImplementation(platform("org.junit:junit-bom:5.11.4"))
  testImplementation("org.junit.jupiter:junit-jupiter")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
