package zircon.web;

import java.net.URI;

import javax.ws.rs.ApplicationPath;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.ServerProperties;
import org.glassfish.jersey.server.TracingConfig;

import zircon.util.LogFormatter;

@ApplicationPath("")
public class Starter extends ResourceConfig {

    /**
     * コンストラクタ
     */
    public Starter() {

        // ログ出力設定
        LogFormatter.setup();

        // リソースクラスのパッケージ
        packages(Starter.class.getPackage().getName());

        // レスポンスヘッダにログ表示
        property(ServerProperties.TRACING, TracingConfig.ALL.name());
    }

    public static void main(String[] args) {
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(
                URI.create("http://localhost:8080/" + Starter.class.getAnnotation(ApplicationPath.class).value()), new Starter());
        Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));
    }
}
