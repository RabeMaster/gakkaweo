package com.gakkaweo.backend.ranking.service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 단일 백엔드에서 게임 변경의 커밋과 캐시 전환을 조정합니다. */
@Component
public class RankingWriteCoordinator {

  private final ReentrantLock lock = new ReentrantLock();
  private final AtomicLong revision = new AtomicLong();

  public void lock() {
    lock.lock();
  }

  public void unlock() {
    lock.unlock();
  }

  public long revision() {
    return revision.get();
  }

  public void changed() {
    revision.incrementAndGet();
  }

  public <T> T write(TransactionTemplate template, TransactionCallback<T> action) {
    return write(template, action, result -> true);
  }

  public <T> T write(
      TransactionTemplate template, TransactionCallback<T> action, Predicate<T> affectsRanking) {
    lock();
    boolean[] registered = {false};
    boolean[] rankingChanged = {false};
    try {
      return template.execute(
          status -> {
            TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                  @Override
                  public void afterCommit() {
                    if (rankingChanged[0]) {
                      changed();
                    }
                  }

                  @Override
                  public void afterCompletion(int completionStatus) {
                    unlock();
                  }
                });
            registered[0] = true;
            T result = action.doInTransaction(status);
            rankingChanged[0] = affectsRanking.test(result);
            return result;
          });
    } finally {
      if (!registered[0]) {
        unlock();
      }
    }
  }
}
