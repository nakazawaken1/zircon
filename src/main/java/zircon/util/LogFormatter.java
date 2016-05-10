package zircon.util;


import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Optional;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

/**
 * 動的に出力フォーマットを設定できるフォーマッタ
 * （Tomcatで有効にするには JREの/lib/extにこのクラスのjarを配置する必要あり）
 */
public class LogFormatter extends Formatter {

    /**
     * 出力フォーマット(1:日付 2:呼び出し元 3:ロガー名 4:レベル 5:メッセージ 6:例外)
     */
    final String format = LogManager.getLogManager().getProperty(this.getClass().getName() + ".format");

    @Override
    public String format(LogRecord r) {
        return String.format(format, r.getMillis(), r.getSourceClassName() + '.' + r.getSourceMethodName(), r.getLoggerName(),
                r.getLevel().getName(), formatMessage(r), Optional.ofNullable(r.getThrown()).map(t -> {
                    try (ByteArrayOutputStream os = new ByteArrayOutputStream(); PrintStream ps = new PrintStream(os)) {
                        t.printStackTrace(ps);
                        return os.toString();
                    } catch (IOException e) {
                    }
                    return "";
                }).orElse(""));
    }

    /**
     * ログ出力設定
     * @return 設定を読み込んだかどうか
     */
    public static boolean setup() {
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("logging.properties")) {
            if (in != null) {
                LogManager.getLogManager().readConfiguration(in);
                return true;
            }
        } catch (SecurityException | IOException e) {
            e.printStackTrace();
        }
        return false;
    }

}
