# Ресурсы и зависимости

- Модели/текстуры/аудио из Skyfront, shadedwar и uncomplicated-fpv не получены и не использованы. Отсутствие ресурсов не трактуется как разрешение на их распространение.
- Временные модель, PNG-текстура и синтезированный двигатель созданы скриптом `tools/build_pack.py` для этого проекта; доступны по CC0-1.0: https://creativecommons.org/publicdomain/zero/1.0/legalcode . Эти ресурсы не являются выбранной моделью Skyfront.
- Paper API 1.21.11 используется как `provided`, в плагин не включается. Лицензии Paper: https://github.com/PaperMC/Paper .
- Зависимости Paper API не упаковываются в JAR. Vanilla spawn egg ссылается на стандартную модель установленной игры; файлы Minecraft не распространяются.
- Maven, JDK, Python и FFmpeg — инструменты разработки среды; не входят в распространяемые JAR/ZIP. Установленный Oracle JDK предназначен для разработки; его лицензия поставляется в каталоге установленного JDK.

При получении исходных JAR проверять исключительно метаданные лицензии и разрешённые пути моделей/текстур. Не декомпилировать и не копировать код, системы управления, другие модели или звук. Если лицензия отсутствует или несовместима — требуется разрешение правообладателя либо сохранение оригинального самостоятельного ресурса.

## SmpLore resource-pack addition (24 October 2026)

The user supplied SmpLore-Paper-1.4.0-beta.3.3-install.zip for adding its
resource pack to Z_Server_Pack. Only the nested resources ZIP is retained at
assets-src/SmpLore-Paper-1.4.0-beta.3.3-resources.zip. All its asset bytes and
original notices under assets/smplore/licenses are preserved. No plugin JAR
or executable codec library from that distribution is included or modified.
These third-party resources retain their original ownership and notices;
they are not relicensed as the project's original procedural artwork.
