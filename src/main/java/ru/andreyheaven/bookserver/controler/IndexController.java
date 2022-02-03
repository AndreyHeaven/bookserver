package ru.andreyheaven.bookserver.controler;

import org.springframework.web.bind.annotation.*;
import ru.andreyheaven.bookserver.service.*;
import java.io.*;

@RestController
@RequestMapping("/")
public class IndexController {
    private final InpxReaderService inpxReaderService;

    public IndexController(InpxReaderService inpxReaderService) {
        this.inpxReaderService = inpxReaderService;
    }

    @GetMapping
    @ResponseBody
    public String index() throws IOException {
        return "index";
    }

    @GetMapping("reload")
    @ResponseBody
    public String reload() throws IOException {
        inpxReaderService.readFile();
        return "reload";
    }

}
