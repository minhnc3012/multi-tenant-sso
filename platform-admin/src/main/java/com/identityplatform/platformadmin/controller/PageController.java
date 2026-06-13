package com.identityplatform.platformadmin.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String root() {
        return "redirect:/admin/users";
    }

    @GetMapping("/admin/users")
    public String users(Model model, Authentication auth) {
        populateModel(model, auth, "users");
        return "admin/users";
    }

    @GetMapping("/admin/organizations")
    public String organizations(Model model, Authentication auth) {
        populateModel(model, auth, "organizations");
        return "admin/organizations";
    }

    @GetMapping("/admin/roles")
    public String roles(Model model, Authentication auth) {
        populateModel(model, auth, "roles");
        return "admin/roles";
    }

    @GetMapping("/admin/clients")
    public String clients(Model model, Authentication auth) {
        populateModel(model, auth, "clients");
        return "admin/clients";
    }

    /**
     * platform-admin exclusively serves PLATFORM_ADMIN users — isPlatformAdmin is always true.
     * orgSlug is always "platform" since this portal manages the platform org.
     * userEmail is read from the "email" custom claim added by MultiTenantTokenCustomizer.
     */
    private void populateModel(Model model, Authentication auth, String page) {
        model.addAttribute("currentPage", page);
        model.addAttribute("orgSlug", "platform");
        model.addAttribute("orgName", "Platform Administration");
        model.addAttribute("isPlatformAdmin", true);

        String email = auth.getName();
        if (auth instanceof OAuth2AuthenticationToken token
                && token.getPrincipal() instanceof OidcUser oidcUser) {
            String claimEmail = oidcUser.getClaim("email");
            if (claimEmail != null) email = claimEmail;
        }
        model.addAttribute("userEmail", email);
    }
}
