# ShadowLink

**Minecraft Fabric** — by AtsukiMC

---

## English

### What is ShadowLink?

In Minecraft there is a vanilla bug technique called **Item Shadowing**, where several inventory slots share the very same item. Changing one of them changes the others too.

Item Shadowing normally breaks when you log out, when the chunk unloads, or when the server restarts. **ShadowLink keeps it alive.** It notices Item Shadowing that you created in vanilla, remembers it, and links the items together again whenever they are loaded back in.

ShadowLink never creates Item Shadowing itself. It only protects what already exists.

### Supported versions

| Minecraft | JAR | Fabric Loader | Java |
| --- | --- | --- | --- |
| 1.18.2 | `ShadowLink-1.18.2-1.0.1.jar` | 0.13.3 or newer | 17 or newer |
| 1.18.1 | `ShadowLink-1.18.1-1.0.1.jar` | 0.13.3 or newer | 17 or newer |
| 1.18 | `ShadowLink-1.18-1.0.1.jar` | 0.13.3 or newer | 17 or newer |
| 1.16.5 | `ShadowLink-1.16.5-1.0.1.jar` | 0.11.3 or newer | 8 or newer |
| 1.16.4 | `ShadowLink-1.16.4-1.0.1.jar` | 0.11.3 or newer | 8 or newer |
| 1.16.3 | `ShadowLink-1.16.3-1.0.1.jar` | 0.11.3 or newer | 8 or newer |
| 1.16.2 | `ShadowLink-1.16.2-1.0.1.jar` | 0.11.3 or newer | 8 or newer |
| 1.16.1 | `ShadowLink-1.16.1-1.0.1.jar` | 0.11.3 or newer | 8 or newer |
| 1.16 | `ShadowLink-1.16-1.0.1.jar` | 0.11.3 or newer | 8 or newer |
| 1.12.2 | `ShadowLink-1.12.2-1.0.1.jar` | 0.15.11 or newer, with [Legacy Fabric](https://legacyfabric.net/) | 8 or newer |

### Installation

- **Dedicated server:** put the JAR in the server's `mods` folder. Players do **not** need to install anything, and they can join with a vanilla client.
- **Singleplayer:** put the same JAR in your client's `mods` folder.

Fabric API is **not** required. Carpet is not required either, but Carpet's fake players are supported.

### How to use it

There is nothing to set up: no commands, no config files, no messages. Just install it and play. Detection and restoration happen automatically in the background.

### What it does

- Detects Item Shadowing automatically.
- Restores it after logging out and back in, chunk reloads, and server restarts.
- Works with players, Carpet fake players, ender chests, chests, barrels, shulker boxes, hoppers, furnaces, storage minecarts, horses, and similar containers.
- If the shared item changed while some part was offline, the latest version is kept. Nothing is rolled back.
- If you break Item Shadowing during normal play, it stays broken. It will not come back by itself.
- Normal items and containers behave exactly as in vanilla.

### Good to know

- If something looks wrong or has been edited from the outside, ShadowLink does not guess. It leaves your items untouched, drops the link, and writes one warning to the server log. Losing the shadow is always preferred over losing items.
- Item frames, armor stands, mob equipment, and dropped items are not supported.
- If the server crashes suddenly, the last moments may not be saved.

---

## 日本語

### ShadowLinkとは？

Minecraftには、複数のインベントリのスロットが全く同じアイテムを共有する **Item Shadowing** というバグ技があります。片方を変更すると、もう片方も変わります。

Item Shadowingは、ログアウトやチャンクのアンロード、サーバーの再起動で通常は壊れてしまいます。**ShadowLinkはそれを維持します。** Vanilla上で成立したItem Shadowingを自動で見つけて記録し、読み込み直されたときに再びアイテム同士をつなぎ直します。

ShadowLink自身がItem Shadowingを作ることはありません。すでに成立しているものを守るだけです。

### 対応バージョン

| Minecraft | JAR | Fabric Loader | Java |
| --- | --- | --- | --- |
| 1.18.2 | `ShadowLink-1.18.2-1.0.1.jar` | 0.13.3以上 | 17以上 |
| 1.18.1 | `ShadowLink-1.18.1-1.0.1.jar` | 0.13.3以上 | 17以上 |
| 1.18 | `ShadowLink-1.18-1.0.1.jar` | 0.13.3以上 | 17以上 |
| 1.16.5 | `ShadowLink-1.16.5-1.0.1.jar` | 0.11.3以上 | 8以上 |
| 1.16.4 | `ShadowLink-1.16.4-1.0.1.jar` | 0.11.3以上 | 8以上 |
| 1.16.3 | `ShadowLink-1.16.3-1.0.1.jar` | 0.11.3以上 | 8以上 |
| 1.16.2 | `ShadowLink-1.16.2-1.0.1.jar` | 0.11.3以上 | 8以上 |
| 1.16.1 | `ShadowLink-1.16.1-1.0.1.jar` | 0.11.3以上 | 8以上 |
| 1.16 | `ShadowLink-1.16-1.0.1.jar` | 0.11.3以上 | 8以上 |
| 1.12.2 | `ShadowLink-1.12.2-1.0.1.jar` | 0.15.11以上＋[Legacy Fabric](https://legacyfabric.net/) | 8以上 |

### 導入方法

- **専用サーバー:** JARをサーバーの `mods` フォルダに入れてください。プレイヤー側は何も入れる必要がなく、Vanillaのクライアントで参加できます。
- **シングルプレイ:** 同じJARをクライアントの `mods` フォルダに入れてください。

Fabric APIは**不要**です。Carpetも必須ではありませんが、Carpetのフェイクプレイヤーにも対応しています。

### 使い方

設定は何もありません。コマンドも設定ファイルもメッセージもありません。入れて遊ぶだけで、検出と復元はバックグラウンドで自動的に行われます。

### できること

- Item Shadowingを自動で検出します。
- ログアウト・ログイン、チャンクの再読み込み、サーバーの再起動のあとに復元します。
- プレイヤー、Carpetのフェイクプレイヤー、エンダーチェスト、チェスト、樽、シュルカーボックス、ホッパー、かまど、チェスト付きトロッコ、ウマなどに対応しています。
- 一部がオフラインの間に共有アイテムが変化した場合は、最新の状態を維持します。巻き戻ることはありません。
- 通常のプレイでItem Shadowingを解除した場合は、解除されたままです。勝手に復活することはありません。
- 通常のアイテムやコンテナの挙動はVanillaと全く同じです。

### ご注意

- 何かおかしい場合や外部から書き換えられた場合、ShadowLinkは推測で動きません。アイテムには触れずにリンクだけを解除し、サーバーログに警告を1回出します。アイテムを失うよりShadowが解除される方を優先します。
- 額縁、アーマースタンド、Mobの装備、ドロップしたアイテムは対象外です。
- サーバーが突然クラッシュした場合、直前の状態が保存されていないことがあります。
