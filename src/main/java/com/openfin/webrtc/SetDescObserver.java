package com.openfin.webrtc;

import dev.onvoid.webrtc.SetSessionDescriptionObserver;

import java.util.concurrent.*;

import static java.util.Objects.nonNull;

public class SetDescObserver implements SetSessionDescriptionObserver, Future<Void> {
    private CountDownLatch latch = new CountDownLatch(1);

    private String error;


    @Override
    public void onSuccess() {
        latch.countDown();
    }

    @Override
    public void onFailure(String error) {
        this.error = error;

        latch.countDown();
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return latch.getCount() == 0;
    }

    @Override
    public Void get() throws InterruptedException, ExecutionException {
        latch.await();

        checkError();

        return null;
    }

    @Override
    public Void get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (latch.await(timeout, unit)) {
            checkError();
            return null;
        }
        else {
            throw new TimeoutException();
        }
    }

    private void checkError() throws ExecutionException {
        if (nonNull(error)) {
            throw new ExecutionException(error, new IllegalStateException());
        }
    }


}
