package zircon;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

import org.junit.Test;

public class Tests {
    
    @Test
    public void testAst() {
        assertThat(Zircon.get("(1 + 2).print"), is("3"));
    }

}
