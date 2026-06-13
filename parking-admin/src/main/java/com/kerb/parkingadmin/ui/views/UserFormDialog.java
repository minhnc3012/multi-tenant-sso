package com.kerb.parkingadmin.ui.views;

import com.kerb.parkingadmin.domain.AppUser;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.TextField;

import java.util.function.Consumer;

/**
 * Dialog for creating or editing a user.
 * Create mode: email enabled, role selector shown.
 * Edit mode:   email read-only (immutable), role selector hidden.
 */
public class UserFormDialog extends Dialog {

    private final boolean editMode;
    private final Consumer<FormData> onSave;

    private final EmailField    emailField     = new EmailField("Email");
    private final TextField     firstNameField = new TextField("First Name");
    private final TextField     lastNameField  = new TextField("Last Name");
    private final TextField     phoneField     = new TextField("Phone");
    private final TextField     plateField     = new TextField("Vehicle Plate");
    private final Select<String> roleSelect    = new Select<>();

    public UserFormDialog(AppUser existingUser, Consumer<FormData> onSave) {
        this.editMode = existingUser != null;
        this.onSave   = onSave;

        setWidth("480px");
        setCloseOnOutsideClick(false);
        setCloseOnEsc(true);

        add(new H3(editMode ? "Edit User" : "Add User"));
        add(buildForm(existingUser));
        add(buildButtons());
    }

    private FormLayout buildForm(AppUser user) {
        emailField.setRequired(true);
        emailField.setWidthFull();
        emailField.setReadOnly(editMode);

        firstNameField.setRequired(true);
        firstNameField.setWidthFull();

        lastNameField.setRequired(true);
        lastNameField.setWidthFull();

        phoneField.setWidthFull();

        plateField.setWidthFull();
        plateField.setPlaceholder("e.g. 51A-12345");

        roleSelect.setLabel("IDP Role");
        roleSelect.setItems("ORG_MEMBER", "ORG_ADMIN");
        roleSelect.setValue("ORG_MEMBER");
        roleSelect.setWidthFull();

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 2));

        if (editMode) {
            emailField.setValue(user.getEmail());
            firstNameField.setValue(user.getFirstName());
            lastNameField.setValue(user.getLastName());
            if (user.getPhone() != null)        phoneField.setValue(user.getPhone());
            if (user.getVehiclePlate() != null) plateField.setValue(user.getVehiclePlate());

            form.add(emailField, firstNameField, lastNameField, phoneField, plateField);
            form.setColspan(emailField, 2);
        } else {
            form.add(emailField, roleSelect, firstNameField, lastNameField, phoneField, plateField);
            form.setColspan(emailField, 2);
        }

        return form;
    }

    private HorizontalLayout buildButtons() {
        Button save = new Button(editMode ? "Save" : "Create & Invite");
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(e -> handleSave());

        Button cancel = new Button("Cancel");
        cancel.addClickListener(e -> close());

        HorizontalLayout buttons = new HorizontalLayout(cancel, save);
        buttons.getStyle().set("justify-content", "flex-end");
        buttons.setWidthFull();
        return buttons;
    }

    private void handleSave() {
        String email     = emailField.getValue().trim();
        String firstName = firstNameField.getValue().trim();
        String lastName  = lastNameField.getValue().trim();
        String phone     = phoneField.getValue().trim();
        String plate     = plateField.getValue().trim();
        String role      = roleSelect.getValue();

        if (!editMode && email.isBlank()) {
            emailField.setErrorMessage("Email is required");
            emailField.setInvalid(true);
            return;
        }
        if (firstName.isBlank()) {
            firstNameField.setErrorMessage("First name is required");
            firstNameField.setInvalid(true);
            return;
        }
        if (lastName.isBlank()) {
            lastNameField.setErrorMessage("Last name is required");
            lastNameField.setInvalid(true);
            return;
        }

        close();
        onSave.accept(new FormData(
                email.isBlank() ? null : email,
                firstName,
                lastName,
                phone.isBlank()  ? null : phone,
                plate.isBlank()  ? null : plate,
                role != null ? role : "ORG_MEMBER"
        ));
    }

    public record FormData(
            String email,
            String firstName,
            String lastName,
            String phone,
            String vehiclePlate,
            String idpRole
    ) {}
}
