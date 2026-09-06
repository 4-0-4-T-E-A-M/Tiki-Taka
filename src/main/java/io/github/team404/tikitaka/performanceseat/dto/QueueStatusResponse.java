package io.github.team404.tikitaka.performanceseat.dto;

public record QueueStatusResponse(long rank, long waitingCount, boolean admitted) {

    public static QueueStatusResponse of(long rank, long waitingCount, boolean admitted) {
        return new QueueStatusResponse(rank, waitingCount, admitted);
    }
}
