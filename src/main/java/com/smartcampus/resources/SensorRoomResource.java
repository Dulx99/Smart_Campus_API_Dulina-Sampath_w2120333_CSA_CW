package com.smartcampus.resources;

import com.smartcampus.models.Room;
import com.smartcampus.services.DataService;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collection;

/**
 * Resource class to manage the /api/v1/rooms path.
 */
@Path("/rooms")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SensorRoomResource {

    private final DataService dataService = DataService.getInstance();

    /**
     * GET /: Provide a comprehensive list of all rooms.
     */
    @GET
    public Collection<Room> getAllRooms() {
        return dataService.getRooms().values();
    }

    /**
     * POST /: Enable the creation of new rooms.
     */
    @POST
    public Response createRoom(Room room) {
        if (room.getId() == null || room.getId().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Room ID is required.")
                    .build();
        }
        
        // Simple duplicate check
        if (dataService.getRooms().containsKey(room.getId())) {
            return Response.status(Response.Status.CONFLICT)
                    .entity("Room with this ID already exists.")
                    .build();
        }

        dataService.getRooms().put(room.getId(), room);
        return Response.status(Response.Status.CREATED).entity(room).build();
    }

    /**
     * GET /{roomId}: Allow users to fetch detailed metadata for a specific room.
     */
    @GET
    @Path("/{roomId}")
    public Response getRoomById(@PathParam("roomId") String roomId) {
        Room room = dataService.getRooms().get(roomId);
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Room not found.")
                    .build();
        }
        return Response.ok(room).build();
    }

    /**
     * DELETE /{roomId}: Implement room decommissioning with safety logic.
     */
    @DELETE
    @Path("/{roomId}")
    public Response deleteRoom(@PathParam("roomId") String roomId) {
        Room room = dataService.getRooms().get(roomId);
        if (room == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Room not found.")
                    .build();
        }

        // Business Logic Constraint: Cannot delete a room if it has active sensors.
        boolean hasSensors = dataService.getSensors().values().stream()
                .anyMatch(sensor -> roomId.equals(sensor.getRoomId()));
                
        if (hasSensors) {
            throw new com.smartcampus.exceptions.RoomNotEmptyException("Cannot delete room: It is currently occupied by active hardware.");
        }

        dataService.getRooms().remove(roomId);
        return Response.noContent().build();
    }
}
