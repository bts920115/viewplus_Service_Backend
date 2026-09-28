package com.bts.viewplustest;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ViewerController {

    /**
     * 웹 시청자 진입점이다. Spring은 루트 경로의 index.html만 welcome page로
     * 인식하므로 /viewer와 /viewer/도 정적 뷰어 파일로 명시적으로 전달한다.
     */
    @GetMapping({"/viewer", "/viewer/"})
    public String viewer() {
        return "forward:/viewer/index.html";
    }
}
