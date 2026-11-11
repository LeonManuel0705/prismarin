package net.prismarin.api.admin;

import java.util.concurrent.CompletableFuture;

public interface NotificationService {

    CompletableFuture<Delivery> notifyStaff(StaffAlert alert);

    default CompletableFuture<Delivery> notifyStaff(String source, NoticeText text) {
        return notifyStaff(StaffAlert.of(source, text));
    }

    CompletableFuture<Delivery> staffChat(StaffChat message);

    default CompletableFuture<Delivery> staffChat(String author, String message) {
        return staffChat(new StaffChat(author, message));
    }

    CompletableFuture<Delivery> broadcast(Broadcast broadcast);
}
