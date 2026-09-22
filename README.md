# Webcraft — Spring Expert Assignment

WebSocket 기반 멀티플레이 게임 서버입니다.
플레이어 등록과 월드 관리는 REST로, 실시간 이동·채팅·접속자 조회는 WebSocket으로 처리하며,
`webcraft-engine`에 액션을 위임하고 Redis로 접속 상태를 관리합니다.

- 과제 원본: [f-api/game-spring-expert-assignment](https://github.com/f-api/game-spring-expert-assignment)

---

## 기술 스택

| 구분 | 내용 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4.1.0 (Web MVC, WebSocket, Data JPA, Data Redis, Validation) |
| Engine | webcraft-engine 2.1.5 |
| Database | MySQL 8 (Docker) / Redis 7 (Docker), H2 (테스트 전용) |
| Build | Gradle |
| Test | JUnit 5, Spring Boot Test, Testcontainers |
| Etc | Lombok, Docker |

---

## 실행 방법

### 1. MySQL · Redis 컨테이너 실행

```bash
docker run -d --name mysql-expert \
  -p 3307:3306 \
  -e MYSQL_ROOT_PASSWORD=1234 \
  -e MYSQL_DATABASE=expert \
  mysql:8

docker run -d --name redis-expert -p 6379:6379 redis:7
```

### 2. 서버 실행

로컬 개발용 설정이라 `src/main/resources/application.properties`는 저장소에 포함되어 있습니다.
위 컨테이너를 그대로 띄웠다면 별도 설정 없이 실행하면 됩니다. (Redis는 기본값 `localhost:6379`를 사용합니다.)

```bash
./gradlew bootRun
```

<details>
<summary>application.properties</summary>

```properties
spring.application.name=game-expert
spring.datasource.url=jdbc:mysql://localhost:3307/expert
spring.datasource.username=root
spring.datasource.password=1234
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
```

</details>

---

## REST API

| Method | URI | 설명 | 성공 응답 |
| --- | --- | --- | --- |
| `POST` | `/players` | 플레이어 등록 (닉네임 중복 불가) | `201 Created` |
| `GET` | `/worlds` | 월드 목록 조회 | `200` `List<WorldSummaryResponse>` |
| `POST` | `/worlds` | 월드 생성 (기본 월드 최대 3개) | `201` `CreatedWorldResponse` |
| `DELETE` | `/worlds/{id}` | 월드 삭제 | `204 No Content` |
| `DELETE` | `/worlds/{id}/if-matches` | 조건부 월드 삭제 | `204 No Content` |
| `GET` | `/worlds/{worldId}/chats?limit=50` | 최근 채팅 조회 (오래된 순) | `200` `List<ChatMessageResponse>` |
| `GET` | `/worlds/{worldId}/chats/history` | 채팅 내역 커서 페이징 | `200` `ChatHistoryPage` |

**닉네임 규칙** — 2~12자, 영문 대소문자·숫자·밑줄만 허용 (`^[a-zA-Z0-9_]+$`)

**주요 에러 코드** — `DUPLICATE_NICKNAME`(409), `WORLD_LIMIT_REACHED`(409), `WORLD_NOT_FOUND`(404), `WORLD_BASELINE_INITIALIZING`(503)

---

## WebSocket API

**엔드포인트** — `ws://localhost:8080/ws/worlds/{worldId}?nickname={nickname}`

핸드셰이크 단계에서 닉네임으로 플레이어를, 경로의 `worldId`로 월드를 조회해
검증에 성공한 연결만 세션 속성(`nickname`, `worldId`, `playerId`, `seed`, `difficulty`)을 갖고 열립니다.

| Close Code | 의미 |
| --- | --- |
| `4000` | 닉네임 누락 또는 등록되지 않은 플레이어 |
| `4001` | 존재하지 않는 월드이거나 차원(하위) 월드 |
| `4002` | 같은 월드에 동일 닉네임이 이미 접속 중 |

### 메시지 타입

| type | 동작 | 응답 |
| --- | --- | --- |
| `ping` | Redis 접속 상태 갱신(heartbeat) | `pong` (요청자에게만) |
| `move` | 좌표·시선·자세를 읽어 엔진 액션 큐에 전달 | 엔진 브로드캐스트 |
| `chat` | 1~200자 검증 → 레이트 리밋 → DB 저장 | `chat` (같은 월드 전체) |
| `onlineUsers` | 현재 월드의 열린 연결 닉네임 목록 | `onlineUsers` (요청자에게만) |

라우팅 실패는 `error` 메시지로 응답합니다 — `INVALID_JSON`, `INVALID_MESSAGE`, `UNKNOWN_TYPE`, `QUEUE_FULL`, `CHAT_COOLDOWN`, `INTERNAL_ERROR`.

---

## 패키지 구조

```
com.gameexpert
├── GameExpertApplication.java
├── common/         ErrorResponse, GlobalExceptionHandler
├── config/         WebSocketConfig, DomainStorageConfiguration
├── player/         controller · service · repository · entity · dto
├── world/          controller · service · repository · entity · dto
├── chat/
│   ├── controller/ WorldChatController, ChatHistoryController
│   ├── service/    ChatService, ChatDelivery, LocalChatSender,
│   │               RecentChatCache, ChatRateLimitService …
│   ├── relay/      ChatRelay, ChatSubscriptionConfig
│   ├── event/      ChatSavedEvent, ChatCacheInvalidationListener
│   ├── repository/ · entity/ · dto/
├── presence/       PresenceService (Redis Sorted Set)
├── trial/          WorldTrialSite, TrialPersistenceService
└── ws/
    ├── GameWebSocketHandler, MessageRouter, WorldBroadcaster
    ├── NicknameHandshakeInterceptor, WorldSessionRegistry, ConnectionCleanup
    ├── handler/    PingWsHandler, MoveWsHandler, ChatWsHandler, OnlineUsersWsHandler
    └── dto/        PongResponse, ChatResponse, OnlineUsersResponse
```

---

## 구현 내용 (커밋 기준)

| Lv | 내용 | 커밋 |
| --- | --- | --- |
| 1 | `application.properties` 환경 설정과 배포용 Dockerfile 작성 | `chore: application.properties 환경 변수 추가와 Dockerfile 생성` |
| 2 | `chat_messages`에 `(world_id, created_at)` 복합 인덱스 선언 | `perf: 월드별 최근 채팅 조회 인덱스 생성` |
| 3 | 플레이어 등록 API — 닉네임 검증(`@Size`, `@Pattern`)과 중복 확인 | `feat: 플레이어 등록 API 구현` |
| 4 | 월드 생성 잠금 안에서 기본 월드 3개 제한 검사 | `feat: 월드 생성 개수 제한` |
| 5 | 채팅 저장과 최근 내역 조회(최신 N건을 오래된 순으로 반환) | `feat: 채팅 저장과 내역 조회` |
| 6 | 최근 채팅 조회 API 매핑 (`GET /worlds/{worldId}/chats`) | `feat: 최근 채팅 조회 API 추가` |
| 7 | 핸드셰이크에서 플레이어·월드 조회 후 연결 속성 저장 | `feat: WebSocket 핸드셰이크에서 플레이어와 월드 조회, 연결 속성 저장` |
| 8 | `HandshakeInterceptor`를 WebSocket 핸들러 등록에 연결 | `feat: HandshakeInterceptor를 WebSocket Handler에 등록` |
| 9 | 월드별 세션 레지스트리 — `putIfAbsent`로 중복 접속 차단 | `feat: 월드별 WebSocket 세션 레지스트리 구현` |
| 10 | Redis Sorted Set으로 월드 접속 상태(join/leave) 관리 | `feat: Redis Sorted Set으로 월드 접속 상태 관리` |
| 11 | 메시지 타입별 핸들러 라우팅과 Ping/Pong 하트비트 | `feat: 메시지 라우팅과 Ping/Pong 하트비트 구현` |
| 12 | 이동 메시지를 파싱해 엔진 액션 큐로 전달 | `feat: 플레이어 이동 요청을 엔진 큐로 전달` |
| 13 | 채팅 메시지 처리와 `ChatResponse` 응답 DTO 완성 | `feat: 채팅 요청 처리와 WebSocket 응답` |
| 14 | 같은 월드 참여자에게 채팅 브로드캐스트 | `feat: 같은 월드 참여자에게 채팅 브로드캐스트` |
| 15 | 월드 접속자 목록 조회 핸들러와 응답 DTO | `feat: 월드 접속자 목록 조회 핸들러` |

> Lv 16~20(채팅 커서 페이징 마무리, Redis 최근 채팅 캐시, 원자적 레이트 리밋, Pub/Sub 채팅 릴레이)은 미구현입니다.

---

## 핵심 구현 포인트

### 중복 접속을 막는 원자적 세션 등록 (Lv 9)

월드별 세션은 `ConcurrentHashMap`으로 관리합니다. "있는지 확인하고 없으면 넣는" 두 단계로 나누면
동시에 들어온 두 연결이 모두 통과할 수 있으므로, `putIfAbsent()`의 반환값으로 등록 성공 여부를 판정해
한 번의 원자적 연산으로 처리했습니다.

```java
boolean added = sessions.putIfAbsent(nicknameKey, candidate) == null;
```

등록에 실패한 연결은 상위에서 `4002`로 닫힙니다.

### Redis Sorted Set을 이용한 접속 상태 관리 (Lv 10)

접속자를 `connectionId`를 member, 만료 시각을 score로 하는 ZSet에 저장했습니다.
score가 만료 시각이므로 `ping` 하트비트로 score만 갱신하면 되고, 끊긴 연결은 score 범위로 한 번에 정리할 수 있습니다.

### 최근 채팅 조회의 정렬 (Lv 5)

"최신 N건"을 가져와야 하므로 조회는 `createdAt DESC, id DESC`로 하고,
응답은 대화 순서대로 보여야 하므로 `reversed()`로 뒤집어 오래된 순으로 반환합니다.
정렬 기준에 `id`를 함께 둔 이유는 `createdAt`이 같은 메시지의 순서를 확정하기 위해서입니다.

### 복합 인덱스 (Lv 2)

위 조회는 `world_id`로 걸러 `created_at`으로 정렬하므로,
`(world_id, created_at)` 순서의 복합 인덱스를 선언해 필터와 정렬을 한 인덱스로 처리하도록 했습니다.

### 메시지 라우팅과 예외 변환 (Lv 11)

`MessageRouter`는 `type` 필드로 핸들러를 찾아 위임하고, 핸들러가 던진 예외를 WebSocket 에러 코드로 변환합니다
(`ActionQueueOverflowException` → `QUEUE_FULL`, `IllegalArgumentException` → `INVALID_MESSAGE`).
각 핸들러는 자신의 메시지 타입만 알면 되고, 에러 응답 형식은 라우터 한 곳에서 관리됩니다.

---

## 트러블슈팅

### `save()` 뒤에 `.orElseThrow()`를 붙여 컴파일 오류 (Lv 5)

`findById()`의 반환 타입에 익숙해진 상태로 `save()`에도 `.orElseThrow()`를 붙였습니다.
`Optional`을 반환하는 것은 "없을 수도 있는" 조회이고, `save()`는 저장된 엔티티를 그대로 반환합니다.
반환 타입을 확인하는 습관으로 해결했습니다.

### `if` 블록 안에서 선언한 변수를 아래에서 쓰지 못함 (Lv 5)

조회 결과를 `if` 블록 안에서 `World`로 선언했더니 블록 밖에서 참조할 수 없었습니다.
조건을 뒤집어 **가드 절**로 먼저 예외를 던지고, 정상 흐름의 변수는 메서드 스코프에 두도록 구조를 바꿨습니다.
중첩도 사라지고 스코프 문제도 함께 해결됐습니다.

### `orElseGet(null)`이 특정 상황에서만 NPE (Lv 7)

"없으면 `null`을 사용한다"를 `orElseGet(null)`로 작성했는데, 평소에는 멀쩡하다가 가끔 NPE가 났습니다.
`orElseGet`은 **값이 없을 때 호출할 `Supplier`**를 받기 때문에, 값이 있을 때는 `Supplier`를 쓰지 않아 아무 일도 없고
빈 `Optional`일 때만 `null.get()`이 되어 터진 것이었습니다.
값 자체를 대체하는 `orElse(null)`로 수정했습니다.

### 테스트 실패: `ws.nickname`이 `null` (Lv 7)

핸드셰이크에서 세션 속성에 닉네임을 저장할 때 조회한 `player.getNickname()`을 넣었더니 테스트가 깨졌습니다.
검증에 사용한 값과 저장해야 하는 값이 다르다는 점을 놓친 것으로, **URL에서 읽은 `nickname`을 그대로 저장**해야 했습니다.

### `ZRANGE` 결과가 계속 비어 있던 문제 (Lv 10)

접속 상태가 Redis에 들어가지 않는 것처럼 보였는데, 원인이 세 겹으로 겹쳐 있었습니다.

**(1) "데이터가 없는 것"과 "조회가 안 되는 것"을 구분하지 못함**
빈 결과만으로는 저장이 실패한 건지 조회 범위가 잘못된 건지 알 수 없었습니다.
`ZADD`로 **대조군 데이터를 직접 심어** 조회 명령 자체는 정상이라는 것을 먼저 확정하고 범위를 좁혔습니다.

**(2) 확인하던 키가 테스트가 쓰는 키가 아니었음**
`leave` 이후에도 데이터가 남아 보였는데, 보고 있던 `world:11`은 (1)에서 **직접 손으로 넣은 키**였습니다.
테스트는 `world:201`, `world:202`를 사용하고 있었습니다. 대조군을 정리하지 않은 것이 원인이었습니다.

**(3) 애초에 다른 Redis를 보고 있었음**
`201`, `202`도 비어 있었는데, 테스트는 **Testcontainers**로 띄운 Redis를 `getMappedPort()`로 접속하므로
로컬 `6379`로 접속한 클라이언트에서는 보일 수가 없었습니다.
테스트를 중단점에 멈춘 상태로 `docker exec`으로 해당 컨테이너에 직접 들어가 확인해 해결했습니다.

> 세 단계 모두 "관측 대상이 내가 생각한 그것이 맞는가"를 확인하지 않아 생긴 문제였습니다.

### TTL과 score 계산이 맞지 않음 (Lv 10)

만료 시각(score)과 키의 TTL을 비교했는데 값이 어긋났습니다.
계산이 틀린 게 아니라 **두 값을 잰 시각이 달랐던** 것으로, 같은 순간에 측정해야 비교가 성립했습니다.

### 채팅 발신자를 요청 본문에서 읽으려 함 (Lv 13)

처음에는 클라이언트가 보낸 메시지에서 `sender`를 읽으려 했습니다.
그러면 아무나 남의 닉네임으로 채팅을 보낼 수 있으므로(사칭 가능),
**서버가 핸드셰이크 때 확정한 `context.nickname()`**을 사용하도록 했습니다.
클라이언트가 보낸 값 중 신원에 해당하는 것은 신뢰하지 않는다는 원칙을 확인한 지점입니다.

### 접속자 목록이 정렬인지 접속 순서인지 구분되지 않음 (Lv 15)

응답이 `["alice", "bob"]`로 나왔지만, 정렬된 결과인지 단순히 접속한 순서인지 알 수 없었습니다.
**접속 순서를 뒤집어** 다시 테스트해 결과가 그대로 `["alice", "bob"]`인 것을 확인하고 정렬이 적용됐음을 검증했습니다.
