package com.gitmini.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private EventBus bus;

    @BeforeEach
    void setUp() {
        bus = EventBus.getInstance();
        bus.clear();
    }

    @Test
    void 이벤트_구독_및_발행() {
        AtomicReference<String> received = new AtomicReference<>();
        bus.subscribe(RepoStatusChangedEvent.class,
                event -> received.set(event.repoPath()));

        bus.publish(new RepoStatusChangedEvent("/test/path"));

        assertEquals("/test/path", received.get());
    }

    @Test
    void 여러_구독자에게_전달() {
        AtomicInteger count = new AtomicInteger(0);
        bus.subscribe(RepoStatusChangedEvent.class, event -> count.incrementAndGet());
        bus.subscribe(RepoStatusChangedEvent.class, event -> count.incrementAndGet());
        bus.subscribe(RepoStatusChangedEvent.class, event -> count.incrementAndGet());

        bus.publish(new RepoStatusChangedEvent("path"));

        assertEquals(3, count.get());
    }

    @Test
    void 구독_해제() {
        AtomicInteger count = new AtomicInteger(0);
        Consumer<RepoStatusChangedEvent> handler = event -> count.incrementAndGet();

        bus.subscribe(RepoStatusChangedEvent.class, handler);
        bus.publish(new RepoStatusChangedEvent("a"));
        assertEquals(1, count.get());

        bus.unsubscribe(RepoStatusChangedEvent.class, handler);
        bus.publish(new RepoStatusChangedEvent("b"));
        assertEquals(1, count.get()); // 증가하지 않아야 함
    }

    @Test
    void 다른_이벤트_타입은_영향_없음() {
        AtomicReference<String> repoEvent = new AtomicReference<>();
        AtomicReference<String> opEvent = new AtomicReference<>();

        bus.subscribe(RepoStatusChangedEvent.class,
                event -> repoEvent.set(event.repoPath()));
        bus.subscribe(GitOperationCompletedEvent.class,
                event -> opEvent.set(event.operation()));

        bus.publish(new RepoStatusChangedEvent("path1"));

        assertEquals("path1", repoEvent.get());
        assertNull(opEvent.get()); // 다른 타입은 영향 없음
    }

    @Test
    void 핸들러_예외가_다른_핸들러를_막지_않음() {
        AtomicInteger count = new AtomicInteger(0);

        bus.subscribe(RepoStatusChangedEvent.class, event -> {
            throw new RuntimeException("의도적 오류");
        });
        bus.subscribe(RepoStatusChangedEvent.class, event -> count.incrementAndGet());

        bus.publish(new RepoStatusChangedEvent("path"));

        assertEquals(1, count.get()); // 두 번째 핸들러는 정상 실행
    }

    @Test
    void 구독자_없는_이벤트_발행_시_에러_없음() {
        assertDoesNotThrow(() ->
                bus.publish(new RepoStatusChangedEvent("path")));
    }

    @Test
    void clear로_모든_구독_해제() {
        AtomicInteger count = new AtomicInteger(0);
        bus.subscribe(RepoStatusChangedEvent.class, event -> count.incrementAndGet());

        bus.clear();
        bus.publish(new RepoStatusChangedEvent("path"));

        assertEquals(0, count.get());
    }
}
