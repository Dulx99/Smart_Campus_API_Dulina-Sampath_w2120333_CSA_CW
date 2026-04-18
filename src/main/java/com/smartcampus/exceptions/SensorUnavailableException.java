package com.smartcampus.exceptions;

/**
 * Custom runtime exception thrown when attempting to append a reading to a sensor
 * that is currently in "MAINTENANCE" status or otherwise unavailable.
 */
public class SensorUnavailableException extends RuntimeException {
    public SensorUnavailableException(String message) {
        super(message);
    }
}
