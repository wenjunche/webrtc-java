package com.openfin.webrtc;

import dev.onvoid.webrtc.CreateSessionDescriptionObserver;
import dev.onvoid.webrtc.RTCSessionDescription;

import java.util.concurrent.*;

import static java.util.Objects.nonNull;

public class CreateDescObserver implements CreateSessionDescriptionObserver, Future<RTCSessionDescription> {
    private CountDownLatch latch = new CountDownLatch(1);

    private RTCSessionDescription description;

    private String error;


    @Override
    public void onSuccess(RTCSessionDescription description) {
        this.description = description;

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
    public RTCSessionDescription get() throws InterruptedException, ExecutionException {
        latch.await();

        checkError();

        return description;
    }

    @Override
    public RTCSessionDescription get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (latch.await(timeout, unit)) {
            checkError();
            return description;
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
