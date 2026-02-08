package com.gitmini.async;

import javafx.concurrent.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * 백그라운드 작업 관리자.
 * <p>
 * 모든 Git/네트워크 작업은 이 클래스를 통해 실행한다.
 * 백그라운드 스레드에서 작업을 실행하고, 성공/실패 콜백은 UI 스레드에서 호출된다.
 * </p>
 *
 * <pre>
 * taskManager.run(
 *     () -> gitService.push(repo),       // 백그라운드 스레드
 *     result -> updateUI(result),         // UI 스레드 (성공)
 *     error -> showError(error)           // UI 스레드 (실패)
 * );
 * </pre>
 */
public class TaskManager {

    private static final Logger log = LoggerFactory.getLogger(TaskManager.class);

    private final ExecutorService executor;

    public TaskManager() {
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "gitmini-task");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 백그라운드에서 작업을 실행하고, 결과를 UI 스레드에서 콜백한다.
     *
     * @param backgroundTask 백그라운드에서 실행할 작업
     * @param onSuccess      성공 시 UI 스레드에서 호출 (결과값 전달)
     * @param onFailure      실패 시 UI 스레드에서 호출 (예외 전달)
     */
    public <T> void run(Callable<T> backgroundTask,
                        Consumer<T> onSuccess,
                        Consumer<Throwable> onFailure) {

        Task<T> fxTask = new Task<>() {
            @Override
            protected T call() throws Exception {
                return backgroundTask.call();
            }
        };

        fxTask.setOnSucceeded(event -> {
            try {
                onSuccess.accept(fxTask.getValue());
            } catch (Exception e) {
                log.error("성공 콜백에서 오류 발생", e);
            }
        });

        fxTask.setOnFailed(event -> {
            Throwable ex = fxTask.getException();
            log.error("백그라운드 작업 실패", ex);
            try {
                onFailure.accept(ex);
            } catch (Exception e) {
                log.error("실패 콜백에서 오류 발생", e);
            }
        });

        executor.submit(fxTask);
    }

    /**
     * 반환값 없는 백그라운드 작업을 실행한다.
     *
     * @param backgroundTask 백그라운드에서 실행할 작업
     * @param onSuccess      성공 시 UI 스레드에서 호출
     * @param onFailure      실패 시 UI 스레드에서 호출
     */
    public void run(Runnable backgroundTask,
                    Runnable onSuccess,
                    Consumer<Throwable> onFailure) {
        run(
                () -> {
                    backgroundTask.run();
                    return null;
                },
                result -> onSuccess.run(),
                onFailure
        );
    }

    /**
     * TaskManager를 종료한다. 앱 종료 시 호출해야 한다.
     */
    public void shutdown() {
        executor.shutdownNow();
        log.info("TaskManager 종료");
    }
}
