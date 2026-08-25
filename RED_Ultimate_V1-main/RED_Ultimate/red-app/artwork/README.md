# YOUNES launcher artwork

`younes-launcher-source.svg` is the editable design source. Android's resource
linker does not accept raw SVG files under `src/main/res`; the packaged launcher
uses the Android VectorDrawable files in `res/drawable` and adaptive-icon XML in
`res/mipmap-anydpi-v26`.

`ic_launcher_mdpi_source.svg` was recovered from `res/mipmap-mdpi/` on
2026-08-19. It had been committed there next to `ic_launcher.png`, which made
`mergeDebugResources` fail: AAPT rejects raw SVG under `res/`, so the build
broke even though the PNG that `@mipmap/ic_launcher` actually resolves to was
present and correct. The file is kept here as an editable source rather than
deleted — nothing references it at build time.
