# Point Quest 应用图标

图案使用打开的书页与四角奖励星，表达学习、进步和积分奖励。陶土红与纸白延续应用当前的纸感主题。

- `point-quest-icon.png`：1254 × 1254 原始 PNG，背景不透明。
- `app/src/main/res/drawable-nodpi/point_quest_icon.png`：432 × 432 运行时 PNG。
- `app/src/main/res/mipmap-anydpi-v26/point_quest_launcher.xml`：普通与圆形桌面共用的自适应图标，由系统施加外形遮罩。
- `app/src/main/res/drawable/point_quest_launcher_foreground.xml`：为图案额外保留 4dp 裁切余量。

生成方式：内置 `image_gen`，2026-09-15。原图经一次背景修正；运行时文件仅进行等比例缩小。原有启动图标资源保留，新图标通过 Manifest 引用。

## 验证

- `:app:processDebugResources --offline` 编译通过，合并后的 Manifest 两个图标属性均指向新资源。
- 运行时 PNG 为 432 × 432、不含透明通道；原图与生成结果 SHA-256 一致。
- 浅色主体经过 4dp 内缩后距画布中心的最大半径约为 32.84dp，位于 33dp 安全半径内。
- 本次交付彩色启动图标；未新增独立的 Android 13 主题单色图层，未执行真机桌面验证。

## 初始生成提示词

```text
Use case: logo-brand.
Asset type: final square Android app launcher icon for Point Quest, a student learning and quiz app where answering questions earns points and rewards.
Primary request: create one distinctive, beautifully crafted app icon that expresses learning, steady progress and earned rewards. The existing app has a calm editorial paper aesthetic, terracotta accents and warm ivory pages.
Scene/backdrop: perfectly solid flat terracotta #A33E26, full bleed to all four square edges. The canvas itself is the icon, no external card, border, framing or mockup.
Subject: a bold warm-ivory #FFFCF5 open book with two elegantly folded pages, and one compact four-point reward star rising directly above the central book spine. The pages form a subtle ascending gesture, calm and optimistic. One visually unified mark, strong readable silhouette, generous negative space. A little warm sand #F2D2B1 may distinguish the lower paper fold, but keep the mark overwhelmingly ivory.
Style/medium: refined geometric paper-cut brand mark, mostly flat vector-like geometry with exceptionally clean smooth edges and subtly softened corners. Quiet, tactile editorial personality without visible paper grain. A memorable professional education app, sophisticated enough for older students and friendly enough for younger ones.
Composition/framing: exactly square 1024x1024. Center the complete book-and-star symbol both optically and geometrically. All parts of the main symbol must fit entirely within the central circular area of diameter 600 px: approximately x=230..794, y=240..770, with generous empty terracotta around it. The open book is the dominant shape; the star is substantial enough to survive at 48px icon size. Precisely balanced spacing between star and book.
Constraints: render only this single finished icon. No text, no lettering, no initials, no numbers, no words, no watermarks. No outer rounded square (the Android launcher supplies its own mask). No gradients, no drop shadows, no metallic gloss, no glow, no fine lines, no photographic objects, no extra sparkles, no piles of coins, no arrows, no graduation cap. Keep the full-bleed background uniformly #A33E26.
```

## 最终修正提示词

```text
Use case: background-extraction (background replacement edit).
Edit target: the provided Point Quest book-and-star icon.
Make one targeted production correction: replace ALL transparent pixels and the entire red-brown haze/halo around the book and star with a perfectly uniform, FULLY OPAQUE terracotta background #A33E26 covering every pixel of the square canvas outside the paper symbol, including all four corners. This is an opaque square illustration, NOT a transparent cutout.
Keep the existing attractive book silhouette, the single four-point star, ivory #FFFCF5 pages and sand #F2D2B1 page folds. Remove all shading and gradients from both the paper and the backdrop: use crisp flat fills. The star and book must have clean ivory-to-terracotta edges with no outline or shadow.
Center the symbol and reduce its overall size slightly so its width is 52% of the full square canvas and its height is about 48%; leave wide, evenly balanced terracotta margins around it. Intended for an Android adaptive launcher icon so the mark must remain inside a circle of radius 30% of the canvas width.
Output a finished FULLY OPAQUE square app icon image, no transparent background, no checkerboard, no black background, no rounded-corner cutout, no added words or characters.
```
