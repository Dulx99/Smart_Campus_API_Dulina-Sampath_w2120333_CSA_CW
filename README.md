# 5COSC022W: Smart Campus API

This repository contains the backend API for the University's "Smart Campus" initiative, managing physical rooms, environmental sensors, and historical telemetry readings.

## 1. API Design Overview
The API is built purely with **Java and Jakarta RESTful Web Services (JAX-RS)** using the **Jersey** framework and **Grizzly** embedded server. To comply with strict coursework constraints, no external database or Spring Boot framework is utilized. 

It implements a dynamic RESTful architecture operating under `/api/v1` featuring:
- **HATEOAS Discovery:** A root endpoint providing metadata and navigation links.
- **In-Memory Concurrency:** Leveraging `ConcurrentHashMap` arrays to safely mimic foreign-key integrity between Rooms and Sensors securely across multiple threads.
- **Sub-Resource Locators:** Deep nesting logic for `/sensors/{sensorId}/readings`.
- **Advanced Exception Mappers:** Guaranteeing leak-proof server responses (409 Conflict for deletion constraints, 422 Unprocessable Entity for invalid foreign keys, 403 Forbidden for state constraints, and a secure generic 500 handler).

---

## 2. Build & Launch Instructions

Make sure you have JDK 17 installed. The application is managed via Maven.

1. **Clone the repository:**
   ```bash
   git clone <your-github-repo-link>
   cd "csa coursework"
   ```
2. **Compile the program:**
   ```bash
   mvn clean compile
   ```
3. **Run the server:**
   ```bash
   mvn exec:java
   ```
The Grizzly server will boot up and bind to `http://localhost:8080/`. You can kill the server using `CTRL-C`.

---

## 3. Sample Interactions (cURL)

Here are five commands showcasing the API's core capabilities:

**1. HATEOAS Discovery:**
```bash
curl -i http://localhost:8080/api/v1/
```

**2. Create a Room:**
```bash
curl -i -X POST -H "Content-Type: application/json" -d "{\"id\":\"LIB-301\", \"name\":\"Library Room\", \"capacity\":10}" http://localhost:8080/api/v1/rooms
```

**3. Register a Sensor (Foreign Key Check):**
*Note: Ensure the room from command #2 exists first.*
```bash
curl -i -X POST -H "Content-Type: application/json" -d "{\"id\":\"TEMP-001\", \"type\":\"Temperature\", \"roomId\":\"LIB-301\", \"status\":\"ACTIVE\"}" http://localhost:8080/api/v1/sensors
```

**4. Filter Sensors by Type:**
```bash
curl -i http://localhost:8080/api/v1/sensors?type=Temperature
```

**5. Append a Sub-Resource Reading:**
*Note: Automatically updates the parent sensor's `currentValue` property!*
```bash
curl -i -X POST -H "Content-Type: application/json" -d "{\"value\": 21.5}" http://localhost:8080/api/v1/sensors/TEMP-001/readings
```

---

## 4. Theory & Concept Report

### **Part 1: Setup & Discovery**
**Q: Explain the default lifecycle of a JAX-RS Resource class. How does it impact data synchronization?**
By default, JAX-RS treats resource classes as Request-Scoped; a completely new instance of the resource class is instantiated for *every single incoming HTTP request*. Because the class instance is destroyed right after the response is sent, any standard variables stored directly within the class are lost. 
To prevent data loss and race conditions, we must decouple our data state from the resource lifecycle. In this project, I used a Singleton pattern for the `DataService` class, backed by thread-safe `ConcurrentHashMap` data structures. Because the `DataService` persists across all requests in the JVM memory, the ephemeral request-scoped JAX-RS controllers can safely pull and mutate data simultaneously without causing index collisions or data wiping.

**Q: Why is "Hypermedia" (HATEOAS) a hallmark of advanced RESTful design?**
HATEOAS allows a REST API to dynamically guide the client through the application's state. When a client visits my `/api/v1` root discovery endpoint, it doesn't just receive static text—it receives a map of URIs pointing exactly to `/rooms` and `/sensors`. This drastically benefits client developers because they no longer have to hardcode URLs or rely fully on external PDF documentation. If the backend routing changes, the client is automatically informed by following the links directly inside the JSON response payload.

### **Part 2: Room Management**
**Q: Implications of returning only IDs versus full objects in lists?**
Returning only IDs significantly reduces network bandwidth strain, especially if a smart campus has 10,000 rooms with heavily nested payload data. It speeds up the initial API transaction. However, it shifts the processing burden onto the client side. If the client actually needs to display the "Names" of those rooms in a dashboard menu, the client software will be forced to execute an "N+1 query issue"—firing off hundreds of individual `GET /rooms/{id}` requests, destroying battery life and severely congesting the network connection anyway.

**Q: Is your DELETE operation idempotent?**
Yes, the `DELETE /rooms/{roomId}` operation in this implementation is idempotent. Idempotency guarantees that a client can make the exact same request once, or 500 times in a row, and the server state will remain identically intended. 
If a client successfully deletes `LIB-301`, the room is removed from the `ConcurrentHashMap`. If a network glitch causes the client to send the identical DELETE request again a second later, `Map.get(roomId)` simply returns null. Instead of crashing, the server gracefully returns a `404 Not Found`. The end result server-state is exactly the same: the room no longer exists.

### **Part 3: Sensor & Filtering**
**Q: Technical consequences of Content-Type mismatches regarding @Consumes.**
Because I explicitly bound `@Consumes(MediaType.APPLICATION_JSON)` to the POST methods, the JAX-RS framework strictly listens to the HTTP `Content-Type` header sent by the client. If a client attempts to send XML or plain text data, the JAX-RS routing engine automatically intercepts the request *before* it even reaches my Java method logic. It immediately throws a `NotSupportedException` mapping back to a `415 Unsupported Media Type` HTTP status.

**Q: QueryParam vs PathParam for filtering collections.**
A Path Param (e.g. `/api/v1/sensors/type/CO2`) implies that "CO2" is a strict structural entity or child folder within the architectural hierarchy. However, filters are contextual attributes, not rigid locations. The `@QueryParam` approach (`?type=CO2`) is vastly superior because queries are optional and stackable. If we want to find CO2 sensors that are also broken, we can easily chain them (`?type=CO2&status=OFFLINE`). Trying to build complex, combinatory queries purely using Path definitions quickly becomes a routing nightmare.

### **Part 4: Sub-Resources**
**Q: Architectural benefits of the Sub-Resource Locator pattern.**
In a large enterprise API, explicitly defining deep paths (like `@Path("/sensors/{sensorId}/readings/{readingId}/diagnostics")`) inside a single monolithic controller class causes the file to become bloated, unreadable, and incredibly vulnerable to merge-conflicts in team environments.
The Sub-Resource Locator pattern (returning a new instance of `SensorReadingResource`) embraces object-oriented delegation. The parent `SensorResource` is only responsible for sensors, while it delegates all specific "Reading" logic neatly to a dedicated class. This divides the API into clean, maintainable micro-contexts.

### **Part 5: Error Handling**
**Q: What are the consequences of returning 404 instead of 422 for a missing foreign key?**
When registering a Sensor, if the `roomId` does not exist, throwing a standard 404 Not Found is semantically misleading. A client receiving a 404 will assume the actual URL endpoint `POST /sensors` doesn't exist. By utilizing an ExceptionMapper to target the specific `LinkedResourceNotFoundException` and returning a `422 Unprocessable Entity`, we explicitly communicate to the client: *"I understood your request structure perfectly, and the endpoint exists, but the JSON payload contains a semantic logic error (the parent room doesn't exist)."*

**Q: Why is a Catch-All ExceptionMapper critical for cybersecurity?**
If a backend throws an unhandled `NullPointerException`, standard Java servers will crash the request and spit the raw JVM stack trace directly into the client's browser. This is an egregious security vulnerability known as "Information Disclosure". Attackers can sift through the stack trace text to discover internal directory structures, the exact versions of the libraries being used (which they can then check for known CVE vulnerabilities), and specific database layout logic. The `GenericExceptionMapper` halts this completely by swallowing the stack trace internally, logging it securely to the backend console, and returning a generic, safe 500 error to the client.

**Q: Advantage of JAX-RS Filters for cross-cutting concerns?**
Inserting `LOGGER.info()` statements inside every single method of an API violates the DRY (Don't Repeat Yourself) principle. If leadership demands a change to the logging format, developers would have to manually edit hundreds of individual methods. JAX-RS Filters provide AOP (Aspect-Oriented Programming). By putting the logging logic in one unified `ApiLoggingFilter` class, we intercept all traffic at the edge of the framework. It keeps the core business logic incredibly clean and allows system-wide changes to be implemented instantly from a single file.
