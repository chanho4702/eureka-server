# eureka-server

MSA_TEMPLATE의 **서비스 레지스트리(Service Registry)**.
"어떤 서비스가 지금 어디(IP:포트)에 살아 있는가"의 단일 장부를 유지하고, 게이트웨이의 `lb://` 라우팅이 이 장부를 바라본다.

> 별도 git repo: `github.com/chanho4702/eureka-server`. 우산 repo(MSA_TEMPLATE)에서는 gitignore 됨.

---

## 왜 필요한가 — 서비스 디스커버리가 푸는 문제

게이트웨이(또는 서비스 간 호출)는 요청을 넘기려면 대상의 실제 주소를 알아야 한다. 방식은 두 가지다.

| 방식 | 동작 | 한계 / 장점 |
|---|---|---|
| **정적 설정** (도입 전) | `http://localhost:9100` 같은 URI를 환경변수에 박음 | 주소·포트가 바뀌거나 인스턴스가 늘 때마다 설정 수정 + 재기동. 다중 인스턴스 분산 불가 |
| **동적 디스커버리** (현재) | 서비스가 기동하며 자기 등록, 호출자는 **이름**으로 조회 | 주소·포트·대수가 바뀌어도 호출자 설정 불변. 클라이언트 사이드 로드밸런싱 |

실제로 이 템플릿의 E2E 검증 중 board-service 포트가 :9100 → :9700 → :9710으로 두 번 바뀌었지만(Windows 포트 예약 문제), **게이트웨이 설정은 한 글자도 바뀌지 않았다** — 이것이 디스커버리의 가치다.

## 동작 원리 — 3가지 축

```
  auth-server(:9000) ──자기등록+하트비트(30s)──▶ ┌────────────────────┐
  board-service      ──자기등록+하트비트(30s)──▶ │ eureka-server:8761 │
                                                │  (인스턴스 장부)     │
  gateway-server ◀──레지스트리 조회+로컬캐시──── └────────────────────┘
      │
      └─▶ lb://board-service → 장부에서 인스턴스 목록 → 라운드로빈 선택 → 프록시
```

1. **자기 등록(self-registration)** — 각 서비스가 기동하면서 `spring.application.name`을 ID로 등록하고, 이후 **하트비트**(기본 30초)로 생존 신고. 하트비트가 끊기면 레지스트리에서 **퇴출(eviction)** 된다.
2. **클라이언트 조회 + 로컬 캐시** — 게이트웨이는 장부 스냅샷을 받아 **로컬에 캐시**하고 30초마다 갱신. 그래서 유레카 서버가 잠깐 죽어도 마지막 스냅샷으로 라우팅이 계속된다 — **유레카는 SPOF가 아니다** (유레카 장애 = "변경 반영이 멈춤"이지 "라우팅 중단"이 아님. 반대급부로 반영 지연도 최대 수십 초 존재).
3. **클라이언트 사이드 로드밸런싱** — `lb://서비스명`을 본 Spring Cloud LoadBalancer가 인스턴스 목록에서 하나를 골라(기본 라운드로빈) 보낸다. 분산 결정을 호출자가 하므로 중간에 nginx/L4 없이 스케일아웃된다.

## CircuitBreaker와의 역할 분담 (상호보완)

| 계층 | 역할 | 판단 근거 |
|---|---|---|
| **유레카** | 죽은 인스턴스를 **목록에서 제거** | 하트비트 (프로세스 생존) |
| **CircuitBreaker** (게이트웨이) | 목록엔 있지만 **응답이 이상한** 인스턴스 차단 | 실제 응답 실패율 |

서비스가 아직 등록 안 된 시점의 요청은 인스턴스 미발견 503 → board 라우트는 게이트웨이의 `/fallback/board`가 받는다. **기동 순서 강제 없음** — 등록되는 대로 라우팅이 살아난다.

## 설정 — standalone 단일 노드

`application.yml` 전문이 사실상 이게 전부다:

```yaml
server:
  port: 8761
eureka:
  client:
    register-with-eureka: false   # 자기 자신을 등록할 레지스트리가 없고
    fetch-registry: false         # 조회할 peer도 없다 (standalone)
```

- 유레카 서버도 내부적으로 유레카 **클라이언트**를 내장하는데(HA에서 peer끼리 복제용), 단일 노드에서는 등록/조회 대상이 없으므로 둘 다 꺼야 기동 시 자기 자신 연결 실패 로그가 안 남는다.
- **HA(peer 복제)는 YAGNI로 미구현** — 로컬 캐시 덕에 단일 노드로도 개발·데모에 충분. 운영 전환 시점에 재검토.

## 기술 스택

Spring Boot 4.0.6 · Java 24 · Spring Cloud BOM **2025.1.2** · Gradle 9.5.1

의존성은 `spring-cloud-starter-netflix-eureka-server` 하나 (대시보드 UI + spring-boot-starter-web 전이 포함).

## 빠른 시작

```powershell
# Windows PowerShell — JDK 24 필요(기본 JDK가 11이면 실패)
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
.\gradlew.bat bootRun        # :8761 기동
```

- 대시보드: `http://localhost:8761` — 등록된 인스턴스(이름·IP:포트·상태)를 눈으로 확인
- 레지스트리 API: `curl -H "Accept: application/json" http://localhost:8761/eureka/apps`
- 정상 상태라면 GATEWAY-SERVER · AUTH-SERVER · BOARD-SERVICE 3개가 UP으로 보인다

> 포트 맵: eureka 8761 / gateway 8000 / auth 9000 / board 9100 / Keycloak 8080 / Postgres 5433.

## 클라이언트 쪽 계약 (등록하는 서비스들이 지킬 것)

각 서비스의 `application.yml`:

```yaml
spring:
  application:
    name: board-service        # 유레카 등록 ID = 게이트웨이 lb://board-service — 반드시 일치
eureka:
  client:
    service-url:
      defaultZone: ${EUREKA_URI:http://localhost:8761/eureka}
  instance:
    prefer-ip-address: true    # 필수 — 아래 트러블슈팅 참고
```

- **새 서비스 추가 절차**: eureka-client 의존성 + 위 설정 + 게이트웨이에 `lb://서비스명` 라우트 한 줄. 끝.
- **단위 테스트는 유레카 없이** 돌아야 한다 — 테스트 설정에 `eureka.client.enabled: false` (이 레포 포함 4개 프로젝트 전부 그렇게 되어 있음).
- 게이트웨이는 `AUTH_SERVER_URI`/`BOARD_SERVICE_URI` env로 직접 URI를 주입하면 유레카 없이도 동작한다(탈출구).

## 트러블슈팅 (E2E 실측)

- **게이트웨이가 `500 UnknownHostException ... mshome.net`** → 서비스가 DNS 해석 불가능한 호스트명(Windows/Hyper-V의 `DESKTOP-xxx.mshome.net` 등)으로 등록된 것. 등록하는 서비스에 `eureka.instance.prefer-ip-address: true` 확인. **유레카 도입 시 필수 체크리스트** — 컨테이너/VM/Windows 어디서든 호스트명 해석은 신뢰 불가.
- **등록했는데 게이트웨이가 아직 503** → 전파 지연(등록 + 게이트웨이 캐시 갱신 합쳐 최대 수십 초)이 정상. 대시보드에서 UP 확인 후 잠시 대기.
- **`Gradle requires JVM 17 or later`** → `JAVA_HOME`을 JDK 24로.
- **인스턴스를 내렸는데 대시보드에 남아있음** → 하트비트 퇴출까지 리스 시간(기본 90초) 대기. 강제 종료(kill)면 graceful 등록해제가 안 돼 더 오래 보일 수 있다.

## K8s로 가면 유레카는 중복이다

Kubernetes는 Service DNS가 디스커버리+로드밸런싱을 자체 제공한다 (`http://board-service`로 끝). 유레카가 진가를 발휘하는 건 **VM·온프레미스·Docker Compose처럼 인프라가 디스커버리를 안 주는 환경**이다. K8s 전환 시 이 서버는 제거하고 라우트를 Service DNS로 바꾸면 된다.

## 테스트

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-24'
.\gradlew.bat test
```

| 테스트 | 검증 내용 |
|---|---|
| `EurekaServerApplicationTest` | standalone 컨텍스트 기동 + 대시보드(/) 200 — 레지스트리로서의 최소 계약 |

설계 문서: 우산 repo `docs/superpowers/specs/2026-07-05-eureka-service-discovery-design.md` (Obsidian 볼트 미러)
