# Core regression checks / コア回帰検証

## English

Run `./build-all.sh` from the repository root to build all ten versions and run their
regression checks. Each version also supports `./gradlew regressionTest` in its directory.
The Gradle 8.8 / Loom 1.7 build requires Java 17 or newer and Python 3 for artifact checks;
the 1.12.2 and 1.16 JARs still target Java 8, and the 1.18 JARs target Java 17.

The independent version directories keep their own Minecraft API, mappings, Loader, and
Java settings. `supported-versions.txt` lists the supported targets. Shared Gradle tasks in
`gradle/verification.gradle` package the existing LGPL-3.0 license and run the same checks
for every target. Test classes in `src/test` and `test-support` are excluded from the mod JARs.

The checks use each version's real Minecraft ItemStack and NBT classes. They cover reference
identity, normal replacements, recorded reloads, stale and consumed items, mismatches,
invalid slots, duplicate group IDs, and malformed saved data. `OfflineLauncher` widens access
in the named Minecraft classes in test memory, as needed after remapping; the published
JARs are not changed by this launcher. No server, world, or network connection is started.

`verifyMixinContracts`, included in each version's Gradle `check`, reads the remapped mod
JAR and the corresponding intermediary Minecraft bytecode supplied by Loom. It checks
the refmap and its namespace consistency, mixin targets, shadow fields, accessors, invokers, injection method descriptors,
callback descriptors, and supported HEAD/TAIL injection points. It also rejects missing
refmap entries and deliberately wrong method descriptors. This is a static contract check;
it does not apply mixins or launch the game.

After every version builds successfully, `build-all.sh` validates the exact JAR set, ZIP CRCs,
license, metadata, Java class targets, production classes, and refmap presence. It also tests
rejection of missing/extra versions, wrong metadata, missing license/refmap, inconsistent namespaces, test classes,
truncated/corrupt archives, and wrong manifests/checksums. Only a complete passing set replaces
the root `build/` collection. It contains the versioned JARs, `manifest.json`, and `SHA256SUMS`.
Logs, worlds, and reports are not distributed. No Release is created.

To recheck an existing build collection:

```sh
python3 scripts/verify-artifacts.py --directory build --self-test
```

Actual mixin application, real gameplay, Carpet, other mods that replace inventories, and
the minimum supported Java/Loader runtimes require separate verification. Loaded inventory
replacements are left untouched and lose their links; equal content alone is not evidence
of a reload. No GitHub Actions workflow is configured in this repository.

## 日本語

リポジトリのルートで `./build-all.sh` を実行すると、全10バージョンのビルドと
回帰検証を行います。各バージョンのディレクトリでは `./gradlew regressionTest` も使えます。
Gradle 8.8 / Loom 1.7でのビルドにはJava 17以上、成果物検査にはPython 3が必要です。
出力JARの対象は引き続き、1.12.2・1.16系がJava 8、1.18系がJava 17です。

各バージョンのディレクトリには、その版のMinecraft API・マッピング・Loader・Java設定を保持します。
対応版一覧は `supported-versions.txt` で管理します。共通のGradleタスクは
`gradle/verification.gradle` に置き、既存のLGPL-3.0ライセンスの同梱と各版の検証に使います。
`src/test`・`test-support` のクラスはModのJARに含めません。

検証には各バージョンの実際のMinecraftのItemStack・NBTクラスを使います。同一参照、通常の
入れ替え、記録済みの再読み込み、古いアイテム・消費済みアイテム、不一致、不正なスロット、
グループIDの重複、不正な保存データを確認します。`OfflineLauncher` はマッピング変換後に必要な
アクセス範囲の調整をテスト用メモリ内で行い、公開JARには変更を加えません。
サーバー・ワールド・ネットワーク接続は起動しません。

各版のGradle `check` に含まれる `verifyMixinContracts` は、マッピング変換済みのModのJARと、
Loomが提供する対応版のintermediary名のMinecraft bytecodeを読み取ります。refmapと名前空間の整合性、Mixinの対象、
Shadowフィールド、Accessor、Invoker、注入先メソッドとコールバックのdescriptor、対応している
HEAD/TAIL注入位置を確認します。refmapの項目欠落と意図的に誤らせたメソッドdescriptorも拒否します。
これは静的な契約検査であり、Mixinの適用やゲームの起動は行いません。

全版のビルド成功後、`build-all.sh` がJARの版別集合、ZIPのCRC、ライセンス、メタデータ、
Javaクラスの対象版、製品用クラス、refmapの存在を検査します。版の欠落・余分な版、版違いの
メタデータ、ライセンス・refmapの欠落、名前空間の不整合、テスト用クラスの混入、ZIPの切断・破損、マニフェスト・
チェックサムの不一致を拒否することも確認します。全検査に合格した集合だけでルートの `build/` を
更新します。収集対象は版別JAR、`manifest.json`、`SHA256SUMS` です。
ログ、ワールド、検証レポートは配布せず、Releaseも作成しません。

既存の成果物一式を再検査するコマンドは次のとおりです。

```sh
python3 scripts/verify-artifacts.py --directory build --self-test
```

実際のMixin適用、ゲームプレイ、Carpet、インベントリを置き換える他Modとの連携、最低要件の
Java・Loaderでの実行には別途確認が必要です。読み込み済みのインベントリが置き換えられた場合、
アイテムには触れずリンクを解除します。内容が同じという理由だけで再読み込みとは判定しません。
このリポジトリにはGitHub Actionsのワークフローを設定していません。
