# Sources and bundled libraries

Z_Lor is a new Paper implementation. The supplied SmpLore handoff identifies its behavioural reference as Fabric 1.4.0-beta.3.3, commit `e0d2579ee3ca8b78a60f4be7d939ab79a4ef8e5e`. Selected pure rules and three reference tests were adapted; original paths, hashes and changes are listed in `reference/provenance.json`. The user's supplied artwork/audio remain attributed to their original authors; this project does not relicense those files as newly generated CC0 artwork. All 20 supplied PNGs and 9 OGGs are preserved byte for byte in the combined pack. Supplied copyright notices are retained.

The plugin bundles these dependencies, with Java packages relocated to `dev.zlor.lib.*` to avoid server plugin conflicts:

| Dependency | License / source |
|---|---|
| Byte Buddy 1.18.8, Rafael Winterhalter | Apache-2.0; https://github.com/raphw/byte-buddy ; bundled ASM notice retained |
| JLayer 1.0.1, JavaZOOM | LGPL-2.1; Maven `javazoom:jlayer:1.0.1` |
| jvorbis 1.3.6 / jogg 1.3.3, axet / Xiph.Org | BSD-style; https://gitlab.com/axet/jvorbis and https://gitlab.com/axet/jogg |
| VorbisSPI 1.0.3.3, JavaZOOM / Soundlibs | LGPL-2.1; Maven `com.googlecode.soundlibs:vorbisspi:1.0.3.3` |
| JOrbis 0.0.17.4, JCraft / Soundlibs | LGPL-2.1; Maven `com.googlecode.soundlibs:jorbis:0.0.17.4` |
| Tritonus Share 0.3.7.4 | LGPL; Maven `com.googlecode.soundlibs:tritonus-share:0.3.7.4` |

The exact corresponding source archives of all six audio dependencies are supplied in `third-party/sources/`. Their canonical URLs and SHA256 hashes are in `third-party/sources.json`. License texts are in `licenses/` and in the plugin's `META-INF/zlor-licenses/`. The library sources were not edited; shading changes package names/references. Z_Lor's Maven project and complete source are supplied so a recipient can replace/rebuild/relink these dependencies. Build with `mvn -f lor/pom.xml verify`; dependency versions and relocations are in `pom.xml`. No restriction on debugging/reverse engineering for modification of the LGPL libraries is imposed.

Paper API, Adventure and Gson are supplied by Paper and are not copied into the JAR. JUnit and MockBukkit are test-only dependencies and are not shipped inside the plugin.
