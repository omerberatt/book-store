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
