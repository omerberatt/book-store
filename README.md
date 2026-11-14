# Book Store

Java ile yazılmış, modüler bir çevrimiçi kitap mağazası.

Projenin amacı: çalışan bir e-ticaret uygulaması ortaya koymak.

> **Durum:** Faz 0–3 tamamlandı: domain modeli, kullanım senaryoları ve JDBC ile veritabanı katmanı testleriyle hazır. Kodun hedef mimariye *(iş alanına göre modüller, Jakarta EE, PostgreSQL)* taşınması Faz 4'te yapılacak. HTTP katmanı ve web sayfaları henüz yok. Ayrıntılar [yol haritasında](docs/ROADMAP.md).

## Dokümanlar

| Doküman | İçerik |
| --- | --- |
| [SPEC.md](docs/SPEC.md) | Sistem ne yapar: aktörler, gereksinimler, kapsam dışı kalanlar |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Hedef mimari: modüller, paketler, uygulama sunucusu, veritabanı, test |
| [adr/](docs/adr/README.md) | Mimari karar kayıtları *(ADR-0001 – ADR-0016)* |
| [CURRICULUM-MAP.md](docs/CURRICULUM-MAP.md) | Ders notlarının her bölümü projenin neresinde |
| [ROADMAP.md](docs/ROADMAP.md) | Fazlar ve sıradaki adımlar |

## Hedef mimari

```
shared-kernel                 Money, Specification, DomainException
catalog | customer | sales | review     iş modülleri; her birinde api, application, domain, persistence, rest
app                           WAR: Payara Micro 7, CDI bağlama, açılış, HTML/JS arayüz
quality/architecture-tests    ArchUnit kuralları
```

İş kuralları domain paketlerinde, yalnızca JDK ile yazılır. Modüller birbirini yalnızca `api` paketleri üzerinden kullanır; bu kurallar ArchUnit testleriyle korunur.

## Teknolojiler

Java 21 · Maven · Jakarta EE 11 · MicroProfile 6.1 · Payara Micro 7 · PostgreSQL 17 · Flyway · JDBC · JUnit · AssertJ · ArchUnit · Testcontainers

*(Bugünkü kod hâlâ Faz 0'daki Spring Boot iskeletini ve H2'yi kullanıyor; geçiş Faz 4–6'da.)*

## Gereksinimler

- JDK 21

Maven kurmanız gerekmez; depo Maven Wrapper içerir. Docker zorunlu değildir; varsa veritabanı testleri gerçek PostgreSQL üzerinde de çalışır *(Faz 4'ten itibaren)*.

## Derleme ve test

Git Bash, macOS veya Linux:

```bash
./mvnw verify
```

Windows komut satırı (`cmd`):

```cmd
mvnw.cmd verify
```

## Geliştirme akışı

Gitflow kullanılır *([ADR-0001](docs/adr/ADR-0001.md))*: işler `develop`'tan açılan `feature/<ad>` dallarında yapılır ve Pull Request ile `develop`'a birleştirilir. Yayınlar `main` üzerinde `v2026.11.1` biçiminde etiketlenir.
