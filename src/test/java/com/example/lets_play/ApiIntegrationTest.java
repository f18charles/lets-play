package com.example.lets_play;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.lets_play.products.ProductRepository;
import com.example.lets_play.users.UserRepository;
import com.example.lets_play.users.models.Role;
import com.example.lets_play.users.models.User;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.mongodb.uri=${MONGODB_URI_TEST:mongodb://localhost:27017/letsplay_test}",
        "spring.data.mongodb.auto-index-creation=true",
        "server.ssl.enabled=false",
        "app.jwt.secret=test-secret-test-secret-test-secret-123456",
        "app.jwt.expiration-ms=3600000",
        "app.admin.email=",
        "app.admin.password=",
        "app.rate-limit.auth-per-minute=100000",
        "app.rate-limit.general-per-minute=100000",
        "app.cors.allowed-origins=https://localhost:3000"
    })
class ApiIntegrationTest {

    record Resp(int status, String body) {}

    @Value("${local.server.port}") int port;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired PasswordEncoder encoder;

    private final HttpClient http = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1).build();

    private static final String PRODUCT = "{\"name\":\"Lamp\",\"description\":\"Desk lamp\",\"price\":19.99}";

    @BeforeEach
    void cleanDatabase() {
        products.deleteAll();
        users.deleteAll();
        users.save(new User("Admin", "admin@test.com", encoder.encode("adminpass123"), Role.ADMIN));
    }

    // ---------- helpers ----------

    private Resp call(String method, String path, String token, String json) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Content-Type", "application/json")
            .method(method, json == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json));
        if (token != null) b.header("Authorization", "Bearer " + token);
        HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
        return new Resp(res.statusCode(), res.body());
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"([^\"]*)\"").matcher(json);
        assertTrue(m.find(), "field '" + name + "' not found in: " + json);
        return m.group(1);
    }

    private String register(String name, String email) throws Exception {
        Resp r = call("POST", "/auth/register", null,
            "{\"name\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"password123\"}");
        assertEquals(201, r.status(), r.body());
        return field(r.body(), "id");
    }

    private String login(String email, String password) throws Exception {
        Resp r = call("POST", "/auth/login", null,
            "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
        assertEquals(200, r.status(), r.body());
        return field(r.body(), "token");
    }

    private String userToken(String name, String email) throws Exception {
        register(name, email);
        return login(email, "password123");
    }

    private String adminToken() throws Exception {
        return login("admin@test.com", "adminpass123");
    }

    private String createProduct(String token) throws Exception {
        Resp r = call("POST", "/products", token, PRODUCT);
        assertEquals(201, r.status(), r.body());
        return field(r.body(), "id");
    }

    // ---------- registration and login ----------

    @Test
    void register_returns201_andNeverExposesPassword() throws Exception {
        Resp r = call("POST", "/auth/register", null,
            "{\"name\":\"Alice\",\"email\":\"alice@test.com\",\"password\":\"password123\"}");
        assertEquals(201, r.status());
        assertFalse(r.body().contains("password"), r.body());
        assertTrue(r.body().contains("\"role\":\"USER\""), r.body());
    }

    @Test
    void register_cannotChooseAdminRole() throws Exception {
        Resp r = call("POST", "/auth/register", null,
            "{\"name\":\"Eve\",\"email\":\"eve@test.com\",\"password\":\"password123\",\"role\":\"ADMIN\"}");
        assertEquals(201, r.status());
        assertTrue(r.body().contains("\"role\":\"USER\""), r.body());
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        register("Alice", "alice@test.com");
        Resp r = call("POST", "/auth/register", null,
            "{\"name\":\"Alice2\",\"email\":\"ALICE@test.com\",\"password\":\"password123\"}");
        assertEquals(409, r.status(), r.body());
    }

    @Test
    void register_invalidInput_returns400() throws Exception {
        Resp r = call("POST", "/auth/register", null,
            "{\"name\":\"\",\"email\":\"not-an-email\",\"password\":\"short\"}");
        assertEquals(400, r.status(), r.body());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        register("Alice", "alice@test.com");
        Resp r = call("POST", "/auth/login", null,
            "{\"email\":\"alice@test.com\",\"password\":\"wrong-password\"}");
        assertEquals(401, r.status(), r.body());
    }

    @Test
    void login_unknownEmail_returns401() throws Exception {
        Resp r = call("POST", "/auth/login", null,
            "{\"email\":\"nobody@test.com\",\"password\":\"password123\"}");
        assertEquals(401, r.status(), r.body());
    }

    // ---------- products: public and authenticated ----------

    @Test
    void listProducts_isPublic() throws Exception {
        assertEquals(200, call("GET", "/products", null, null).status());
    }

    @Test
    void createProduct_withoutToken_returns401() throws Exception {
        assertEquals(401, call("POST", "/products", null, PRODUCT).status());
    }

    @Test
    void createProduct_withGarbageToken_returns401() throws Exception {
        assertEquals(401, call("POST", "/products", "not.a.token", PRODUCT).status());
    }

    @Test
    void createProduct_setsOwnerFromToken() throws Exception {
        String id = register("Alice", "alice@test.com");
        String token = login("alice@test.com", "password123");
        Resp r = call("POST", "/products", token,
            "{\"name\":\"Lamp\",\"description\":\"x\",\"price\":5,\"userId\":\"someone-else\"}");
        assertEquals(201, r.status(), r.body());
        assertEquals(id, field(r.body(), "userId"));
    }

    @Test
    void createProduct_negativePrice_returns400() throws Exception {
        String token = userToken("Alice", "alice@test.com");
        Resp r = call("POST", "/products", token, "{\"name\":\"Lamp\",\"price\":-1}");
        assertEquals(400, r.status(), r.body());
    }

    @Test
    void getProduct_unknownId_returns404() throws Exception {
        assertEquals(404, call("GET", "/products/does-not-exist", null, null).status());
    }

    // ---------- ownership rules ----------

    @Test
    void update_byOwner_returns200() throws Exception {
        String token = userToken("Alice", "alice@test.com");
        String pid = createProduct(token);
        Resp r = call("PUT", "/products/" + pid, token,
            "{\"name\":\"Lamp v2\",\"description\":\"x\",\"price\":25}");
        assertEquals(200, r.status(), r.body());
    }

    @Test
    void update_byOtherUser_returns403() throws Exception {
        String alice = userToken("Alice", "alice@test.com");
        String bob = userToken("Bob", "bob@test.com");
        String pid = createProduct(alice);
        assertEquals(403, call("PUT", "/products/" + pid, bob, PRODUCT).status());
    }

    @Test
    void delete_byOtherUser_returns403_andByAdmin_returns204() throws Exception {
        String alice = userToken("Alice", "alice@test.com");
        String bob = userToken("Bob", "bob@test.com");
        String pid = createProduct(alice);
        assertEquals(403, call("DELETE", "/products/" + pid, bob, null).status());
        assertEquals(204, call("DELETE", "/products/" + pid, adminToken(), null).status());
        assertEquals(404, call("GET", "/products/" + pid, null, null).status());
    }

    // ---------- admin-only user endpoints ----------

    @Test
    void users_withoutToken_returns401() throws Exception {
        assertEquals(401, call("GET", "/users", null, null).status());
    }

    @Test
    void users_asNormalUser_returns403() throws Exception {
        String token = userToken("Alice", "alice@test.com");
        assertEquals(403, call("GET", "/users", token, null).status());
    }

    @Test
    void users_asAdmin_returns200_withoutPasswords() throws Exception {
        register("Alice", "alice@test.com");
        Resp r = call("GET", "/users", adminToken(), null);
        assertEquals(200, r.status());
        assertTrue(r.body().contains("alice@test.com"));
        assertFalse(r.body().contains("password"), r.body());
    }

    @Test
    void deletingUser_removesTheirProducts() throws Exception {
        String aliceId = register("Alice", "alice@test.com");
        String alice = login("alice@test.com", "password123");
        createProduct(alice);
        assertEquals(204, call("DELETE", "/users/" + aliceId, adminToken(), null).status());
        assertEquals(0, products.findByUserId(aliceId).size());
    }

    @Test
    void deletedUser_tokenStopsWorking() throws Exception {
        String aliceId = register("Alice", "alice@test.com");
        String alice = login("alice@test.com", "password123");
        call("DELETE", "/users/" + aliceId, adminToken(), null);
        assertEquals(401, call("POST", "/products", alice, PRODUCT).status());
    }

    @Test
    void login_withOperatorObjectInsteadOfString_returns400() throws Exception {
        Resp r = call("POST", "/auth/login", null,
            "{\"email\":{\"$ne\":\"\"},\"password\":\"anything\"}");
        assertEquals(400, r.status(), r.body());
    }

    @Test
    void login_withOperatorObjectAsPassword_returns400() throws Exception {
        register("Alice", "alice@test.com");
        Resp r = call("POST", "/auth/login", null,
            "{\"email\":\"alice@test.com\",\"password\":{\"$ne\":\"\"}}");
        assertEquals(400, r.status(), r.body());
    }
}