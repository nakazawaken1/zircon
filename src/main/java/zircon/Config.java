package zircon;

import java.net.URI;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.TracingConfig;

@ApplicationPath("")
public class Config extends Application {

    static final String uri = "http://localhost:8080/";

    public static void main(String[] args) {
        GrizzlyHttpServerFactory.createHttpServer(URI.create(uri), new ResourceConfig().packages(Config.class.getPackage().getName())
                .property(ServerProperties.TRACING, TracingConfig.ALL.name()));
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
