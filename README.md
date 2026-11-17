# Book Store

Java ile yazılmış, modüler bir çevrimiçi kitap mağazası.

Projenin amacı: çalışan bir e-ticaret uygulaması ortaya koymak.

> **Durum:** Faz 0–3 ve Faz 4.1–4.5 tamamlandı. Kod iş alanına göre modüllerde *(katalog, müşteri, satış, yorum)*; modüller birbirini yalnızca `api` paketleriyle kullanıyor. Bu kural ve mimari kararların diğer kuralları ArchUnit testleriyle korunuyor. Jakarta EE, PostgreSQL, HTTP katmanı ve web sayfaları henüz yok. Ayrıntılar [yol haritasında](docs/ROADMAP.md).

## Dokümanlar

| Doküman | İçerik |
| --- | --- |
| [SPEC.md](docs/SPEC.md) | Sistem ne yapar: aktörler, gereksinimler, kapsam dışı kalanlar |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | Hedef mimari: modüller, paketler, uygulama sunucusu, veritabanı, test |
| [adr/](docs/adr/README.md) | Mimari karar kayıtları *(ADR-0001 – ADR-0018)* |
| [CURRICULUM-MAP.md](docs/CURRICULUM-MAP.md) | Ders notlarının her bölümü projenin neresinde |
| [ROADMAP.md](docs/ROADMAP.md) | Fazlar ve sıradaki adımlar |

## Hedef mimari

```
shared-kernel                 Money, Address, Entity, hatalar
shared-jdbc                   JDBC araç seti, modül başına migration'lar
catalog | customer | sales | review     iş modülleri; her birinde api, application, domain, persistence, rest
app                           WAR: Payara Micro 7, CDI bağlama, açılış, HTML/JS arayüz
quality/architecture-tests    ArchUnit kuralları
```

İş kuralları domain paketlerinde, yalnızca JDK ile yazılır. Modüller birbirini yalnızca `api` paketleri üzerinden kullanır; bu kurallar ArchUnit testleriyle korunur.

## Teknolojiler

Java 21 · Maven · Jakarta EE 11 · MicroProfile 6.1 · Payara Micro 7 · PostgreSQL 17 · Flyway · JDBC · JUnit · AssertJ · ArchUnit · Testcontainers

*(Testler H2'nin PostgreSQL modunda çalışıyor; yerel PostgreSQL için `docker compose up -d`. Jakarta EE geçişi Faz 6'da.)*

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

Değişiklikler doğrudan `main` dalına gönderilir *([ADR-0017](docs/adr/ADR-0017.md))*; `main`'e giden her commit'te `./mvnw verify` geçer. Yayınlar `main` üzerinde `v2026.11.1` biçiminde etiketlenir.
