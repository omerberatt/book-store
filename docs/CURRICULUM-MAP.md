# Müfredat Eşlemesi

Projenin izlediği müfredat, **Java Patterns and Practices** ders notlarıdır: [software-lecture-labs/java-study-notes](https://github.com/software-lecture-labs/java-study-notes), Bölüm 01–14. Bu doküman, her bölümde anlatılan konunun kitap mağazasında **nerede** ve **hangi durumda** olduğunu gösterir.

Ders notları, konuları Northwind veritabanı üzerinde bir `sales` modülüyle anlatıyor ve bunların "Lab projesi"nde uygulanacağını söylüyor. Kitap mağazası bu Lab projesinin yerine geçer: aynı kavramlar katalog, müşteri, satış ve yorum modüllerinde uygulanır.

**Durum işaretleri**

| İşaret | Anlamı |
| --- | --- |
| ✅ | Uygulandı ve testi var. |
| 🔁 | Uygulandı ama ders notlarındaki biçimden farklı; belirtilen fazda dersle uyumlu hale getirilecek. |
| ⏳ | Henüz yok; belirtilen fazda yapılacak. |
| — | Projede kod gerektirmeyen ya da bilinçli olarak uygulanmayan konu. |

Faz numaraları [ROADMAP.md](ROADMAP.md) içindeki aşamalardır; ADR'ler [docs/adr](adr/README.md) klasöründedir.

---

## Bölüm 01 — Gitflow ve İlk İş Nesnesi

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| Gitflow: `main`, `develop`, `feature/*`, `release/*`, `hotfix/*` | [ADR-0001](adr/ADR-0001.md). Faz 0–3 doğrudan `main`'e yapıldı. | ⏳ | 4 |
| Sürüm etiketleri, CalVer | `v2026.11.1` biçimi *(ADR-0001)* | ⏳ | 4 |
| POJO'dan değişmez *(immutable)* iş nesnesine | `Money`, `Isbn`, `Email`, `PersonName`, `Address`, `Stock`, `Rating` (`record`) | ✅ | 1 |
| Kurucuda doğrulama, `final` sınıf | Değer nesnelerinin compact constructor'ları; `shared-kernel` `Guard` | ✅ | 1 |
| Boş `catch` ile hatayı yutmamak | Exception'lar sarılıp yeniden fırlatılıyor *(`DataAccessException`)* | ✅ | 3 |
| Notification pattern — tüm hataları birden bildirmek | `ValidationException` birden çok `Violation` taşır; `PasswordPolicy` tüm kuralları birlikte raporlar | ✅ | 1 |

## Bölüm 02 — Git Pratikleri, Anemik Model ve Record'lar

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| Feature dalı, `--no-ff` merge, Pull Request | [ADR-0001](adr/ADR-0001.md) | ⏳ | 4 |
| Anemik model ve sorunları | Domain baştan zengin tasarlandı; anemik karşı örnek tutulmuyor | — | — |
| Value object, `equals` / `hashCode` | `record`'lar değere göre; `Entity` kimliğe göre eşit *(`EntityTest`)* | ✅ | 1 |
| Class ile record karşılaştırması | Değer nesneleri `record`; kimliği olan varlıklar sınıf | ✅ | 1 |
| Record'ların sığ değişmezliği, `List.copyOf` | `Page`, `CartView`, `OrderDetails`, `BookDetails` kurucuları listeleri kopyalar | ✅ | 1, 2 |
| `invokedynamic`, bytecode | Projede kod gerektirmez | — | — |

## Bölüm 03 — Money Value Object ve Rich Entity Tasarımı

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| `double` / `float` yuvarlama hataları | `MoneyTest.addsDecimalFractionsExactly` | ✅ | 1 |
| `Money` record: `BigDecimal`, para birimi, `setScale`, `HALF_UP` | `shared-kernel` `Money` | ✅ | 1 |
| Farklı para birimleriyle işlem yasağı | `Money.requireSameCurrency` | ✅ | 1 |
| Zengin entity: `Order`, `OrderLine`, `OrderStatus` | `sales` `Order.place`, `markPaid`, `ship`, `deliver`, `cancel` | ✅ | 1 |
| Sipariş anındaki fiyatın kopyalanması | `OrderLine` başlık ve birim fiyatı kopyalar | ✅ | 1 |
| Value object mi entity mi? | `Entity` taban sınıfı ile `record` değer nesneleri ayrımı | ✅ | 1 |

## Bölüm 04 — Aggregate Sınırları, State Machine ve Domain Exception'lar

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| Niyet bildiren metotlar *(setter yerine)* | `Customer.rename`, `changeEmail`; `Book.reserve`, `changePrice` | ✅ | 1 |
| Aggregate kökü ve parçaları | `Order` → `OrderLine`, `Customer` → `SavedAddress`, `Cart` → `CartItem` | ✅ | 1 |
| Aggregate'ler arası kimlikle referans | `Order` ve `Review` müşteriye `long` kimlikle bağlı. Ancak `Order.place` ve `Cart.add` başka bir aggregate'in nesnesini *(`Book`)* alıyor. Modüller ayrılınca `sales` kendi `ProductSnapshot` değerini kullanacak *(ADR-0002)*. | 🔁 | 4 |
| `OrderStatus` state machine, `EnumSet`, final durumlar | `OrderStatus.allowedNext`, `canMoveTo`, `isFinal` | ✅ | 1 |
| Domain exception hiyerarşisi | Bugün `BookstoreException` alt türleri; `DomainException` + `Kind` + `code` yapısına dönüştürülecek *(ADR-0004)* | 🔁 | 4 |
| JUnit ve AssertJ ile birim testler | Tüm modüllerde; parametreli testler *(`@CsvSource`, `@EnumSource`)* | ✅ | 1 |
| "Turkish I problem", `Locale.ROOT` | `Email`, `Isbn`, `TextNormalizer` | ✅ | 1 |

## Bölüm 05 — Optional, Stream API ve Sorgu Tasarımı

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| `Optional` dönüş tipi sözleşmesi | `findBy…` metotları, `Book.publicationYear()`, `Customer.defaultAddress()` | ✅ | 1 |
| `Optional` alan olarak kullanılmaz | `BookSearchCriteria` ve `BookSearchQuery` record bileşenleri `Optional`; kural ihlali *(ADR-0004)* | 🔁 | 4 |
| Stream API, `reduce(Money::plus)` | `Money.sum`, `Cart.subtotal`, `Order` toplamları | ✅ | 1 |
| `Collectors.groupingBy`, `toMap`, `mapping` | `RatingDistribution`, repository'lerde kimliğe göre gruplama | ✅ | 1, 3 |
| Bellek içi sorgu nesnesi *(OrderBook)* | Test çiftleri: `InMemoryBookRepository` … `InMemoryStore<T>` | ✅ | 2 |
| Big-O: liste yerine `Map` indeksi | `InMemoryStore` kimliğe, ISBN'e ve e-postaya göre `Map` | ✅ | 2 |
| Deterministik sıralama | `BookSort` eşitlikte başlık ve ISBN'e göre sıralar | ✅ | 1 |

## Bölüm 06 — Yazılım Mimarileri

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| Coupling, cohesion, test edilebilirlik, bilişsel yük | ADR-0002'nin gerekçeleri | ✅ | — |
| Katmanlı, hexagonal *(ports & adapters)*, onion/clean | Her iş modülünün içi hexagonal: `domain` merkezde, repository'ler ve portlar arayüz, `persistence` ve `rest` adaptör | 🔁 | 4 |
| Modüler monolit | İş alanına göre Maven modülleri *(ADR-0002)*; bugün modüller katmana göre | 🔁 | 4 |
| Mikroservis, SOA | Bilinçli olarak seçilmedi *(ADR-0002)* | — | — |

## Bölüm 07 — Typed Identity, Static Factory, Factory ve Builder

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| Tipli kimlikler *(`CustomerId`, `OrderId`)* | `BookId`, `AuthorId`, `CategoryId`, `CustomerId`, `OrderId`, `ReviewId` *(ADR-0004)*; bugün `long` | ⏳ | 4 |
| Static factory metotları | `Money.of`, `Customer.register`, `Cart.guest`, `OrderNumber.of`, `Rating.of` | ✅ | 1 |
| Factory ve kimlik üreteci arayüzü | `Order.place` + `OrderNumberSequence` portu *(bellek içi ve JDBC uygulamaları)* | ✅ | 1, 3 |
| Builder | `Book.Builder`, `BookSearchCriteria.Builder` | ✅ | 1 |

## Bölüm 08 — Strategy, Specification, Repository ve Mapper

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| Strategy | `ShippingPolicy` / `FreeShippingThreshold`, `BookSortField` | ✅ | 1 |
| Strategy için lambda | `ShippingPolicy.alwaysFree()`, testte `subtotal -> …` | ✅ | 1 |
| Specification *(kuralı veri haline getirmek)* | Bugün `BookSearchCriteria.asPredicate()` birleşik bir `Predicate`. `shared-kernel`'e `Specification<T>` *(and / or / not)* ve `BookSpecifications` eklenecek. | 🔁 | 4 |
| Specification'ı SQL'e çevirmek | `BookSearchSql` *(ders bunu "bir sonraki adım" olarak bırakıyor)* | ✅ | 3 |
| Repository deseni, arayüzün yeri | Arayüzler domain'de *(ders ADR-0003'ün risk notu baştan önlendi)* | ✅ | 1 |
| Mapper *(domain ↔ DTO)* | Bugün okuma modeli `record`'larının `of(...)` metotları. `api` paketindeki DTO'lar ve `application` paketindeki mapper'lar olacak *(ADR-0003)*. | 🔁 | 4 |
| Paket düzeni: `api`, `application`, `domain`, `persistence` | Her iş modülünde *(ADR-0002)* | ⏳ | 4 |

## Bölüm 09 — Maven Modülleri ve ArchUnit

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| Çok modüllü Maven, `pom` / `jar` paketleme | Altı modül var ama katmana göre ayrılmış | 🔁 | 4 |
| İş alanına göre modüller *(package-by-feature)* | `shared-kernel`, `catalog`, `customer`, `sales`, `review`, `app` *(ADR-0002)* | ⏳ | 4 |
| `shared-kernel` | Bugünkü `bookstore-common` yeniden adlandırılacak *(ADR-0005)* | 🔁 | 4 |
| Test fixture'ını `test-jar` ile paylaşmak | `TestData` ve `Shop` düzenekleri | ⏳ | 4 |
| Uçtan uca smoke test | `app` modülünde tüm modülleri kuran bir test | ⏳ | 4 |
| ArchUnit ile mimari testler | `quality/architecture-tests`; ADR-0003–0008 kuralları | ⏳ | 4 |
| `no_standard_streams`, `no_dumping_ground_packages` | [ADR-0005](adr/ADR-0005.md) | ⏳ | 4 |

## Bölüm 10 — Uygulama Servisleri ve Manuel Dependency Injection

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| Kullanım senaryosu servisleri | `CatalogService`, `CartService`, `AccountService`, `CheckoutService`, `OrderService`, `OrderAdminService`, `ReviewService` | ✅ | 2 |
| Komut nesneleri *(commands)* | `RegistrationCommand`, `PlaceOrderCommand`, `ReviewCommand` | ✅ | 2 |
| `api` paketi ve façade arayüzleri | Komutlar ve görünümler `api` paketine taşınacak; façade arayüzleri eklenecek | 🔁 | 4 |
| Constructor injection, `private final` işbirlikçiler | Tüm servisler | ✅ | 2 |
| `Clock` enjeksiyonu, `SteppingClock` | `Clock.fixed` ve `Clock.offset` ile testler | ✅ | 1, 2 |
| Composition root | Testlerde `Shop`; üretimde `app` modülünde `BookstoreModule` | 🔁 | 4 |
| Fake, stub, mock | `application` testlerinde `FakePorts` *(fake)* | ✅ | 2 |

## Bölüm 11 — Domain Events ve Observer Deseni

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| Olaylar `sealed` arayüz + `record` | `DomainEvent`, `OrderPlaced`, `OrderStatusChanged`; modül başına olay arayüzüne bölünecek | 🔁 | 4 |
| Aggregate olayı biriktirir, servis toplar *(`pullEvents`)* | `Entity.raise` / `pullEvents` | ✅ | 1 |
| Önce kayıt, sonra yayın | `CheckoutService`, `OrderAdminService` | ✅ | 2 |
| Yayıncı portu ve gözlemciler | Bugün tek bir `Notifier` portu; `DomainEventPublisher` + `InProcessEventPublisher` + gözlemciler olacak *(ADR-0007)* | 🔁 | 4 |
| Dual-write problemi | Bilinçli olarak belgeleyen bir test | ⏳ | 4 |
| `switch` ile tür eşleme *(exhaustiveness)* | `OrderTest.target`, `CartService.load`, `CheckoutService` ödeme sonucu | ✅ | 1, 2 |

## Bölüm 12 — Persistence Boundary, JDBC ve Flyway

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| Flyway ile şema yönetimi | `V1__initial_schema.sql`, `DatabaseMigrator` | ✅ | 3 |
| Migration'ların sahibi olan modül | Bugün tek dosya; modül başına `db/migration/<modül>` olacak *(ADR-0008)* | 🔁 | 4 |
| JDBC repository, aggregate = depolama birimi | `JdbcBookRepository`, `JdbcOrderRepository` … | ✅ | 3 |
| Alt satırları silip yeniden yazmak | `cart_items`, `book_authors` | ✅ | 3 |
| Veritabanından yeniden oluşturma *(reconstitute)* | Bugün adı `restore(...)`; `reconstitute` olacak | 🔁 | 4 |
| N+1 sorgu problemi | Bir sayfa kitap üç sorguyla yüklenir | ✅ | 3 |
| Specification sorgularının bedeli | `BookSearchSql` tüm filtreyi SQL'de yapar | ✅ | 3 |
| `BigDecimal` ölçek problemi | `Money` her zaman 2 basamağa yuvarlar | ✅ | 1 |
| `CHECK (... IN ...)` ve H2 2.4.240 hatası | Faz 3'te bulundu, `CASE` ile aşıldı; referans tablolarla değiştirilecek *(ADR-0008)* | 🔁 | 4 |
| `SQLException`'ı sarmalamak | `DataAccessException`; kısıt ihlalleri `ConflictException`'a çevrilir | ✅ | 3 |
| Sipariş numarası veritabanından | `order_number_sequences` + `JdbcOrderNumberSequence` | ✅ | 3 |
| Transaction sınırı kullanım senaryosunda | `TransactionRunner` portu + `JdbcTransactionRunner` *(`ThreadLocal` ile bağlantı)* | ✅ | 2, 3 |
| Sözleşme testleri *(contract tests)* | Her repository için tek sözleşme; bellek içi, H2 ve PostgreSQL implementasyonlarının hepsi geçecek | ⏳ | 4 |
| Bellek içi repository'nin kopya saklaması | Bugün referans saklıyor *(ders Bölüm 12'deki açığın aynısı)* | 🔁 | 4 |
| PostgreSQL ve Testcontainers | Bugün H2 MySQL modu; PostgreSQL 17, H2 PostgreSQL modu ve Testcontainers olacak *(ADR-0008)* | 🔁 | 4 |
| Atomik kayıt, geri alma | `CheckoutWithDatabaseTest.declinedPaymentRollsBack…` | ✅ | 3 |
| JPA'nın neleri ortadan kaldırdığı | Ayrı persistence modeliyle ikinci uygulama | ⏳ | 9 |

## Bölüm 13 — HTTP Boundary, DTO, Validasyon ve Hata Sözleşmeleri

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| `rest` paketi, façade üzerinden erişim | Her iş modülünde *(ADR-0009)* | ⏳ | 5 |
| Request DTO ve Bean Validation *(biçimsel doğrulama)* | `AddToCartRequest`, `PlaceOrderRequest`, `RegistrationRequest` … | ⏳ | 5 |
| Doğrulamanın yeri: sınır mı domain mi? | Biçim sınırda, kural domain'de *(ADR-0009)* | ⏳ | 5 |
| `DomainException.Kind` → durum kodu | 404 / 409 / 422 / 403, biçim hatası 400 | ⏳ | 4, 5 |
| RFC 9457 Problem Details | `ProblemMapper`, `application/problem+json` | ⏳ | 5 |
| JSON dönüşümleri | Faz 5'te Jackson, Faz 6'da JSON-B | ⏳ | 5, 6 |
| JDK HTTP sunucusu *(`com.sun.net.httpserver`)* | Elle yönlendirme ve hata eşleme | ⏳ | 5 |
| HTTP testleri | `java.net.http.HttpClient` ile uçtan uca | ⏳ | 5 |

## Bölüm 14 — Uygulama Sunucusu: CDI, JTA, JAX-RS ve Payara Micro

| Konu | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| WAR paketleme, Payara Micro 7 | `app` modülü → `bookstore.war` *(ADR-0010)* | ⏳ | 6 |
| CDI: `beans.xml`, `annotated` keşif, kapsamlar, proxy | Servisler ve repository'ler `@ApplicationScoped` | ⏳ | 6 |
| Producer'lar *(container'ın kuramadığı nesneler)* | `BookstoreWiring`: `Clock`, `DataSource`, kargo kuralı | ⏳ | 6 |
| `@DataSourceDefinition` ve MicroProfile Config | Bağlantı bilgisi ortamdan, WAR'da parola yok | ⏳ | 6 |
| Açılışta migration ve demo verisi | `BookstoreStartup` *(`bookstore.demo-data`)* | ⏳ | 6 |
| JTA `@Transactional`, repository'ler commit etmez | `JdbcTransactionRunner`'ın yerini alır | ⏳ | 6 |
| CDI olayları, `@Observes(during = AFTER_SUCCESS)` | `CdiDomainEventPublisher`, bildirim gözlemcisi | ⏳ | 6 |
| JAX-RS kaynakları, JSON-B, `ExceptionMapper` | Faz 5'teki HTTP katmanının taşınması | ⏳ | 6 |
| JerseyTest | Faz 5'teki HTTP beklentileri aynen geçer | ⏳ | 6 |
| MicroProfile OpenAPI | `/openapi` *(API-4)* | ⏳ | 6 |

---

## Ders notlarının soru bölümlerinden alınanlar

Ders, bazı konuları sorularda bırakıp Lab projesine erteliyor. Kitap mağazasında bunlardan uygulananlar ve planlananlar:

| Soru *(bölüm)* | Kitap mağazasında | Durum | Faz |
| --- | --- | --- | --- |
| İyimser kilitleme, `version` kolonu *(12, 14)* | Tüm aggregate tabloları; [ADR-0015](adr/ADR-0015.md) | ✅ | 3 |
| Aynı anda son kopyayı alan iki istemci *(14)* | `CheckoutWithDatabaseTest.twoCustomersRacingForTheLastCopyCannotBothGetIt` | ✅ | 3 |
| Sipariş numarasında boşluk ve geri alma *(12)* | Geri alınan numara yeniden verilir; testle belgelendi | ✅ | 3 |
| Transactional Outbox *(11, 12, 14)* | — | ⏳ | 9 |
| `ETag` / `If-Match` *(13)* | — | ⏳ | 9 |
| `Idempotency-Key` *(13)* | — | ⏳ | 9 |
| Hata yanıtına korelasyon kimliği *(13)* | — | ⏳ | 9 |
| MicroProfile Health readiness *(14)* | — | ⏳ | 9 |
| PostgreSQL ile entegrasyon testi CI'da *(14)* | GitHub Actions | ⏳ | 9 |

## Dersin dışında kalan, projede uygulanan konular

Bunlar ders notlarında yok; kitap mağazasının ihtiyacından doğdu ya da Faz 0–3'te genel Java müfredatı varsayılarak eklendi. Korunuyorlar ama müfredatın parçası sayılmıyorlar.

| Konu | Nerede |
| --- | --- |
| `ThreadLocal` | `JdbcTransactionRunner` *(Faz 6'da JTA'ya bırakılınca kalkar)* |
| Yansıma *(reflection)*, `RecordComponent` | `RecordRowMapper` |
| İç sınıf, `Iterator` | `Cart.LineIterator` |
| `ArrayDeque` ile genişlik öncelikli gezinme | `Category.selfAndDescendants` |
| Diziler ve kontrol basamağı hesabı | `Isbn` *(ISBN-10 → ISBN-13)* |
| Java serileştirmesi | `Cart` *(misafir sepeti oturumda; ADR-0011 ile yeniden değerlendirilecek)* |
| Eşzamanlılık testleri *(`ExecutorService`, `CountDownLatch`)* | `JdbcRepositoriesTest.OrderNumbers`, `CheckoutWithDatabaseTest` |
| Kimlik doğrulama, BCrypt | Faz 7 *(ADR-0011)* |
| HTML / JavaScript arayüz | Faz 7 *(ADR-0011)* |

## Plandan çıkarılanlar

Önceki planda, ders notları görülmeden önce eklenmiş ama müfredatla ilgisi olmayan konular: thread havuzuyla arka plan bildirimleri, `ConcurrentCache`, NIO.2 ile dosya işleme ve CSV'den örnek veri yükleme. Bildirimler ADR-0007'deki gözlemcilere, örnek veri yükleme ADR-0010'daki açılış adımına taşındı. Kapak görseli depolama yönetim paneliyle birlikte Faz 8'de yapılacak.
