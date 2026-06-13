package com.kerb.parkingadmin.ui.views;

import com.kerb.parkingadmin.domain.AppUser;
import com.kerb.parkingadmin.service.AppUserService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.kerb.parkingadmin.ui.MainLayout;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.domain.PageRequest;

import java.util.Set;
import java.util.UUID;

@Route(value = "users", layout = MainLayout.class)
@RouteAlias(value = "", layout = MainLayout.class)
@PageTitle("Users | Kerb Parking Admin")
@PermitAll
public class UserManagementView extends VerticalLayout {

    private final AppUserService userService;

    private final Grid<AppUser> grid = new Grid<>(AppUser.class, false);
    private final TextField searchField = new TextField();

    public UserManagementView(AppUserService userService) {
        this.userService = userService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(new H2("User Management"));
        add(buildToolbar());
        add(buildGrid());

        refreshGrid(null);
    }

    private HorizontalLayout buildToolbar() {
        searchField.setPlaceholder("Search by name or email...");
        searchField.setPrefixComponent(VaadinIcon.SEARCH.create());
        searchField.setClearButtonVisible(true);
        searchField.setWidth("320px");
        searchField.setValueChangeMode(ValueChangeMode.LAZY);
        searchField.addValueChangeListener(e -> refreshGrid(e.getValue()));

        Button addButton = new Button("Add User", VaadinIcon.PLUS.create());
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addButton.addClickListener(e -> openCreateDialog());

        Button syncButton = new Button("Sync from IDP", VaadinIcon.REFRESH.create());
        syncButton.addThemeVariants(ButtonVariant.LUMO_CONTRAST);
        syncButton.getElement().setAttribute("title", "Pull users created in IDP into this app");
        syncButton.addClickListener(e -> syncFromIdp(syncButton));

        HorizontalLayout toolbar = new HorizontalLayout(searchField, syncButton, addButton);
        toolbar.setWidthFull();
        toolbar.expand(searchField);
        toolbar.setAlignItems(Alignment.END);
        return toolbar;
    }

    private Grid<AppUser> buildGrid() {
        grid.setSizeFull();

        grid.addColumn(AppUser::getFullName)
                .setHeader("Name").setSortable(true).setFlexGrow(1);
        grid.addColumn(AppUser::getEmail)
                .setHeader("Email").setSortable(true).setFlexGrow(2);
        grid.addColumn(AppUser::getPhone)
                .setHeader("Phone").setFlexGrow(1);
        grid.addColumn(AppUser::getVehiclePlate)
                .setHeader("Vehicle Plate").setFlexGrow(1);
        grid.addColumn(u -> u.getStatus().name())
                .setHeader("Status").setFlexGrow(1);
        grid.addColumn(u -> u.getIdpSyncStatus().name())
                .setHeader("IDP Sync").setFlexGrow(1);

        grid.addComponentColumn(user -> {
            Button editBtn = new Button(VaadinIcon.EDIT.create());
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            editBtn.addClickListener(e -> openEditDialog(user));

            Button statusBtn;
            if (user.getStatus() == AppUser.AppUserStatus.ACTIVE) {
                statusBtn = new Button(VaadinIcon.BAN.create());
                statusBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                        ButtonVariant.LUMO_ERROR);
                statusBtn.getElement().setAttribute("title", "Suspend");
                statusBtn.addClickListener(e -> confirmSuspend(user));
            } else {
                statusBtn = new Button(VaadinIcon.CHECK.create());
                statusBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL,
                        ButtonVariant.LUMO_SUCCESS);
                statusBtn.getElement().setAttribute("title", "Activate");
                statusBtn.addClickListener(e -> activate(user.getId()));
            }

            HorizontalLayout actions = new HorizontalLayout(editBtn, statusBtn);
            actions.setSpacing(false);
            return actions;
        }).setHeader("Actions").setWidth("120px").setFlexGrow(0);

        return grid;
    }

    private void refreshGrid(String search) {
        var page = userService.search(
                search == null || search.isBlank() ? null : search,
                PageRequest.of(0, 200));
        grid.setItems(page.getContent());
    }

    private void openCreateDialog() {
        UserFormDialog dialog = new UserFormDialog(null, request -> {
            try {
                userService.createUser(new AppUserService.CreateUserRequest(
                        request.email(), request.firstName(), request.lastName(),
                        request.phone(), request.vehiclePlate(),
                        request.idpRole() != null ? Set.of(request.idpRole()) : null));
                showSuccess("User created and invite sent to " + request.email());
                refreshGrid(searchField.getValue());
            } catch (Exception ex) {
                showError("Failed to create user: " + ex.getMessage());
            }
        });
        dialog.open();
    }

    private void openEditDialog(AppUser user) {
        UserFormDialog dialog = new UserFormDialog(user, request -> {
            try {
                userService.updateUser(user.getId(), new AppUserService.UpdateUserRequest(
                        request.firstName(), request.lastName(),
                        request.phone(), request.vehiclePlate()));
                showSuccess("User updated");
                refreshGrid(searchField.getValue());
            } catch (Exception ex) {
                showError("Failed to update user: " + ex.getMessage());
            }
        });
        dialog.open();
    }

    private void confirmSuspend(AppUser user) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Suspend User");
        dialog.setText("Suspend " + user.getFullName() + "? They won't be able to log in.");
        dialog.setCancelable(true);
        dialog.setConfirmText("Suspend");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                userService.suspendUser(user.getId());
                showSuccess("User suspended");
                refreshGrid(searchField.getValue());
            } catch (Exception ex) {
                showError("Failed to suspend user: " + ex.getMessage());
            }
        });
        dialog.open();
    }

    private void activate(UUID id) {
        try {
            userService.activateUser(id);
            showSuccess("User activated");
            refreshGrid(searchField.getValue());
        } catch (Exception ex) {
            showError("Failed to activate user: " + ex.getMessage());
        }
    }

    private void syncFromIdp(Button syncButton) {
        syncButton.setEnabled(false);
        syncButton.setText("Syncing...");
        UI ui = UI.getCurrent();

        // Run in background thread to avoid blocking Vaadin UI thread
        new Thread(() -> {
            try {
                AppUserService.SyncResult result = userService.syncFromIdp();
                ui.access(() -> {
                    syncButton.setEnabled(true);
                    syncButton.setText("Sync from IDP");
                    refreshGrid(searchField.getValue());
                    String msg = String.format(
                        "Sync complete — %d total: %d created, %d updated, %d unchanged",
                        result.total(), result.created(), result.updated(), result.skipped());
                    Notification n = Notification.show(msg, 5000, Notification.Position.BOTTOM_END);
                    n.addThemeVariants(result.created() > 0 || result.updated() > 0
                        ? NotificationVariant.LUMO_SUCCESS
                        : NotificationVariant.LUMO_CONTRAST);
                });
            } catch (Exception ex) {
                ui.access(() -> {
                    syncButton.setEnabled(true);
                    syncButton.setText("Sync from IDP");
                    showError("Sync failed: " + ex.getMessage());
                });
            }
        }).start();
    }

    private void showSuccess(String msg) {
        Notification n = Notification.show(msg, 3000, Notification.Position.BOTTOM_END);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    private void showError(String msg) {
        Notification n = Notification.show(msg, 5000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
