# Yol Haritası

Fazlar müfredatın doğal sırasını izler: önce saf Java, sonra veritabanı, sonra web. Her faz sonunda `./mvnw verify` geçer ve uygulama çalışır durumdadır.

| Faz | Durum | Başlık | Çıktı | Ana müfredat konuları |
|---|---|---|---|---|
| **0** | ✅ | İskelet | Altı modül, derleme hattı, dokümanlar, açılan boş web uygulaması | Maven, katmanlı mimari |
| **1** | ✅ | Domain modeli | `common` ve `domain`: değer nesneleri, varlıklar, sipariş durum makinesi, kargo kuralı, arama ölçütü, repository arayüzleri; 139 birim testi | OOP, `record`, `enum`, `sealed`, istisnalar, generics, koleksiyonlar |
| **2** | | Kullanım senaryoları | `application` servisleri ve portlar; bellek içi repository'lerle uçtan uca iş akışı testleri (katalog, sepet, sipariş, yorum) | Lambda, Stream, `Optional`, test çiftleri |
| **3** | | Kalıcılık | `persistence`: Flyway şeması, JDBC repository'leri, işlem yönetimi; H2 üzerinde veritabanı testleri | SQL, JDBC, işlemler, DAO |
| **4** | | Altyapı | `infrastructure`: dosya depolama, arka plan bildirimleri, önbellek, parola özetleme, örnek veri yükleme | G/Ç, NIO.2, thread'ler, eşzamanlı koleksiyonlar |
| **5** | | Web arayüzü | `web`: Thymeleaf sayfaları (katalog, arama, sepet, sipariş, hesap, yorum), giriş/kayıt, iki dilli arayüz | HTTP, MVC, oturum, güvenlik, i18n |
| **6** | | REST API | `/api/v1` uçları, Problem Details hata biçimi, OpenAPI belgesi, `HttpClient` ile uçtan uca testler | REST, JSON, HTTP istemcisi |
| **7** | | Yönetim paneli | Kitap/yazar/kategori yönetimi, kapak yükleme, sipariş ve kullanıcı yönetimi, yorum denetimi | Yetkilendirme, dosya yükleme |
| **8** | | Sağlamlaştırma | MySQL üzerinde doğrulama, performans hedefi (NFR-9), kapsam hedefi (NFR-6), CI | Profil oluşturma, test kapsamı |

### Faz 2'ye devredilenler

Faz 1'de domain kuralları yazıldı; aşağıdakiler bilinçli olarak kullanım senaryolarına bırakıldı:

- **CRT-5** (sepeti güncel fiyat ve stokla yeniden doğrulama ve kullanıcıyı uyarma): kitapları yükleyip sepetle karşılaştırmak `CartService` işi.
- **REV-2** (yalnızca teslim alınmış kitaba yorum): `OrderRepository.hasDeliveredBook` ile `ReviewService` denetler.
- **REV-3** (bir müşteri bir kitaba tek yorum) ve yorum değişince `Book.ratings()` güncellemesi: `ReviewService`.
- **ACC-1** (e-posta benzersizliği): `CustomerRepository.existsByEmail` ile `AccountService` denetler.
- **ORD-10** sıra numarası üretimi: `OrderNumber` biçimi hazır; yıllık sayacı bir port sağlayacak.

## İsteğe bağlı ekler

Çekirdek tamamlandıktan sonra, aynı mimariye dokunmadan eklenebilecekler:

- JPA / Hibernate ile ikinci bir `persistence` uygulaması (aynı repository arayüzleri, profil ile seçim)
- Java modül sistemi (`module-info.java`) ile `common` ve `domain` sınırlarını JVM seviyesinde zorlama
- Satış raporları ve CSV/JSON içe-dışa aktarım
- TCP soket tabanlı canlı bildirim servisi
