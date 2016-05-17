package zircon.data;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

import javax.validation.constraints.Digits;
import javax.validation.constraints.Size;
import javax.ws.rs.DefaultValue;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.SelectProvider;
import org.apache.ibatis.annotations.UpdateProvider;

public class Account extends Table {

    @NotEmpty
    @Size(max = 64)
    public String login_id;

    @NotEmpty
    @Size(max = 64)
    @DefaultValue("A1234bcd")
    public String password;

    @NotEmpty
    @Size(max = 20)
    public String name;

    @Digits(integer = 3, fraction = 1)
    public BigDecimal tall;

    public ZonedDateTime last_login;

    @Mapper
    public interface Dao {

        @UpdateProvider(type = Account.class, method = "createTable")
        void create();

        @UpdateProvider(type = Account.class, method = "dropTable")
        void drop();

        @SelectProvider(type = Account.class, method = "selectAll")
        List<Account> selectAll();
    }
}
