package zircon;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import org.junit.Test;

import zircon.Zircon.Ast;
import zircon.Zircon.ZrNumber;

public class Tests {
    
    @Test
    public void testAst() {
        assertThat(new Ast(new ZrNumber("1"), new ZrNumber("2"), new ZrNumber("3")).toString(), is("1, 2, 3"));
    }

}
