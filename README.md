# Smart Campus — Sensor & Room Management API

**Module:** 5COSC022W Client-Server Architectures  
**Student:** Dulina Sampath  
**Student ID:** W2120333  
**GitHub Repository:** https://github.com/Dulx99/csa-coursework

---

## Overview

This project is a RESTful backend API built for the university's Smart Campus initiative. The system manages physical rooms, the environmental sensors installed in them (temperature monitors, CO2 detectors, occupancy trackers), and the historical data those sensors record over time.

The API is written entirely in Java using the **JAX-RS (Jakarta RESTful Web Services)** specification with **Jersey 3** as the implementation, running on a **Grizzly** embedded HTTP server. No external database is used — all data is stored in memory using thread-safe Java collections as per the coursework constraints.

---

## How to Build and Run

You will need **JDK 17** installed. Maven is used to manage dependencies and run the project.

**Step 1 — Clone the repository:**
```bash
git clone https://github.com/Dulx99/csa-coursework.git
cd "csa coursework"
```

**Step 2 — Compile:**
```bash
mvn clean compile
```

**Step 3 — Start the server:**
```bash
mvn exec:java
```

Once running, the API is available at `http://localhost:8080/api/v1/`. Stop the server with `CTRL-C`.

---

## Sample curl Commands

Below are five commands that cover the main features of the API.

**1. Root Discovery Endpoint**
```bash
curl -i http://localhost:8080/api/v1/
```

**2. Create a Room**
```bash
curl -i -X POST -H "Content-Type: application/json" \
  -d "{\"id\":\"LIB-301\", \"name\":\"Library Quiet Study\", \"capacity\":30}" \
  http://localhost:8080/api/v1/rooms
```

**3. Register a Sensor (validates that the roomId exists)**
```bash
curl -i -X POST -H "Content-Type: application/json" \
  -d "{\"id\":\"TEMP-001\", \"type\":\"Temperature\", \"status\":\"ACTIVE\", \"roomId\":\"LIB-301\"}" \
  http://localhost:8080/api/v1/sensors
```

**4. Filter Sensors by Type**
```bash
curl -i "http://localhost:8080/api/v1/sensors?type=Temperature"
```

**5. Add a Sensor Reading (updates the parent sensor's current value as a side effect)**
```bash
curl -i -X POST -H "Content-Type: application/json" \
  -d "{\"value\": 21.5}" \
  http://localhost:8080/api/v1/sensors/TEMP-001/readings
```

---

## Conceptual Report

### Part 1 — Architecture & Setup

**Q: Explain the default lifecycle of a JAX-RS Resource class. Is a new instance created per request or is it a singleton? How does this affect data synchronisation?**

By default, JAX-RS creates a brand new instance of each resource class for every incoming HTTP request. Once the request is handled and a response is sent, that instance is discarded. This means any data stored as a field inside the resource class itself would be completely lost between requests — which is not what we want.

To get around this, I separated the data storage entirely from the resource classes. The `DataService` class follows a Singleton pattern, meaning only one instance of it ever exists in memory across the entire lifetime of the application. The resource classes (`SensorRoomResource`, `SensorResource`, etc.) all call `DataService.getInstance()` to access a shared reference to the same data store.

For thread safety, the data is stored in `ConcurrentHashMap` collections rather than standard `HashMap`. The reason for this is that multiple HTTP requests can arrive at the same time (especially in a real campus system with hundreds of sensors reporting data simultaneously), and a standard `HashMap` is not safe to read and write from multiple threads at once — it can throw exceptions or silently corrupt data. `ConcurrentHashMap` handles this internally, so two requests modifying the sensor list at the same time won't step on each other.

**Q: Why is Hypermedia (HATEOAS) considered a key feature of good RESTful design, and how does it help client developers?**

HATEOAS stands for Hypermedia as the Engine of Application State. The idea is that an API response should not just contain data — it should also contain links telling the client where to go next, rather than the client having to know the URL structure in advance.

In this project, the root endpoint `GET /api/v1/` returns a JSON object that includes a `resources` map pointing to `/api/v1/rooms` and `/api/v1/sensors`. This means a client application does not need to hardcode those URLs or rely on external documentation. If the API is ever restructured (for example, rooms move to `/api/v2/locations`), the client just needs to re-read the discovery endpoint and update accordingly — rather than tracking down every hardcoded URL in the codebase.

For a campus system that might be consumed by mobile apps, building automation systems, and third-party dashboards all at once, this kind of self-describing API is genuinely useful.

---

### Part 2 — Room Management

**Q: What are the trade-offs between returning a list of full room objects versus returning just a list of IDs?**

Returning full room objects (id, name, capacity, sensorIds) in a list response means the client gets everything it needs in one call, which is convenient. However, if there are thousands of rooms and each room has a large list of nested sensor IDs, that single response becomes very large and slow to transfer — especially on a mobile network.

Returning only IDs keeps the initial response small and fast, which is better for bandwidth. The trade-off is that the client now has to make a separate `GET /rooms/{id}` call for each room it wants details on. If the client needs details on 500 rooms, that's 500 additional network requests, which can actually end up being slower and more expensive than just returning the full objects in the first place. This is commonly referred to as the N+1 problem.

In this implementation I return the full room objects in the list because the campus use case involves facility managers needing to see room names and capacities at a glance, and the scale is manageable.

**Q: Is your DELETE /rooms/{roomId} operation idempotent? What happens if the same request is sent multiple times?**

Yes, the DELETE operation in this implementation is idempotent. Idempotency means that sending the same request multiple times produces the same end state as sending it once.

Here is what happens step by step: When `DELETE /rooms/LIB-301` is first received, the code looks up `LIB-301` in the rooms map. If it exists and has no sensors attached, it is removed and a `204 No Content` is returned. If the same request is sent again, the lookup returns null because the room is already gone. The code then returns a `404 Not Found` rather than crashing. The important thing is that the *state of the server* is the same after both calls — `LIB-301` does not exist — even though the HTTP status codes differ. This is the correct and expected behaviour for a DELETE operation according to REST principles.

---

### Part 3 — Sensor Operations

**Q: The POST endpoint uses @Consumes(MediaType.APPLICATION_JSON). What happens if a client sends data in a different format like text/plain or application/xml?**

The `@Consumes(MediaType.APPLICATION_JSON)` annotation tells JAX-RS that this method only accepts requests where the `Content-Type` header is `application/json`. If a client sends a POST request with `Content-Type: text/plain` or `Content-Type: application/xml`, the Jersey framework intercepts the request before it even reaches my Java method. It checks the declared `@Consumes` contract, sees that the incoming content type does not match, and automatically throws a `NotSupportedException`. This gets mapped to an HTTP `415 Unsupported Media Type` response. No custom code is needed — this validation is handled entirely by the framework, which is one of the benefits of using JAX-RS annotations properly.

**Q: Why is filtering via @QueryParam (e.g., ?type=CO2) better than putting the filter value in the URL path (e.g., /sensors/type/CO2)?**

A path segment like `/sensors/type/CO2` implies that "CO2" is a specific resource or a fixed structural location in the API hierarchy — similar to how `/sensors/TEMP-001` means "the sensor with ID TEMP-001". That works fine for identifying specific resources, but it does not work well for filtering.

Query parameters are designed for exactly this kind of optional, contextual filtering. Using `@QueryParam`, the filter is completely optional — `GET /sensors` still works without it and returns everything. More importantly, query parameters compose naturally. If a facility manager wants to find CO2 sensors that are currently offline, the query would be `?type=CO2&status=OFFLINE`. Trying to express that same combination as URL path segments gets messy very quickly and creates a routing explosion where you need separate paths for every possible filter combination.

---

### Part 4 — Sub-Resources

**Q: What are the architectural benefits of the Sub-Resource Locator pattern compared to putting all nested paths in one controller?**

The sub-resource locator pattern means that when a request comes in for `/sensors/TEMP-001/readings`, the `SensorResource` class does not handle the reading logic itself. Instead, it has a method annotated with `@Path("/{sensorId}/readings")` that creates and returns a new `SensorReadingResource` instance, which then handles the actual GET and POST operations for readings.

The main benefit is separation of concerns. If all the room, sensor, and reading logic lived in one class, that file would grow to hundreds or thousands of lines and become very hard to maintain or debug. By delegating to a separate class, each resource class has a clearly defined responsibility. `SensorResource` knows about sensors. `SensorReadingResource` knows about readings for a specific sensor. Changes to how readings work do not require touching the sensor logic at all.

In a real team environment, different developers can work on different resource classes simultaneously without constantly conflicting. The pattern also makes unit testing much simpler because each class can be tested in isolation.

---

### Part 5 — Error Handling & Logging

**Q: Why is HTTP 422 (Unprocessable Entity) more accurate than 404 (Not Found) when a sensor is registered with a roomId that doesn't exist?**

A 404 Not Found response is meant to say: "the URL you requested does not exist on this server." If a client calls `POST /api/v1/sensors` and receives a 404, they will likely assume the `/sensors` endpoint itself does not exist, which is wrong — it is working perfectly fine.

The real problem is a data validation failure: the JSON body was syntactically valid, the endpoint exists, but the value of `roomId` refers to a resource that does not exist in the system. HTTP 422 is specifically designed for this situation — it means "I understood your request, I can parse the JSON, but I cannot process it because the data inside it is semantically invalid." This gives the client a much clearer signal about exactly what went wrong, and they know to fix the `roomId` value in their request rather than wondering why the URL is broken.

**Q: What are the cybersecurity risks of exposing Java stack traces in API error responses?**

A raw Java stack trace contains a large amount of information that is not meant for external consumers. It reveals the internal package structure of the application (e.g., `com.smartcampus.services.DataService`), the exact versions of third-party libraries being used, the filenames and line numbers of the source code, and the logical flow of execution up to the point of failure.

An attacker who receives a stack trace can cross-reference the library versions against public vulnerability databases (like the National Vulnerability Database) to find known exploits. They can also map out the internal code structure to understand how to craft targeted requests that trigger specific failure paths. For example, knowing a `NullPointerException` occurs at line 47 of `DataService.java` tells an attacker exactly what input to send to cause predictable crashes.

The `GenericExceptionMapper<Throwable>` in this project catches all unexpected exceptions, logs the full stack trace server-side where only developers can see it, and returns a generic JSON error message to the client that reveals nothing about the internal implementation.

**Q: Why is using JAX-RS filters for logging better than adding Logger.info() statements to every resource method?**

If logging statements are placed manually inside each resource method, there is immediate code duplication. Every single `getAllRooms()`, `createSensor()`, `addReading()`, and so on would need its own logging lines. When the format or destination of those logs needs to change — say, we want to start including a request ID or change from console output to a file — every one of those methods needs to be edited individually. It is easy to miss some and end up with inconsistent logs.

JAX-RS `ContainerRequestFilter` and `ContainerResponseFilter` intercept traffic at the framework level, before it even reaches the resource method. The `ApiLoggingFilter` in this project logs the HTTP method, URI, and response status code for every single request automatically — even for routes that don't exist (404s) — without any resource method needing to know about it. Logging is a cross-cutting concern and is much better handled this way, keeping the business logic in resource classes clean and focused on what they are actually supposed to do.
