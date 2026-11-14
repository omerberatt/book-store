# Mimari Kararlar

Her karar kısa bir kayıt olarak tutulur: ne seçildi, neden, neyi göze aldık. Bir karar değişirse eski kayıt silinmez; "Yerini aldı: K-xx" notu düşülür.

## K-01 — Çok modüllü katmanlı monolit

**Karar:** Tek uygulama, altı Maven modülü: `common`, `domain`, `application`, `persistence`, `infrastructure`, `web`.

**Neden:** Projenin amacı müfredatın tamamını uygulamak. İş kurallarını saf Java modüllerinde tutmak, temel konuların (OOP, koleksiyonlar, JDBC, thread) framework arkasına saklanmasını engeller. Modül sınırları bağımlılık kuralını derleme zamanında zorlar.

**Göze alınan:** Tek modüllü bir Spring Boot projesine göre daha fazla dosya ve bağlama kodu.

**Elenen seçenekler:** Tek modül Spring Boot (düşük seviye konular görünmez kalır), saf Jakarta EE (modern framework deneyimi olmaz), mikroservis (müfredatın ötesinde karmaşıklık).

## K-02 — İş katmanlarında framework yok

**Karar:** `common`, `domain` ve `application` modülleri yalnızca JDK'ya (ve SLF4J API'sine) bağımlıdır. Spring anotasyonu taşımazlar; `web` modülündeki `@Configuration` sınıfları onları `@Bean` olarak oluşturur.

**Neden:** İş kuralları veritabanı ve web sunucusu olmadan, milisaniyeler içinde test edilebilir. Bağımlılık enjeksiyonunun bir framework özelliği değil, kurucu parametresi olduğu görünür.

**Göze alınan:** Her servis için elle bir `@Bean` metodu yazılır.

## K-03 — Veri erişimi elle JDBC ile

**Karar:** Repository arayüzleri `persistence` modülünde `PreparedStatement` kullanan sınıflarla uygulanır. İşlem yönetimi kendi `JdbcTransactionRunner` sınıfımızdadır.

**Neden:** SQL, bağlantı, işlem ve satır eşleme müfredatın çekirdeğidir; ORM bunları gizler.

**Göze alınan:** JPA'ya göre daha fazla kod. Repository arayüzleri teknolojiden bağımsız olduğu için ileride aynı arayüzlere JPA tabanlı ikinci bir uygulama eklenebilir.

## K-04 — MySQL; testlerde H2

**Karar:** Çalışma zamanı veritabanı MySQL 8.4. Otomatik testler ve `h2` profili, MySQL uyumluluk modunda bellek içi H2 kullanır. Şema Flyway ile yönetilir.

**Neden:** MySQL tercih edildi. Geliştirme makinesinde Docker bulunmadığı için testlerin gerçek bir MySQL'e ihtiyaç duymadan çalışması gerekiyor.

**Göze alınan:** H2'nin MySQL modu birebir aynı değildir. Migration dosyaları her iki veritabanında çalışan ortak SQL ile yazılır; MySQL'e özgü bir özellik gerekirse ayrıca MySQL üzerinde doğrulanır.

## K-05 — Thymeleaf + REST API

**Karar:** Arayüz sunucu tarafında Thymeleaf ile üretilir. Aynı işlemler `/api/v1` altında JSON olarak da sunulur. İki katman da aynı application servislerini çağırır.

**Neden:** Tüm proje Java ekosisteminde kalır, ayrı bir ön yüz derleme hattı gerekmez. REST API, HTTP ve JSON konularını ve servis katmanının sunumdan bağımsızlığını gösterir.

**Göze alınan:** Her özellik için iki giriş noktası (controller ve REST controller) yazılır.

## K-06 — Web'de oturum, API'de HTTP Basic

**Karar:** Web arayüzü form girişi ve sunucu tarafı oturum kullanır. REST API oturumsuzdur ve HTTP Basic ile doğrular.

**Neden:** İkisi de Spring Security'nin hazır mekanizmalarıdır ve oturumlu/oturumsuz farkını açıkça gösterir. JWT/OAuth2 kapsam dışıdır.

**Göze alınan:** HTTP Basic yalnızca HTTPS arkasında güvenlidir; gerçek bir yayında TLS zorunludur.

## K-07 — Java 21, Spring Boot 4.1

**Karar:** Java 21 (LTS) ve Spring Boot 4.1 serisi.

**Neden:** Java 21; `record`, `sealed`, örüntü eşleme ve sanal thread'leri kararlı olarak içerir ve araç desteği en geniş LTS sürümüdür. Spring Boot 4.1 güncel kararlı seridir.

## K-08 — Kod İngilizce, doküman Türkçe

**Karar:** Sınıf, metot, değişken, tablo adları ve commit mesajları İngilizce. `docs/` altındaki dokümanlar ve README Türkçe.

**Neden:** Java ekosisteminin dili İngilizce; dokümanların ana dilde olması okumayı ve öğrenmeyi kolaylaştırır.

## K-09 — Kimliği repository atar

**Karar:** Varlıkların kimliği veritabanının otomatik artan `id` değeridir. Yeni bir varlık kimliksiz oluşturulur; repository kaydettikten sonra `Entity.markPersisted(id, version)` çağırır. Kimlik bir kez atanınca değiştirilemez. `equals` kimliğe göre çalışır; `hashCode` sınıfa göre sabittir, böylece varlık kaydedilmeden önce bir `HashSet` içine konup kaydedildikten sonra da bulunabilir.

**Neden:** MySQL'in `AUTO_INCREMENT` yapısıyla doğal uyum. Ayrı bir kimlik üreteci veya UUID gerektirmez.

**Göze alınan:** Kaydedilmemiş bir varlığın `id()` metodu istisna fırlatır; kod, kimliğe ihtiyaç duymadan önce varlığın kaydedildiğinden emin olmalıdır. `markPersisted` herkese açıktır, çünkü repository'ler başka bir modüldedir; yalnızca repository'ler çağırmalıdır.

## K-10 — Puan özeti kitapta tutulur

**Karar:** Kitabın yorum sayısı ve toplam puanı `Book.ratings()` (`RatingSummary`) olarak kitapla birlikte saklanır. Yorum yazılınca, düzenlenince veya silinince application katmanı aynı işlem içinde kitabı da günceller.

**Neden:** Katalog listesi ortalama puanı göstermek ve puana göre sıralamak zorunda (REV-5, SRC-3). Her listelemede yorum tablosunu gruplamak gereksiz yük olur.

**Göze alınan:** Veri iki yerde durur (yorumlar ve özet). Tutarlılık işlem sınırıyla sağlanır; `RatingSummary` imkânsız toplamları (ör. 2 yorum, 11 puan) reddeder.

## K-11 — Ödeme yöntemine göre ilk sipariş durumu

**Karar:** Kartla ödemede kart sipariş sırasında (simülasyon olarak) çekilir ve sipariş `PAID` olur. Havale ve kapıda ödemede sipariş `CREATED` kalır; ödemeyi yönetici kaydeder (`CREATED → PAID`). Kart reddedilirse sipariş hiç oluşmaz.

**Neden:** Spesifikasyondaki durum makinesi (ORD-6) `CREATED → PAID` adımını içeriyor; bu adımın gerçek bir anlamı ancak ödemenin sonradan geldiği yöntemlerde var. Reddedilen kart için "iptal edilmiş sipariş" kaydı bırakmak müşterinin geçmişini gereksiz kalabalıklaştırır.

**Göze alınan:** Kart çekildikten sonra kayıt başarısız olursa gerçek bir sistemde iade gerekir. Ödeme simülasyon olduğu için bu durum ele alınmadı.

## K-12 — Sipariş öncesi sepet kontrolü ayrı işlemde

**Karar:** Sipariş vermeden önce sepet ayrı bir işlemde güncel katalogla karşılaştırılır. Bir şey değiştiyse düzeltilmiş sepet kaydedilir ve sipariş durdurulur. Sipariş işleminde aynı kontrol bir kez daha yapılır.

**Neden:** Müşteri, sepette görmediği bir fiyatı ödememeli (CRT-5). Düzeltmenin kalıcı olması için sipariş işleminden ayrı commit edilmesi gerekir; aynı işlemde yapılsaydı, hata fırlatınca düzeltme de geri alınırdı.

**Göze alınan:** İki işlem arasında küçük bir pencere var; ikinci kontrol bu pencereyi kapatıyor.

## K-13 — Servisler okuma modeli döner

**Karar:** Application servisleri domain varlıklarını dışarı vermez; `record` kopyaları döner. Değişmez domain değer nesneleri (`Money`, `Address`, `OrderLine`) bu kopyaların içinde doğrudan kullanılabilir.

**Neden:** Web katmanı bir `Book` nesnesini alıp `changePrice` çağıramamalı. Kopyalar ayrıca şablonların ve JSON'un ihtiyaç duyduğu biçimi tek yerde toplar.

**Göze alınan:** Her ekran için küçük bir `record` sınıfı daha.

## K-14 — Stok tutarlılığı iyimser kilitlemeyle

**Karar:** Kitap, sipariş, müşteri ve yorum satırları `version` sütunu taşır. Kayıt `UPDATE … WHERE id = ? AND version = ?` ile yapılır; hiçbir satır değişmezse `ConflictException` (`concurrent-modification`) fırlar ve işlem geri alınır. Stok ayrıca `CHECK (stock >= 0)` ile korunur.

**Neden:** Mimari taslağı stoğu `stock = stock - ?` biçiminde fark üzerinden düşmeyi öneriyordu. Bu, domain'in stok kuralını SQL'e taşımayı ve kitabın geri kalanını ayrı kaydetmeyi gerektirirdi. Sürüm kontrolü bütün varlıklar için tek ve genel bir mekanizma; son kopyayı aynı anda isteyen iki siparişten birinin reddedildiği test ile doğrulandı.

**Göze alınan:** Aynı kitabı aynı anda alan iki müşteriden biri, stok yeterli olsa bile "kayıt değişti, tekrar deneyin" hatası alabilir. Yoğun bir mağazada fark üzerinden güncellemeye veya otomatik yeniden denemeye geçmek gerekebilir.

## K-15 — Arama için normalleştirilmiş anahtar sütunları

**Karar:** `books.title_key` ve `authors.name_key`, kaydedilirken `TextNormalizer.searchKey` ile üretilir. Arama metni de aynı şekilde normalleştirilip `LIKE` ile karşılaştırılır.

**Neden:** MySQL ve H2'nin harf karşılaştırma kuralları (*collation*) Türkçe `ı/I` ve `i/İ` dönüşümlerini birbirinden farklı ele alır. Normalleştirmeyi Java'da tek yerde yapmak, iki veritabanında ve bellek içi testlerde aynı sonucu verir.

**Göze alınan:** Veri iki kez durur (başlık ve anahtarı). `%kelime%` biçimindeki `LIKE` indeks kullanamaz; NFR-9'daki 10.000 kitaplık hedef için bu yeterli, çok daha büyük bir katalogda tam metin araması gerekir.
