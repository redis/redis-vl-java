package com.redis.vl.langchain4j;

import com.redis.vl.query.Filter;
import dev.langchain4j.store.embedding.filter.comparison.*;
import dev.langchain4j.store.embedding.filter.logical.And;
import dev.langchain4j.store.embedding.filter.logical.Not;
import dev.langchain4j.store.embedding.filter.logical.Or;

/**
 * Maps LangChain4J Filter types to RedisVL Filter queries.
 *
 * <p>Converts dev.langchain4j.store.embedding.filter.Filter to com.redis.vl.query.Filter for use
 * with RedisVL SearchIndex.
 *
 * <p>Supported filters:
 *
 * <ul>
 *   <li>Comparison: IsEqualTo, IsNotEqualTo, IsGreaterThan, IsGreaterThanOrEqualTo, IsLessThan,
 *       IsLessThanOrEqualTo, IsIn, IsNotIn
 *   <li>Logical: And, Or, Not
 * </ul>
 */
public class LangChain4JFilterMapper {

  /**
   * Map LangChain4J Filter to RedisVL Filter.
   *
   * @param filter LangChain4J filter (can be null)
   * @return RedisVL Filter, or wildcard (*) filter if input is null
   */
  public static Filter map(dev.langchain4j.store.embedding.filter.Filter filter) {
    if (filter == null) {
      // Return wildcard filter for "match all"
      return Filter.custom("*");
    }

    // Comparison filters
    if (filter instanceof IsEqualTo) {
      return mapEqual((IsEqualTo) filter);
    } else if (filter instanceof IsNotEqualTo) {
      return mapNotEqual((IsNotEqualTo) filter);
    } else if (filter instanceof IsGreaterThan) {
      return mapGreaterThan((IsGreaterThan) filter);
    } else if (filter instanceof IsGreaterThanOrEqualTo) {
      return mapGreaterThanOrEqual((IsGreaterThanOrEqualTo) filter);
    } else if (filter instanceof IsLessThan) {
      return mapLessThan((IsLessThan) filter);
    } else if (filter instanceof IsLessThanOrEqualTo) {
      return mapLessThanOrEqual((IsLessThanOrEqualTo) filter);
    } else if (filter instanceof IsIn) {
      return mapIn((IsIn) filter);
    } else if (filter instanceof IsNotIn) {
      return mapNotIn((IsNotIn) filter);
    }

    // Logical filters
    else if (filter instanceof And) {
      return mapAnd((And) filter);
    } else if (filter instanceof Or) {
      return mapOr((Or) filter);
    } else if (filter instanceof Not) {
      return mapNot((Not) filter);
    }

    throw new UnsupportedOperationException(
        "Unsupported filter type: " + filter.getClass().getName());
  }

  private static Filter mapEqual(IsEqualTo filter) {
    String key = filter.key();
    Object value = filter.comparisonValue();

    // Try numeric first
    if (value instanceof Number) {
      Number num = (Number) value;
      if (value instanceof Integer || value instanceof Long) {
        return Filter.numeric(key).eq(num.intValue());
      } else {
        return Filter.numeric(key).eq(num.doubleValue());
      }
    }

    // String values - use tag for better performance
    return Filter.tag(key, value.toString());
  }

  private static Filter mapNotEqual(IsNotEqualTo filter) {
    return Filter.not(mapEqual(new IsEqualTo(filter.key(), filter.comparisonValue())));
  }

  private static Filter mapGreaterThan(IsGreaterThan filter) {
    String key = filter.key();
    Object value = filter.comparisonValue();

    if (!(value instanceof Number)) {
      throw new IllegalArgumentException(
          "Greater than comparison only supports numeric values, got: " + value.getClass());
    }

    Number num = (Number) value;
    if (value instanceof Integer || value instanceof Long) {
      return Filter.numeric(key).gt(num.intValue());
    } else {
      return Filter.numeric(key).gt(num.doubleValue());
    }
  }

  private static Filter mapGreaterThanOrEqual(IsGreaterThanOrEqualTo filter) {
    String key = filter.key();
    Object value = filter.comparisonValue();

    if (!(value instanceof Number)) {
      throw new IllegalArgumentException(
          "Greater than or equal comparison only supports numeric values, got: "
              + value.getClass());
    }

    Number num = (Number) value;
    if (value instanceof Integer || value instanceof Long) {
      return Filter.numeric(key).gte(num.intValue());
    } else {
      return Filter.numeric(key).gte(num.doubleValue());
    }
  }

  private static Filter mapLessThan(IsLessThan filter) {
    String key = filter.key();
    Object value = filter.comparisonValue();

    if (!(value instanceof Number)) {
      throw new IllegalArgumentException(
          "Less than comparison only supports numeric values, got: " + value.getClass());
    }

    Number num = (Number) value;
    if (value instanceof Integer || value instanceof Long) {
      return Filter.numeric(key).lt(num.intValue());
    } else {
      return Filter.numeric(key).lt(num.doubleValue());
    }
  }

  private static Filter mapLessThanOrEqual(IsLessThanOrEqualTo filter) {
    String key = filter.key();
    Object value = filter.comparisonValue();

    if (!(value instanceof Number)) {
      throw new IllegalArgumentException(
          "Less than or equal comparison only supports numeric values, got: " + value.getClass());
    }

    Number num = (Number) value;
    if (value instanceof Integer || value instanceof Long) {
      return Filter.numeric(key).lte(num.intValue());
    } else {
      return Filter.numeric(key).lte(num.doubleValue());
    }
  }

  private static Filter mapIn(IsIn filter) {
    String key = filter.key();
    var values = filter.comparisonValues();

    if (values == null || values.isEmpty()) {
      return Filter.custom("*"); // Match all if no values
    }

    // Convert all values to strings for tag filter
    String[] tagValues = values.stream().map(Object::toString).toArray(String[]::new);
    return Filter.tag(key, tagValues);
  }

  private static Filter mapNotIn(IsNotIn filter) {
    return Filter.not(mapIn(new IsIn(filter.key(), filter.comparisonValues())));
  }

  private static Filter mapAnd(And filter) {
    Filter left = map(filter.left());
    Filter right = map(filter.right());
    return Filter.and(left, right);
  }

  private static Filter mapOr(Or filter) {
    Filter left = map(filter.left());
    Filter right = map(filter.right());
    return Filter.or(left, right);
  }

  private static Filter mapNot(Not filter) {
    return Filter.not(map(filter.expression()));
  }
}
