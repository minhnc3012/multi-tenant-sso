package com.kerb.parkingadmin.ui;

import com.kerb.parkingadmin.ui.views.UserManagementView;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.menubar.MenuBar;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;
import jakarta.annotation.security.PermitAll;

@PermitAll
public class MainLayout extends AppLayout {

    private final AuthenticationContext authContext;

    public MainLayout(AuthenticationContext authContext) {
        this.authContext = authContext;
        createHeader();
        createDrawer();
    }

    private void createHeader() {
        DrawerToggle toggle = new DrawerToggle();

        H1 title = new H1("Kerb Parking Admin");
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("margin", "0");

        String username = authContext.getPrincipalName().orElse("Unknown");
        MenuBar userMenu = new MenuBar();
        HorizontalLayout userInfo = new HorizontalLayout(VaadinIcon.USER.create(), new Span(username));
        userInfo.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        userInfo.setSpacing(true);
        userMenu.addItem(userInfo);
        userMenu.addItem("Logout", e -> authContext.logout());

        HorizontalLayout header = new HorizontalLayout(toggle, title);
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.expand(title);
        header.setWidthFull();
        header.addClassNames("py-0", "px-m");

        addToNavbar(header, userMenu);
    }

    private void createDrawer() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Users", UserManagementView.class, VaadinIcon.USERS.create()));
        addToDrawer(nav);
    }
}
