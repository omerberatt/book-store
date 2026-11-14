# Yol Haritası

Fazlar müfredatın doğal sırasını izler: önce saf Java, sonra veritabanı, sonra web. Her faz sonunda `./mvnw verify` geçer ve uygulama çalışır durumdadır.

| Faz | Durum | Başlık | Çıktı | Ana müfredat konuları |
|---|---|---|---|---|
| **0** | ✅ | İskelet | Altı modül, derleme hattı, dokümanlar, açılan boş web uygulaması | Maven, katmanlı mimari |
| **1** | ✅ | Domain modeli | `common` ve `domain`: değer nesneleri, varlıklar, sipariş durum makinesi, kargo kuralı, arama ölçütü, repository arayüzleri; 139 birim testi | OOP, `record`, `enum`, `sealed`, istisnalar, generics, koleksiyonlar |
| **2** | ✅ | Kullanım senaryoları | `application`: katalog, sepet, hesap, sipariş verme, sipariş geçmişi, sipariş durum yönetimi ve yorum servisleri; beş port; bellek içi repository'lerle 54 iş akışı testi | Lambda, Stream, `Optional`, sealed + `switch`, test çiftleri |
| **3** | ✅ | Kalıcılık | `persistence`: Flyway şeması, JDBC araç seti, yedi repository ve sipariş numarası sayacı, işlem yönetimi; H2 üzerinde 59 veritabanı testi (geri alma ve eşzamanlılık dahil) | SQL, JDBC, işlemler, DAO, `ThreadLocal`, yansıma |
| **4** | | Altyapı | `infrastructure`: dosya depolama, arka plan bildirimleri, önbellek, parola özetleme, örnek veri yükleme | G/Ç, NIO.2, thread'ler, eşzamanlı koleksiyonlar |
| **5** | | Web arayüzü | `web`: Thymeleaf sayfaları (katalog, arama, sepet, sipariş, hesap, yorum), giriş/kayıt, iki dilli arayüz | HTTP, MVC, oturum, güvenlik, i18n |
| **6** | | REST API | `/api/v1` uçları, Problem Details hata biçimi, OpenAPI belgesi, `HttpClient` ile uçtan uca testler | REST, JSON, HTTP istemcisi |
| **7** | | Yönetim paneli | `CatalogAdminService`, `UserAdminService`, `ReviewAdminService` ve `FileStorage` portu; kitap/yazar/kategori yönetimi, kapak yükleme, sipariş ve kullanıcı yönetimi, yorum denetimi ekranları | Yetkilendirme, dosya yükleme |
| **8** | | Sağlamlaştırma | MySQL üzerinde doğrulama, performans hedefi (NFR-9), kapsam hedefi (NFR-6), CI | Profil oluşturma, test kapsamı |

### Faz 2 notları

Faz 1'den devredilen dört madde Faz 2'de tamamlandı:

- **CRT-5:** `Cart.reconcile` ve `CartService`; fiyat değişikliği, düşen stok ve satıştan kalkan kitap için uyarı.
- **REV-2 / REV-3:** `ReviewService`; teslim şartı, tek yorum, puan özetinin yorumla aynı işlemde güncellenmesi.
- **ACC-1:** `AccountService`; e-posta benzersizliği büyük/küçük harf duyarsız.
- **ORD-10:** `OrderNumberSequence` portu; üretim uygulaması Faz 3'te veritabanında.

Bilinen sınır: bellek içi test çiftleri işlemi geri alamaz. "Hata olunca hiçbir şey kaydedilmez" davranışının veritabanında gerçekten sağlandığı Faz 3'te JDBC testleriyle doğrulanacak.

### Faz 3 notları

- Faz 2'deki sınır kapandı: `CheckoutWithDatabaseTest`, reddedilen ödemede stok, sipariş, sepet ve sipariş numarasının geri alındığını ve son kopyayı aynı anda isteyen iki müşteriden yalnızca birinin aldığını gerçek işlemlerle doğruluyor.
- Testler yalnızca H2'nin MySQL modunda çalıştı. Şemanın gerçek MySQL 8.4 üzerinde doğrulanması Faz 8'de (makinede MySQL ve Docker kurulu değil).
- HikariCP bağlantı havuzu ve Flyway'in uygulama açılışında çalıştırılması Faz 5'te web modülünde kurulacak.

## İsteğe bağlı ekler

Çekirdek tamamlandıktan sonra, aynı mimariye dokunmadan eklenebilecekler:

- JPA / Hibernate ile ikinci bir `persistence` uygulaması (aynı repository arayüzleri, profil ile seçim)
- Java modül sistemi (`module-info.java`) ile `common` ve `domain` sınırlarını JVM seviyesinde zorlama
- Satış raporları ve CSV/JSON içe-dışa aktarım
- TCP soket tabanlı canlı bildirim servisi
