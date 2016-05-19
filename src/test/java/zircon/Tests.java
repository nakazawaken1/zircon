package zircon;

public class Tests extends Tester {{
    
    beforeEach(() -> System.out.println("[start]"));
    
    afterEach(() -> System.out.println("[end]"));

    expect("1 足す 2 は 3", () -> 1 + 2).toEqual(3);

    group("割り算", () -> {

        beforeEach(() -> System.out.println("[start2]"));
        
        afterEach(() -> System.out.println("[end2]"));

        expect("6 割る 3 は 2", () -> 6 / 3).toEqual(2);

        group("例外", () -> {
            expect("1 割る 0 は例外", () -> 1 / 0).toThrow(ArithmeticException.class);
            expect("0 割る 0 は例外", () -> 0 / 0).toThrow(ArithmeticException.class);
        });
    });
    
    expect("こんにちはを出力1", () -> {
        System.out.println("こん");
        System.out.println("にちは");
    }).toOutput("こん", "にちは");
    
    expect("こんにちはを出力2", () -> "こん" + System.lineSeparator() + "にちは").toEqual("こん", "にちは");

}}