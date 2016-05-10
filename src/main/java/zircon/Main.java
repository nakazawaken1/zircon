package zircon;
import java.io.PrintStream;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.FormParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

@Path("")
public class Main {

    @Context
    HttpServletRequest request;

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Response index(@FormParam("code") String code) {
        StreamingOutput output = out -> Zircon.run(code, m -> m.put("remoteAddress", request.getRemoteAddr()), new PrintStream(out));
        return Response.ok(output).build();
    }
}
