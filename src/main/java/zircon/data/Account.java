package zircon.data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.validation.constraints.Null;
import javax.ws.rs.DefaultValue;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.UpdateProvider;

public class Account {
    @Id
    public int id;

    @Length(64)
    public String login_id;

    @Length(64)
    @DefaultValue("A1234bcd")
    public String password;

    @Length(20)
    public String name;

    @Length({ 4, 1 })
    @Null
    public BigDecimal tall;

    public ZonedDateTime last_login;

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
                if (timestamp.value())
                    suffix += " ON UPDATE CURRENT_TIMESTAMP";
            } else if (String.class.isAssignableFrom(tt)) {
                ss.append(" VARCHAR");
            } else if (BigDecimal.class.isAssignableFrom(tt)) {
                ss.append(" DECIMAL");
            } else if (Integer.class.isAssignableFrom(tt)) {
                ss.append(" INT");
            } else if (ZonedDateTime.class.isAssignableFrom(tt)) {
                ss.append(" DATETIME");
            }
            Length length;
            if ((length = f.getDeclaredAnnotation(Length.class)) != null) {
                int[] a = length.value();
                ss.append('(').append(a[0]).append(a.length > 1 ? "," + a[1] : "").append(')');
            }
            if (suffix.isEmpty()) {
                ss.append(f.getDeclaredAnnotation(Null.class) != null ? "" : " NOT NULL");
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

    @Mapper
    public interface Sql {

        @UpdateProvider(type = Account.class, method = "createTable")
        void create();

        @UpdateProvider(type = Account.class, method = "dropTable")
        void drop();

        @Select("SELECT * FROM account")
        List<Account> selectAll();
    }
}
