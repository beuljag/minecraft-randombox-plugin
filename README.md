# RandomBox

![Minecraft](https://img.shields.io/badge/Minecraft-Paper%2026.1.x-brightgreen)
![Java](https://img.shields.io/badge/Java-25%2B-orange)
[![Release](https://img.shields.io/github/v/release/beuljag/randombox)](https://github.com/beuljag/randombox/releases/latest)
[![License](https://img.shields.io/github/license/beuljag/randombox)](LICENSE)

마인크래프트 랜덤박스(뽑기 상자) 플러그인.
**설정 파일을 건드릴 필요 없이 게임 안에서 전부 만들고, 데이터는 MariaDB 에 저장한다.**

## 주요 기능

- **인게임 GUI 편집** — 보상 추가·가중치·이름·연출까지 클릭과 채팅으로 끝낸다
- **가중치 확률** — 합계가 자동으로 분모가 되므로 보상을 추가해도 다른 값을 안 건드려도 된다
- **10연차 개봉** — 웅크리고 우클릭하면 10개를 한 번에, 왼쪽부터 확정되는 연출
- **확률 미리보기** — 상자를 들고 `F` 키를 누르면 확률표가 뜬다
- **명령어 보상** — 아이템뿐 아니라 명령어도 당첨 보상으로 지정할 수 있다
- **yml import/export** — GUI 가 싫으면 파일로 편집하고 불러올 수 있다
- **MariaDB 저장** — 테이블 자동 생성. 서버를 여러 대 굴려도 데이터가 공유된다

## 목차

- [요구사항](#요구사항)
- [설치](#설치)
- [명령어](#명령어)
- [박스 만들기](#박스-만들기)
- [여는 방법](#여는-방법)
- [yml 로 설정하기](#yml-로-설정하기)
- [config.yml](#configyml)
- [빌드](#빌드)
- [문제가 생기면](#문제가-생기면)
- [라이선스](#라이선스)

---

## 요구사항

| 항목 | 값 |
|---|---|
| 서버 | Paper **26.1.x** |
| Java | **25** 이상 |
| DB | MariaDB (빈 데이터베이스 하나) |
| 선행 플러그인 | **[SP-Framework](https://github.com/daeil0102/SP-Framework)** |

DB 접속 정보는 **SP-Framework 의 `config.yml`** 에서 관리한다. 이 플러그인에는 DB 설정이 없다.
테이블(`rb_box` `rb_reward` `rb_open_log`)은 첫 실행에 자동 생성된다.

---

## 설치

[Releases](../../releases) 에서 `randombox-1.0.0.jar` 를 받는다.

**SP-Framework 는 따로 받아야 한다** — 이 저장소에 포함되어 있지 않다.
→ [daeil0102/SP-Framework](https://github.com/daeil0102/SP-Framework) (Paper 1.17+ 는 `-base` 빌드)

```
plugins/
├── SP-Framework-1.0.0-plugin-base.jar   ← 먼저 로드돼야 함
└── randombox-1.0.0.jar
```

`depend: [SP-Framework]` 라서 프레임워크가 없으면 로드되지 않는다.
기동에 성공하면 콘솔에 이렇게 뜬다.

```
[RandomBox] DB: SP-Framework 의 설정을 사용합니다.
[RandomBox] 랜덤박스 0개를 DB 에서 불러왔습니다.
```

**DB 에 못 붙으면 플러그인이 스스로 꺼진다.** 박스가 0개인 채로 도는 것보다 낫기 때문이다.

---

## 명령어

**관리자** — `randombox.admin` (기본 OP)

| 명령어 | 설명 |
|---|---|
| `/rbox create <id> [표시이름]` | 박스 생성 → 편집창이 바로 열린다 |
| `/rbox edit <id>` | 보상·설정 편집창 |
| `/rbox delete <id> confirm` | 삭제 |
| `/rbox item <id> [대상] [개수]` | 상자 아이템 지급 |
| `/rbox test <id>` | 상자 소모 없이 테스트 개봉 |
| `/rbox import <id\|all>` | boxes.yml → DB |
| `/rbox export <id\|all>` | DB → boxes.yml |
| `/rbox reload` | DB 에서 다시 읽기 |

**플레이어** — `randombox.use` (기본 허용)

| 명령어 | 설명 |
|---|---|
| `/rbox list` | 박스 목록 |
| `/rbox preview <id>` | 보상·확률 보기 |

`/rbox` 만 치면 도움말이 나온다. `rb` 로 줄여 써도 된다.

---

## 박스 만들기

```
/rbox create test 테스트상자
```

편집창이 열린다. 조작은 전부 클릭이다.

| 조작 | 동작 |
|---|---|
| **아래 내 인벤토리의 아이템 클릭** | 보상으로 등록 (가중치 10). 아이템은 없어지지 않는다 |
| 보상 **좌클릭** / **쉬프트+좌클릭** | 가중치 +1 / +10 |
| 보상 **우클릭** / **쉬프트+우클릭** | 가중치 -1 / -10 |
| 보상 **가운데클릭** | 표시 이름을 채팅으로 입력 |
| 보상 위에서 **Q** | 그 보상 삭제 |
| **명령어 보상 추가** | 당첨 시 실행할 명령어를 채팅으로 입력 (`%player%` 치환) |
| **박스 설정** | 아래 표 |

**확률은 가중치 비율이다.** 합계가 분모가 되므로 보상을 추가해도 다른 항목을 손댈 필요가 없다.
(10 / 30 / 60 이면 10% / 30% / 60%)

**창을 닫으면 자동 저장된다.**

### 박스 설정

| 항목 | 설명 |
|---|---|
| 표시 이름 | 채팅으로 입력. `&` 색코드 |
| 상자 아이템 | 아래 인벤토리의 아이템을 클릭해서 지정. 우클릭하면 기본 상자로 |
| 뽑기 개수 | 한 번에 뽑는 보상 수 (1~9) |
| 전체 방송 | 당첨 결과를 서버 전체에 알림 |
| 개봉 연출 | 룰렛 애니메이션 |
| 왼손 키로 확률 보기 | 상자를 들고 `F` → 확률표 |

---

## 여는 방법

```
/rbox item test 10        → 상자 10개 지급
```

| 조작 | 동작 |
|---|---|
| **우클릭** | 상자 1개를 소모하고 개봉 |
| **웅크리고 우클릭** | 10개를 한 번에 개봉 (10연차). 10개 미만이면 1개만 |
| **`F` 키** | 그 상자의 확률표 |

10연차는 칸들이 동시에 돌다가 왼쪽부터 하나씩 확정되는 연출로 나온다.

---

## yml 로 설정하기

GUI 말고 파일로도 만들 수 있다. 단 **데이터 원본은 DB 이고**, `plugins/RandomBox/boxes.yml` 은 창구다.
파일만 고치고 import 를 안 하면 게임에는 아무 변화가 없다.

```
/rbox import <id|all>    파일 → DB   (파일이 이김. 보상 목록째로 덮어씀)
/rbox export <id|all>    DB → 파일   (DB 가 이김)
```

> 형식이 헷갈리면 **GUI 로 하나 만들고 `/rbox export` 로 뽑아보는 게 제일 빠르다.**

```yaml
boxes:
  starter:
    display-name: "&6초보자 상자"
    roll-count: 1
    broadcast: false
    animate: true
    preview-on-swap: true

    rewards:
      - weight: 50
        item: { material: IRON_INGOT, amount: 8 }

      - weight: 30
        item:
          material: DIAMOND_SWORD
          name: "&b서리검"
          lore: ["&7전설의 검"]
          enchants: { sharpness: 3 }

      - weight: 5
        command: "give %player% netherite_ingot 1"
        label: "&5네더라이트 주괴"
```

`box-item` 을 적으면 그 아이템을 상자로 쓴다.
아이템 항목: `material` `amount` `name` `lore` `enchants` `unbreakable` `custom-model-data`

포션·머리처럼 위 형식으로 표현이 안 되는 아이템은 export 할 때 **`data:` (Base64) 줄이 자동으로 붙는다.**
그 줄이 있으면 위 항목은 무시되므로, 손으로 고칠 거면 `data:` 를 지워야 한다.

---

## config.yml

```yaml
prefix: "&6[랜덤박스] &f"
animation-ticks: 60        # 룰렛 길이. 20틱 = 1초
drop-when-full: true       # 인벤 가득 찼을 때 바닥에 떨구기
                           # false 면 개봉이 취소되고 상자도 안 없어진다
auto-import-on-start: false
```

**`auto-import-on-start`** — 재시작마다 boxes.yml 을 DB 로 밀어넣을지.
`true` 로 두면 **GUI 로 고친 내용이 재시작 때 파일 내용으로 덮어써진다.** 기본 `false` 권장.

---

## 빌드

> ⚠ **`libs/` 에 `SP-Framework-1.0.0-plugin-base.jar` 를 먼저 넣어야 한다.**
> 이 저장소에 포함되어 있지 않다. [daeil0102/SP-Framework](https://github.com/daeil0102/SP-Framework) 에서 받을 것.
> 자세한 건 [libs/README.md](libs/README.md) 참고.

JDK **25** 가 필요하다. (없어도 Gradle toolchain 이 자동으로 받아온다)

```bash
./gradlew build
```

→ `build/libs/randombox-1.0.0.jar`

> **Gradle 이 JDK 25 에서 실행되지 않는 경우** — Gradle 8.14.3 은 JDK 25 위에서 못 돈다.
> Gradle 실행만 JDK 21~24 로 내리면 된다. 컴파일 타깃은 toolchain 이 25 로 맞춘다.
> ```bash
> ./gradlew build "-Dorg.gradle.java.home=<JDK 21~24 경로>"
> ```
>
> **`Unable to delete directory 'build'` 로 실패하는 경우** — OneDrive·Dropbox 같은
> 동기화 폴더 안에서 흔히 난다. 출력 위치를 밖으로 빼면 된다.
> ```bash
> ./gradlew build -PoutDir=/tmp/randombox-build
> ```

테스트 서버 실행: `./gradlew runServer`

## 문제가 생기면

| 증상 | 확인 |
|---|---|
| 플러그인이 안 뜸 | SP-Framework 가 먼저 뜨는지. 콘솔에 `DB:` 줄이 있는지 |
| DB 연결 실패 | **SP-Framework 의** `config.yml` — host / port / user / password |
| 보상이 종이로 보임 | 아이템 데이터를 못 읽은 것. MC 버전이 크게 바뀌면 생길 수 있다 |
| import 실패 | 에러 메시지에 어느 박스 몇 번째 보상인지 나온다 |
| 서버 켠 채로 jar 교체 후 오류 | `NoClassDefFoundError` 가 난다. **끄고 교체할 것** |

### 엔티티 필드를 지웠을 때

프레임워크가 `hbm2ddl.auto=update` 로 고정돼 있어서 **컬럼을 추가만 하고 지우지 않는다.**
필드를 빼면 남은 `NOT NULL` 컬럼 때문에 INSERT 가 거부된다. DB 에서 직접 지울 것.

```sql
ALTER TABLE rb_box DROP COLUMN <컬럼명>;
```

---

## 라이선스

[MIT](LICENSE)
