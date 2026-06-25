package com.caciopee.loganalyzer.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executor;

@Service
public class ImportBackgroundService {

    private final Executor importTaskExecutor;

    public ImportBackgroundService(@Qualifier("importTaskExecutor") Executor importTaskExecutor) {
        this.importTaskExecutor = importTaskExecutor;
    }

    public void schedule(Runnable task) {
        importTaskExecutor.execute(task);
    }
}
