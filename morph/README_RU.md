# Z_Morph 1.0.0

Визуальная маскировка игрока для Paper 1.21.11 и Java 21. Модель видна обычным клиентам; ресурс-пак не нужен. Внешность подменяется пакетами, настоящий хитбокс, предметы и учётная запись игрока остаются прежними. Морф действует до снятия или выхода с сервера, сохраняется после смерти и виден новым наблюдателям.

## Установка

1. Установите Paper 1.21.11 и Java 21.
2. Положите в `plugins` **три** файла: `Z_Morph-1.0.0.jar` и официальные серверные плагины [PacketEvents 2.14.0](https://github.com/retrooper/packetevents/releases/download/v2.14.0/packetevents-spigot-2.14.0.jar) и [LibsDisguises 26.10.24](https://github.com/libraryaddict/LibsDisguises/releases/download/v26.10.24/LibsDisguises-26.10.24-Github.jar). Эти зависимости в наш JAR не включены.
3. Перезапустите сервер. Проверьте в консоли, что загрузились все три плагина.

## Команды и права

`/morph set zombie` — превратиться; `/morph set creeper` — сменить облик; `/morph remove` — снять. Подсказка Tab перечисляет поддерживаемых мобов. Нужен `zmorph.use` (по умолчанию OP). Плагин не заменяет активную чужую маскировку, если она была установлена другой системой.

## Сборка

Установите JDK 21 и Maven. Скачайте официальный LibsDisguises по ссылке выше, затем:

```
mvn install:install-file -Dfile=LibsDisguises-26.10.24-Github.jar -DgroupId=me.libraryaddict.disguises -DartifactId=libsdisguises -Dversion=26.10.24 -Dpackaging=jar -DgeneratePom=true
mvn -f pom.xml verify
```

Результат: `target/Z_Morph-1.0.0.jar`. Зависимость LibsDisguises используется только для компиляции.

## Что проверялось

Автоматический тест на Paper 1.21.11 с PacketEvents, LibsDisguises и активным Z_Nick: смена двух моделей, движение и телепортация, поздно подключившийся наблюдатель, смерть/возрождение, снятие маскировки, сохранение инвентаря и отказ без права. Это не означает проверку со всеми сторонними плагинами сервера.
