package com.slotmind.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class Errors {
  private Errors() {}

  public static ResponseStatusException notFound(String kind, String id) {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, kind + " not found: " + id);
  }

  public static ResponseStatusException conflict(String message) {
    return new ResponseStatusException(HttpStatus.CONFLICT, message);
  }

  public static ResponseStatusException badRequest(String message) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
