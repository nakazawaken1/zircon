package zircon.data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.Digits;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.DefaultValue;

public class Table {
    @Id
    public int id;

    @Timestamp
    public ZonedDateTime create_time;

    @Timestamp(true)
    public ZonedDateTime update_time;

    public String createTable() {
        StringBuilder s = new StringBuilder("CREATE TABLE IF NOT EXISTS ");
        Class<?> t = getClass();
        s.append(t.getSimpleName()).append('(');
        s.append(Stream.of(t.getFields()).map(f -> {
            StringBuilder ss = new StringBuilder(f.getName());
            Class<?> tt = f.getType();
            Timestamp timestamp;
            String suffix = Optional.ofNullable(f.getDeclaredAnnotation(DefaultValue.class)).map(d -> " DEFAULT '" + d.value() + "'")
                    .orElse("");
            if (f.getDeclaredAnnotation(Id.class) != null) {
                ss.append(" SERIAL PRIMARY KEY");
            } else if ((timestamp = f.getDeclaredAnnotation(Timestamp.class)) != null) {
                ss.append(" TIMESTAMP");
                suffix = " DEFAULT CURRENT_TIMESTAMP";
                if (timestamp.value()) {
                    suffix += " ON UPDATE CURRENT_TIMESTAMP";
                }
            } else if (String.class.isAssignableFrom(tt)) {
                ss.append(" VARCHAR");
            } else if (BigDecimal.class.isAssignableFrom(tt)) {
                ss.append(" DECIMAL");
            } else if (Integer.class.isAssignableFrom(tt)) {
                ss.append(" INT");
            } else if (ZonedDateTime.class.isAssignableFrom(tt)) {
                ss.append(" DATETIME");
            }
            Digits digits;
            Size size;
            if ((digits = f.getDeclaredAnnotation(Digits.class)) != null) {
                int integer = digits.integer();
                int fraction = digits.fraction();
                ss.append('(').append(integer + fraction).append(fraction > 0 ? "," + fraction : "").append(')');
            } else if ((size = f.getDeclaredAnnotation(Size.class)) != null) {
                ss.append('(').append(size.max()).append(')');
            }
            if (!suffix.contains(" CURRENT_TIMESTAMP")) {
                ss.append((f.isAnnotationPresent(NotEmpty.class) || f.isAnnotationPresent(NotNull.class)) ? " NOT NULL" : "");
            }
            ss.append(suffix);
            return ss;
        }).collect(Collectors.joining(", ")));
        s.append(')');
        return s.toString();
    }

    public String dropTable() {
        return "DROP TABLE IF EXISTS " + getClass().getSimpleName();
    }

    public String selectAll() {
        return "SELECT * FROM " + getClass().getSimpleName();
    }
}
