# Compile-only MCreator API artifact

`mcreator-api-2026.3.jar` is used only for compilation (`compileOnly`). It is not
packaged into the plugin archive and is not loaded at runtime; MCreator supplies
the real classes.

Source: the class files embedded in
`reference_resources/MCreatorEAP2026344621/mcreator.exe` (MCreator EAP
2026.3.44621). MCreator is licensed under GPLv3; see `MCreator-LICENSE.txt`.
The plugin's own source remains CC BY-NC 4.0. Replace this artifact with a
locally generated API stub if the distribution policy requires it.
