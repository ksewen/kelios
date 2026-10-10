package com.github.ksewen.kelios.platform;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StatusController {
    @GetMapping("/status")
    public Status status() {
        return new Status("KELIOS", "ready");
    }

    public record Status(String application, String status) {}
}
