package zircon;

import java.io.PrintStream;
import java.util.Optional;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;

@Path("")
public class Main {

    @Context
    HttpHeaders headers;

    @GET
    public Object index() {
        return file("index.html");
    }

    @GET
    @Path("{file:.*}")
    public Object file(@PathParam("file") String file) {
        return Optional.ofNullable(getClass().getResourceAsStream(file)).map(s -> Response.ok(s).build())
                .orElseGet(() -> Response.status(Status.NOT_FOUND).entity("Page not found : " + file).build());
    }

    @POST
    public Object index(@FormParam("code") String code) {
        return (StreamingOutput) out -> Zircon.run(code,
                m -> headers.getRequestHeaders().entrySet().stream().forEach(i -> m.put(i.getKey(), i.getValue().get(0))),
                new PrintStream(out));
    }
}
