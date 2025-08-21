package com.redis.vl.exceptions;

/** Base exception for RedisVL operations */
public class RedisVLException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  public RedisVLException(String message) {
    super(message);
  }

  public RedisVLException(String message, Throwable cause) {
    super(message, cause);
  }
}
