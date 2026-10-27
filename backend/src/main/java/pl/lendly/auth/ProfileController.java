package pl.lendly.auth;

import jakarta.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.lendly.auth.AuthDtos.ProfilePatch;
import pl.lendly.auth.AuthDtos.PrivateProfile;
import pl.lendly.auth.AuthDtos.PublicProfile;

@RestController
@RequestMapping("/api")
public class ProfileController {
    private final AuthService auth;
    private final CsrfService csrf;

    public ProfileController(AuthService auth, CsrfService csrf) { this.auth = auth; this.csrf = csrf; }

    @GetMapping("/me")
    ResponseEntity<PrivateProfile> me(@AuthenticationPrincipal AuthPrincipal principal) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(auth.profile(auth.current(principal)));
    }

    @PatchMapping("/me")
    ResponseEntity<PrivateProfile> update(@AuthenticationPrincipal AuthPrincipal principal,
                                           @RequestBody JsonNode body, HttpServletRequest request) {
        if (!body.isObject()) throw new pl.lendly.common.api.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR", "The request body is invalid.");
        var fields = body.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (!field.equals("displayName") && !field.equals("areaLabel")) {
                throw new pl.lendly.common.api.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                        "IMMUTABLE_FIELD", "The requested field cannot be changed.");
            }
        }
        boolean hasName = body.has("displayName");
        boolean hasArea = body.has("areaLabel");
        String name = stringField(body, "displayName", hasName, false);
        String area = stringField(body, "areaLabel", hasArea, true);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(auth.profile(auth.updateProfile(principal, new ProfilePatch(name, area), hasName, hasArea)));
    }

    @GetMapping("/users/{id}")
    PublicProfile publicProfile(@PathVariable String id) { return auth.publicProfile(id).publicProfile(); }

    private String stringField(JsonNode body, String name, boolean present, boolean nullable) {
        if (!present) return null;
        JsonNode value = body.get(name);
        if (value.isNull() && nullable) return null;
        if (!value.isTextual()) throw new pl.lendly.common.api.ApiException(org.springframework.http.HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR", "The request contains invalid fields.");
        return value.textValue();
    }
}
