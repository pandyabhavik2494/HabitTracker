# Cat sticker art

The 24 stickers are drawn in the approved UX mock (`mock.html`, functions `cat()`, `body()` and
`stickerSvg()`), plus `extra.js` for the cats the mock didn't show. `export.js` renders each one in
Chromium and writes `app/src/main/assets/cats.json`: a small draw list (paths, gradients, clip paths,
text) with the idle animation of each part (`a: [kind, originX, originY]`). The app draws it natively
with `CatArt.kt`, so the stickers stay sharp at any size and animate at the display's frame rate.

    node export.js mock.html extra.js ../../app/src/main/assets/cats.json
