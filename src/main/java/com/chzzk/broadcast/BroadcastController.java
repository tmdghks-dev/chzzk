package com.chzzk.broadcast;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;


@Controller
@RequestMapping("/broadcasts")
public class BroadcastController {
    private final BroadcastRepository broadcastRepository;

    public BroadcastController(BroadcastRepository broadcastRepository) {
        this.broadcastRepository = broadcastRepository;
    }

    @GetMapping
    public String list(Model model){
        model.addAttribute("broadcasts", broadcastRepository.findAll());
        return "broadcast/list";
    }

    @GetMapping("/new")
    public String form(){
        return "broadcast/form";
    }

    @PostMapping
    public String create(@RequestParam String title, @RequestParam String videoPath){
        Broadcast broadcast = broadcastRepository.save(new Broadcast(title,videoPath));
        return "redirect:/broadcasts/" + broadcast.getId();
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model){
        model.addAttribute("broadcast", broadcastRepository.findById(id).orElseThrow());
        return "broadcast/detail";
    }
}
