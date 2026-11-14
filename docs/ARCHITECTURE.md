# Book Store — Mimari

Bu doküman sistemin **nasıl** kurulduğunu tanımlar. **Ne** yaptığı [SPEC.md](SPEC.md) içinde, kararların gerekçeleri [DECISIONS.md](DECISIONS.md) içindedir.

## 1. Genel bakış

Book Store, tek bir süreç olarak çalışan (**monolit**), Maven ile **çok modüllü** ve **katmanlı** bir web uygulamasıdır.

Temel fikir: iş kuralları **saf Java** ile yazılır ve hiçbir framework'e bağlı değildir. Veritabanı, dosya sistemi ve web gibi dış dünya ayrıntıları, iş kurallarının tanımladığı arayüzleri (*port*) uygulayan ayrı modüllerdedir (*adapter*). Spring Boot yalnızca en dış modülde yer alır ve parçaları birbirine bağlar.

Bu yapı iki işe yarar:

- Müfredatın temel konuları (OOP, koleksiyonlar, generics, JDBC, thread, I/O) framework'ün arkasına saklanmadan, elle yazılmış kodda görünür.
- Modern framework kullanımı (Spring MVC, Security, Thymeleaf) aynı projede, doğru yerde durur.

| Konu | Seçim |
|---|---|
| Dil | Java 21 (LTS) |
| Derleme | Maven 3.9, Maven Wrapper (`mvnw`) |
| Web framework | Spring Boot 4.1 (Spring MVC, Spring Security) |
| Arayüz | Thymeleaf (sunucu tarafı HTML) + JSON REST API |
| Veritabanı | MySQL 8.4; testlerde H2 (MySQL uyumluluk modu) |
| Şema yönetimi | Flyway |
| Veri erişimi | Elle yazılmış JDBC (DAO kalıbı) |
| Test | JUnit 5, AssertJ, Mockito |
| Kayıt | SLF4J + Logback |

## 2. Modüller

```mermaid
graph TD
    web["<b>bookstore-web</b><br/>Spring Boot, MVC, REST, Security, Thymeleaf"]
    persistence["<b>bookstore-persistence</b><br/>JDBC, Flyway, MySQL"]
    infrastructure["<b>bookstore-infrastructure</b><br/>dosya, thread, önbellek, BCrypt"]
    application["<b>bookstore-application</b><br/>kullanım senaryoları, portlar"]
    domain["<b>bookstore-domain</b><br/>varlıklar, iş kuralları, repository arayüzleri"]
    common["<b>bookstore-common</b><br/>değer nesneleri, hatalar, sayfalama"]

    web --> application
    web --> persistence
    web --> infrastructure
    persistence --> application
    infrastructure --> application
    application --> domain
    domain --> common
```

| Modül | Sorumluluk | Bağımlı olabildiği |
|---|---|---|
| `bookstore-common` | Her katmanın kullandığı küçük, genel yapı taşları: `Money`, hata hiyerarşisi, `Page<T>`, doğrulama yardımcıları. | Yalnızca JDK |
| `bookstore-domain` | İş modeli ve iş kuralları: `Book`, `Cart`, `Order` … Repository **arayüzleri** burada tanımlanır. | `common` |
| `bookstore-application` | Kullanım senaryoları (*use case*): "sepete ekle", "sipariş ver". Domain nesnelerini orkestre eder, işlem (*transaction*) sınırını belirler. Dış dünyaya ihtiyaç duyduğu şeyleri **port** arayüzü olarak tanımlar. | `domain` |
| `bookstore-persistence` | Repository arayüzlerinin JDBC ile uygulanması, SQL, şema migration'ları. | `application` |
| `bookstore-infrastructure` | Veritabanı dışındaki port uygulamaları: dosya depolama, arka plan bildirimleri, önbellek, parola özetleme, ödeme simülasyonu, demo verisi yükleme. | `application` |
| `bookstore-web` | HTTP katmanı: controller'lar, şablonlar, REST API, güvenlik, yapılandırma ve tüm parçaların birbirine bağlanması. Çalıştırılabilir uygulama burasıdır. | hepsi |

### Bağımlılık kuralı

> Bağımlılıklar her zaman **içe doğru** akar: `web → application → domain → common`.

- `common`, `domain` ve `application` modüllerinde **Spring, Servlet, JDBC veya herhangi bir framework sınıfı kullanılmaz.** Yalnızca JDK ve SLF4J API'si.
- `domain`, veritabanının ya da web'in varlığından habersizdir.
- `persistence` ve `infrastructure` birbirini görmez.
- Kural Maven tarafından zorlanır: bir modülün `pom.xml` dosyasında olmayan bağımlılık derlenmez.

## 3. Paket yapısı

Kök paket `com.bookstore`. Her modülün içi **özelliğe göre** paketlenir (katmana göre değil): `catalog`, `cart`, `order`, `customer`, `review`.

```
bookstore-common          com.bookstore.common
  ├─ money                Money, Currency işlemleri
  ├─ error                BookstoreException ve alt türleri
  ├─ paging               Page<T>, PageRequest, SortOrder
  ├─ validation           Guard (ön koşul denetimleri)
  └─ text                 Türkçe duyarlı metin normalleştirme

bookstore-domain          com.bookstore.domain
  ├─ catalog              Book, Author, Category, Isbn, Stock, BookRepository …
  ├─ cart                 Cart, CartItem, CartRepository
  ├─ order                Order, OrderLine, OrderStatus, OrderNumber, ShippingPolicy, OrderRepository
  ├─ customer             Customer, Address, Email, Role, CustomerRepository
  ├─ review               Review, Rating, ReviewRepository
  └─ event                DomainEvent, OrderPlaced, OrderStatusChanged

bookstore-application     com.bookstore.application
  ├─ catalog              CatalogService, BookSearchQuery
  ├─ cart                 CartService
  ├─ order                CheckoutService, OrderService
  ├─ account              AccountService
  ├─ review               ReviewService
  ├─ admin                CatalogAdminService, OrderAdminService, UserAdminService
  └─ port                 TransactionRunner, PasswordHasher, Notifier, FileStorage, PaymentGateway

bookstore-persistence     com.bookstore.persistence
  ├─ jdbc                 JdbcExecutor, RowMapper<T>, JdbcTransactionRunner
  ├─ catalog | cart | order | customer | review     Jdbc…Repository sınıfları
  └─ resources/db/migration                          V1__….sql (Flyway)

bookstore-infrastructure  com.bookstore.infrastructure
  ├─ storage              LocalFileStorage (NIO.2)
  ├─ notification         AsyncLogNotifier (ExecutorService)
  ├─ cache                ConcurrentCache<K,V>
  ├─ security             BCryptPasswordHasher
  ├─ payment              SimulatedPaymentGateway
  └─ seed                 DemoDataLoader (CSV)

bookstore-web             com.bookstore.web
  ├─ config               Bean tanımları, güvenlik, i18n
  ├─ mvc                  Thymeleaf controller'ları ve form nesneleri
  ├─ api.v1               REST controller'ları, DTO'lar, hata eşleme
  ├─ security             Spring Security ↔ Customer uyarlaması
  └─ resources            templates/, static/, messages_*.properties, application.yml
```

## 4. Katmanların ayrıntısı

### 4.1 Domain

- **Varlıklar** (`Book`, `Order` …) kimlikleri olan, durumu değişebilen sınıflardır. Kendi kurallarını kendileri korur: geçersiz bir `Order` nesnesi oluşturulamaz.
- **Değer nesneleri** (`Money`, `Isbn`, `Email`, `Rating`, `Stock`) değişmezdir ve `record` olarak yazılır. Doğrulama kurucu içinde yapılır.
- **Sipariş durumu** bir durum makinesidir. İzin verilen geçişler `OrderStatus` içinde tanımlıdır; geçersiz geçiş istisna fırlatır.
- **Kargo ücreti** `ShippingPolicy` arayüzü ile hesaplanır (Strategy kalıbı).
- **Repository arayüzleri** domain dilini konuşur (`findByIsbn`, `save`), SQL ya da tablo adı içermez.
- **Domain olayları** (`OrderPlaced`, `OrderStatusChanged`) bir şey olduğunda üretilir; kimin dinlediğini domain bilmez (Observer kalıbı).

### 4.2 Application

Her kullanım senaryosu bir servis metodudur. Tipik bir metot:

1. Girdiyi bir **komut** nesnesi (`record`) olarak alır.
2. Bir işlem başlatır (`TransactionRunner`).
3. Repository'lerden domain nesnelerini yükler.
4. İş kuralını **domain nesnesine** yaptırır.
5. Sonucu kaydeder, olayları yayınlar, bir **sonuç** nesnesi döner.

Application servisleri HTTP, HTML, JSON ya da SQL bilmez. Bu yüzden veritabanı olmadan, sahte (*in-memory*) repository'lerle test edilebilir.

### 4.3 Persistence

- Her repository arayüzü için bir `Jdbc…Repository` sınıfı vardır (DAO kalıbı).
- Yalnızca `PreparedStatement` ve parametreli sorgu kullanılır.
- `JdbcTransactionRunner`, bir işlem boyunca aynı `Connection` nesnesinin kullanılmasını, hata durumunda `rollback`, başarı durumunda `commit` yapılmasını sağlar.
- Bağlantı havuzu (HikariCP) `DataSource` olarak dışarıdan verilir; modül Spring'e bağımlı değildir.
- **Stok tutarlılığı** iki katmanla korunur: koşullu güncelleme (`UPDATE … SET stock = stock - ? WHERE id = ? AND stock >= ?`) ve varlıklardaki `version` sütunu ile iyimser kilitleme.
- Şema değişiklikleri yalnızca Flyway migration dosyalarıyla yapılır.

### 4.4 Infrastructure

Veritabanı dışındaki her "dış dünya" ayrıntısı burada, birer port uygulaması olarak durur:

| Port (application) | Uygulama (infrastructure) | Açıklama |
|---|---|---|
| `FileStorage` | `LocalFileStorage` | Kapak görsellerini diske yazar ve okur. |
| `Notifier` | `AsyncLogNotifier` | Bildirimleri bir kuyruk ve işçi thread havuzuyla arka planda işler; e-posta yerine log'a yazar. |
| `PasswordHasher` | `BCryptPasswordHasher` | Parolayı özetler ve doğrular. |
| `PaymentGateway` | `SimulatedPaymentGateway` | Ödemeyi her zaman kurala göre onaylar ya da reddeder; dış servis çağırmaz. |

Ayrıca kategori ağacı gibi sık okunan veriler için thread-güvenli `ConcurrentCache` ve ilk çalıştırmada örnek katalog yükleyen `DemoDataLoader` buradadır.

### 4.5 Web

- **MVC** (`/…`): Controller, application servisini çağırır, sonucu modele koyar, bir Thymeleaf şablonu döner. Form doğrulaması Bean Validation ile yapılır.
- **REST** (`/api/v1/…`): Aynı application servislerini çağırır, DTO döner. Domain nesneleri doğrudan JSON'a çevrilmez.
- **Hata eşleme**: `BookstoreException` alt türleri tek bir yerde HTTP durum koduna çevrilir (web'de hata sayfası, API'de *Problem Details*).
- **Bağlama**: `common`, `domain`, `application` modüllerindeki sınıflar Spring anotasyonu taşımaz. `config` paketindeki `@Configuration` sınıfları bunları `@Bean` olarak oluşturur ve bağımlılıklarını kurucu üzerinden verir.
- **Ziyaretçi sepeti** HTTP oturumunda tutulur; giriş sonrası `CartService` ile kalıcı sepete birleştirilir.

## 5. Bir isteğin yolu: sipariş verme

```mermaid
sequenceDiagram
    actor M as Müşteri
    participant C as CheckoutController<br/>(web)
    participant S as CheckoutService<br/>(application)
    participant D as Cart / Order<br/>(domain)
    participant R as Jdbc…Repository<br/>(persistence)
    participant N as AsyncLogNotifier<br/>(infrastructure)

    M->>C: POST /checkout
    C->>S: placeOrder(command)
    activate S
    Note over S: işlem başlar
    S->>R: sepeti ve kitapları yükle
    S->>D: Order.place(cart, address, shippingPolicy)
    D-->>S: Order + OrderPlaced olayı
    S->>R: stoğu düş (koşullu UPDATE)
    S->>R: siparişi kaydet, sepeti boşalt
    Note over S: commit
    S-)N: OrderPlaced (arka planda)
    deactivate S
    S-->>C: OrderResult
    C-->>M: 302 → /orders/{no}
```

Stok yetersizse `InsufficientStockException` fırlar, işlem geri alınır ve müşteri sepet sayfasına uyarıyla döner.

## 6. Veritabanı

```mermaid
erDiagram
    USERS ||--o{ ADDRESSES : has
    USERS ||--o| CARTS : owns
    USERS ||--o{ ORDERS : places
    USERS ||--o{ REVIEWS : writes
    CATEGORIES ||--o{ CATEGORIES : parent
    CATEGORIES ||--o{ BOOKS : contains
    BOOKS ||--o{ BOOK_AUTHORS : ""
    AUTHORS ||--o{ BOOK_AUTHORS : ""
    CARTS ||--o{ CART_ITEMS : contains
    BOOKS ||--o{ CART_ITEMS : ""
    ORDERS ||--|{ ORDER_LINES : contains
    BOOKS ||--o{ ORDER_LINES : ""
    BOOKS ||--o{ REVIEWS : receives
```

- Tablo ve sütun adları `snake_case`, İngilizce.
- Her tabloda `id` (BIGINT, otomatik artan), `created_at`, `updated_at`; eşzamanlı güncellenen tablolarda `version`.
- Tutarlar `DECIMAL(12,2)`.
- `order_lines`, sipariş anındaki `unit_price` ve `book_title` değerlerini kopyalar.
- `reviews` üzerinde `(user_id, book_id)` benzersiz kısıtı vardır.

## 7. Güvenlik

| Konu | Yaklaşım |
|---|---|
| Kimlik doğrulama (web) | Form tabanlı giriş, sunucu tarafı oturum |
| Kimlik doğrulama (API) | HTTP Basic, oturumsuz |
| Yetkilendirme | URL kuralları (`/admin/**`, `/api/v1/admin/**` → `ADMIN`) ve servis seviyesinde sahiplik denetimi (müşteri yalnızca kendi siparişini görür) |
| Parola | BCrypt |
| CSRF | Web formlarında açık; oturumsuz API'de kapalı |
| Girdi | Bean Validation + domain değer nesneleri |
| SQL enjeksiyonu | Yalnızca `PreparedStatement` |
| XSS | Thymeleaf'in varsayılan HTML kaçışı |

## 8. Hata yönetimi

Tek bir hata hiyerarşisi vardır; kökü `common` modülündeki `BookstoreException` (denetlenmeyen istisna).

| İstisna | Anlamı | HTTP |
|---|---|---|
| `ValidationException` | Girdi iş kuralına uymuyor | 400 |
| `NotFoundException` | İstenen kayıt yok | 404 |
| `AccessDeniedException` | Kaydın sahibi değil | 403 |
| `ConflictException` | Durum uyuşmazlığı (ör. e-posta kayıtlı, geçersiz durum geçişi) | 409 |
| `InsufficientStockException` | Stok yetmiyor (`ConflictException` alt türü) | 409 |

Alt katmanlardaki teknik hatalar (`SQLException`, `IOException`) yakalandıkları adapter'da anlamlı bir istisnaya sarılır; application ve domain katmanına sızmaz.

## 9. Yapılandırma ve çalışma profilleri

| Profil | Veritabanı | Kullanım |
|---|---|---|
| *(varsayılan)* | MySQL (`localhost:3306/bookstore`) | Normal çalışma. Bağlantı bilgisi ortam değişkenlerinden okunur. |
| `h2` | Bellek içi H2 (MySQL modu) | MySQL kurmadan hızlı deneme. Veri kalıcı değildir. |
| `test` | Bellek içi H2 (MySQL modu) | Otomatik testler. |

Parola, bağlantı bilgisi gibi gizli değerler repoya yazılmaz; ortam değişkeni ile verilir.

## 10. Test stratejisi

| Katman | Test türü | Araç |
|---|---|---|
| `common`, `domain` | Birim testi; dış bağımlılık yok | JUnit 5, AssertJ |
| `application` | Birim testi; bellek içi sahte repository'ler | JUnit 5, Mockito |
| `persistence` | Gerçek SQL ile veritabanı testi | H2 (MySQL modu), Flyway |
| `infrastructure` | Birim testi; geçici dizin, eşzamanlılık testleri | JUnit 5 (`@TempDir`) |
| `web` | Controller testi ve uçtan uca test | MockMvc, `java.net.http.HttpClient` |

`./mvnw verify` tüm testleri çalıştırır ve yalnızca JDK 21 gerektirir.

## 11. Kod kuralları

- Kod, commit mesajları ve tablo adları **İngilizce**; dokümanlar **Türkçe**.
- Değişmezlik varsayılandır: değer nesneleri `record`, alanlar `final`.
- `null` dönülmez: tek değer için `Optional<T>`, çoklu değer için boş koleksiyon.
- Para için yalnızca `Money`; tarih-saat için yalnızca `java.time`.
- Bağımlılıklar kurucu üzerinden verilir; `static` durum ve alan enjeksiyonu yoktur.
- Her `public` tür için Javadoc; her paket için `package-info.java`.
