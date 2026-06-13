package com.identityplatform.authserver.controller;

import com.identityplatform.authserver.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            Model model) {

        if (error != null) {
            model.addAttribute("errorMessage", "Invalid email or password.");
        }
        if (logout != null) {
            model.addAttribute("logoutMessage", "You have been successfully logged out.");
        }
        return "login";
    }

    @GetMapping("/error/org-access-denied")
    public String orgAccessDenied(Model model) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal up) {
            model.addAttribute("userEmail", up.getEmail());
        }
        return "org-access-denied";
    }

    @GetMapping("/invite/accept")
    public String acceptInvitePage(
            @RequestParam String token,
            Model model) {
        model.addAttribute("token", token);
        return "invite-accept";
    }
}
