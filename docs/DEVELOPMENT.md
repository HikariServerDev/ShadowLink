# Development and verification / 開発と検証

## English

### Structure and compatibility

```text
src/main/java/dev/atsukimc/shadowlink/  # One entrypoint/core/mixin source tree
src/main/resources/                   # One metadata/mixin template and icon
src/test/java/                        # One shared ShadowRegression
test-support/                        # Offline, mixin and JAR comparison tools
gradle/targets.json                  # Mappings, Loader minimum and Java per target
gradle/mixins.json                   # Version predicates for existing mixin classes
gradle/compat.gradle                # Yarn name replacements and source selection
gradle/verification.gradle          # Checks shared by all targets
gradle/wrapper/                     # One Gradle Wrapper
scripts/                            # Structure/source/artifact/collection checks
docs/migration/baseline.json        # Frozen pre-migration reference
versions/                           # Generated Stonecutter projects; ignored by Git
build/distributions/                # Validated JARs, manifest.json, SHA256SUMS; ignored
```

The former ten top-level projects are removed: 217 production Java files become 27 maintained
files; ten core regression copies become one. `versions/` contains generated build data only.
Do not edit or commit it. All targets use the root build script, resources and Wrapper.

The design follows [Hikari-Tweaks at 5504932](https://github.com/HikariServerDev/Hikari-Tweaks/tree/5504932d342116f26ce541e26e527a571336e97c):
one source tree, Stonecutter conditions, centralized dependencies/resources and aggregate builds.
Stonecutter 0.7.10 retains Gradle 8.8, Loom 1.7.4 and Legacy Looming 1.7.7. Stonecutter
0.8.4/0.9.7 require Gradle 9, so no unrelated Loom upgrade is forced. Groovy DSL is retained;
VCS and IDE active versions both remain 1.18.2.

- `supported-versions.txt` is the build/CI list; `gradle/targets.json` holds each target's original mappings, Loader minimum and Java 8/17 release.
- Only 1.12.2 applies Legacy Looming. Integer dimension IDs, guarded world lookup, the reflective public `ShadowState(String)` constructor and `stateOwner` handoff are retained without a separate Legacy Java copy.
- 1.16–1.16.4 use `CompoundTag`/`ListTag`: Stonecutter replaces canonical `NbtCompound`/`NbtList` names in both main and test sources. The 1.16.5 serialization method names remain distinct where required.
- Local conditions preserve chunk/entity lifecycle hooks, player/cursor APIs, Java 8 classes versus Java 17 records, and persistence APIs. Incompatible mixin classes are excluded from compilation and the generated mixin list. Refmap names, `required: true` and `defaultRequire: 1` are preserved.
- Compatibility stays at compile time rather than adding runtime adapters. Original packages, member layouts, instructions, ItemStack identity comparisons and NBT code can therefore be compared with the baseline.

### Commands and distribution

Use JDK 21 and Python 3.12. Mod classes still target Java 8 (1.12.2/1.16.x) or Java 17
(1.18.x). Fabric API and Carpet are not dependencies. Gradle defaults to two workers, a
3 GiB heap and no parallel project execution.

```sh
./build-all.sh
# Equivalent shared Gradle entry point:
./gradlew chiseledBuild --no-daemon --max-workers=2

# One target; keep the VCS baseline registered for preprocessing:
./gradlew -PtargetMinecraft=1.16.5 :1.16.5:buildAndCollect --no-daemon --max-workers=2

python3 scripts/verify-artifacts.py --directory build/distributions --self-test
python3 scripts/check-structure.py
```

Each target runs the existing OfflineLauncher regression, static MixinContractCheck,
effective-source/dependency comparison and normalized JAR comparison. `buildAndCollect`
stages its checked JAR in `build/collected/<MC>/`. `chiseledBuild` validates the complete set
and atomically replaces `build/distributions/` only after artifact and negative tests pass.
Development/sources JARs, logs, worlds, caches, models and reports are not distributed.

The original source/JARs remain at [19ab5ac](https://github.com/HikariServerDev/ShadowLink/tree/19ab5ac)
and were separately backed up before removing old directories and tracked outputs. No history,
tag or Release is deleted or rewritten. New binaries come from successful CI's
`shadowlink-distribution` artifact (retention: 30 days), or a local build. No Release is
published automatically. Existing manifest schema and `ShadowLink-<MC>-<mod>.jar` naming
are retained; this migration uses 1.0.5 instead of overwriting published 1.0.4 artifacts.

### Verification boundaries

The frozen baseline records actual pre-migration source tokens, normalized class/resource
hashes, JAR checksums and resolved runtime coordinates for all ten versions. Source comparison
ignores comments/whitespace while preserving identifiers and literals. Class comparison strips
debug tables and rebuilds the constant pool, preserving descriptors, access flags, instructions,
annotations, frames and bootstrap methods. Resource comparison ignores JSON formatting/key order
and the declared Mod version only. Never regenerate the baseline just to make a failed check
pass. Intentional later behavior changes need a separate review of the reference and policy.

CI uses the same target list and checks, with independent jobs (maximum three concurrently),
followed by the same complete-set collector. Triggers are PRs, pushes to main and manual dispatch;
ordinary branch pushes do not duplicate PR runs. Fork approval settings are unchanged.
Local builds, hosted CI, static checks, actual runtime injection and in-game Item Shadowing/
FakePlayer acceptance are separate evidence. Unit/static checks do not claim Runtime PASS.
Actual IDE import/completion needs checking in the IDE.

## 日本語

### 構成と互換性

Javaソースは `src/main/java` の27ファイル、コア回帰テストは `src/test/java` の1ファイルへ
統合しました。旧10ディレクトリを削除し、リソース、Gradle設定、Wrapperもルートへ集約しています。
`versions/` はStonecutterの生成データです。直接編集・Git管理しません。
配布物は `build/distributions/` に収集します。

[Hikari-Tweaksの5504932](https://github.com/HikariServerDev/Hikari-Tweaks/tree/5504932d342116f26ce541e26e527a571336e97c)
を参考に、単一ソース、条件付きコンパイル、依存関係・リソースの集中管理、一括ビルドを採用しました。
Gradle 9を必須とするStonecutter 0.8.4・0.9.7ではなく0.7.10を使い、Gradle 8.8、Loom 1.7.4、
Legacy Looming 1.7.7を維持しています。Groovy DSLを継続し、VCS基準版とIDEのアクティブ版は
いずれも1.18.2です。

- `supported-versions.txt`：ビルド・CI対象。`gradle/targets.json`：各版の既存マッピング、Loader下限、Java 8/17要件。
- `gradle/mixins.json`：Mixin選択条件。`gradle/compat.gradle`：型名変換と対象外ソースの除外。
- `gradle/verification.gradle`：共通検証。`test-support/`：OfflineLauncher、Mixin検査、JAR比較。
- `docs/migration/baseline.json`：移行前の実測結果を記録した固定基準。

Legacy Loomingは1.12.2だけに適用します。整数のdimension ID、未知のIDがOverworldへ解決される
ことを防ぐ検査、公開コンストラクター `ShadowState(String)` と `stateOwner` による保存状態の
受け渡しを保持しています。Legacy用Javaツリーの複製はありません。

1.16～1.16.4のYarnでは `CompoundTag`・`ListTag` を使うため、共通ソースの
`NbtCompound`・`NbtList` を製品・テストの両方で置換します。1.16.5固有の保存メソッド名も保持します。
Chunk・Entityのロード／アンロード、プレイヤー・カーソル、Java 8の通常クラスとJava 17のrecord、
PersistentStateの差分は、必要な箇所の条件分岐に残しています。対応しないMixinクラスはコンパイル対象と
Mixin一覧の両方から除外し、refmap名と注入必須条件は変更しません。

実行時アダプターを追加せず、コンパイル時に互換性を処理します。既存のパッケージ、クラス・メンバー構成、
実行コード、ItemStackの同一参照比較、NBT処理を移行前と直接照合できる構成です。

### コマンドと配布

ビルドにはJDK 21とPython 3.12を使います。Modの要件は従来どおり、1.12.2・1.16系がJava 8、
1.18系がJava 17です。Fabric API・Carpet依存は追加していません。Gradleは最大2ワーカー、
ヒープ3 GiB、プロジェクト間の並列実行なしに制限します。

```sh
./build-all.sh
# 同じ一括ビルドをGradleから実行:
./gradlew chiseledBuild --no-daemon --max-workers=2

# 1版だけをビルド・検証。前処理用のVCS基準版も登録します。
./gradlew -PtargetMinecraft=1.16.5 :1.16.5:buildAndCollect --no-daemon --max-workers=2

python3 scripts/verify-artifacts.py --directory build/distributions --self-test
python3 scripts/check-structure.py
```

各版で既存のコア回帰テスト、Mixin静的検査、実効ソース・依存関係の比較、JAR等価性検査を実行します。
検査済みJARを `build/collected/<MC>/` に出力し、一括ビルドが全版の成果物と不正入力の拒否を検証した後に
`build/distributions/` を置き換えます。配布対象は版別JAR、`manifest.json`、`SHA256SUMS` だけです。
開発用・ソースJAR、ログ、ワールド、キャッシュ、検証モデル、レポートは配布しません。

旧1.0.4のソースと成果物は[19ab5acの履歴](https://github.com/HikariServerDev/ShadowLink/tree/19ab5ac)
に残り、削除前に別途バックアップも作成しました。履歴・タグ・Releaseは削除・書き換えません。
新しいJARは成功したCIの `shadowlink-distribution` 成果物から取得するか、ローカルでビルドします。
CI成果物は30日保持し、Releaseは自動公開しません。既存1.0.4を上書きせず、同じ命名規則の1.0.5として
出力します。マニフェストの既存スキーマも維持します。

### 検証範囲

固定基準には移行前の全10版について、ソースのトークン、正規化したクラス・リソース、JARのチェックサム、
解決した依存関係を記録しています。ソース比較はコメントと空白を除外し、識別子とリテラルを保持します。
クラス比較はデバッグ情報と定数プールの配置差を除き、descriptor、アクセス条件、命令、注釈、
スタックフレーム、bootstrap methodを照合します。リソースではJSONの書式・キー順とMod版番号だけを除きます。
検査を通すためだけに基準を再生成しないでください。将来の意図的な動作変更では、基準と検証方針を別途レビューします。

CIは同じ対応版一覧を使い、独立した版別ジョブを最大3並列で実行してから、共通の収集処理で全配布物を検査します。
起動条件はPR、mainへのPush、手動実行です。通常の作業ブランチへのPushではPRの実行を重複させず、
forkの承認設定も変更しません。
ローカルビルド、GitHub Actions、静的Mixin検査、実際のMixin適用、ゲーム内のItem Shadowing・FakePlayer確認は
別々に扱います。単体・静的検査の成功をRuntime PASSとは扱いません。IDEの読み込みと補完も実際のIDEで別途確認します。
