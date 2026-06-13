package com.identityplatform.authserver.controller;

import com.identityplatform.authserver.security.UserPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Handles the auth-server root URL.
 * The admin portal has moved to platform-admin (port 8090).
 * This controller only handles direct browser visits to auth-server itself.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String root(Authentication auth, Model model) {
        if (auth == null || !auth.isAuthenticated()) {
            return "redirect:/login";
        }

        boolean isPlatformAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_PLATFORM_ADMIN"::equals);

        if (isPlatformAdmin) {
            // Admin portal has moved — redirect to the standalone platform-admin app.
            return "redirect:http://localhost:8090/";
        }

        // Tenant users who somehow land here — show informational page.
        if (auth.getPrincipal() instanceof UserPrincipal principal) {
            model.addAttribute("userEmail", principal.getEmail());
        } else {
            model.addAttribute("userEmail", auth.getName());
        }
        return "no-portal-access";
    }
}
