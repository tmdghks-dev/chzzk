package com.chzzk.live;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequestMapping("/lives")
public class LiveController {

    private final LiveRepository liveRepository;

    public LiveController(LiveRepository liveRepository) {
        this.liveRepository = liveRepository;
    }

    @GetMapping("/new")
    public String form() {
        return "live/form";
    }

    @PostMapping
    public String create(@RequestParam String title) {
        Live live = liveRepository.save(new Live(title));
        return "redirect:/lives/" + live.getId() + "/broadcast?key=" + live.getStreamKey();
    }

    @GetMapping("/{id}/broadcast")
    public String broadcast(@PathVariable Long id, @RequestParam String key, Model model) {
        Live live = findLive(id);
        checkKey(live, key);
        model.addAttribute("live", live);
        return "live/broadcaster";
    }

    @GetMapping("/{id}")
    public String watch(@PathVariable Long id, Model model) {
        model.addAttribute("live", findLive(id));
        return "live/viewer";
    }

    @PostMapping("/{id}/end")
    public String end(@PathVariable Long id, @RequestParam String key) {
        Live live = findLive(id);
        checkKey(live, key);
        live.end();
        liveRepository.save(live);
        return "redirect:/broadcasts";
    }

    private Live findLive(Long id) {
        return liveRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private void checkKey(Live live, String key) {
        if (!live.isOwnedBy(key)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}