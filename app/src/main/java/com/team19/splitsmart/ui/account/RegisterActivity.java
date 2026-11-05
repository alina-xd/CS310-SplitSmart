package com.team19.splitsmart.ui.account;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.team19.splitsmart.R;
import com.team19.splitsmart.data.model.User;
import com.team19.splitsmart.service.AuthService;
import com.team19.splitsmart.service.ValidationException;
import com.team19.splitsmart.ui.EdgeToEdgeUtil;
import com.team19.splitsmart.ui.group.GroupListActivity;
import com.team19.splitsmart.util.Callback;

/** C1 Account Screens. Owner: Part A. Registration form. */
public class RegisterActivity extends AppCompatActivity {

    private AuthService authService;
    private EditText editName;
    private EditText editEmail;
    private EditText editUscId;
    private EditText editPassword;
    private Button buttonCreate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        EdgeToEdgeUtil.applySystemBarPadding(findViewById(R.id.main));
        authService = new AuthService();
        editName = findViewById(R.id.editName);
        editEmail = findViewById(R.id.editEmail);
        editUscId = findViewById(R.id.editUscId);
        editPassword = findViewById(R.id.editPassword);
        buttonCreate = findViewById(R.id.buttonCreateAccount);
        buttonCreate.setOnClickListener(v -> onCreateAccountClicked());
    }

    private void onCreateAccountClicked() {
        String name = editName.getText().toString();
        String email = editEmail.getText().toString();
        String uscId = editUscId.getText().toString();
        String password = editPassword.getText().toString();
        if (!checkAllFields(name, email, uscId, password)) {
            return;
        }
        buttonCreate.setEnabled(false);
        authService.register(name, email, uscId, password, new Callback<User>() {
                    @Override
                    public void onSuccess(User user) {
                        Intent intent = new Intent(RegisterActivity.this, GroupListActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        buttonCreate.setEnabled(true);
                        if (e instanceof ValidationException) {
                            showFieldError(((ValidationException) e).getField(), e.getMessage());
                        } else {
                            Toast.makeText(RegisterActivity.this, e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    /**
     * Marks every invalid field at once (UC-1 4.1.2) and keeps what was typed. AuthService checks
     * the same rules again, plus the duplicate email and USC ID checks.
     */
    private boolean checkAllFields(String name, String email, String uscId, String password) {
        boolean ok = true;
        if (name.trim().isEmpty()) {
            editName.setError("Enter your name");
            ok = false;
        }
        if (!authService.isValidUscEmail(email)) {
            editEmail.setError("Use your @usc.edu email");
            ok = false;
        }
        if (!authService.isValidUscId(uscId)) {
            editUscId.setError("USC ID must be exactly 10 digits");
            ok = false;
        }
        if (password.isEmpty()) {
            editPassword.setError("Enter a password");
            ok = false;
        }
        return ok;
    }

    private void showFieldError(String field, String message) {
        EditText input;
        switch (field) {
            case "name":
                input = editName;
                break;
            case "email":
                input = editEmail;
                break;
            case "uscId":
                input = editUscId;
                break;
            case "password":
                input = editPassword;
                break;
            default:
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                return;
        }
        input.setError(message);
        input.requestFocus();
    }
}
