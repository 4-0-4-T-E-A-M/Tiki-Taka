package io.github.team404.tikitaka.notification.service;

public interface EmailSender {

    void sendReservationCompleteEmail(String email, String userName, String performanceName);
}
