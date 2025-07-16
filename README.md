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

*   [📖 Гайд по отправке HTTP‑запроса через Feign и проверке ответа (пример: «Ставка»)](#гайд-по-отправке-httpзапроса-и-проверке-ответа)
    *   [1. Создать модель для тела запроса](#1-создать-модель-для-тела-запроса)
    *   [2. Создать модель для тела ответа](#2-создать-модель-для-тела-ответа)
    *   [3. Описать HTTP‑эндпоинт в Feign‑интерфейсе](#3-описать-httpэндпоинт-в-feignинтерфейсе)
    *   [4. Инжектировать `ManagerClient` в тест](#4-инжектировать-managerclient-в-тест)
    *   [5. Сформировать запрос](#5-сформировать-запрос)
    *   [6. Отправить запрос](#6-отправить-запрос)
    *   [7. Сделать ассерты](#7-сделать-ассерты)
    *   [8. Автоматические логи и аттачи для Allure ✨](#8-не-переживать-за-аттачи-)
    *   [9. Полный пример тестового класса (`BetApiTest.java`)](#9-полный-пример-тестового-класса-betapitestjava)
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

##  Гайд по отправке HTTP‑запроса и проверке ответа

---

### 1. Создать модель для тела запроса

Определите Java-класс, соответствующий структуре JSON тела вашего запроса. Используйте аннотации Lombok (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`) для сокращения бойлерплейт-кода.

```java
package com.uplatform.example.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

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
```

### 2. Создать модель для тела ответа
Аналогично создайте Java-класс для ожидаемого JSON тела ответа.

```java
package com.uplatform.example.models; 

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GamblingResponseBody {
    private BigDecimal balance;
    private String     transactionId;
}
```

### 3. Описать HTTP‑эндпоинт в Feign‑интерфейсе
Используйте Feign для декларативного описания вашего HTTP API клиента.
Укажите name клиента (для конфигурации Spring).
Задайте базовый URL (url) через property ${app.api.manager.base-url}.

```java
package com.uplatform.example.clients; 

import com.yourcompany.models.BetRequestBody;
import com.yourcompany.models.GamblingResponseBody;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "managerClient",
        url  = "${app.api.manager.base-url}"
)
public interface ManagerClient {

    @PostMapping("/_core_gas_processing/bet")
    ResponseEntity<GamblingResponseBody> bet(
            @RequestHeader("X-Casino-Id") String casinoId,
            @RequestHeader("Signature")   String signature,
            @RequestBody                  BetRequestBody request
    );
}
```

### 4. Инжектировать ManagerClient в тест
В вашем Spring Boot тесте используйте @Autowired для внедрения созданного Feign-клиента. См. полный пример теста ниже (шаг 9).

### 5. Сформировать запрос
Создайте экземпляр вашей модели запроса (BetRequestBody) с необходимыми данными. Если требуется подпись запроса (как Signature в примере), сгенерируйте ее с помощью соответствующей утилиты (здесь httpSignatureUtil). См. полный пример теста ниже (шаг 9).

### 6. Отправить запрос
Вызовите метод вашего Feign-клиента (managerClient.bet(...)), передав все необходимые параметры: заголовки (X-Casino-Id, Signature) и тело запроса (bet). См. полный пример теста ниже (шаг 9).

### 7. Сделать ассерты
Используйте JUnit 5 assertAll и другие ассерты для проверки ответа: статус-кода, наличия тела ответа и корректности данных в теле ответа. См. полный пример теста ниже (шаг 9).

### 8. Не переживать за аттачи ✨
AllureFeignLoggerConfig берет на себя всю работу по логированию и аттачам для Allure-отчета. Вам не нужно писать дополнительный код для этого.

![Пример аттачей Http Request/Response в Allure отчете](src/test/resources/%D0%A1%D0%BA%D1%80%D0%B8%D0%BD%D1%88%D0%BE%D1%82%2021-04-2025%20132058.jpg)


### 9. Полный пример тестового класса (BetApiTest.java)
Вот как может выглядеть полный тестовый класс, объединяющий все шаги:

```java
com.uplatform.example.test; 

import com.yourcompany.clients.ManagerClient;
import com.yourcompany.models.BetRequestBody;
import com.yourcompany.models.GamblingResponseBody;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest // Загружает контекст Spring Boot для теста
class BetApiTest {

    // --- Шаг 4: Инжектим наш Feign клиент   ---
    @Autowired
    private ManagerClient managerClient;                                         

    @Test
    void shouldSuccessfullyPlaceBetAndReturnBalance() {
       step("Manager API: Совершение ставки", () -> {
            // --- Шаг 5: Сформировать запрос ---
            String transactionId = UUID.randomUUID().toString();
            String roundId = UUID.randomUUID().toString();
            String sessionToken = "95aa0509-21f3-4985-9950-92a2da95244d"; // Игровую сессию мы "выдумали" заранее, чтоб не отягощать пример
            String casinoId = "demo-casino";
    
            BetRequestBody betRequest = BetRequestBody.builder()
                    .sessionToken(sessionToken)
                    .amount(new BigDecimal("10.15"))
                    .transactionId(transactionId)
                    .type("bet")
                    .roundId(roundId)
                    .roundClosed(false)
                    .build();
    
    
            // --- Шаг 6: Отправить запрос ---
            ResponseEntity<GamblingResponseBody> response = managerClient.bet(
                    casinoId,   // Значение для X-Casino-Id
                    signature,  // Сгенерированная подпись
                    betRequest  // Тело запроса
            );
        
            // --- Шаг 7: Сделать ассерты ---
            assertAll("Проверка ответа на запрос Bet",
                    () -> assertEquals(HttpStatus.OK, response.getStatusCode(), "Ожидался статус ответа OK (200)"),
                    () -> assertEquals(transactionId, responseBody.getTransactionId(), "TransactionId в ответе должен совпадать с отправленным"),
                    () ->  assertNotNull(responseBody.getBalance(), "Поле balance не должно быть null")
            );
        });
    }
}
```

---

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
