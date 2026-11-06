package ar.edu.utn.dds.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

/** Human readable rendering only. All rules and calculations belong to the APIs. */
public final class ResultPresenter {
  private ResultPresenter() {}

  private static final Map<String, String> LABELS =
      Map.ofEntries(
          Map.entry("_id", "Referencia"),
          Map.entry("id", "Referencia"),
          Map.entry("donadorID", "Donador"),
          Map.entry("cantidadFaltante", "Unidades por recibir"),
          Map.entry("cantidadRecibida", "Unidades recibidas"),
          Map.entry("unidadesAsignadasSinEntregar", "Reservadas, pendientes de entrega"),
          Map.entry("stockPorDeposito", "Disponibilidad por depósito"),
          Map.entry("misionEnCurso", "Misión en curso"),
          Map.entry("donacionesPorEstado", "Donaciones por estado"),
          Map.entry("entidadesBeneficiadas", "Entidades beneficiadas"),
          Map.entry("tiempoPromedioEntregaSegundos", "Tiempo medio de entrega (segundos)"),
          Map.entry("ocupacion", "Ocupación (proporción de capacidad)"),
          Map.entry("proporcionQuejas", "Proporción de donaciones con queja"));

  public static String present(String title, JsonNode data) {
    StringBuilder result = new StringBuilder(title).append("\n\n");
    if (data == null || data.isNull() || data.isMissingNode())
      return result.append("Operación completada. No hay información adicional.").toString();
    if (data.isArray() && data.isEmpty())
      return result.append("No hay resultados para esta consulta.").toString();
    append(data, result, 0);
    return result.toString().strip();
  }

  private static void append(JsonNode node, StringBuilder out, int depth) {
    String indent = "  ".repeat(Math.min(depth, 5));
    if (node.isObject()) {
      node.fields()
          .forEachRemaining(
              f -> {
                if (Set.of("version", "fuente", "eliminado", "tipoRecurso").contains(f.getKey()))
                  return;
                if(f.getKey().equals("id") && node.has("_id") && f.getValue().equals(node.get("_id"))) return;
                if(f.getKey().equals("cantidadAsignada") && node.has("cantidadRecibida")) return;
                String label = label(f.getKey());
                if (f.getValue().isContainerNode()) {
                  out.append(indent).append(label).append(":\n");
                  append(f.getValue(), out, depth + 1);
                } else
                  out.append(indent)
                      .append(label)
                      .append(": ")
                      .append(value(f.getValue()))
                      .append('\n');
              });
    } else if (node.isArray()) {
      if (node.isEmpty()) out.append(indent).append("Sin registros\n");
      int i = 1;
      for (JsonNode item : node) {
        if (item.isContainerNode()) {
          out.append(indent).append("• Resultado ").append(i++).append('\n');
          append(item, out, depth + 1);
        } else out.append(indent).append("• ").append(value(item)).append('\n');
      }
    } else out.append(indent).append(value(node)).append('\n');
  }

  private static String label(String key) {
    String s = key.replaceAll("([a-záéíóú])([A-Z])", "$1 $2").replace('_', ' ');
    return LABELS.getOrDefault(
        key, s.isBlank() ? key : Character.toUpperCase(s.charAt(0)) + s.substring(1));
  }

  private static String value(JsonNode n) {
    if (n.isNull()) return "Sin datos";
    if (n.isBoolean()) return n.asBoolean() ? "Sí" : "No";
    if (n.isObject() && n.has("$date")) return n.path("$date").asText();
    return n.asText();
  }
}
