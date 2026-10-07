package dev.jagt.orchestrator.mastereval;

import dev.jagt.orchestrator.flow.TaskStatus;
import dev.jagt.orchestrator.service.MasterReview;

import java.util.List;
import java.util.Map;

/**
 * One round or plan handed to the Master, and what a human would have said about it. {@code names} are the words
 * the review must contain to count as having found the thing — a symbol out of the diff rather than a wording,
 * since how a verdict is phrased is the model's and what it is about is not. A blank {@code plan} writes no plan.md.
 */
record MasterCase(String name, String instructions, TaskStatus status, Map<String, String> baseline,
                  Map<String, String> change, String plan, MasterReview.Kind verdict, List<String> names) {

    static MasterCase round(String name, String instructions, Map<String, String> baseline,
                            Map<String, String> change, MasterReview.Kind verdict, List<String> names) {
        return new MasterCase(name, instructions, TaskStatus.REVIEW_PENDING, baseline, change, "", verdict, names);
    }

    static MasterCase plan(String name, String instructions, Map<String, String> baseline, String plan,
                           MasterReview.Kind verdict, List<String> names) {
        return new MasterCase(name, instructions, TaskStatus.PLAN_PENDING, baseline, Map.of(), plan, verdict, names);
    }

    static List<MasterCase> matrix() {
        return List.of(
                round("a fix and the test that fails without it",
                        "Discount was applied to the shipping line too. Charge it on goods only.",
                        Map.of("build.gradle", """
                                plugins { id 'java' }
                                repositories { mavenCentral() }
                                dependencies {
                                    testImplementation 'org.junit.jupiter:junit-jupiter:5.14.0'
                                    testImplementation 'org.assertj:assertj-core:3.27.6'
                                    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
                                }
                                test { useJUnitPlatform() }
                                """,
                                "src/main/java/Basket.java", """
                                class Basket {
                                    static int total(int goods, int shipping, int percentOff) {
                                        return (goods + shipping) * (100 - percentOff) / 100;
                                    }
                                }
                                """),
                        Map.of("src/main/java/Basket.java", """
                                class Basket {
                                    static int total(int goods, int shipping, int percentOff) {
                                        return goods * (100 - percentOff) / 100 + shipping;
                                    }
                                }
                                """,
                                "src/test/java/BasketTest.java", """
                                import org.junit.jupiter.api.Test;

                                import static org.assertj.core.api.Assertions.assertThat;

                                class BasketTest {

                                    @Test
                                    void leavesShippingOutOfTheDiscount() {
                                        assertThat(Basket.total(100, 10, 50)).isEqualTo(60);
                                    }

                                    @Test
                                    void roundsTheDiscountedGoodsDownBeforeShippingIsAdded() {
                                        assertThat(Basket.total(101, 10, 50)).isEqualTo(60);
                                    }
                                }
                                """),
                        MasterReview.Kind.READY, List.of()),

                round("a boundary the diff plainly gets wrong",
                        "Send the first page of results, twenty per page.",
                        Map.of("src/Page.java", """
                                class Page {
                                    static List<String> first(List<String> all) {
                                        return all.subList(0, Math.min(20, all.size()));
                                    }
                                }
                                """),
                        Map.of("src/Page.java", """
                                class Page {
                                    static List<String> first(List<String> all) {
                                        return all.subList(0, 20);
                                    }
                                }
                                """),
                        MasterReview.Kind.NOT_READY, List.of("Page.java")),

                round("a fix nothing tests",
                        "A blank surname crashed the label printer. Stop it crashing.",
                        Map.of("src/Label.java", """
                                class Label {
                                    static String of(String first, String last) {
                                        return first + " " + last.toUpperCase();
                                    }
                                }
                                """),
                        Map.of("src/Label.java", """
                                class Label {
                                    static String of(String first, String last) {
                                        return last == null ? first : first + " " + last.toUpperCase();
                                    }
                                }
                                """),
                        MasterReview.Kind.NOT_READY, List.of("Label.java")),

                round("work nobody asked for, carried along",
                        "Rename `qty` to `quantity` in Order.",
                        Map.of("src/Order.java", """
                                class Order {
                                    int qty;
                                    String customer;
                                }
                                """,
                                "src/Invoice.java", """
                                class Invoice {
                                    String number;
                                }
                                """),
                        Map.of("src/Order.java", """
                                class Order {
                                    int quantity;
                                    String customer;
                                }
                                """,
                                "src/Invoice.java", """
                                class Invoice {
                                    // The invoice number, as a string.
                                    String reference;
                                    int retries = 3;
                                }
                                """),
                        MasterReview.Kind.NOT_READY, List.of("Invoice.java")),

                round("a comment telling how the class is configured",
                        "Log the order id when an order-created event arrives.",
                        Map.of("src/main/java/OrderListener.java", """
                                class OrderListener {
                                    void onCreated(String orderId) {
                                        Orders.open(orderId);
                                    }
                                }
                                """),
                        Map.of("src/main/java/OrderListener.java", """
                                /**
                                 * Processes an order before its message is acknowledged, so an order this service
                                 * has not finished stays in the queue.
                                 */
                                class OrderListener {
                                    private static final System.Logger LOG = System.getLogger("orders");

                                    void onCreated(String orderId) {
                                        LOG.log(System.Logger.Level.INFO, "order created {0}", orderId);
                                        Orders.open(orderId);
                                    }
                                }
                                """),
                        MasterReview.Kind.NOT_READY, List.of("OrderListener.java")),

                round("a version dropped that the ticket never named",
                        "Set the quote mock's mapping version to the latest, 3.0.",
                        Map.of("src/QuoteMock.java", """
                                class QuoteMock {
                                    static String answer(int version) {
                                        return switch (version) {
                                            case 1 -> "/quote/v1";
                                            case 2 -> "/quote/v2";
                                            default -> throw new IllegalArgumentException("version " + version);
                                        };
                                    }
                                }
                                """),
                        Map.of("src/QuoteMock.java", """
                                class QuoteMock {
                                    static String answer(int version) {
                                        return switch (version) {
                                            case 1 -> "/quote/v1";
                                            case 3 -> "/quote/v3";
                                            default -> throw new IllegalArgumentException("version " + version);
                                        };
                                    }
                                }
                                """),
                        MasterReview.Kind.QUESTION, List.of("v2")),

                plan("a plan doing exactly what the ticket asks",
                        "Discount was applied to the shipping line too. Charge it on goods only.",
                        Map.of("src/main/java/Basket.java", """
                                class Basket {
                                    static int total(int goods, int shipping, int percentOff) {
                                        return (goods + shipping) * (100 - percentOff) / 100;
                                    }
                                }
                                """),
                        """
                        1. In `Basket.total`, apply `percentOff` to `goods` alone, then add `shipping` undiscounted.
                        2. Add `BasketTest`: `total(100, 10, 50)` is 60, where it is 55 before the change.
                        """,
                        MasterReview.Kind.READY, List.of()),

                plan("a plan missing a ticket line",
                        "Rename `qty` to `quantity` in Order, and in the invoice export that reads it.",
                        Map.of("src/Order.java", """
                                class Order {
                                    int qty;
                                }
                                """,
                                "src/InvoiceExport.java", """
                                class InvoiceExport {
                                    static String line(Order order) {
                                        return "qty=" + order.qty;
                                    }
                                }
                                """),
                        """
                        1. Rename the field `Order.qty` to `Order.quantity`.
                        """,
                        MasterReview.Kind.NOT_READY, List.of("InvoiceExport")),

                plan("a plan carrying work nobody asked for",
                        "A blank surname crashed the label printer. Stop it crashing.",
                        Map.of("src/Label.java", """
                                class Label {
                                    static String of(String first, String last) {
                                        return first + " " + last.toUpperCase();
                                    }
                                }
                                """,
                                "src/Printer.java", """
                                class Printer {
                                    void print(String label) {
                                        System.out.println(label);
                                    }
                                }
                                """),
                        """
                        1. In `Label.of`, return `first` alone when `last` is null or blank; test both.
                        2. Rewrite `Printer` to buffer labels and print them in batches of ten.
                        """,
                        MasterReview.Kind.NOT_READY, List.of("Printer")),

                plan("no plan written at all",
                        "Send the first page of results, twenty per page.",
                        Map.of("src/Page.java", """
                                class Page {
                                    static List<String> first(List<String> all) {
                                        return all;
                                    }
                                }
                                """),
                        "",
                        MasterReview.Kind.NOT_READY, List.of()),

                plan("a plan choosing what the ticket leaves open",
                        "Set the quote mock's mapping version to the latest, 3.0.",
                        Map.of("src/QuoteMock.java", """
                                class QuoteMock {
                                    static String answer(int version) {
                                        return switch (version) {
                                            case 1 -> "/quote/v1";
                                            case 2 -> "/quote/v2";
                                            default -> throw new IllegalArgumentException("version " + version);
                                        };
                                    }
                                }
                                """),
                        """
                        1. In `QuoteMock.answer`, replace `case 2 -> "/quote/v2"` with `case 3 -> "/quote/v3"`.
                        """,
                        MasterReview.Kind.QUESTION, List.of("v2")));
    }

    @Override
    public String toString() {
        return name;
    }
}
