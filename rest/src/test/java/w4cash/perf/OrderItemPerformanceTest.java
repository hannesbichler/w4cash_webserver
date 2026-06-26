package w4cash.perf;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Tag("performance")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderItemPerformanceTest {

    private static final int N_THREADS = 10;
    private static final int REQUESTS_PER_THREAD = 200;
    private static final int WARMUP_REQUESTS = 50;
    private static final int PRODUCTS_PER_ORDER = 3;

    private final String baseUrl = System.getProperty("server.url", "http://localhost:3000");
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private List<TableRef> tables = List.of();
    private List<ProductRef> products = List.of();
    private final Map<String, ObjectNode> originalOrders = new LinkedHashMap<>();
    private final Map<String, ObjectNode> bookedOrders = new LinkedHashMap<>();

    @BeforeAll
    void requireServerRunning() {
        try {
            restTemplate.getForObject(baseUrl + "/persons", String.class);
        } catch (Exception e) {
            assumeTrue(false,
                    "Server not reachable at " + baseUrl + " — start it first:\n" +
                            "  cd C:\\BTech\\java\\w4cash_webserver && mvnw -pl rest spring-boot:run");
        }

        tables = loadTables();
        products = loadProducts();

        assumeTrue(!tables.isEmpty(), "No tables found via /floors and /places/{floorId}");
        assumeTrue(!products.isEmpty(), "No products found via /products");

        for (int i = 0; i < tables.size(); i++) {
            TableRef table = tables.get(i);
            ObjectNode originalOrder = fetchOrderItem(table);
            originalOrders.put(table.id(), originalOrder.deepCopy());
            bookedOrders.put(table.id(), buildBookedOrder(table, originalOrder, i));
        }
    }

    @AfterAll
    void cleanUpTestData() {
        if (tables.isEmpty()) {
            return;
        }

        RestTemplate client = new RestTemplate();
        ExecutorService pool = Executors.newFixedThreadPool(N_THREADS);
        for (TableRef table : tables) {
            pool.submit(() -> {
                ObjectNode id = objectMapper.createObjectNode();
                id.put("id", table.id());
                // putOrder(client, table.id(), id);
            });
        }

        pool.shutdown();
        try {
            pool.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        System.out.printf("Deleted order items for %d tables.%n", tables.size());
    }

    @Test
    void orderItemThroughput() throws Exception {
        RestTemplate warmupClient = new RestTemplate();

        System.out.println("=== OrderItem Performance Test ===");
        System.out.printf("Server: %s%n", baseUrl);
        System.out.printf("Tables discovered: %d | Products loaded: %d%n", tables.size(), products.size());
        System.out.printf("Threads: %d | Requests/thread: %d | Total: %d%n",
                N_THREADS, REQUESTS_PER_THREAD, N_THREADS * REQUESTS_PER_THREAD);

        System.out.printf("Warm-up: %d request cycles...%n", WARMUP_REQUESTS);
        for (int i = 0; i < WARMUP_REQUESTS; i++) {
            exerciseOrderFlow(warmupClient, tableFor(i));
        }

        int total = N_THREADS * REQUESTS_PER_THREAD;
        long[] latenciesMs = new long[total];
        AtomicInteger slot = new AtomicInteger(0);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(N_THREADS);

        ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
        for (int t = 0; t < N_THREADS; t++) {
            final int threadId = t;
            executor.submit(() -> {
                RestTemplate client = new RestTemplate();
                try {
                    startGate.await();
                    for (int i = 0; i < REQUESTS_PER_THREAD; i++) {
                        TableRef table = tableFor(threadId * REQUESTS_PER_THREAD + i);
                        long start = System.nanoTime();
                        exerciseOrderFlow(client, table);
                        latenciesMs[slot.getAndIncrement()] = (System.nanoTime() - start) / 1_000_000L;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long wallStart = System.currentTimeMillis();
        startGate.countDown();
        doneLatch.await();
        long elapsedMs = System.currentTimeMillis() - wallStart;
        executor.shutdown();

        Arrays.sort(latenciesMs);
        long avg = (long) Arrays.stream(latenciesMs).average().orElse(0);
        double throughput = total * 1000.0 / elapsedMs;

        System.out.println();
        System.out.println("Results:");
        System.out.printf("  Elapsed:    %d ms%n", elapsedMs);
        System.out.printf("  Throughput: %.1f req/s%n", throughput);
        System.out.printf("  Min:        %d ms%n", latenciesMs[0]);
        System.out.printf("  Avg:        %d ms%n", avg);
        System.out.printf("  p50:        %d ms%n", latenciesMs[(int) (total * 0.50)]);
        System.out.printf("  p95:        %d ms%n", latenciesMs[(int) (total * 0.95)]);
        System.out.printf("  p99:        %d ms%n", latenciesMs[(int) (total * 0.99)]);
        System.out.printf("  Max:        %d ms%n", latenciesMs[total - 1]);
        System.out.println("==================================");
    }

    private List<TableRef> loadTables() {
        List<TableRef> discoveredTables = new ArrayList<>();
        for (JsonNode floorNode : readEmbeddedCollection("/floors")) {
            String floorId = floorNode.path("id_").asText("");
            if (floorId.isBlank()) {
                continue;
            }

            for (JsonNode tableNode : readEmbeddedCollection("/places/{floorId}", floorId)) {
                String tableId = tableNode.path("id_").asText("");
                String tableName = tableNode.path("name").asText(tableId);
                if (!tableId.isBlank()) {
                    discoveredTables.add(new TableRef(tableId, tableName));
                }
            }
        }
        return discoveredTables;
    }

    private List<ProductRef> loadProducts() {
        List<ProductRef> discoveredProducts = new ArrayList<>();
        for (JsonNode productNode : readEmbeddedCollection("/products")) {
            String productId = productNode.path("id_").asText("");
            String productName = productNode.path("name").asText("");
            if (productId.isBlank() || productName.isBlank()) {
                continue;
            }

            discoveredProducts.add(new ProductRef(
                    productId,
                    productName,
                    productNode.path("pricesell").asDouble(0.0),
                    productNode.path("attributeSetId").asText("")));
        }
        return discoveredProducts;
    }

    private List<JsonNode> readEmbeddedCollection(String path, Object... uriVariables) {
        JsonNode response = restTemplate.getForObject(baseUrl + path, JsonNode.class, uriVariables);
        if (response == null || !response.has("_embedded")) {
            return List.of();
        }

        JsonNode embedded = response.path("_embedded");
        var collections = embedded.elements();
        if (!collections.hasNext()) {
            return List.of();
        }

        JsonNode firstCollection = collections.next();
        if (!firstCollection.isArray()) {
            return List.of();
        }

        List<JsonNode> items = new ArrayList<>();
        firstCollection.forEach(items::add);
        return items;
    }

    private ObjectNode fetchOrderItem(TableRef table) {
        JsonNode response = restTemplate.getForObject(
                baseUrl + "/orderitem/{tableId}/{tableName}",
                JsonNode.class,
                table.id(),
                table.name());
        return sanitizeOrderItem(response, table.id());
    }

    private ObjectNode sanitizeOrderItem(JsonNode source, String tableId) {
        ObjectNode sanitized = objectMapper.createObjectNode();
        sanitized.put("id_", textValue(source, "id_", tableId));
        sanitized.put("tickettype", intValue(source, "tickettype", 0));
        sanitized.put("ticketId", intValue(source, "ticketId", 0));

        ArrayNode lines = sanitized.putArray("lines");
        JsonNode sourceLines = source == null ? null : source.path("lines");
        if (sourceLines != null && sourceLines.isArray()) {
            for (JsonNode sourceLine : sourceLines) {
                lines.add(sanitizeOrderLine(sourceLine));
            }
        }

        return sanitized;
    }

    private ObjectNode sanitizeOrderLine(JsonNode sourceLine) {
        ObjectNode sanitizedLine = objectMapper.createObjectNode();
        sanitizedLine.put("id", textValue(sourceLine, "id", ""));
        sanitizedLine.put("orderId", textValue(sourceLine, "orderId", ""));
        sanitizedLine.put("productId", textValue(sourceLine, "productId", ""));
        sanitizedLine.put("productName", textValue(sourceLine, "productName", ""));
        sanitizedLine.put("pricesell", doubleValue(sourceLine, "pricesell", 0.0));
        sanitizedLine.put("qty", doubleValue(sourceLine, "qty", 0.0));
        sanitizedLine.put("productAttSetId", textValue(sourceLine, "productAttSetId", ""));
        sanitizedLine.put("attSetInstDesc", textValue(sourceLine, "attSetInstDesc", ""));

        ArrayNode attributes = sanitizedLine.putArray("attributes");
        JsonNode sourceAttributes = sourceLine.path("attributes");
        if (sourceAttributes.isArray()) {
            for (JsonNode attribute : sourceAttributes) {
                attributes.add(attribute.deepCopy());
            }
        }

        return sanitizedLine;
    }

    private ObjectNode buildBookedOrder(TableRef table, ObjectNode originalOrder, int seed) {
        ObjectNode bookedOrder = originalOrder.deepCopy();
        ArrayNode lines = bookedOrder.withArray("lines");
        lines.removeAll();

        for (int i = 0; i < Math.min(PRODUCTS_PER_ORDER, products.size()); i++) {
            ProductRef product = products.get((seed + i) % products.size());
            lines.add(createBookedLine(table, product, i));
        }

        return bookedOrder;
    }

    private ObjectNode createBookedLine(TableRef table, ProductRef product, int lineIndex) {
        ObjectNode line = objectMapper.createObjectNode();
        line.put("id", "perf-" + table.id() + "-" + lineIndex);
        line.put("orderId", table.id());
        line.put("productId", product.id());
        line.put("productName", product.name());
        line.put("pricesell", product.priceSell());
        line.put("qty", 1.0);
        line.put("productAttSetId", product.attributeSetId());
        line.put("attSetInstDesc", "");
        line.putArray("attributes");
        return line;
    }

    private void exerciseOrderFlow(RestTemplate client, TableRef table) {
        client.getForObject(baseUrl + "/orderitem/{tableId}/{tableName}", JsonNode.class, table.id(), table.name());
        putOrder(client, table.id(), bookedOrders.get(table.id()));
    }

    // private void deleteOrder(RestTemplate client, String tableId) {
    // client.delete(baseUrl + "/orderitem/{id}", tableId);
    // }

    private void putOrder(RestTemplate client, String tableId, ObjectNode order) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<ObjectNode> request = new HttpEntity<>(order.deepCopy(), headers);
        ResponseEntity<Void> response = client.exchange(
                baseUrl + "/orderitem/{id}",
                HttpMethod.PUT,
                request,
                Void.class,
                tableId);
        assumeTrue(response.getStatusCode().is2xxSuccessful(), () -> "Booking failed for table " + tableId);
    }

    private TableRef tableFor(int index) {
        return tables.get(index % tables.size());
    }

    private String textValue(JsonNode node, String fieldName, String fallback) {
        return node != null && node.has(fieldName) && !node.get(fieldName).isNull()
                ? node.get(fieldName).asText(fallback)
                : fallback;
    }

    private int intValue(JsonNode node, String fieldName, int fallback) {
        return node != null && node.has(fieldName) && !node.get(fieldName).isNull()
                ? node.get(fieldName).asInt(fallback)
                : fallback;
    }

    private double doubleValue(JsonNode node, String fieldName, double fallback) {
        return node != null && node.has(fieldName) && !node.get(fieldName).isNull()
                ? node.get(fieldName).asDouble(fallback)
                : fallback;
    }

    private record TableRef(String id, String name) {
    }

    private record ProductRef(String id, String name, double priceSell, String attributeSetId) {
    }
}
