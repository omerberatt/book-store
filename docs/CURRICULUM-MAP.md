# Müfredat Eşlemesi

Lisans seviyesi Java müfredatındaki her konunun projede **nerede** ve **ne için** uygulanacağını gösterir. Amaç, konuların yapay örneklerle değil gerçek bir ihtiyaç üzerinden kullanılmasıdır.

"Faz" sütunu [ROADMAP.md](ROADMAP.md) içindeki aşamayı gösterir; ✅ işareti o fazda kodu ve testi yazılmış demektir. İşaretsiz sınıf adları plandır; uygulama sırasında değişirse bu tablo güncellenir.

## 1. Dil temelleri

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| İlkel türler, operatörler, kontrol yapıları | `common.validation.Guard`, `domain.catalog.Isbn` | Ön koşul denetimleri, ISBN-10 ve ISBN-13 kontrol basamağı hesabı | 1 ✅ |
| `String`, `StringBuilder`, metin işlemleri | `common.text.TextNormalizer`, `domain.order.OrderNumber` | Türkçe duyarlı arama anahtarı, `String.format` ile sipariş numarası | 1 ✅ |
| Diziler | `domain.catalog.Isbn`, `domain.review.RatingDistribution` | Basamak dizisi, 1–5 puan sayaçları | 1 ✅ |
| Düzenli ifadeler (`regex`) | `domain.customer.Email`, `PasswordPolicy`, `Address`, `OrderNumber` (yakalama grupları) | Biçim doğrulama ve ayrıştırma | 1 ✅ |
| `BigDecimal`, sayısal hassasiyet | `common.money.Money`, `catalog.RatingSummary` | Para hesabı, yuvarlama, ortalama puan | 1 ✅ |
| Tarih-saat API'si (`java.time`) | `domain.order.Order`, `domain.review.Review`, `Book` (`Year`) | Sipariş ve yorum zamanı, `Clock` ile test edilebilir zaman | 1 ✅ |

## 2. Nesne yönelimli programlama

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| Sınıf, nesne, kapsülleme | `domain.catalog.Book`, `domain.cart.Cart`, `domain.customer.Customer` | Durumu yalnızca kendi metotlarıyla değişen varlıklar | 1 ✅ |
| Kurucular, `static` fabrika metotları | `Money.of(…)`, `Order.place(…)`, `Customer.register(…)`, `…restore(…)` | Geçersiz nesne oluşturmayı engelleme; yeni nesne ile depodan yüklenen nesneyi ayırma | 1 ✅ |
| Değişmezlik, `record` | `Money`, `Isbn`, `Email`, `Rating`, `Stock`, `Address`, `OrderLine`, `CartItem` | Kurucuda doğrulanan değer nesneleri | 1 ✅ |
| `equals` / `hashCode` / `toString` | `domain.Entity` (kimliğe göre), `record`'lar (değere göre), `RatingDistribution` (dizi içeriğine göre) | Koleksiyonlarda doğru davranış | 1 ✅ |
| Kalıtım, `abstract` sınıf | `common.error.BookstoreException` hiyerarşisi, `domain.Entity` | Ortak davranışı paylaşma | 1 ✅ |
| Arayüz, çok biçimlilik | `BookRepository` ve diğer repository'ler, `ShippingPolicy`, `application.port.*` | Uygulamadan bağımsız sözleşme | 1 ✅ |
| `enum` (alan ve davranışla) | `OrderStatus`, `BookSortField`, `Language`, `SortOrder`, `Role`, `PaymentMethod` | Durum makinesi, sabit kümeler, sabite bağlı davranış | 1 ✅ |
| `sealed` tür ve örüntü eşleme (`switch`) | `domain.event.DomainEvent` (sealed interface + record'lar), `OrderStatus.allowedNext` (switch ifadesi) | Kapalı tür hiyerarşisinde eksiksiz `switch` | 1 ✅ |
| İç sınıf, iç içe `static` sınıf | `Cart.LineIterator` (iç sınıf), `Book.Builder`, `BookSearchCriteria.Builder` (`static`) | Kapsayan nesnenin durumuna erişim; kurucu yardımcısı | 1 ✅ |
| `Comparable`, `Comparator` | `Money`, `Rating`, `BookSortField`, `BookSort` | Doğal sıralama, zincirlenmiş özel sıralama | 1 ✅ |

## 3. İstisna yönetimi

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| Özel istisna hiyerarşisi | `common.error.*`, `domain.catalog.InsufficientStockException` | İş hatalarını türle ve hata koduyla ayırma | 1 ✅ |
| Denetlenen / denetlenmeyen istisna | `persistence` (`SQLException` sarma), `infrastructure.storage` (`IOException` sarma) | Teknik hatayı sınırda anlamlı hataya çevirme | 3 |
| `try-with-resources`, `finally` | Tüm JDBC ve dosya kodu | Kaynakların kapatılması | 3 |
| Merkezi hata işleme | `web.api.v1.ApiExceptionHandler` | İstisnayı HTTP yanıtına eşleme | 5 |

## 4. Generics

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| Generic sınıf ve arayüz | `common.paging.Page<T>` ✅, `persistence.jdbc.RowMapper<T>`, `infrastructure.cache.ConcurrentCache<K,V>` | Türden bağımsız yeniden kullanım | 1, 3, 4 |
| Generic metot, sınırlı tür (`<T extends …>`) | `Guard.requireBetween(<T extends Comparable<? super T>>)`, `Guard.requireNotEmpty(<C extends Collection<?>>)`, `SortOrder.apply` | Tür güvenli, her türe uyan denetim | 1 ✅ |
| Joker türler (`? extends`, `? super`) | `Page.map(Function<? super T, ? extends R>)`, `Page.fromList(List<? extends T>)` | PECS kuralı | 1 ✅ |

## 5. Koleksiyonlar, veri yapıları ve algoritmalar

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| `List`, `Set`, `Map` ve uygulamaları | `Cart` (`LinkedHashMap`), `Book` (`LinkedHashSet<Author>`), `OrderStatus` (`EnumSet`), bellek içi repository'ler | Sırayı koruma, tekrarı önleme, enum kümeleri | 1 ✅, 2 |
| `Queue`, `Deque` | `Category.selfAndDescendants` (`ArrayDeque` ile genişlik öncelikli gezinme) ✅, `infrastructure.notification` (bildirim kuyruğu) | FIFO işleme | 1 ✅, 4 |
| Ağaç yapısı ve özyineleme | `domain.catalog.Category` (`depth`, `path`) | Üst kategori zincirini dolaşma | 1 ✅ |
| `Iterable` / `Iterator` | `Cart` | `for-each` ile gezilebilir, silmeye kapalı sepet | 1 ✅ |
| Sıralama ve arama | `BookSort.comparator()`, `BookSearchCriteria.asPredicate()` | Çok ölçütlü sıralama, kararlı sayfalama için eşitlik bozucu | 1 ✅ |
| Karmaşıklık analizi (Big-O) | Bellek içi repository'lerde `Map` indeksleri, `ConcurrentCache` | Doğrusal tarama yerine sabit zamanlı erişim | 2 |
| Değiştirilemez koleksiyonlar | `List.copyOf`, `Collections.unmodifiableList/Set` (varlıkların dışarı verdiği koleksiyonlar) | İç durumu koruma | 1 ✅ |

## 6. Fonksiyonel programlama

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| Lambda, metot referansı | `Cart.subtotal(Function<Long, Money>)`, `BookSortField`, `ShippingPolicy.alwaysFree()`; ileride `TransactionRunner`, `RowMapper` | Davranışı parametre olarak verme | 1 ✅, 2, 3 |
| Fonksiyonel arayüzler | `ShippingPolicy` (`@FunctionalInterface`), `Predicate<Book>` (`BookSearchCriteria`), `RowMapper<T>` | Tek metotlu sözleşmeler, `Predicate.and` ile birleştirme | 1 ✅, 3 |
| Stream API | `Money.sum`, `Cart.subtotal`, `Order` toplamları, `Language.fromCode` | Bildirimsel veri işleme | 1 ✅ |
| `Optional` | `Category.parent()`, `Book.publicationYear()`, `Customer.defaultAddress()`, tüm `findBy…` metotları | `null` yerine açık yokluk | 1 ✅ |
| `Collectors` (gruplama, özetleme) | `RatingDistribution.of` (`groupingBy` + `counting`), `ValidationException` (`joining`), yönetim özeti | Gruplayarak sayma | 1 ✅, 7 |

## 7. Girdi / çıktı

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| NIO.2 (`Path`, `Files`) | `infrastructure.storage.LocalFileStorage` | Kapak görsellerini kaydetme ve okuma | 4 |
| Bayt ve karakter akışları, kodlama | `LocalFileStorage`, `infrastructure.seed.DemoDataLoader` | Görsel (bayt) ve CSV (UTF-8 metin) okuma | 4 |
| Metin ayrıştırma | `DemoDataLoader` | CSV satırlarından örnek katalog üretme | 4 |
| Nesne serileştirme (`Serializable`) | `domain.cart.Cart` (`serialVersionUID`, `CartTest` içinde gidiş-dönüş testi) | Ziyaretçi sepetinin HTTP oturumunda saklanması | 1 ✅, 5 |
| Sınıf yolu kaynakları, `Properties` | `messages_*.properties`, örnek veri dosyaları | Yapılandırma ve çeviri metinleri | 5 |

## 8. Eşzamanlılık

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| `Thread`, `Runnable`, yaşam döngüsü | `infrastructure.notification.AsyncLogNotifier` | İşçi thread'ler ve düzgün kapanış | 4 |
| `ExecutorService`, thread havuzu | `AsyncLogNotifier` | Bildirimleri isteği bekletmeden işleme | 4 |
| Üretici–tüketici, `BlockingQueue` | `AsyncLogNotifier` | Olay kuyruğu | 4 |
| `synchronized`, kilitler, yarış durumu | Bellek içi repository'lerde stok düşme | Aynı anda gelen siparişlerde tutarlılık | 4 |
| Eşzamanlı koleksiyonlar, atomik türler | `ConcurrentCache` (`ConcurrentHashMap`), sipariş numarası sayacı (`AtomicLong`) | Kilitsiz thread güvenliği | 4 |
| `CompletableFuture` | `application.admin` özet ekranı | Bağımsız sorguları paralel çalıştırma | 4 |
| Sanal thread'ler (Java 21) | `bookstore-web` yapılandırması | İstek başına hafif thread | 5 |
| `ThreadLocal` | `persistence.jdbc.JdbcTransactionRunner` | İşlem boyunca aynı bağlantıyı taşıma | 3 |

## 9. Veritabanı ve JDBC

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| İlişkisel modelleme, normalizasyon | `db/migration/V1__…sql` | Şema tasarımı | 3 |
| SQL (DDL, DML, JOIN, gruplama) | `persistence.*` | Arama, sayfalama, puan ortalaması | 3 |
| JDBC: `Connection`, `PreparedStatement`, `ResultSet` | `persistence.jdbc.JdbcExecutor` | Sorgu çalıştırma ve satır eşleme | 3 |
| DAO / Repository kalıbı | `persistence.*.Jdbc…Repository` | Veri erişimini soyutlama | 3 |
| İşlemler (ACID, `commit`, `rollback`) | `JdbcTransactionRunner`, `CheckoutService` | Sipariş oluşturmanın bölünmezliği | 3 |
| Bağlantı havuzu | HikariCP `DataSource` | Bağlantıları yeniden kullanma | 3 |
| Eşzamanlılık denetimi | Koşullu `UPDATE`, `version` sütunu | Stok tutarlılığı | 3 |
| Şema sürümleme | Flyway | Tekrarlanabilir veritabanı kurulumu | 3 |

## 10. Web ve ağ programlama

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| HTTP (metotlar, durum kodları, başlıklar) | `web.mvc`, `web.api.v1` | İstek–yanıt modeli | 5 |
| Servlet modeli, filtreler | Spring MVC `DispatcherServlet`, güvenlik filtre zinciri | İsteğin işlenme hattı | 5 |
| MVC kalıbı | `web.mvc` + Thymeleaf şablonları | Sunum ile iş mantığını ayırma | 5 |
| Oturum ve çerez | Giriş oturumu, ziyaretçi sepeti | Durumsuz protokolde durum | 5 |
| REST, JSON | `web.api.v1` | Makineler için arayüz | 6 |
| HTTP istemcisi (`java.net.http`) | `bookstore-web` uçtan uca testleri | API'yi gerçek HTTP ile çağırma | 6 |
| Kimlik doğrulama ve yetkilendirme | `web.security`, `web.config.SecurityConfig` | Rol tabanlı erişim | 5 |
| Uluslararasılaştırma (`Locale`, `ResourceBundle`) | `messages_tr.properties`, `messages_en.properties` | İki dilli arayüz | 5 |

## 11. Yazılım mühendisliği

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| SOLID ilkeleri | Modül sınırları, port–adapter ayrımı | Bağımlılığın tersine çevrilmesi başta olmak üzere | hepsi |
| Katmanlı mimari | Altı Maven modülü | Sorumlulukların ayrılması | 0 |
| Tasarım kalıpları | Aşağıdaki tablo | — | hepsi |
| Birim testi, test çiftleri | Her modülün `src/test` dizini; parametreli testler (`@CsvSource`, `@EnumSource`), sabit `Clock` | Davranışı doğrulama | hepsi |
| Derleme otomasyonu | Maven çok modüllü yapı, Maven Wrapper | Tekrarlanabilir derleme | 0 |
| Anotasyonlar ve yansıma (*reflection*) | Bean Validation, Spring `@Configuration`; `persistence.jdbc` içinde `record` bileşenlerinden satır eşleme | Üst veri ile davranış | 3, 5 |
| Kayıt tutma | SLF4J + Logback | Çalışma zamanı gözlemi | hepsi |
| Javadoc | Tüm `public` türler, `package-info.java` | Belgeleme | hepsi |

### Tasarım kalıpları

| Kalıp | Nerede |
|---|---|
| Strategy | `ShippingPolicy` / `FreeShippingThreshold`, `BookSortField` |
| State | `OrderStatus` geçiş kuralları |
| Observer | `Entity.raise` / `pullEvents` ile `DomainEvent` toplama; yayın ve `Notifier` Faz 2 ve 4'te |
| Builder | `Book.Builder`, `BookSearchCriteria.Builder` |
| Factory method | `Money.of`, `Order.place`, `OrderNumber.of`, `Customer.register`, `Cart.guest` |
| Repository / DAO | `domain.*.…Repository`, `persistence.*.Jdbc…Repository` |
| Adapter | `infrastructure.*`, `web.security` |
| Template method | `persistence.jdbc.JdbcExecutor` |
| Facade | `application` servisleri |
| Dependency injection | `web.config` içindeki `@Bean` tanımları |

## 12. Bilinçli olarak dışarıda bırakılanlar

| Konu | Neden |
|---|---|
| Masaüstü arayüz (Swing / JavaFX) | Proje bir web uygulaması; arayüz Thymeleaf ile yapılıyor. |
| Ham soket programlama (`ServerSocket`) | Ayrı bildirim sunucusu kapsam dışı bırakıldı. Ağ programlama HTTP katmanı ve `java.net.http` istemcisi ile karşılanıyor. |
| Büyük dosya içe/dışa aktarım, raporlama | Kapsam dışı bırakıldı. Dosya G/Ç, kapak görseli depolama ve örnek veri yükleme ile karşılanıyor. |
| JPA / Hibernate | İlk sürümde veri erişimi elle JDBC. Aynı repository arayüzlerine ikinci bir uygulama olarak eklenmesi yol haritasında isteğe bağlı. |
| Java modül sistemi (`module-info.java`) | Yol haritasında isteğe bağlı. |
