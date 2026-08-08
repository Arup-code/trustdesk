package app.dexcode.trustdesk.controllers;

import app.dexcode.trustdesk.dto.AdminResetSummary;
import app.dexcode.trustdesk.services.AdminService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/reset-demo")
    public AdminResetSummary resetDemo() {
        return adminService.resetDemoData();
    }
}
