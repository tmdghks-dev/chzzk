package com.chzzk;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 주소만 치고 들어오면(앱 포함) 방송 목록으로 보낸다
@Controller
public class HomeController {
    @GetMapping("/")
    public String home() {
        return "redirect:/broadcasts";
    }
}
