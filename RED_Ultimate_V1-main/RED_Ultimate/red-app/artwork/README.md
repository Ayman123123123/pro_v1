# YOUNES launcher artwork

`younes-launcher-source.svg` is the editable design source. Android's resource
linker does not accept raw SVG files under `src/main/res`; the packaged launcher
uses the Android VectorDrawable files in `res/drawable` and adaptive-icon XML in
`res/mipmap-anydpi-v26`.
