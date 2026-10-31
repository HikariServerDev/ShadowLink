# Core regression checks / コア回帰検証

## English

Run `./build-all.sh` from the repository root to build all ten versions and run their
regression checks. Each version also supports `./gradlew regressionTest` in its directory.
The Gradle 8.8 / Loom 1.7 build requires Java 17 or newer; the 1.12.2 and 1.16 JARs
still target Java 8, and the 1.18 JARs target Java 17.

The checks use each version's real Minecraft ItemStack and NBT classes. They cover reference
identity, normal replacements, recorded reloads, stale and consumed items, mismatches,
invalid slots, duplicate group IDs, and malformed saved data. `OfflineLauncher` widens access
in the named Minecraft classes in test memory, as needed after remapping; the published
JARs are not changed by this launcher. No server, world, or network connection is started.

These checks do not verify mixin application, real gameplay, Carpet, or other mods that
replace inventories. Loaded inventory replacements are left untouched and lose their links;
equal content alone is not evidence of a reload.

## 日本語

リポジトリのルートで `./build-all.sh` を実行すると、全10バージョンのビルドと
回帰検証を行います。各バージョンのディレクトリでは `./gradlew regressionTest` も使えます。
Gradle 8.8 / Loom 1.7でのビルドにはJava 17以上が必要です。出力JARの対象は引き続き、
1.12.2・1.16系がJava 8、1.18系がJava 17です。

検証には各バージョンの実際のMinecraftのItemStack・NBTクラスを使います。同一参照、通常の
入れ替え、記録済みの再読み込み、古いアイテム・消費済みアイテム、不一致、不正なスロット、
グループIDの重複、不正な保存データを確認します。`OfflineLauncher` はマッピング変換後に必要な
アクセス範囲の調整をテスト用メモリ内で行い、公開JARには変更を加えません。
サーバー・ワールド・ネットワーク接続は起動しません。

Mixinの適用、実際のゲームプレイ、Carpet、インベントリを置き換える他Modとの連携は検証範囲外です。
読み込み済みのインベントリが置き換えられた場合、アイテムには触れずリンクを解除します。
内容が同じという理由だけで再読み込みとは判定しません。
