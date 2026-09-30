package dev.jagt.orchestrator.mastereval;

import dev.jagt.orchestrator.service.MasterReview;

import java.util.List;
import java.util.Map;

/**
 * One round handed to the Master, and what a human would have said about it. {@code names} are the words the
 * review must contain to count as having found the thing — a symbol out of the diff rather than a wording,
 * since how a verdict is phrased is the model's and what it is about is not.
 */
record MasterCase(String name, String instructions, Map<String, String> baseline, Map<String, String> change,
                  MasterReview.Kind verdict, List<String> names) {

    static List<MasterCase> matrix() {
        return List.of(
                new MasterCase("a fix and the test that fails without it",
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

                new MasterCase("a boundary the diff plainly gets wrong",
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

                new MasterCase("a fix nothing tests",
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
                        MasterReview.Kind.NOT_READY, List.of("Label.java", "test")),

                new MasterCase("work nobody asked for, carried along",
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

                new MasterCase("a version dropped that the ticket never named",
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
                        MasterReview.Kind.QUESTION, List.of("v2")));
    }

    @Override
    public String toString() {
        return name;
    }
}
