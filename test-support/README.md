# Test support / 検証用コード

These classes run for every target and are excluded from production JARs:

- `OfflineLauncher`: runs the shared `src/test/java/.../ShadowRegression.java` against real Minecraft ItemStack/NBT classes. Access widening is confined to its test classloader.
- `MixinContractCheck`: checks shipped refmaps, runtime bytecode, callbacks, and rejection of missing mappings/wrong descriptors.
- `JarEquivalenceCheck`: compares classes/resources with the frozen pre-migration baseline, ignoring debug/ZIP data, JSON key order and the declared Mod version only.

See [Development and verification](../docs/DEVELOPMENT.md) for commands and evidence limits.

全ターゲットで次の検査を実行します。これらのクラスは製品JARには含めません。

- `OfflineLauncher`：共通の `src/test/java/.../ShadowRegression.java` を、実際のMinecraftのItemStack・NBTクラスで実行します。アクセス範囲の調整はテスト用クラスローダー内に限定します。
- `MixinContractCheck`：配布用refmap、実行時のbytecode、コールバックを照合し、マッピングの欠落と誤ったdescriptorの拒否も確認します。
- `JarEquivalenceCheck`：移行前の固定基準とクラス・リソースを比較します。除外するのはデバッグ情報、ZIP情報、JSONのキー順、Modの版番号だけです。

コマンドと検証範囲は[開発と検証](../docs/DEVELOPMENT.md)を参照してください。
