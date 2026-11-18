# Modelador 3D Android

Aplicativo nativo Android de modelagem 3D, inspirado no Blender. Não usa Godot.

## Base técnica

- Kotlin e Android SDK para a aplicação e a interface.
- OpenGL ES 3.0 para o viewport 3D inicial.
- Gradle para gerar o APK no GitHub Actions.
- Arquitetura preparada para um núcleo de geometria em C++/NDK e adaptadores de IA.

## Build

O workflow **Build APK** usa Java 17, Android SDK e Gradle. O APK de debug é publicado
como o artefato `modelador3d-apk`.

## Controles

Touch e mouse orbitam o viewport; dois dedos fazem zoom; a roda do mouse também faz
zoom; Delete remove o objeto selecionado; teclado e mouse são suportados pelo viewport.
