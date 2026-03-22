import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PatientIntegrationTest {

    @BeforeAll
    static void setUp(){
        RestAssured.baseURI = "http://localhost:4004";
    }

    @Test
    public void shouldReturnPatientsWithValidToken(){

        String token = getToken();

        Response response = given()
                .header("Authorization", "Bearer " + token)
                .when()
                .get("/api/patients")
                .then()
                .statusCode(200)
                .body("patients", notNullValue()).extract().response();

        System.out.println("The response from patient integration test "+response.getBody().prettyPrint());

    }

    @Test
    public void shouldReturn429AfterLimitExceeded() throws InterruptedException{

        String token = getToken();
        int total=10;
        int tooManyRequests=0;

        for (int i = 1; i <= total ; i++) {
            Response response = RestAssured
                    .given()
                    .header("Authorization", "Bearer " + token)
                    .get("/api/patients");
            LocalDateTime now = LocalDateTime.now();
            System.out.printf("Request %d -> Status: %d at Current Time: %tH:%tM:%tS:%tL%n", i , response.statusCode(),now,now,now,now);

            if(response.statusCode()==429){
                tooManyRequests++;
            }
            Thread.sleep(100);
        }
        assertTrue(tooManyRequests >= 1 , "Expected at least 1 request to be rate limited (429)");
    }

    private static String getToken() {
        String loginPayload = """
                {
                    "email": "testuser@test.com",
                    "password": "password123"
                }
                """;

        String token = given()
                .contentType("application/json")
                .body(loginPayload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .get("token");
        return token;
    }
}
