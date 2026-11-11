package net.prismarin.api.admin;

public record StaffChat(String author, String message) {

    public static final int MAX_AUTHOR = 32;
    public static final int MAX_MESSAGE = 256;

    public StaffChat {
        author = author == null ? "" : author.trim();
        message = message == null ? "" : message.trim();
        if (author.isEmpty() || author.length() > MAX_AUTHOR) {
            throw new IllegalArgumentException("a staff chat author needs 1 to " + MAX_AUTHOR + " characters");
        }
        if (message.isEmpty() || message.length() > MAX_MESSAGE) {
            throw new IllegalArgumentException("a staff chat message needs 1 to " + MAX_MESSAGE + " characters");
        }
    }
}
