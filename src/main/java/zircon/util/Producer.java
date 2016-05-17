package zircon.util;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

public class Producer {

    static SqlSessionFactory factory;

    public static SqlSession openSession() {
        if (factory == null) {
            factory = new SqlSessionFactoryBuilder()
                    .build(Thread.currentThread().getContextClassLoader().getResourceAsStream("mybatis-config.xml"));
        }
        return factory.openSession();
    }
}
