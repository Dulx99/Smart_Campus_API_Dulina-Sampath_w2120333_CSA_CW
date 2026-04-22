package com.smartcampus.resources;

import com.smartcampus.exceptions.LinkedResourceNotFoundException;
import com.smartcampus.models.Room;
import com.smartcampus.models.Sensor;
import com.smartcampus.services.DataService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Resource class to manage the /api/v1/sensors collection.
 */
@Path("/sensors")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorResource {

    private final DataService dataService = DataService.getInstance();

    /**
     * POST /: Register a new sensor.
     * Verifies that the roomId specified actually exists.
     */
    @POST
    public Response registerSensor(Sensor sensor) {
        if (sensor.getId() == null || sensor.getId().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Sensor ID is required.")
                    .build();
        }

        // Making sure the room actually exists before linking the sensor
        String roomId = sensor.getRoomId();
        if (roomId == null) {
            throw new LinkedResourceNotFoundException("Room ID must be provided to register a sensor.");
        }

        Room targetRoom = dataService.getRooms().get(roomId);
        if (targetRoom == null) {
            // Throwing our custom exception that will get picked up by the 422 ExceptionMapper
            throw new LinkedResourceNotFoundException("Validation failed: The provided roomId '" + roomId + "' does not exist.");
        }

        // Duplicate check
        if (dataService.getSensors().containsKey(sensor.getId())) {
             return Response.status(Response.Status.CONFLICT)
                    .entity("Sensor with this ID already exists.")
                    .build();
        }

        // Register the sensor
        dataService.getSensors().put(sensor.getId(), sensor);

        // Keep the room's sensor list in sync
        targetRoom.getSensorIds().add(sensor.getId());

        return Response.status(Response.Status.CREATED).entity(sensor).build();
    }

    /**
     * GET /: Retrieve sensors. Optional filter by type.
     */
    @GET
    public Collection<Sensor> getSensors(@QueryParam("type") String type) {
        Collection<Sensor> allSensors = dataService.getSensors().values();

        // If a type filter is provided, filter the collection
        if (type != null && !type.trim().isEmpty()) {
            return allSensors.stream()
                    .filter(s -> type.equalsIgnoreCase(s.getType()))
                    .collect(Collectors.toList());
        }

        // Otherwise return all
        return allSensors;
    }

    /**
     * GET /{sensorId}: Fetch detailed metadata for a specific sensor.
     */
    @GET
    @Path("/{sensorId}")
    public Response getSensorById(@PathParam("sensorId") String sensorId) {
        Sensor sensor = dataService.getSensors().get(sensorId);
        if (sensor == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Sensor not found.")
                    .build();
        }
        return Response.ok(sensor).build();
    }

    /**
     * Sub-Resource Locator for nested readings.
     * Delegates requests from /sensors/{sensorId}/readings to SensorReadingResource.
     */
    @Path("/{sensorId}/readings")
    public SensorReadingResource getReadingsResource(@PathParam("sensorId") String sensorId) {
        return new SensorReadingResource(sensorId);
    }
}
