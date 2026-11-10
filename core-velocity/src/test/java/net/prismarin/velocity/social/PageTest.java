package net.prismarin.velocity.social;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PageTest {

    private final List<Integer> numbers = IntStream.range(0, 30).boxed().toList();

    @Test
    void slicesAndCountsPages() {
        Page<Integer> first = Page.of(numbers, 0, 28);
        assertEquals(28, first.entries().size());
        assertEquals(2, first.pages());
        assertEquals(30, first.total());
        Page<Integer> second = Page.of(numbers, 1, 28);
        assertEquals(List.of(28, 29), second.entries());
    }

    @Test
    void clampsPagesOutsideTheRange() {
        assertEquals(1, Page.of(numbers, 9, 28).page());
        assertEquals(0, Page.of(numbers, -3, 28).page());
        Page<Integer> empty = Page.of(List.of(), 4, 28);
        assertEquals(0, empty.page());
        assertEquals(1, empty.pages());
        assertEquals(List.of(), empty.entries());
    }
}
