package com.smartcampus.exceptions;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.HashMap;
import java.util.Map;

/**
 * Maps the LinkedResourceNotFoundException to an HTTP 422 Unprocessable Entity response.
 */
@Provider
public class LinkedResourceNotFoundExceptionMapper implements ExceptionMapper<LinkedResourceNotFoundException> {

    @Override
    public Response toResponse(LinkedResourceNotFoundException exception) {
        Map<String, String> responseBody = new HashMap<>();
        responseBody.put("error", "Unprocessable Entity");
        responseBody.put("message", exception.getMessage());
        
        // JAX-RS Response.Status enum does not include 422 by default in older versions,
        // but Jersey provides it, or we can use the raw int value. 
        // 422 is standard for "Unprocessable Entity".
        return Response.status(422)
                .type(MediaType.APPLICATION_JSON)
                .entity(responseBody)
                .build();
    }
}
