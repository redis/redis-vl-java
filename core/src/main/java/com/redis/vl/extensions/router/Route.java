package com.redis.vl.extensions.router;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Data;

/**
 * Model representing a routing path with associated metadata and thresholds.
 *
 * <p>Ported from Python: redisvl/extensions/router/schema.py:12
 */
@Data
@Builder
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification =
        "Route is a data class with intentionally mutable fields for Python API compatibility")
@SuppressWarnings("javadoc") // Lombok generates constructors
public class Route {
  private String name;
  private List<String> references;
  @Builder.Default private Map<String, Object> metadata = Map.of();
  @Builder.Default private double distanceThreshold = 0.5;

  /** Custom builder to ensure references list is mutable. */
  @SuppressWarnings("javadoc") // Lombok generates constructors
  public static class RouteBuilder {
    /**
     * Set the references list with a mutable copy.
     *
     * @param references List of reference strings
     * @return this builder
     */
    public RouteBuilder references(List<String> references) {
      // Convert to ArrayList to ensure mutability for addRouteReferences/deleteRouteReferences
      this.references = references != null ? new ArrayList<>(references) : new ArrayList<>();
      return this;
    }
  }

  /**
   * Validate the route configuration.
   *
   * @throws IllegalArgumentException if validation fails
   */
  public void validate() {
    if (name == null || name.trim().isEmpty()) {
      throw new IllegalArgumentException("Route name must not be empty");
    }
    if (references == null || references.isEmpty()) {
      throw new IllegalArgumentException("References must not be empty");
    }
    for (String ref : references) {
      if (ref == null || ref.trim().isEmpty()) {
        throw new IllegalArgumentException("All references must be non-empty strings");
      }
    }
    if (distanceThreshold <= 0 || distanceThreshold > 2) {
      throw new IllegalArgumentException(
          "Distance threshold must be greater than 0 and less than or equal to 2");
    }
  }

  /**
   * Convert route to a map for JSON serialization. Ported from Python: model_to_dict(route)
   *
   * @return Map representation of the route
   */
  public Map<String, Object> toDict() {
    Map<String, Object> dict = new HashMap<>();
    dict.put("name", name);
    dict.put("references", new ArrayList<>(references));
    dict.put("metadata", new HashMap<>(metadata));
    dict.put("distance_threshold", distanceThreshold);
    return dict;
  }

  /**
   * Convert route to JSON string.
   *
   * @return JSON representation of the route
   */
  public String toJson() {
    Gson gson = new Gson();
    return gson.toJson(toDict());
  }

  /**
   * Create route from map representation. Ported from Python: Route(**dict)
   *
   * @param dict Map containing route data
   * @return Route instance
   */
  public static Route fromDict(Map<String, Object> dict) {
    @SuppressWarnings("unchecked")
    List<String> refs = (List<String>) dict.get("references");
    @SuppressWarnings("unchecked")
    Map<String, Object> meta =
        dict.containsKey("metadata") ? (Map<String, Object>) dict.get("metadata") : Map.of();

    return Route.builder()
        .name((String) dict.get("name"))
        .references(refs != null ? refs : List.of())
        .metadata(meta)
        .distanceThreshold(
            dict.containsKey("distance_threshold")
                ? ((Number) dict.get("distance_threshold")).doubleValue()
                : 0.5)
        .build();
  }

  /**
   * Create route from JSON string.
   *
   * @param json JSON string
   * @return Route instance
   */
  public static Route fromJson(String json) {
    Gson gson = new Gson();
    Type type = new TypeToken<Map<String, Object>>() {}.getType();
    Map<String, Object> dict = gson.fromJson(json, type);
    return fromDict(dict);
  }
}
