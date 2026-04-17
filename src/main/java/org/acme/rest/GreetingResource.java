package org.acme.rest;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/")
@Tag(name = "Greeting", description = "Plain-text hello and goodbye endpoints.")
public class GreetingResource {

    private final String greetingMessage;

    // Constructor injection
    public GreetingResource(
            @ConfigProperty(name = "greeting.message", defaultValue = "Hello from Quarkus REST") String greetingMessage) {
        this.greetingMessage = greetingMessage;
    }

    @GET
    @Path("/hello")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(
            operationId = "getHello",
            summary = "Greeting message",
            description = "Returns the configured greeting string (see `greeting.message`).")
    public String hello() {
        return greetingMessage;
    }



    @GET
    @Path("/bye")
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(
            operationId = "getBye",
            summary = "Goodbye message",
            description = "Returns a fixed goodbye string (demo endpoint).")
    public String bye() {
        return "Goodbye!";
    }
}
