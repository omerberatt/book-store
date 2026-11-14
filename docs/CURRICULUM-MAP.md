# Müfredat Eşlemesi

Lisans seviyesi Java müfredatındaki her konunun projede **nerede** ve **ne için** uygulanacağını gösterir. Amaç, konuların yapay örneklerle değil gerçek bir ihtiyaç üzerinden kullanılmasıdır.

"Faz" sütunu [ROADMAP.md](ROADMAP.md) içindeki aşamayı gösterir. Sınıf adları plandır; uygulama sırasında değişirse bu tablo güncellenir.

## 1. Dil temelleri

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| İlkel türler, operatörler, kontrol yapıları | `common.validation.Guard`, `domain.catalog.Isbn` | Ön koşul denetimleri, ISBN-13 kontrol basamağı hesabı | 1 |
| `String`, `StringBuilder`, metin işlemleri | `common.text.TextNormalizer`, `domain.order.OrderNumber` | Türkçe duyarlı arama anahtarı, sipariş numarası biçimi | 1 |
| Diziler | `domain.catalog.Isbn`, `domain.review.RatingDistribution` | Basamak dizisi, 1–5 puan sayaçları | 1 |
| Düzenli ifadeler (`regex`) | `domain.customer.Email`, parola kuralı | Biçim doğrulama | 1 |
| `BigDecimal`, sayısal hassasiyet | `common.money.Money` | Para hesabı ve yuvarlama | 1 |
| Tarih-saat API'si (`java.time`) | `domain.order.Order`, `domain.review.Review` | Sipariş ve yorum zamanı, `Clock` ile test edilebilir zaman | 1 |

## 2. Nesne yönelimli programlama

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| Sınıf, nesne, kapsülleme | `domain.catalog.Book`, `domain.cart.Cart` | Durumu yalnızca kendi metotlarıyla değişen varlıklar | 1 |
| Kurucular, `static` fabrika metotları | `Money.of(…)`, `Order.place(…)` | Geçersiz nesne oluşturmayı engelleme | 1 |
| Değişmezlik, `record` | `Money`, `Isbn`, `Email`, `Rating`, komut ve sonuç nesneleri | Değer nesneleri | 1 |
| `equals` / `hashCode` / `toString` | Tüm varlıklar (kimliğe göre), değer nesneleri (değere göre) | Koleksiyonlarda doğru davranış | 1 |
| Kalıtım, `abstract` sınıf | `common.error.BookstoreException` hiyerarşisi, `domain.event.DomainEvent` | Ortak davranışı paylaşma | 1 |
| Arayüz, çok biçimlilik | `BookRepository`, `ShippingPolicy`, `application.port.*` | Uygulamadan bağımsız sözleşme | 1 |
| `enum` (alan ve davranışla) | `OrderStatus`, `Role`, `PaymentMethod`, `SortOrder` | Durum makinesi, sabit kümeler | 1 |
| `sealed` tür ve örüntü eşleme (`switch`) | `domain.event.DomainEvent` alt türleri, `application` sonuç türleri | Kapalı tür hiyerarşisinde eksiksiz `switch` | 1 |
| İç sınıf, anonim sınıf | `Cart` içindeki yineleyici, `Book.Builder` | Kapsayan sınıfın durumuna erişim | 1 |
| `Comparable`, `Comparator` | `Money`, `Rating`, katalog sıralama seçenekleri | Doğal ve özel sıralama | 1 |

## 3. İstisna yönetimi

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| Özel istisna hiyerarşisi | `common.error.*` | İş hatalarını türle ayırma | 1 |
| Denetlenen / denetlenmeyen istisna | `persistence` (`SQLException` sarma), `infrastructure.storage` (`IOException` sarma) | Teknik hatayı sınırda anlamlı hataya çevirme | 3 |
| `try-with-resources`, `finally` | Tüm JDBC ve dosya kodu | Kaynakların kapatılması | 3 |
| Merkezi hata işleme | `web.api.v1.ApiExceptionHandler` | İstisnayı HTTP yanıtına eşleme | 5 |

## 4. Generics

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| Generic sınıf ve arayüz | `common.paging.Page<T>`, `persistence.jdbc.RowMapper<T>`, `infrastructure.cache.ConcurrentCache<K,V>` | Türden bağımsız yeniden kullanım | 1, 3 |
| Generic metot, sınırlı tür (`<T extends …>`) | `Page.map(Function<? super T, ? extends R>)`, `Guard` metotları | Tür güvenli dönüşüm | 1 |
| Joker türler (`? extends`, `? super`) | `Page.map`, sıralama yardımcıları | PECS kuralı | 1 |

## 5. Koleksiyonlar, veri yapıları ve algoritmalar

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| `List`, `Set`, `Map` ve uygulamaları | `Cart` (`LinkedHashMap` ile satırlar), `Book` (`Set<Author>`), bellek içi repository'ler | Uygun yapıyı seçme | 1 |
| `Queue`, `Deque` | `infrastructure.notification` (bildirim kuyruğu), kategori ağacında genişlik öncelikli gezinme | FIFO işleme | 4 |
| Ağaç yapısı ve özyineleme | `domain.catalog.Category` | Alt kategorileri toplama | 1 |
| `Iterable` / `Iterator` | `Cart` | `for-each` ile gezilebilir sepet | 1 |
| Sıralama ve arama | Bellek içi katalog araması, `Comparator` zincirleri | Çok ölçütlü sıralama | 2 |
| Karmaşıklık analizi (Big-O) | Bellek içi repository'lerde `Map` indeksleri, `ConcurrentCache` | Doğrusal tarama yerine sabit zamanlı erişim | 2 |
| Değiştirilemez koleksiyonlar | Varlıkların dışarı verdiği listeler (`List.copyOf`) | İç durumu koruma | 1 |

## 6. Fonksiyonel programlama

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| Lambda, metot referansı | `TransactionRunner.inTransaction(…)`, `RowMapper` | Davranışı parametre olarak verme | 2, 3 |
| Fonksiyonel arayüzler | `ShippingPolicy`, `RowMapper<T>`, `java.util.function.*` | Tek metotlu sözleşmeler | 1 |
| Stream API | Sepet toplamı, puan ortalaması ve dağılımı, bellek içi arama | Bildirimsel veri işleme | 1, 2 |
| `Optional` | Tüm `findBy…` metotları | `null` yerine açık yokluk | 1 |
| `Collectors` (gruplama, özetleme) | `RatingDistribution`, yönetim özeti | Gruplayarak sayma | 2 |

## 7. Girdi / çıktı

| Konu | Nerede | Ne için | Faz |
|---|---|---|---|
| NIO.2 (`Path`, `Files`) | `infrastructure.storage.LocalFileStorage` | Kapak görsellerini kaydetme ve okuma | 4 |
| Bayt ve karakter akışları, kodlama | `LocalFileStorage`, `infrastructure.seed.DemoDataLoader` | Görsel (bayt) ve CSV (UTF-8 metin) okuma | 4 |
| Metin ayrıştırma | `DemoDataLoader` | CSV satırlarından örnek katalog üretme | 4 |
| Nesne serileştirme (`Serializable`) | `domain.cart.Cart` | Ziyaretçi sepetinin HTTP oturumunda saklanması | 5 |
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
| Birim testi, test çiftleri | Her modülün `src/test` dizini | Davranışı doğrulama | hepsi |
| Derleme otomasyonu | Maven çok modüllü yapı, Maven Wrapper | Tekrarlanabilir derleme | 0 |
| Anotasyonlar ve yansıma (*reflection*) | Bean Validation, Spring `@Configuration`; `persistence.jdbc` içinde `record` bileşenlerinden satır eşleme | Üst veri ile davranış | 3, 5 |
| Kayıt tutma | SLF4J + Logback | Çalışma zamanı gözlemi | hepsi |
| Javadoc | Tüm `public` türler, `package-info.java` | Belgeleme | hepsi |

### Tasarım kalıpları

| Kalıp | Nerede |
|---|---|
| Strategy | `ShippingPolicy`, katalog sıralama seçenekleri |
| State | `OrderStatus` geçiş kuralları |
| Observer | `DomainEvent` yayını ve `Notifier` |
| Builder | `Book.Builder`, `BookSearchQuery` |
| Factory method | `Money.of`, `Order.place`, `OrderNumber.next` |
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
