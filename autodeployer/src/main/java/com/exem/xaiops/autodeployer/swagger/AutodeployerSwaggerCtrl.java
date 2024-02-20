package com.exem.xaiops.autodeployer.swagger;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import springfox.documentation.annotations.ApiIgnore;

@ApiIgnore
@Controller
public class AutodeployerSwaggerCtrl {
    @GetMapping("/swagger")
    public String swagger(){
        return "redirect:/swagger-ui.html";
    }
}
