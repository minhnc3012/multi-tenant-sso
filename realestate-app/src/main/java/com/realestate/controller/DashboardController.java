package com.realestate.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class DashboardController {

    @GetMapping("/")
    public String home() {
        return "home";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, @AuthenticationPrincipal OidcUser user) {
        model.addAttribute("name", user.getFullName() != null ? user.getFullName() : user.getEmail());
        model.addAttribute("email", user.getEmail());
        model.addAttribute("orgId", user.getClaim("org_id"));
        model.addAttribute("roles", claimAsList(user.getClaim("roles")));
        model.addAttribute("permissions", claimAsList(user.getClaim("permissions")));
        return "dashboard";
    }

    @SuppressWarnings("unchecked")
    private List<String> claimAsList(Object claim) {
        if (claim instanceof List<?> list) {
            return (List<String>) list;
        }
        return List.of();
    }
}
