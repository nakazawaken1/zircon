package zircon.web;

import javax.ws.rs.ApplicationPath;

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
        packages("zircon");

        // レスポンスヘッダにログ表示
        property(ServerProperties.TRACING, TracingConfig.ALL.name());
    }

// 単体で動作せる場合（pomのjersey-container-grizzly2-http, jersey-weld2-seを有効にしweld-servlet-coreを無効にする）
//    public static void main(String[] args) {
//        Weld weld = new Weld();
//        weld.initialize();
//        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(
//                URI.create("http://localhost:8080/" + Starter.class.getAnnotation(ApplicationPath.class).value()), new Starter());
//        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
//            server.shutdownNow();
//            weld.shutdown();
//        }));
//    }
}
