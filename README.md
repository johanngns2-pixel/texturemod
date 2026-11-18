# Pack Studio 1.1.0 (Quellcode)

Neu: virtueller Raum (nichts wird in der Welt gebaut), GIF-Export in reinem Java (kein ffmpeg),
Raster-Layout wie das Kino's-Ribbon-GIF, Item-Waehler mit Icons, Live-Vorschau, Vanilla-GUI.

## Bauen
Das Projekt ist fertig aufgesetzt (Gradle/Loom, Mod Menu optional).

- **GitHub:** Ordner ins Repo pushen -> Tab *Actions* -> Workflow `build` -> Artifact `packstudio-jar`.
- **Lokal:** `gradle wrapper` (einmalig), dann `./gradlew build` -> `build/libs/packstudio-1.1.0.jar`.

Versionen in `gradle.properties` bei Fehlern mit https://fabricmc.net/develop abgleichen.
Das Paket `dev.packstudio.core` hat keine Minecraft-Abhaengigkeit. Die GUI-Klassen sind nicht gegen 1.21.11 kompiliert; bei Compile-Fehlern Log schicken.
