# Book Store

Java ile yazılmış, katmanlı ve çok modüllü bir çevrimiçi kitap mağazası.

Projenin iki amacı var: çalışan bir e-ticaret uygulaması ortaya koymak.

> **Durum:** Faz 3 tamamlandı — domain modeli, kullanım senaryoları ve JDBC ile veritabanı katmanı testleriyle hazır. Altyapı servisleri ve web sayfaları henüz yok. Sıradaki adımlar için [yol haritasına](docs/ROADMAP.md) bakın.

## Dokümanlar

| Doküman | İçerik |
|---|---|
| [SPEC.md](docs/SPEC.md) | Sistem ne yapar: aktörler, gereksinimler, kapsam dışı kalanlar |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Sistem nasıl kurulu: modüller, katmanlar, veritabanı, güvenlik, test |
| [CURRICULUM-MAP.md](docs/CURRICULUM-MAP.md) | Hangi müfredat konusu projenin neresinde uygulanıyor |
| [DECISIONS.md](docs/DECISIONS.md) | Mimari kararlar ve gerekçeleri |
| [ROADMAP.md](docs/ROADMAP.md) | Geliştirme fazları |

## Mimari özeti

```
bookstore-web             Spring Boot, MVC, REST, Security, Thymeleaf
   ├── bookstore-persistence      JDBC, Flyway, MySQL
   ├── bookstore-infrastructure   dosya, thread, önbellek, BCrypt
   └── bookstore-application      kullanım senaryoları, portlar
          └── bookstore-domain    varlıklar, iş kuralları
                 └── bookstore-common   değer nesneleri, hatalar
```

İş kuralları (`common`, `domain`, `application`) saf Java'dır ve hiçbir framework'e bağlı değildir. Spring yalnızca `bookstore-web` modülünde kullanılır.

## Teknolojiler

Java 21 · Maven · Spring Boot 4.1 · Thymeleaf · MySQL 8.4 · Flyway · JDBC · JUnit 5

## Gereksinimler

- JDK 21

Maven kurmanız gerekmez; depo Maven Wrapper içerir.

## Derleme ve test

```bash
./mvnw verify
```

Windows komut satırında `./mvnw` yerine `mvnw.cmd` kullanın.

## Çalıştırma

```bash
./mvnw -pl bookstore-web -am install -DskipTests
```

```bash
./mvnw -pl bookstore-web spring-boot:run
```

Uygulama <http://localhost:8080> adresinde açılır.
