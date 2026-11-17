# Java Guidelines

## Formatting (Spotless + google-java-format)

- Java formatting is enforced by Spotless (`spotless-maven-plugin`, google-java-format GOOGLE style, 2-space indent); `spotless:check` is bound to the `validate` phase, so an unformatted tree fails every build
- After editing any `.java` file, run `mvn spotless:apply` (in `backend/`) before running tests or finishing; never hand-format against the formatter
- Never disable, skip or weaken the check (`-Dspotless.check.skip`, removing the execution) to get a build through; fix the formatting instead

## Imports

- No wildcard imports (`import x.y.*;`, `import static x.Y.*;`); import only the classes and members actually used, one per line
- Applies to every `.java` file, main and test, and to every new or edited file; when touching a file that still has a wildcard import, replace it
- IDE "optimize imports" must not collapse imports into `*` (set the class-count threshold high)

## Core Principles

- Minimum Java 17; prefer newer language features (record, sealed class, pattern matching, text block)
- No bare `null`; wrap return values with `Optional<T>`, and never pass null as an argument
- Prefer immutability: use `final` for fields, `List.of()`, `Map.of()`, `Collections.unmodifiable*` for collections
- No magic numbers or magic strings; extract them into constants

## Naming

- Classes and interfaces: `PascalCase` (`UserService`, `Configurable`)
- Methods and variables: `camelCase` (`getUserById`, `isValid`)
- Constants: `UPPER_SNAKE_CASE` (`MAX_RETRY_COUNT`)
- Package names: all lowercase (`com.example.userservice`)
- Booleans: `is`/`has`/`can`/`should` prefix
- No `I` prefix for interfaces; implementation classes use an `Impl` suffix or a descriptive name (`JdbcUserRepository`)

## Modern Java Features (17+)

```java
public class UserDto {
    private String name;
    private int age;
    // getter, setter, equals, hashCode, toString...
}

public record UserDto(String name, int age) {}

if (shape instanceof Circle) {
    Circle c = (Circle) shape;
    return c.radius();
}

// 正确：pattern matching
if (shape instanceof Circle c) {
    return c.radius();
}

public sealed interface Shape permits Circle, Rectangle, Triangle {}
public record Circle(double radius) implements Shape {}
public record Rectangle(double width, double height) implements Shape {}
```

## Class Design

- A single class should not exceed 300 lines
- A single method should not exceed 30 lines
- No more than 4 method parameters; use a parameter object when exceeding this limit
- Composition over inheritance; inheritance depth should not exceed 3 levels
- Utility classes should be `final class` with a private constructor; do not use `abstract class`

## Optional

```java
User user = userRepository.findById(id);
if (user != null) { ... }


userRepository.findById(id)
    .map(User::getName)
    .orElseThrow(() -> new UserNotFoundException(id));

void process(Optional<String> name) { ... }


void process(String name) { ... }
void process() { process("default"); }
```

## Collections and Streams

- Create immutable collections: `List.of()`, `Set.of()`, `Map.of()`
- Stream chains should not exceed 5 steps; extract intermediate variables or methods when they do
- Do not force Streams for simple loops; use `for-each` when it is clearer
- No side effects inside Streams (do not modify external state)

```java
List<String> results = new ArrayList<>();
users.stream().forEach(u -> results.add(u.getName()));

List<String> results = users.stream()
    .map(User::getName)
    .toList();
```

## Exception Handling

- Catch specific exceptions; no `catch (Exception e)`
- Business exceptions should extend a custom base class and carry an error code
- No swallowing exceptions (empty catch blocks)
- Use `try-with-resources` to manage resources

```java
try {
    readFile(path);
} catch (Exception e) {

}


try (var reader = Files.newBufferedReader(path)) {
    return reader.lines().toList();
} catch (NoSuchFileException e) {
    throw new ConfigNotFoundException("配置文件不存在: " + path, e);
}
```

## Concurrency

- Prefer `ExecutorService` / `CompletableFuture`; do not use bare `new Thread()`
- On Java 21+, prefer virtual threads (`Thread.ofVirtual()`)
- Use thread-safe classes for shared mutable state: `ConcurrentHashMap`, `AtomicReference`, etc.
- Do not `synchronized` an entire method; minimize the synchronized scope


## Spring boot
Opinionated rules plus a router to deeper references. Code samples in the references were compiled and run on Spring Boot 4.1.1 (Spring Framework 7) and, where they differ, on Boot 3.5.16 (Framework 6.2). Boot 3 vs 4 differences are marked "Boot 3" / "Boot 4" in each reference.

## Before writing code

1. **Detect, do not assume:** read `pom.xml`/`build.gradle(.kts)` for the Spring Boot version, Java version, starters in use (`spring-boot-starter-web` is Boot 3, `spring-boot-starter-webmvc` is Boot 4), persistence (JPA, JDBC), the migration tool, the test setup, and the package layout and naming conventions of the existing code. Match them.
2. **Never upgrade Spring Boot, Java or dependencies implicitly.** A Boot 3 -> 4 move is a deliberate task (`spring-boot-4-migration`).
3. **Ask before adding infrastructure:** a broker (Kafka, RabbitMQ), a cache provider, a new database, or a scheduler/async setup. Add only what the user requested, with the Boot-managed version (no explicit version for BOM-managed artifacts, never `latest`).
4. **Verify every change** with the project's own build: `./mvnw verify` or `./gradlew build` (plain `mvn`/`gradle` if no wrapper). Integration tests need Docker when they use Testcontainers.

## Router

| Task | Read |
| --- | --- |
| Repositories, projections, pagination, N+1, `@Transactional`, auditing, Flyway/Liquibase, Spring Data JDBC | `references/data.md` |
| Controllers, validation, `ProblemDetail`, `RestClient`, `@HttpExchange`, API versioning, OpenAPI, JSpecify | `references/rest-api.md` |
| Kafka/AMQP, `@Cacheable`/Caffeine, `@Scheduled`, `@Async`, virtual threads, outbox | `references/messaging-caching.md` |
| `@ConfigurationProperties`, profiles, secrets, config import, Docker Compose / Testcontainers at dev time | `references/configuration.md` |
| AOT, CDS/AppCDS, lazy initialization, native image | `references/aot-and-startup.md` |

If these standalone skills are installed, also use them for their topic; otherwise rely on the official docs: `java-hibernate-jpa` (JPA mapping), `spring-boot-testing` (tests), `spring-security` (authentication, authorization, CSRF/CORS), `spring-observability` (metrics, tracing, Actuator, logs), `create-spring-boot-java-project` (project defaults, Boot 3 vs 4 build changes), `java-add-graalvm-native-image-support` (native executable).

## Core rules

- **Structure and layers:** package by feature; constructor injection; controllers return DTOs/records, never entities; services own the transaction.
- **Validation and errors:** Bean Validation on request records with `@Valid`; one `@RestControllerAdvice` returning `ProblemDetail`. JSpecify annotations document nullability only and do not validate.
- **Jackson:** Boot 4 uses Jackson 3 (`tools.jackson.databind.*`, `JsonMapper`; annotations stay in `com.fasterxml.jackson.annotation`); Boot 3 uses Jackson 2 (`com.fasterxml.jackson.databind.*`, `ObjectMapper`).
- **Slice-test gotcha:** put `@EnableJpaAuditing`, `@EnableCaching`, `@EnableAsync`, `@EnableScheduling` on dedicated `@Configuration` classes, not on the `@SpringBootApplication` class, or slices such as `@DataJpaTest` fail to start (for example, no `CacheManager`). Slices do not scan `@Configuration` classes: `@Import` the one a test needs.
- **Proxied beans:** `@Cacheable`, `@Async` and `@Transactional` work through proxies: they do nothing on self-invocation or on `private`/`final` methods; test them through the injected bean.
