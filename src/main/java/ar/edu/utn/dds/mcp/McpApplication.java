package ar.edu.utn.dds.mcp;

import com.fasterxml.jackson.databind.*;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.server.transport.StdioServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.*;
import java.util.concurrent.CountDownLatch;

public class McpApplication {
  public static void main(String[] args) throws Exception {
    // stdout is reserved exclusively for MCP JSON-RPC; diagnostics go to stderr.
    var mapper = new ObjectMapper();
    var gateway = new ApiGateway(serviceUrls());
    JsonNode catalog;
    try (var stream = McpApplication.class.getResourceAsStream("/tools.json")) {
      catalog = mapper.readTree(Objects.requireNonNull(stream, "Falta tools.json"));
    }
    var server =
        McpServer.sync(
                new StdioServerTransportProvider(
                    new io.modelcontextprotocol.json.jackson2.JacksonMcpJsonMapper(mapper)))
            .serverInfo("donatrack", "1.0.0")
            .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
            .build();
    for (JsonNode operation : catalog) {
      var tool =
          McpSchema.Tool.builder()
              .name(operation.path("name").asText())
              .description(operation.path("description").asText())
              .annotations(
                  mapper.treeToValue(operation.get("annotations"), McpSchema.ToolAnnotations.class))
              .inputSchema(mapper.treeToValue(operation.get("schema"), McpSchema.JsonSchema.class))
              .build();
      server.addTool(
          McpServerFeatures.SyncToolSpecification.builder()
              .tool(tool)
              .callHandler(
                  (exchange, request) -> {
                    try {
                      String response = gateway.execute(operation, request.arguments());
                      return McpSchema.CallToolResult.builder()
                          .content(
                              List.of(
                                  new McpSchema.TextContent(
                                      ResultPresenter.present(
                                          operation.path("description").asText(),
                                          mapper.readTree(response).path("resultado")))))
                          .structuredContent(mapper.readValue(response, Map.class))
                          .isError(false)
                          .build();
                    } catch (Exception ex) {
                      Object detail;
                      try {
                        detail =
                            ex instanceof ApiGateway.ApiFailure
                                ? mapper.readValue(ex.getMessage(), Map.class)
                                : Map.of(
                                    "detalle",
                                    ex.getMessage() == null
                                        ? "Operación no disponible"
                                        : ex.getMessage());
                      } catch (Exception parseError) {
                        detail = Map.of("detalle", "No se pudo completar la operación");
                      }
                      return McpSchema.CallToolResult.builder()
                          .content(
                              List.of(
                                  new McpSchema.TextContent(
                                      ResultPresenter.present(
                                          "No se pudo completar la operación",
                                          mapper.valueToTree(detail)))))
                          .structuredContent(detail)
                          .isError(true)
                          .build();
                    }
                  })
              .build());
    }
    System.err.printf("DonaTrack MCP listo: %d herramientas%n", catalog.size());
    Runtime.getRuntime().addShutdownHook(new Thread(server::close));
    new CountDownLatch(1).await();
  }

  private static String url(String name, String fallback) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? fallback : value;
  }

  static Map<String, String> serviceUrls() {
    String base = url("GATEWAY_URL", "http://localhost:8080").replaceAll("/+$", "");
    return Map.of(
        "donaciones",
        url("DONACIONES_API_URL", base + "/api/donaciones"),
        "donadores",
        url("DONADORES_API_URL", base + "/api/donadores-entidades"),
        "logistica",
        url("LOGISTICA_API_URL", base + "/api/logistica"),
        "incentivos",
        url("INCENTIVOS_API_URL", base + "/api/incentivos"),
        "consultas",
        url("CONSULTAS_API_URL", base + "/api/consultas"),
        "gateway",
        base);
  }
}
