-- 評価に関する2つの表。
--
-- 【重要】ここには重みも閾値も入れない。
-- 仕様書7章「スコアリングの重み・5段階の閾値を、AIが独自に決定しないこと」。
-- 第5段階で案を出し、諏訪さんが選んだものを入れる。
-- この段階では、値が後から差し替わることを前提にした「入れ物」だけを作る。


-- ── 回答1つの分析結果 ──
--
-- 掘る対象（技術用語）の抽出や、矛盾の有無は LLM が判定する。
-- ただし「何段目か」のカウントはアプリ側（仕様書4-1）。
-- ここに入るのは LLM の判定であって、進行の判断ではない。
CREATE TABLE answer_analyses (
  id                bigserial PRIMARY KEY,
  turn_id           bigint NOT NULL REFERENCES turns(id) ON DELETE CASCADE,

  -- 判定の中身。JSONB にしてある。
  --
  -- なぜ列に分けないか
  --   第5・第7・第8段階で軸が増える見込みがある（STAR構造、語数、
  --   詰まった回数）。そのたびにマイグレーションを足すのは、
  --   プロトタイプの試行速度に合わない。
  --   集計に使うと決まった軸だけ、あとから列に昇格させればよい。
  --
  -- 形の目安:
  --   {
  --     "technicalTerms": ["React", "Redux"],
  --     "specificity":    {"hasNumber": true, "hasProperNoun": true,
  --                        "isFirstPerson": true},
  --     "hasContradiction": false,
  --     "contradictionWith": null,
  --     "note": "..."
  --   }
  analysis          jsonb NOT NULL,

  -- どの実装が出した判定か。スタブの固定値と本物を区別する。
  engine_kind       text NOT NULL
                      CHECK (engine_kind IN ('STUB','CLAUDE')),
  -- 実際に使ったモデル。'claude-sonnet-5' など。スタブなら空。
  model             text NOT NULL DEFAULT '',

  created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX answer_analyses_turn_idx ON answer_analyses (turn_id);


-- ── 最終スコアと5段階判定 ──
CREATE TABLE scores (
  id                bigserial PRIMARY KEY,
  session_id        bigint NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,

  -- S 即内定 / A 内定 / B 保留 / C 見送り / D お祈り（仕様書7章）
  grade             text NOT NULL CHECK (grade IN ('S','A','B','C','D')),
  total             smallint NOT NULL CHECK (total BETWEEN 0 AND 100),

  -- 内訳。仕様書7章「この内訳表示が、アプリの価値の中心」。
  --   {"specificity":72,"conciseness":55,"consistency":90,
  --    "depth":40,"silence":80}
  breakdown         jsonb NOT NULL,

  -- 【重要】どの基準で出た評価か。
  --
  -- 重みと閾値は第5段階で決め、そのあとも変わりうる。
  -- 版を残さないと、基準を変えた瞬間に過去のスコアの意味が変わる。
  -- 「前回はBだったのに」が、実力の変化なのか基準の変化なのか
  -- 区別できなくなる。
  threshold_version text NOT NULL,

  created_at        timestamptz NOT NULL DEFAULT now()
);

-- 1セッションにつき1つ。再評価するなら作り直す。
CREATE UNIQUE INDEX scores_session_uidx ON scores (session_id);
