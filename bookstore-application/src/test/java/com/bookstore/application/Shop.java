package com.bookstore.application;

import com.bookstore.application.account.AccountService;
import com.bookstore.application.account.CustomerView;
import com.bookstore.application.account.RegistrationCommand;
import com.bookstore.application.admin.OrderAdminService;
import com.bookstore.application.cart.CartRef;
import com.bookstore.application.cart.CartService;
import com.bookstore.application.catalog.CatalogService;
import com.bookstore.application.fake.FakePorts;
import com.bookstore.application.fake.InMemoryBookRepository;
import com.bookstore.application.fake.InMemoryCartRepository;
import com.bookstore.application.fake.InMemoryCategoryRepository;
import com.bookstore.application.fake.InMemoryCustomerRepository;
import com.bookstore.application.fake.InMemoryOrderRepository;
import com.bookstore.application.fake.InMemoryReviewRepository;
import com.bookstore.application.order.CheckoutService;
import com.bookstore.application.order.OrderDetails;
import com.bookstore.application.order.OrderService;
import com.bookstore.application.order.PlaceOrderCommand;
import com.bookstore.application.review.ReviewService;
import com.bookstore.common.money.Money;
import com.bookstore.domain.catalog.Author;
import com.bookstore.domain.catalog.Book;
import com.bookstore.domain.catalog.Category;
import com.bookstore.domain.catalog.Isbn;
import com.bookstore.domain.catalog.Stock;
import com.bookstore.domain.customer.Address;
import com.bookstore.domain.order.OrderStatus;
import com.bookstore.domain.order.PaymentMethod;
import com.bookstore.domain.order.ShippingPolicy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The whole application wired together with in-memory repositories and fake ports, plus helpers
 * for setting up typical situations. Each test creates a fresh shop.
 */
public final class Shop {

    /** A clock stopped at a fixed moment, so tests can assert exact times. */
    public static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-14T10:00:00Z"), ZoneOffset.UTC);

    /** A password that satisfies the password policy. */
    public static final String PASSWORD = "kitap2026";

    // ---------------------------------------------------------------- storage and ports

    public final InMemoryBookRepository books = new InMemoryBookRepository();
    public final InMemoryCategoryRepository categories = new InMemoryCategoryRepository();
    public final InMemoryCustomerRepository customers = new InMemoryCustomerRepository();
    public final InMemoryCartRepository carts = new InMemoryCartRepository();
    public final InMemoryOrderRepository orders = new InMemoryOrderRepository();
    public final InMemoryReviewRepository reviews = new InMemoryReviewRepository();

    public final FakePorts.DirectTransactions transactions = new FakePorts.DirectTransactions();
    public final FakePorts.PrefixPasswordHasher passwordHasher = new FakePorts.PrefixPasswordHasher();
    public final FakePorts.RecordingNotifier notifier = new FakePorts.RecordingNotifier();
    public final FakePorts.ScriptedPaymentGateway payments = new FakePorts.ScriptedPaymentGateway();
    public final FakePorts.CountingOrderNumbers orderNumbers = new FakePorts.CountingOrderNumbers();
    public final ShippingPolicy shipping = ShippingPolicy.standard();

    // ---------------------------------------------------------------- services under test

    public final CatalogService catalog = new CatalogService(books, categories, reviews);
    public final CartService cart = new CartService(books, carts, shipping, transactions);
    public final AccountService accounts = new AccountService(customers, passwordHasher, transactions, CLOCK);
    public final CheckoutService checkout = new CheckoutService(customers, carts, books, orders, orderNumbers,
            payments, shipping, transactions, notifier, CLOCK);
    public final OrderService myOrders = new OrderService(orders);
    public final OrderAdminService orderAdmin = new OrderAdminService(orders, books, transactions, notifier, CLOCK);
    public final ReviewService reviewService = new ReviewService(reviews, books, orders, customers, transactions,
            CLOCK);

    // ---------------------------------------------------------------- catalog

    /** "Literature", with the subcategories "Novel" and "Poetry". */
    public final Category literature;
    public final Category novels;
    public final Category poetry;
    /** "Science", without subcategories. */
    public final Category science;

    private final AtomicInteger isbnCounter = new AtomicInteger();

    public Shop() {
        literature = categories.save(Category.root("Literature"));
        novels = categories.save(literature.addSubcategory("Novel"));
        poetry = categories.save(literature.addSubcategory("Poetry"));
        science = categories.save(Category.root("Science"));
    }

    /** Stores an author with a new id. */
    public Author author(String name) {
        Author author = Author.create(name);
        author.markPersisted(500 + isbnCounter.incrementAndGet(), 0);
        return author;
    }

    /** Stores an active novel. */
    public Book book(String title, String price, int stock) {
        return book(title, price, stock, novels, author("Author of " + title));
    }

    /** Stores an active book in a category, by an author. */
    public Book book(String title, String price, int stock, Category category, Author author) {
        return books.save(Book.builder()
                .isbn(nextIsbn())
                .title(title)
                .author(author)
                .category(category)
                .price(Money.of(price))
                .stock(Stock.of(stock))
                .build());
    }

    // ---------------------------------------------------------------- customers and orders

    /** Registers a customer with one saved address and returns the customer's id. */
    public long customer(String email) {
        CustomerView registered = accounts.register(new RegistrationCommand(email, "Ayşe", "Yılmaz", PASSWORD));
        accounts.addAddress(registered.id(), "Ev", address(), true);
        return registered.id();
    }

    /** Returns the id of a customer's default address. */
    public long defaultAddressId(long customerId) {
        return accounts.profile(customerId).addresses().stream()
                .filter(CustomerView.AddressView::isDefault)
                .findFirst()
                .orElseThrow()
                .id();
    }

    /** Puts a book in a customer's cart and pays by card. */
    public OrderDetails buy(long customerId, Book book, int quantity) {
        cart.add(CartRef.customer(customerId), book.id(), quantity);
        return checkout.placeOrder(new PlaceOrderCommand(customerId, defaultAddressId(customerId),
                PaymentMethod.CREDIT_CARD));
    }

    /** Buys a book and has the order shipped and delivered, which allows the customer to review it. */
    public OrderDetails buyAndReceive(long customerId, Book book) {
        OrderDetails order = buy(customerId, book, 1);
        orderAdmin.changeStatus(order.orderNumber(), OrderStatus.SHIPPED);
        return orderAdmin.changeStatus(order.orderNumber(), OrderStatus.DELIVERED);
    }

    /** A valid delivery address. */
    public static Address address() {
        return new Address("Ayşe Yılmaz", "+90 532 000 00 00", "Bağdat Cad. No: 1", "Kadıköy", "İstanbul", "34710");
    }

    private Isbn nextIsbn() {
        String body = "978" + String.format("%09d", isbnCounter.incrementAndGet());
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            sum += (body.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
        }
        return new Isbn(body + (10 - sum % 10) % 10);
    }
}
