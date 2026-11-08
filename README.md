# PBackup

Plugin na **Paper 1.21.4 / Java 21**: automatycznie zapisuje każdą śmierć gracza (cały ekwipunek, zbroja, offhand, XP, koordynaty, przyczyna, zabójca) w bazie **SQLite** i pozwala adminom w GUI przeglądać historię, podglądać ekwipunek oraz **wielokrotnie** nadawać kopię zapasową.

## Jak dostać gotowy plik .jar (przez GitHub)

To jest **plugin Paper** (wrzucasz go do folderu `plugins/`), nie mod.

1. Załóż repozytorium na GitHubie (np. `PBackup`).
2. Wrzuć do niego **całą zawartość** tego folderu (`pom.xml`, `src/`, `.github/`, `README.md`, `.gitignore`).
   - Jeśli przeglądarka nie wrzuci ukrytego folderu `.github`, utwórz plik ręcznie: **Add file → Create new file**, nazwa `.github/workflows/build.yml`, i wklej zawartość pliku z tego projektu.
3. Wejdź w zakładkę **Actions** - po wrzuceniu kodu uruchomi się workflow **Build PBackup** (możesz też kliknąć *Run workflow*).
4. Po 1-2 minutach wejdź na stronę główną repozytorium → **Releases** (prawa kolumna) i pobierz **PBackup.jar** bezpośrednio. Ten sam plik jest też w przebiegu w Actions → Artifacts.

## Budowanie lokalnie (Maven)

Wymagania: JDK 21 i Maven 3.9+.

```bash
mvn clean package
```

Gotowy plik: `target/PBackup.jar` - wrzuć go do folderu `plugins/` serwera i zrestartuj serwer.

## Komenda i uprawnienia

| Komenda | Opis |
|---|---|
| `/pbackup <nick>` | Otwiera menu gracza (online lub offline) |

| Uprawnienie | Opis | Domyślnie |
|---|---|---|
| `pbackup.use` | Komenda i główne menu | OP |
| `pbackup.view` | Historia śmierci i podgląd ekwipunku | OP |
| `pbackup.give` | Nadawanie kopii zapasowych | OP |
| `pbackup.delete` | Usuwanie historii | OP |

Uprawnienia są sprawdzane przy każdej operacji, także po kliknięciu w GUI.

## Obsługa GUI

**Historia śmierci** (najnowsze na początku, 45 wpisów na stronę):

- **LPM** na śmierci - nadaj kopię zapasową (z potwierdzeniem)
- **PPM** na śmierci - podgląd ekwipunku z chwili śmierci (tylko do odczytu)
- **SHIFT + PPM** - usuń ten jeden wpis (z potwierdzeniem)
- Dolny rząd: poprzednia / następna strona, numer strony, powrót do menu, usunięcie całej historii

Kopię z tej samej śmierci można nadać dowolną liczbę razy. Każde nadanie jest zapisane (kto, komu, kiedy, ile przedmiotów/XP), a ostatnie nadania widać w opisie wpisu.

## Jak działa nadanie kopii

- Przedmioty są **dodawane** do obecnego ekwipunku gracza - niczego, co gracz ma teraz, nie nadpisują ani nie kasują.
- Zbroja i offhand trafiają na swoje sloty, jeśli są wolne; jeśli zajęte - do zwykłego ekwipunku.
- Co się nie zmieści, upada pod nogi gracza (nic nie ginie).
- XP (`restore-xp`): tryb `ADD` dodaje punkty z chwili śmierci, `SET` ustawia XP dokładnie jak wtedy.
- Gracz, który ma dostać kopię, **musi być online**. Historię offline można przeglądać bez ograniczeń.

## Baza danych

Plik `plugins/PBackup/pbackup.db` (SQLite, tryb WAL). Wszystkie zapytania idą przez jeden osobny wątek, więc nie obciążają głównego wątku serwera. Przedmioty są zapisywane w pełnym formacie Paper (wszystkie metadane: zaklęcia, nazwy, lore, zawartość shulkerów itd.). Przy wyłączaniu serwera plugin czeka na dokończenie zapytań i zamyka połączenie.

Sterownik SQLite jest wbudowany w Paper - nic dodatkowego nie trzeba instalować.

## Konfiguracja (`config.yml`)

| Klucz | Opis |
|---|---|
| `messages.prefix` | Prefiks wiadomości |
| `settings.timezone` | Strefa czasowa dat (domyślnie `Europe/Warsaw`) |
| `settings.skip-keep-inventory-deaths` | Pomija zapis śmierci przy keepInventory |
| `settings.restore-xp` | Czy przywracać XP |
| `settings.xp-mode` | `ADD` albo `SET` |
| `settings.notify-target` | Czy informować gracza o otrzymaniu kopii |

## Struktura projektu

```
pl.pbackup
├── PBackup.java                  - klasa główna
├── commands/PBackupCommand.java
├── listeners/                    - PlayerDeathListener, InventoryListener
├── gui/                          - MainMenuGUI, DeathHistoryGUI, InventoryPreviewGUI, ConfirmGUI, GuiNavigator, GuiItems, AbstractGui
├── managers/                     - DeathHistoryManager, BackupManager
├── database/DatabaseManager.java
├── models/                       - DeathRecord, BackupRecord, DeathPage, TargetPlayer
└── utils/                        - ItemSerializer, Messages, Text, Format, XpUtil, CauseNames, Permissions
```
