package com.redis.vl.query;

/** Query to count documents matching a filter */
public class CountQuery {

  private final String filterString;
  private final Filter filter;

  public CountQuery(String filterString) {
    this.filterString = filterString;
    this.filter = null;
  }

  public CountQuery(Filter filter) {
    this.filterString = null;
    this.filter = filter;
  }

  public String getFilterString() {
    if (filterString != null) {
      return filterString;
    }
    if (filter != null) {
      return filter.build();
    }
    return "*";
  }

  public Filter getFilter() {
    return filter;
  }

  @Override
  public String toString() {
    return "CountQuery{filter='" + getFilterString() + "'}";
  }
}
