package com.smartcampus.exceptions;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global safety net to catch all unhandled runtime exceptions.
 * This prevents raw Java stack traces from leaking to the client, 
 * fulfilling the cybersecurity requirement of the coursework (Part 5.2).
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(GenericExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {
        // Log the actual error internally so we can still debug the server,
        // but DO NOT send this stack trace back to the client.
        LOGGER.log(Level.SEVERE, "Unexpected server error occurred", exception);

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("error", "Internal Server Error");
        responseBody.put("message", "An unexpected error occurred while processing your request.");

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(responseBody)
                .build();
    }
}
