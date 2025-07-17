# 📖 README
# Наш подход к автоматизации тестирования

Наш **фреймворк для автоматизации тестирования** построен на **Spring Boot**. Использование этого стека дает нам ключевые преимущества при разработке самих тестов:

*   **Мощное внедрение зависимостей (DI)** позволяет легко управлять и инжектировать все необходимые компоненты тестов: различные API-клиенты (HTTP, NATS и др.), клиенты баз данных, хелперы для очередей сообщений (Kafka), утилиты и сервисы. Это делает тестовый код более **модульным, переиспользуемым и читаемым**.
*   Kafka-хелперы представлены отдельными клиентами (`PlayerAccountKafkaClient`, `WalletProjectionKafkaClient`, `GameSessionKafkaClient`, `LimitKafkaClient`), которые можно инжектировать напрямую в тесты без общего фасада.
*   **Автоконфигурация** и поддержка `@SpringBootTest` упрощают **настройку тестового окружения** и запуск комплексных интеграционных тестов, автоматически подтягивая нужные конфигурации и бины в контекст теста.
*   Возможность **централизованного управления конфигурацией** (через `application.properties/.yml` и аннотацию `@Value`) делает тесты гибкими и легко адаптируемыми к разным окружениям.

В результате, Spring Boot помогает нам строить **масштабируемую, легко поддерживаемую и структурированную тестовую базу**, способную эффективно проверять сложные системы с множеством точек интеграции.

---

## Оглавление

*   [🌐 Работа с HTTP](#работа-с-http)
    *   [1. Как устроена работа с HTTP](#1-как-устроена-работа-с-http)
    *   [2. Как настроить подключение](#2-как-настроить-подключение)
    *   [3. Где прописать базовый-url](#3-где-прописать-базовый-url)
    *   [4. Как описать эндпоинт](#4-как-описать-эндпоинт)
    *   [5. Пример DTO](#5-пример-dto)
    *   [6. Пример клиента](#6-пример-клиента)
    *   [7. Использование в тестах](#7-использование-в-тестах)
*   [⚙️ Работа с Kafka](#работа-с-kafka)
    *   [1. Как устроена работа с Kafka](#1-как-устроена-работа-с-kafka)
    *   [2. Как настроить подключение](#2-как-настроить-подключение)
    *   [3. Где прописать адрес брокера](#3-где-прописать-адрес-брокера)
    *   [4. Как работает абстрактный класс](#4-как-работает-абстрактный-класс)
    *   [5. Подключение нового топика](#5-подключение-нового-топика)
    *   [6. Пример клиента для нового топика](#6-пример-клиента-для-нового-топика)
*   [7. Использование в тестах](#7-использование-в-тестах)
*   [⚡ Работа с Redis](#работа-с-redis)
    *   [1. Как устроена работа с Redis](#1-как-устроена-работа-с-redis)
    *   [2. Как настроить подключение](#2-как-настроить-подключение-1)
    *   [3. Где прописать адрес сервера](#3-где-прописать-адрес-сервера)
    *   [4. Как работает абстрактный класс](#4-как-работает-абстрактный-класс-1)
    *   [5. Подключение нового инстанса](#5-подключение-нового-инстанса)
    *   [6. Пример клиента для нового инстанса](#6-пример-клиента-для-нового-инстанса)
    *   [7. Использование в тестах](#7-использование-в-тестах-1)

*(По мере добавления новых секций, не забудьте обновить оглавление)*

---


## Работа с HTTP

### 1. Как устроена работа с HTTP

В основе лежат Feign-клиенты, описанные интерфейсами с аннотациями Spring MVC.
Все такие интерфейсы собираются в бины через автоконфигурацию и доступны в
контексте тестов. За логирование запросов и ответов отвечает конфигурация
`AllureFeignLoggerConfig`, поэтому каждая HTTP‑операция автоматически
добавляется в отчёт Allure вместе с телом и заголовками.

### 2. Как настроить подключение

Перед запуском тестов укажите системное свойство `-Denv=<имя_окружения>`.
Нужный файл конфигурации находится в каталоге `configs`. В разделе `api`
описываются `baseUrl`, ключи и другие параметры доступа. Класс
`DynamicPropertiesConfigurator` читает эти значения и передаёт их в свойства
Spring Boot.

### 3. Где прописать базовый-url

В файле окружения присутствует параметр `api.baseUrl`:

```json
"api": {
  "baseUrl": "https://manager.test.host"
}
```

### 4. Как описать эндпоинт

Создайте интерфейс и пометьте его `@FeignClient`. В параметре `url` используйте
переменную `${app.api.manager.base-url}` так, чтобы один клиент можно было
подключать к разным окружениям. Методы описывайте стандартными аннотациями
Spring MVC (`@GetMapping`, `@PostMapping` и др.).

```java
@FeignClient(name = "managerClient", url = "${app.api.manager.base-url}")
public interface ManagerClient {
    @PostMapping("/_core_gas_processing/bet")
    ResponseEntity<GamblingResponseBody> bet(
            @RequestHeader("X-Casino-Id") String casinoId,
            @RequestHeader("Signature") String signature,
            @RequestBody BetRequestBody request
    );
}
```

### 5. Пример DTO
Классы запросов и ответов представляют собой обычные POJO, аннотированные
Lombok для лаконичности.

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BetRequestBody {
    private String     sessionToken;
    private BigDecimal amount;
    private String     transactionId;
    private String     type;
    private String     roundId;
    private Boolean    roundClosed;
}

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GamblingResponseBody {
    private BigDecimal balance;
    private String     transactionId;
}
```

### 6. Пример клиента
Инжектируйте клиент в тестовый класс через `@Autowired`.

```java
@Autowired
private ManagerClient managerClient;
```

### 7. Использование в тестах
Оформите запрос к API прямо в шаге `Allure.step` и сделайте проверки:

```java
@Autowired
private ManagerClient managerClient;

step("HTTP: отправка запроса Bet", () -> {
    BetRequestBody request = BetRequestBody.builder()
            .sessionToken(sessionToken)
            .amount(new BigDecimal("10.15"))
            .transactionId(transactionId)
            .type("bet")
            .roundId(roundId)
            .roundClosed(false)
            .build();

    ResponseEntity<GamblingResponseBody> response = managerClient.bet(
            casinoId,
            signature,
            request
    );

    assertAll(
            () -> assertEquals(HttpStatus.OK, response.getStatusCode()),
            () -> assertEquals(transactionId, response.getBody().getTransactionId())
    );
});
```
## Работа с Kafka

### 1. Как устроена работа с Kafka

В тестовом фреймворке сообщения Kafka читаются фоновым сервисом. 
Класс `KafkaBackgroundConsumer` запускает `KafkaPollingService`. 
`KafkaPollingService` создаёт `KafkaMessageListenerContainer` для каждого указанного в конфигурации топика и подписывается на него. 
Полученные записи помещаются в буфер `MessageBuffer`, представляющий собой кольцевую очередь. 
Поиск по буферу и десериализацию выполняет `MessageFinder`, возвращая тесту DTO нужного типа.

### 2. Как настроить подключение

При запуске тестов укажите системное свойство `-Denv=<имя_окружения>`. 
В каталоге `configs` хранятся json-файлы с настройками для разных окружений. 
В разделе `kafka` задаются `bootstrapServer`, `groupId`, список топиков и дополнительные параметры (тайм-ауты, размер пула и т. п.).

### 3. Где прописать адрес брокера

Адреса брокеров и набор прослушиваемых топиков задаются в том же файле конфигурации. Значения читаются классом `DynamicPropertiesConfigurator` и передаются в `KafkaConsumerConfig`. Ниже приведён фрагмент из `beta-09.json`:

```json
"kafka": {
  "bootstrapServer": "kafka-development-01.b2bdev.pro:9092,kafka-development-02.b2bdev.pro:9092,kafka-development-03.b2bdev.pro:9092",
  "groupId": "cb-wallet-test-consumer-beta-09",
  "listenTopicSuffixes": [
    "player.v1.account",
    "wallet.v8.projectionSource",
    "core.gambling.v1.GameSessionStart",
    "limits.v2"
    "bonus.v1.award" // новый топик
  ]
}
```

### 4. Как работает абстрактный класс

Базовый клиент `AbstractKafkaClient` инкапсулирует логику поиска сообщений в `MessageBuffer`. 
Конкретные клиенты наследуют его и указывают тип возвращаемого DTO.

Основные методы:
- `expectMessage(filter, messageClass)` — ждёт первое сообщение, удовлетворяющее фильтру (например, по ключу `sequence`).
- `expectUniqueMessage(filter, messageClass)` — аналогично, но дополнительно убеждается, что найдено единственное сообщение.
Также класс содержит стандартный тайм-аут ожидания и методы для проверки отсутствия сообщений.

### 5. Подключение нового топика

1. Посмотрите пример сообщения, приходящего в топик, и по его структуре создайте DTO в пакете `api/kafka/dto`.
2. Затем напишите клиент в `api/kafka/client`, наследующий `AbstractKafkaClient`(Пример ниже)
3. Зарегистрируйте соответствие между DTO и суффиксом топика в `KafkaConsumerConfig`.
Ниже приведён фрагмент метода `kafkaTopicMappingRegistry` со всеми маппингами и новым топиком:
```java
@Bean
public KafkaTopicMappingRegistry kafkaTopicMappingRegistry() {
    Map<Class<?>, String> mappings = new HashMap<>();

    // существующие топики
    mappings.put(PlayerAccountMessage.class, "player.v1.account");
    mappings.put(WalletProjectionMessage.class, "wallet.v8.projectionSource");
    mappings.put(GameSessionStartMessage.class, "core.gambling.v1.GameSessionStart");
    mappings.put(LimitMessage.class, "limits.v2");

    // добавляем новый топик
    mappings.put(BonusAwardMessage.class, "bonus.v1.award");

    return new SimpleKafkaTopicMappingRegistry(mappings);
}
```
4. Укажите этот суффикс в списке `listenTopicSuffixes` конфигурационного файла.
   Ниже пример фрагмента json после добавления нового топика:

   ```json
   "listenTopicSuffixes": [
     "player.v1.account",
     "wallet.v8.projectionSource",
     "core.gambling.v1.GameSessionStart",
     "limits.v2",
     "bonus.v1.award" // новый топик
   ]
   ```
   После перезапуска тестов `MessageBuffer` начнёт слушать этот топик.

### 6. Пример клиента для нового топика


```java
package com.uplatform.wallet_tests.api.kafka.client;

import com.uplatform.wallet_tests.api.kafka.consumer.KafkaBackgroundConsumer;
import com.uplatform.wallet_tests.api.kafka.dto.BonusAwardMessage;
import com.uplatform.wallet_tests.config.EnvironmentConfigurationProvider;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class BonusAwardKafkaClient extends AbstractKafkaClient {

    public BonusAwardKafkaClient(
            KafkaBackgroundConsumer kafkaBackgroundConsumer,
            EnvironmentConfigurationProvider configProvider
    ) {
        super(kafkaBackgroundConsumer, configProvider);
    }

    public BonusAwardMessage expectBonusAward(String playerId) {
        return expectMessage(
                Map.of("playerId", playerId),
                BonusAwardMessage.class
        );
    }
}
```

Такой класс помещается в пакет `api/kafka/client` и автоматически становится доступным в контексте Spring.

### 7. Использование в тестах

Инжектируйте нужный клиент в тестовый класс и ожидайте событие внутри `Allure.step`:

```java
@Autowired
private WalletProjectionKafkaClient walletProjectionKafkaClient;

step("Kafka: получение сообщения", () -> {
    var message = walletProjectionKafkaClient.expectWalletProjectionMessageBySeqNum(
            testData.someEvent.getSequence());
    assertTrue(utils.areEquivalent(message, testData.someEvent));
});
```

## Работа с Redis

### 1. Как устроена работа с Redis

Базовый класс `AbstractRedisClient` инкапсулирует логику общения с Redis:
он проверяет соединение при старте, предоставляет методы `getWithRetry` и
`getWithCheck` для извлечения данных с повторами через `RedisRetryHelper` и
автоматически формирует аттачи в Allure при каждом обращении. Конкретные
клиенты, например `PlayerRedisClient` и `WalletRedisClient`, лишь расширяют
его и реализуют методы для своих ключей.

### 2. Как настроить подключение

Запускайте тесты с системным свойством `-Denv=<имя_окружения>`. В каталоге
`configs` хранится конфигурация, где раздел `redis` содержит параметры повторов
и список инстансов. Пример из `beta-09.json`:

```json
"redis": {
    "aggregate": {
        "maxGamblingCount": 50,
        "maxIframeCount": 500,
        "retryAttempts": 10,
        "retryDelayMs": 200
    },
    "instances": {
        "player": {
          "host": "redis-01.b2bdev.pro",
          "port": 6389,
          "database": 9,
          "timeout": "5000ms",
          "lettucePool": {
            "maxActive": 8,
            "maxIdle": 8,
            "minIdle": 0,
            "shutdownTimeout": "100ms"
          }
        },
        "wallet": {
          "host": "redis-01.b2bdev.pro",
          "port": 6390,
          "database": 9,
          "timeout": "5000ms",
          "lettucePool": {
            "maxActive": 8,
            "maxIdle": 8,
            "minIdle": 0,
            "shutdownTimeout": "100ms"
          }
        }
    }
}
```

### 3. Где прописать адрес сервера

Все данные подключения располагаются в этом же конфигурационном файле в разделе
`redis.instances`. Класс `DynamicPropertiesConfigurator` считывает значения и
передает их в `RedisConfig`, который создает необходимые `RedisTemplate`.

### 4. Как работает абстрактный класс

`AbstractRedisClient` инкапсулирует логику получения значения из Redis с
повторами. Он использует `RedisRetryHelper` для ожидания появления ключа и
формирует аттачи в Allure при каждой попытке. Детали каждой попытки
прикладываются автоматически, отдельного флага управления не требуется.

### 5. Подключение нового инстанса

1. Добавьте параметры нового инстанса в `redis.instances` конфигурационного
   файла.
2. В `RedisConfig` опишите бины `RedisProperties`, `LettuceConnectionFactory` и
   `RedisTemplate` по образцу существующих.
3. Создайте клиент, расширяющий `AbstractRedisClient`.

Пример фрагмента `RedisConfig` для инстанса `bonus`:

```java
@Bean("bonusRedisProperties")
@ConfigurationProperties(prefix = "spring.data.redis.bonus")
public RedisProperties bonusRedisProperties() {
    return new RedisProperties();
}

@Bean("bonusRedisConnectionFactory")
public LettuceConnectionFactory bonusRedisConnectionFactory(
        @Qualifier("bonusRedisProperties") RedisProperties properties) {
    return createConnectionFactory(properties, bonusLettucePoolingConfig(properties));
}

@Bean("bonusRedisTemplate")
public RedisTemplate<String, String> bonusRedisTemplate(
        @Qualifier("bonusRedisConnectionFactory") RedisConnectionFactory connectionFactory) {
    return createStringRedisTemplate(connectionFactory);
}
```

### 6. Пример клиента для нового инстанса

```java
package com.uplatform.wallet_tests.api.redis.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import com.fasterxml.jackson.core.type.TypeReference;
import org.springframework.stereotype.Component;

@Component
public class BonusRedisClient extends AbstractRedisClient {

    public BonusRedisClient(
            @Qualifier("bonusRedisTemplate") RedisTemplate<String, String> template,
            RedisRetryHelper retryHelper,
            AllureAttachmentService attachmentService
    ) {
        super("BONUS", template, retryHelper, attachmentService);
    }

    public BonusAggregate getBonus(String key) {
        return getWithRetry(key, new TypeReference<BonusAggregate>() {});
    }
}
```

### 7. Использование в тестах

Инжектируйте нужный клиент и получайте данные в шаге `Allure.step`:

```java
@Autowired
private BonusRedisClient bonusRedisClient;

step("Redis: получение бонуса", () -> {
    var aggregate = bonusRedisClient.getBonus(testData.bonusKey);
    assertNotNull(aggregate, "redis.bonus.not_null");
});
```
