package io.github.team404.tikitaka.performanceseat.dto;

public record QueueStatusResponse(long rank, long waitingCount) {

    public static QueueStatusResponse of(long rank, long waitingCount) {
        return new QueueStatusResponse(rank, waitingCount);
    }
}
