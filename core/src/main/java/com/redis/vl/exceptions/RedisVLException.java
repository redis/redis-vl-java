package com.redis.vl.exceptions;

/** Base exception for RedisVL operations */
public class RedisVLException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * Constructs a new RedisVLException with the specified detail message.
   *
   * @param message the detail message
   */
  public RedisVLException(String message) {
    super(message);
  }

  /**
   * Constructs a new RedisVLException with the specified detail message and cause.
   *
   * @param message the detail message
   * @param cause the cause of the exception
   */
  public RedisVLException(String message, Throwable cause) {
    super(message, cause);
  }
}
