package com.smartcampus.exceptions;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Global safety net (Part 5.4): Catches all unexpected runtime exceptions.
 *
 * This prevents raw Java stack traces from leaking to the client — a critical
 * cybersecurity requirement. Attackers can extract library versions, classpaths,
 * and internal logic from stack traces to identify known CVE vulnerabilities.
 *
 * NOTE: We deliberately skip WebApplicationException so that Jersey's own
 * well-formed HTTP responses (e.g. 404 Not Found, 405 Method Not Allowed)
 * are not overridden by a generic 500.
 */
@Provider
public class GenericExceptionMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOGGER = Logger.getLogger(GenericExceptionMapper.class.getName());

    @Override
    public Response toResponse(Throwable exception) {
        // Pass through Jersey's own HTTP exceptions (404, 405, etc.) unchanged
        if (exception instanceof WebApplicationException) {
            return ((WebApplicationException) exception).getResponse();
        }

        // For all other unexpected errors, log internally and return a safe 500
        LOGGER.log(Level.SEVERE, "Unexpected server error occurred — stack trace hidden from client", exception);

        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("error", "Internal Server Error");
        responseBody.put("message", "An unexpected error occurred. Please contact the administrator.");

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(responseBody)
                .build();
    }
}
