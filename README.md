<H1 align="center">Benchcraft</H1>

<img src="./app_benchcraft/src/main/ic_launcher-playstore.png" align="left" width="150" height="150" alt="Benchcraft logo">

* Benchcraft is a launcher that lets you play Minecraft: Java Edition on your Android device.

* It's a fork of [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher) (itself based on [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher)) with a redesigned interface and a built-in mod manager.

* It can run almost every version of Minecraft, and supports Forge, Fabric, Quilt and NeoForge, as well as .jar installers like [OptiFine](https://optifine.net).

<br clear="left">

## Navigation
- [Features](#features)
- [Getting Benchcraft](#getting-benchcraft)
- [Building](#building)
- [Relationship to MojoLauncher](#relationship-to-mojolauncher)
- [Known issues](#known-issues)
- [License](#license)
- [Contributing](#contributing)
- [Credits, third party components and their licenses](#credits-third-party-components-and-their-licenses)

## Features
* **Dark, Modrinth-style interface** across the launcher, dialogs and settings
* **Dashboard** with your current instance, quick actions and a big Play button
* **Mod manager**: browse and install mods, resource packs and shaders from [Modrinth](https://modrinth.com), and turn installed mods on or off without deleting them
* **Instance icons**: pick a built-in icon, upload your own image, or use the icon of any Modrinth project
* Everything MojoLauncher can do:
  * Minecraft versions from rd-132211 to 26.x snapshots, including Combat Test versions
  * Microsoft, Ely.by and local accounts
  * mrpack and CurseForge modpack import

## Getting Benchcraft
There are no prebuilt releases yet. Once there are, they'll be on the [releases page](https://github.com/sanicodes/benchcraft/releases). For now, [build it](#building) from source.

Benchcraft has its own package ID (`io.github.sanicodes.benchcraft`), so it installs next to MojoLauncher instead of replacing it. The two don't share instances or accounts.

## Building
Clone with submodules, then build. Gradle downloads the other components it needs.
```
git clone --recursive https://github.com/sanicodes/benchcraft.git
cd benchcraft
./gradlew :app_benchcraft:assembleFullDebug
```

To build a release APK instead:
```
./gradlew :app_benchcraft:assembleFullBenchcraft
```
The APK ends up in `app_benchcraft/build/outputs/apk/full/benchcraft/`. It's signed with the debug key that's checked into the repo. That key is public, so this build is meant for personal installs, not for distribution.

Build one variant per Gradle run. Building two at once (for example debug and release) fails, because they share their download folders.

If you are building on Windows:
* Replace `./gradlew` with `.\gradlew.bat`
* Make sure `mojoexec`, `sdl` and `glfw` are symlinked into `app_benchcraft/src/main/jni/`

## Relationship to MojoLauncher
Benchcraft branched off from MojoLauncher. The game runtime, renderers and most of the launcher are MojoLauncher's work, and Benchcraft regularly merges in their changes. The internal code (the `git.artdeell.mojo` Java package, native libraries and plugin IDs) keeps MojoLauncher's names so those merges stay easy.

Please report Benchcraft bugs [here](https://github.com/sanicodes/benchcraft/issues), not to the MojoLauncher team.

## Known issues
- Some physical mice may have very slow mouse speed
- On Holy GL4ES, large texture atlases may be distorted (resulting in stretched/blocky textures in modpacks)

## License
- Benchcraft is licensed under [GNU LGPLv3](./LICENSE), like MojoLauncher.

## Contributing
Contributions are welcome! Open a pull request on this repository. The description should explain what the change does and how to test it.

## Credits, third party components and their licenses
- [MojoLauncher](https://github.com/MojoLauncher/MojoLauncher), which Benchcraft is a fork of: [GNU LGPLv3 License](https://github.com/MojoLauncher/MojoLauncher/blob/v3_openjdk/LICENSE)
- [PojavLauncher](https://github.com/PojavLauncherTeam/PojavLauncher): [GNU LGPLv3 License](https://github.com/PojavLauncherTeam/PojavLauncher/blob/v3_openjdk/LICENSE)
- Android Support Libraries: [Apache License 2.0](https://android.googlesource.com/platform/prebuilts/maven_repo/android/+/master/NOTICE.txt).
- [Holy GL4ES](https://github.com/artdeell/gl4es_extra_extra/): [MIT License](https://github.com/ptitSeb/gl4es/blob/master/LICENSE).
- [OpenJDK](https://github.com/PojavLauncherTeam/openjdk-multiarch-jdk8u): [GNU GPLv2 License](https://openjdk.java.net/legal/gplv2+ce.html).
- [GLFW](https://github.com/MojoLauncher/glfw): [zlib license](https://github.com/MojoLauncher/glfw/blob/glfw34/LICENSE.md)
- [SDL](https://github.com/MojoLauncher/MojoSDL): [zlib license](https://github.com/MojoLauncher/MojoSDL/blob/main/LICENSE.txt)
- [LWJGL2-GLFW](https://github.com/MojoLauncher/lwjgl2-glfw): 3-Clause BSD license
- [LWJGL3](https://github.com/LWJGL/lwjgl3): [BSD-3 License](https://github.com/LWJGL/lwjgl3/blob/master/LICENSE.md).
- [mojoexec](https://github.com/MojoLauncher/mojoexec): [MIT License](https://github.com/MojoLauncher/mojoexec/blob/master/LICENSE)
- [Mesa 3D Graphics Library](https://gitlab.freedesktop.org/mesa/mesa): [MIT License](https://docs.mesa3d.org/license.html).
- [pro-grade](https://github.com/pro-grade/pro-grade) (Java sandboxing security manager): [Apache License 2.0](https://github.com/pro-grade/pro-grade/blob/master/LICENSE.txt).
- [Authlib-Injector](https://github.com/yushijinhun/authlib-injector) (Used for authorisation via ely.by): [AGPL-3.0](https://github.com/yushijinhun/authlib-injector/blob/develop/LICENSE).
- [alsoft](https://github.com/kcat/openal-soft/) (Audio output library): [GNU Library General Public License](https://github.com/kcat/openal-soft/blob/master/COPYING) and [Modified PFFFT](https://github.com/kcat/openal-soft/blob/master/LICENSE-pffft).
- [oboe](https://github.com/google/oboe): [Apache License 2.0](https://github.com/google/oboe/blob/main/LICENSE).
- [Modrinth](https://modrinth.com) for the mod, resource pack and shader catalogue.
- Thanks to [Mineskin](https://mineskin.eu/) for providing Minecraft avatars.
