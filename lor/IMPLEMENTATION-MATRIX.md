# Матрица реализации и приёмки Z_Lor 1.0.0

Эталон: переданный Fabric 1.4.0-beta.3.3 / commit e0d2579.
Названия и настройки исходного задания применяются к новому имени Z_Lor.
Исторические заметки не являются включёнными игровыми возможностями.

| Модуль | Источник | Paper / обычный клиент | Проверка | Текущий статус |
|---|---|---|---|---|
| Роли и аудит | VoidData/ActionAudit, spec §3 | UUID, JSON, закрытые диалоги | роли, снятие, рестарт, аудит | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Граница | DimensionVoid, spec §4 | API offset + адаптер нативного тика | часы, три мира, лимиты, защита | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Войдометр | VoidometerItem/Forecast | PDC/item_model, actionbar, свой OGG | обе руки, отмена, 4 знака | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Предметы и рецепты | пять JSON, spec §6 | PDC, exact-choice, личная книга | подделки, сетки, стаки | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Устройства | DeviceEntity/CoreRuntime | full-block carrier, ItemDisplay, виртуальные 9 слотов | добыча, выгрузка, хопперы, поворот | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Топливо | DeviceFuel/WorldFuelClock | сохранённые uptime-часы | 15 минут, резерв, перенос, рестарт | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Купола | HexDomeMesh/Union/Animation | ограниченная сотовая сетка частиц | объединение, открытие, распад, нагрузка | Код: сетка частиц вместо surface shader; LIVE BLOCKED |
| Граница купола | DomeBoundary | actual movement + velocity | шаг/бег/вход/касательная/телепорт | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Сигнализатор | AlarmSettings | аналоговый full-block carrier | мощность, pulse, подавление | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Сирена | SoundCatalogue | моно, позиция, пул voices | 8 файлов, поздний подход, stop | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Ядра | RelaySignal/Session, CoreRedstone | направленные очереди и tickets | оба направления, 15/8/1/0, петля | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Жёлтый щит | LootActivation/Seal/Item | настоящие дропы, PDC deadline | смерть, один тотем, рестарт, merge | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Врата/элитры | RestrictionSettings/FlightFailures | нативные события | пороги, 0.2 сек, ×4, полный запрет | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Перенос сооружений | EndStructures/Heights | журнал before/after, DragonBattle origin | +10, вверх/вниз, collision, rollback | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Драконы/ритуал | DragonRuntime/Rules | vanilla body, native combat adapter | 4/8 кристаллов, 200/500HP, stop | Код: ванильное тело; нативный размер/бой LIVE BLOCKED |
| Луч | BeamPattern/Arbiter | ItemDisplay columns + частицы | 151 центров, maxHP, арбитр, таймеры | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| ★ и HUD | DragonLeaders/Packets | bossbars, личные text displays/actionbar | выбор, треть урона, приватность | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Меню | документ 02 | центрированные Paper Dialog, range slider | токены, ввод, роль, навигация | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |
| Импорт аудио | AudioImporter/Transfer | папка сервера, Java conversion, HTTP общий пак | форматы, лимиты, отмена, SHA | Код + AUTOTEST форматов/сборки; публичная доставка LIVE BLOCKED, локального file picker нет |
| Царь (B) | KingRecovery, документ 03 | 1500 logical HP, vanilla body | урон/лечение/death/restart | Логика HP проверена AUTOTEST; бой/размер LIVE BLOCKED; новый облик NOT_IMPLEMENTED |
| Волны (B) | Encounters, документ 03 | ручной persisted controller | 10 составов, UUID, stop/restart | Код реализован; составы/правила AUTOTEST PASS, LIVE BLOCKED |
| Зелья, алтари, посох, рост мобов (C) | документ 03 | не включать неутверждённый баланс | явный статус в справке | Не определены в исходном задании |
| Общий RP | 20 PNG/9 OGG + последний серверный пак | добавление namespaces/atlas без потерь | hashes, JSON, PNG, Vorbis, collisions | Код реализован; AUTOTEST частично PASS, LIVE BLOCKED |

Проверки в настоящем Paper и vanilla-клиентах отмечаются отдельно от unit tests.
Не считать сохранённый Fabric-тест доказательством запуска нового Paper.

## Неполное соответствие эталону

Полная игровая приёмка не завершена. AUTOTEST в этой таблице означает проверенные части логики, а не прохождение всех перечисленных сценариев. Каждый сценарий реального сервера отдельно указан BLOCKED в TEST_RESULTS.json.

- Carrier TARGET: собственная нативная взрывоустойчивость и строго горизонтальная передача сигнала Fabric-блока не воспроизведены полностью (NOT_IMPLEMENTED); добыча корректируется отдельно.
- Купола — ограниченная сетка частиц; HUD — приватные надписи в мире. Экранный HUD и поверхностный shader Fabric не воспроизводятся этим vanilla-портом.
- Нативные ticker/redstone/dragon/fight-origin адаптеры требуют настоящего Paper 1.21.11. Тест учебного tick list не подтверждает работу NMS.
- Внешность Царя/розовые глаза, цвет личных сердец и отдельные принудительные диагностические плевок/дыхание/таран — NOT_IMPLEMENTED. Реальные диагностические действия: луч/пикирование с подтверждением в меню и остановкой.
- Зелья/алтари/посох/сюжетное яйцо/рост/активный эндермит/автокалендарь не включены: согласованных правил недостаточно.
- Измерение FPS/TPS, vanilla-визуал, три клиента и совместимость с текущим сервером — BLOCKED, выдуманных результатов нет.
