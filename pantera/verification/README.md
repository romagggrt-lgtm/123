# Проверка атласа Minecraft 1.21.11

`vanilla-1.21.11-items-atlas.json` — определение ванильного атласа предметов,
полученное из распакованных игровых ресурсов по адресу:
https://raw.githubusercontent.com/InventivetalentDev/minecraft-assets/1.21.11/assets/minecraft/atlases/items.json

В нём directory-source читает `textures/item/` с префиксом sprite ID `item/`.
Наличие PNG в `textures/entity/` не включает его в этот атлас. Это была причина
чёрно-фиолетовых поверхностей модели в Z_Pantera 1.0.0.

`tools/check_pantera_pack.py` проверяет ссылки моделей относительно этих правил,
а `tools/test_pantera_pack.py` воспроизводит прежнюю ошибку и подтверждает, что
проверка её обнаруживает. Здесь нет игровых текстур, JAR Minecraft или шейдеров.
