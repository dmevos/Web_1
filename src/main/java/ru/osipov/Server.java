package ru.osipov;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {
    private final static int SERVER_PORT = 9999;
    private final static int THREAD_NUMBER = 64;
    final List<String> validPaths = List.of("/index.html", "/spring.svg", "/spring.png", "/resources.html",
            "/styles.css", "/app.js", "/links.html", "/forms.html", "/classic.html", "/events.html", "/events.js");
    private final ExecutorService executor;

    //Создание объекта сервера и создание пула потоков
    public Server() {
        executor = Executors.newFixedThreadPool(THREAD_NUMBER);
    }

    //Запуск сервера
    public void start() {
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            while (true) {
                final var socket = serverSocket.accept();
                log("[INFO] Новое подключение: " + socket.getRemoteSocketAddress());
                executor.execute(() -> handleNewConnection(socket));
            }
        } catch (IOException e) {
            log("[ERROR] Ошибка запуска сервера: " + e.getMessage());
        }
    }

    //Обработка запроса от нового подключения
    public void handleNewConnection(Socket socket) {
        try (final var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             final var out = new BufferedOutputStream(socket.getOutputStream())) {

            // read only request line for simplicity
            // must be in form GET /path HTTP/1.1
            final var requestLine = in.readLine();
            final var parts = requestLine.split(" ");

            // just close socket
            if (parts.length != 3) return;

            final var path = parts[1];
            if (!validPaths.contains(path)) {
                out.write((
                        """
                                HTTP/1.1 404 Not Found\r
                                Content-Length: 0\r
                                Connection: close\r
                                \r
                                """
                ).getBytes());
                out.flush();
                return;
            }

            final var filePath = Path.of(".", "public", path);
            final var mimeType = Files.probeContentType(filePath);

            // special case for classic
            if (path.equals("/classic.html")) {
                final var template = Files.readString(filePath);
                final var content = template.replace(
                        "{time}",
                        LocalDateTime.now().toString()
                ).getBytes();
                out.write((
                        "HTTP/1.1 200 OK\r\n" +
                                "Content-Type: " + mimeType + "\r\n" +
                                "Content-Length: " + content.length + "\r\n" +
                                "Connection: close\r\n" +
                                "\r\n"
                ).getBytes());
                out.write(content);
                out.flush();
                return;
            }

            final var length = Files.size(filePath);
            out.write((
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: " + mimeType + "\r\n" +
                            "Content-Length: " + length + "\r\n" +
                            "Connection: close\r\n" +
                            "\r\n"
            ).getBytes());
            Files.copy(filePath, out);
            out.flush();

        } catch (IOException e) {
            log("[ERROR] Ошибка установки нового соединения: " + e.getMessage());
        }
    }

    //Логирование сообщений
    private void log(String message) {
        System.out.println(new Timestamp(System.currentTimeMillis()) + ": " + message);
    }
}