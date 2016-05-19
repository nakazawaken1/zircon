package zircon.web;

import java.io.IOException;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Optional;
import java.util.logging.Logger;

import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.StreamingOutput;
import javax.ws.rs.core.UriInfo;

import org.apache.ibatis.session.SqlSession;

import zircon.Zircon;
import zircon.data.Account;
import zircon.util.Producer;

@Path("")
public class Main {

    @Context
    HttpHeaders headers;

    @Context
    UriInfo uri;

    @Context
    ResourceInfo info;

    @GET
    public Object index() {
        try (SqlSession s = Producer.openSession()) {
            Account.Dao m = s.getMapper(Account.Dao.class);
            m.drop();
            m.create();
            Account a = new Account();
            a.name = "テスト";
            a.login_id = "test";
            m.save(a, true);
            a.tall = BigDecimal.valueOf(185.1);
            m.save(a, false);
            return String.valueOf(m.selectAll().size());
        }
        // return file("index.html");
    }

    String fileContentType(String file) {
        try {
            URI uri = getClass().getResource(file).toURI();
            Logger.getGlobal().info(uri.toString());
            try {
                return Files.probeContentType(Paths.get(uri));
            } catch (FileSystemNotFoundException e) {
                try (FileSystem fs = FileSystems.newFileSystem(uri, Collections.<String, Object> emptyMap())) {
                    return Files.probeContentType(fs.provider().getPath(uri));
                }
            }
        } catch (IOException | URISyntaxException e) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    @GET
    @Path("{file:.*}")
    public Object file(@PathParam("file") String file) {
        return Optional.ofNullable(getClass().getResourceAsStream(file)).map(s -> Response.ok(s, fileContentType(file)).build())
                .orElseGet(() -> Response.status(Status.NOT_FOUND).entity("Page not found: " + file).build());
    }

    @POST
    @Produces(MediaType.TEXT_PLAIN)
    public Object index(@FormParam("code") String code) {
        return (StreamingOutput) out -> Zircon.run(code, m -> m.put("requestHeaders", Zircon.can(
                n -> headers.getRequestHeaders().entrySet().stream().forEach(i -> n.put(i.getKey(), String.join(", ", i.getValue()))))),
                new PrintStream(out));
    }
}
