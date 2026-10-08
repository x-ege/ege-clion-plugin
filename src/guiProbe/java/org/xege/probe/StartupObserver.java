package org.xege.probe;

import com.intellij.remoterobot.RemoteRobot;
import javax.imageio.ImageIO;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/** Only GET hierarchy/screenshot observations. No clicks, input or script execution. */
public final class StartupObserver {
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        Files.createDirectories(output);
        String loopback = "http://127.0.0.1:8082/";
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        HttpResponse<String> hierarchy = http.send(
            HttpRequest.newBuilder(URI.create(loopback)).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        if (hierarchy.statusCode() != 200) {
            throw new IllegalStateException("Robot hierarchy HTTP " + hierarchy.statusCode());
        }
        Files.writeString(output.resolve("robot-hierarchy.html"), hierarchy.body());
        // The supervising process bounds the complete read-only client call.
        RemoteRobot robot = new RemoteRobot(loopback);
        if (!ImageIO.write(robot.getScreenshot(), "png", output.resolve("robot-screen.png").toFile())) {
            throw new IllegalStateException("Cannot encode Robot screenshot");
        }
        System.out.println("Matched RemoteRobot 0.11.23: hierarchy and screenshot observed on loopback");
    }
}
