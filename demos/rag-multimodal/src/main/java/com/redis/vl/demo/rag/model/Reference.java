package com.redis.vl.demo.rag.model;

/**
 * A reference to a source location in the PDF document.
 *
 * @param page Page number (1-indexed)
 * @param type Content type (TEXT or IMAGE)
 * @param preview Short preview of the content
 */
public record Reference(int page, String type, String preview) {

  /**
   * Creates a reference with a truncated preview.
   *
   * @param page Page number
   * @param type Content type
   * @param content Full content text
   * @param maxLength Maximum preview length
   * @return Reference with truncated preview
   */
  public static Reference of(int page, String type, String content, int maxLength) {
    String preview = content;
    if (preview != null && preview.length() > maxLength) {
      preview = preview.substring(0, maxLength) + "...";
    }
    return new Reference(page, type, preview);
  }
}
