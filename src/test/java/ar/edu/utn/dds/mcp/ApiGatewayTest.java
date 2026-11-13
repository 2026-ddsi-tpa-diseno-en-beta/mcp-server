package ar.edu.utn.dds.mcp;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class ApiGatewayTest {
  final ObjectMapper mapper = new ObjectMapper();
  JsonNode catalog;

  @BeforeEach
  void load() throws Exception {
    catalog = mapper.readTree(getClass().getResourceAsStream("/tools.json"));
  }

  JsonNode tool(String name) {
    for (JsonNode item : catalog) if (item.path("name").asText().equals(name)) return item;
    throw new IllegalArgumentException(name);
  }

  @Test
  void catalogCoversFourComponentsAndSixFlowsWithoutAdminWipes() {
    Set<String> names = new HashSet<>(), components = new HashSet<>();
    for (JsonNode operation : catalog) {
      assertTrue(names.add(operation.path("name").asText()));
      components.add(operation.path("component").asText());
      assertFalse(operation.path("path").asText().contains("/admin"));
    }
    assertEquals(
        Set.of("donaciones", "donadores", "logistica", "incentivos", "consultas", "gateway"),
        components);
    assertTrue(
        names.containsAll(
            List.of(
                "realizar_donacion",
                "reportar_entrega",
                "registrar_queja",
                "procesar_donador",
                "crear_necesidad",
                "estadisticas_donador")));
  }

  @Test
  void requiredInputsAreRejectedBeforeAnyNetworkCall() {
    var gateway = new ApiGateway(Map.of("donaciones", "http://localhost:1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> gateway.execute(tool("realizar_donacion"), Map.of("datos", Map.of("cantidad", 3))));
  }

  @Test
  void idsCannotChangeTheApiRoute() {
    var gateway = new ApiGateway(Map.of("donaciones", "http://localhost:1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> gateway.execute(tool("buscar_producto"), Map.of("id", "../admin")));
  }

  @Test
  void categoryParentBelongsOnlyToTheCreationContract() throws Exception {
    assertTrue(
        tool("crear_categoria")
            .path("schema").path("properties").path("datos")
            .path("properties").has("categoriaPadreID"));
    assertFalse(
        tool("modificar_categoria")
            .path("schema").path("properties").path("datos")
            .path("properties").has("categoriaPadreID"));

    var gateway = new ApiGateway(Map.of("donaciones", "http://localhost:1"));
    assertThrows(
        IllegalArgumentException.class,
        () -> gateway.execute(
            tool("modificar_categoria"),
            Map.of("id", "categoria", "datos", Map.of(
                "nombre", "Nombre", "descripcion", "Descripción", "categoriaPadreID", "otro"))));

    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<String> body = new AtomicReference<>(), method = new AtomicReference<>();
    server.createContext("/categorias/categoria", exchange -> {
      method.set(exchange.getRequestMethod());
      body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] output = "{\"id\":\"categoria\",\"categoriaPadreID\":\"original\"}"
          .getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, output.length);
      exchange.getResponseBody().write(output);
      exchange.close();
    });
    server.start();
    try {
      var updated = Map.of("nombre", "Nuevo nombre", "descripcion", "Nueva descripción");
      var response = mapper.readTree(
          new ApiGateway(Map.of("donaciones", "http://127.0.0.1:" + server.getAddress().getPort()))
              .execute(tool("modificar_categoria"), Map.of("id", "categoria", "datos", updated)));
      assertEquals("PUT", method.get());
      assertEquals(mapper.valueToTree(updated), mapper.readTree(body.get()));
      assertEquals("original", response.path("resultado").path("categoriaPadreID").asText());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void sendsOriginalBodyAndTraceToTheCorrectComponent() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<String> body = new AtomicReference<>(), trace = new AtomicReference<>();
    server.createContext(
        "/donaciones",
        exchange -> {
          body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          trace.set(exchange.getRequestHeaders().getFirst("X-Trace-Id"));
          byte[] output = "{\"id\":\"nueva\"}".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(201, output.length);
          exchange.getResponseBody().write(output);
          exchange.close();
        });
    server.start();
    try {
      var gateway =
          new ApiGateway(Map.of("donaciones", "http://127.0.0.1:" + server.getAddress().getPort()));
      var data =
          Map.of(
              "donadorID",
              "d",
              "depositoID",
              "dep",
              "productoID",
              "p",
              "descripcion",
              "Donación",
              "cantidad",
              3);
      var response =
          mapper.readTree(gateway.execute(tool("realizar_donacion"), Map.of("datos", data)));
      assertEquals(mapper.valueToTree(data), mapper.readTree(body.get()));
      assertEquals("nueva", response.path("resultado").path("id").asText());
      assertEquals(trace.get(), response.path("traceId").asText());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void remoteFailuresRemainFailures() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/productos/p",
        exchange -> {
          exchange.sendResponseHeaders(503, -1);
          exchange.close();
        });
    server.start();
    try {
      var gateway =
          new ApiGateway(Map.of("donaciones", "http://127.0.0.1:" + server.getAddress().getPort()));
      assertThrows(
          ApiGateway.ApiFailure.class,
          () -> gateway.execute(tool("buscar_producto"), Map.of("id", "p")));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void reportFiltersAreEncodedAndWritesAreMarkedAsSuch() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicReference<String> query = new AtomicReference<>();
    server.createContext(
        "/depositos/reporte",
        e -> {
          query.set(e.getRequestURI().getRawQuery());
          byte[] out = "[]".getBytes(StandardCharsets.UTF_8);
          e.sendResponseHeaders(200, out.length);
          e.getResponseBody().write(out);
          e.close();
        });
    server.start();
    try {
      new ApiGateway(Map.of("consultas", "http://127.0.0.1:" + server.getAddress().getPort()))
          .execute(
              tool("reporte_depositos"),
              Map.of("filtros", Map.of("depositoID", "A & B", "desde", "2026-11-06")));
      assertTrue(query.get().contains("depositoID=A%20%26%20B"));
      assertTrue(
          tool("ficha_integral_donador").path("annotations").path("readOnlyHint").asBoolean());
      assertTrue(tool("eliminar_producto").path("annotations").path("destructiveHint").asBoolean());
      assertFalse(tool("realizar_donacion").path("annotations").path("readOnlyHint").asBoolean());
    } finally {
      server.stop(0);
    }
  }
}
