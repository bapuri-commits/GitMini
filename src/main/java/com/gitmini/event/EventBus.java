package com.gitmini.event;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 컴포넌트 간 통신을 위한 이벤트 버스 (Singleton).
 * <p>
 * 발행/구독 패턴으로 Controller 간 직접 참조 없이 이벤트를 전달한다.
 * 예: Push 완료 → RepoStatusChangedEvent 발행 → 대시보드가 구독하여 자동 갱신
 * </p>
 *
 * <h3>스레드 안전성</h3>
 * <p>
 * {@link #enableFxThreadDispatch(boolean)}을 활성화하면,
 * 백그라운드 스레드에서 publish해도 핸들러가 JavaFX Application Thread에서 실행된다.
 * 이를 통해 UI 핸들러가 FX 스레드 밖에서 실행되어 크래시하는 문제를 방지한다.
 * 앱 시작 시 활성화하고, 테스트에서는 비활성화 상태로 사용한다.
 * </p>
 */
public final class EventBus {

    private static final Logger log = LoggerFactory.getLogger(EventBus.class);
    private static final EventBus INSTANCE = new EventBus();

    private final Map<Class<?>, List<Consumer<?>>> subscribers = new ConcurrentHashMap<>();
    private volatile boolean fxThreadDispatch = false;

    private EventBus() {
    }

    public static EventBus getInstance() {
        return INSTANCE;
    }

    /**
     * JavaFX 스레드 안전 디스패치를 활성화/비활성화한다.
     * <p>
     * 활성화하면 모든 이벤트 핸들러가 JavaFX Application Thread에서 실행된다.
     * 앱 시작 시 호출하며, 단위 테스트에서는 비활성화 상태로 사용한다.
     * </p>
     */
    public void enableFxThreadDispatch(boolean enabled) {
        this.fxThreadDispatch = enabled;
        log.info("EventBus FX 스레드 디스패치: {}", enabled ? "활성화" : "비활성화");
    }

    /**
     * 특정 이벤트 타입에 대한 구독을 등록한다.
     *
     * @param eventType 구독할 이벤트 클래스
     * @param handler   이벤트 수신 시 실행할 핸들러
     */
    public <T> void subscribe(Class<T> eventType, Consumer<T> handler) {
        subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(handler);
        log.debug("이벤트 구독 등록: {}", eventType.getSimpleName());
    }

    /**
     * 특정 이벤트 타입에 대한 구독을 해제한다.
     */
    public <T> void unsubscribe(Class<T> eventType, Consumer<T> handler) {
        List<Consumer<?>> handlers = subscribers.get(eventType);
        if (handlers != null) {
            handlers.remove(handler);
        }
    }

    /**
     * 이벤트를 발행한다. 해당 타입의 모든 구독자에게 전달된다.
     * <p>
     * fxThreadDispatch가 활성화된 경우, 백그라운드 스레드에서 호출하더라도
     * 핸들러는 JavaFX Application Thread에서 실행된다.
     * 핸들러에서 발생한 예외는 로깅되고 다른 핸들러 실행을 막지 않는다.
     * </p>
     */
    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        List<Consumer<?>> handlers = subscribers.get(event.getClass());
        if (handlers == null || handlers.isEmpty()) {
            return;
        }

        if (fxThreadDispatch) {
            dispatchOnFxThread(() -> doPublish(event, handlers));
        } else {
            doPublish(event, handlers);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> void doPublish(T event, List<Consumer<?>> handlers) {
        for (Consumer<?> handler : handlers) {
            try {
                ((Consumer<T>) handler).accept(event);
            } catch (Exception e) {
                log.error("이벤트 핸들러 오류 [{}]: {}",
                        event.getClass().getSimpleName(), e.getMessage(), e);
            }
        }
        log.debug("이벤트 발행: {} → {}개 구독자",
                event.getClass().getSimpleName(), handlers.size());
    }

    /**
     * FX Application Thread에서 실행을 보장한다.
     * JavaFX toolkit이 초기화되지 않은 환경(단위 테스트)에서는 현재 스레드에서 실행한다.
     */
    private void dispatchOnFxThread(Runnable action) {
        try {
            if (Platform.isFxApplicationThread()) {
                action.run();
            } else {
                Platform.runLater(action);
            }
        } catch (IllegalStateException e) {
            // JavaFX toolkit not initialized (e.g., unit tests)
            action.run();
        }
    }

    /**
     * 모든 구독을 해제한다 (테스트용).
     */
    public void clear() {
        subscribers.clear();
    }
}
