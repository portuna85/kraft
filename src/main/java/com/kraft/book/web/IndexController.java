package com.kraft.book.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class IndexController {

    @GetMapping(value = "/", produces = "text/html; charset=UTF-8")
    public String index() {

        return "index"; // returns the index.html file located in src/main/resources/templates
    }
}
