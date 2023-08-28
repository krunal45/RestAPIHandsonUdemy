import io.restassured.RestAssured;
import static io.restassured.RestAssured.*;

import io.restassured.filter.session.SessionFilter;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.io.File;

public class createDefect {
    Response response;
    String issueKey,expectedComment;
    int commentID;
    SessionFilter session = new SessionFilter();
    JsonPath jsonPath;

    @BeforeTest
    void setup(){
        RestAssured.baseURI = "http://localhost:8080/";
    }

    @Test
    void loginToJira(){
        given().header("Content-Type","application/json").body(new File("src/test/resources/requestBody/loginCredentials.json"))
                .filter(session).when().post("rest/auth/1/session").then().assertThat().statusCode(200);
    }

    @Test(dependsOnMethods = {"loginToJira"})
    void createDefectInJira(){
        response = given().header("Content-Type","application/json").body(new File("src/test/resources/requestBody/createDefect.json"))
                .filter(session).when().post("rest/api/2/issue").then().assertThat().statusCode(201).extract().response();
        issueKey = response.jsonPath().getString("id");
    }

    @Test(dependsOnMethods = {"createDefectInJira"})
    void addCommentInJira(){
        String response = given().pathParam("issueID",issueKey).header("Content-Type","application/json").body(new File("src/test/resources/requestBody/addComment.json"))
                .filter(session).when().post("rest/api/2/issue/{issueID}/comment").then().log().all().assertThat().statusCode(201).extract().response().asString();
        jsonPath = new JsonPath(response);
        commentID = Integer.parseInt(jsonPath.getString("id"));
        expectedComment = jsonPath.getString("body");
    }

    @Test(dependsOnMethods = {"createDefectInJira","addCommentInJira"},enabled = false)
    void addAttachment(){
        given().pathParam("issueID",issueKey).header("X-Atlassian-Token","no-check").header("Content-Type","multipart/form-data")
                .multiPart(new File("src/test/resources/attachment/jiraAttachment.txt"))
                .filter(session).when().log().all().post("/rest/api/2/issue/{issueID}/attachments").then()
                .log().all().assertThat().statusCode(200);
    }

    @Test(dependsOnMethods = {"createDefectInJira","addCommentInJira"})
    void getIssue(){
        String response = given().pathParam("issueID",issueKey)
                .queryParam("fields","comment")
                .filter(session).when().log().all().get("/rest/api/2/issue/{issueID}").then().log().all().assertThat().statusCode(200).extract().response().asString();
        System.out.println(response);
        String actualComment = null;
        jsonPath = new JsonPath(response);
        int totalComments = jsonPath.get("fields.comment.comments.size()");
        for (int i=0;i<totalComments;i++) {
            if (jsonPath.getInt("fields.comment.comments[0].id") == commentID) {
                actualComment = jsonPath.getString("fields.comment.comments["+i+"].body");
            }
        }
        Assert.assertEquals(actualComment,expectedComment,"FAIL: Comments do not match!!");
    }

}
