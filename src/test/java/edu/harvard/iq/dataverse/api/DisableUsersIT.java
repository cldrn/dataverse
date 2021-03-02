package edu.harvard.iq.dataverse.api;

import com.jayway.restassured.RestAssured;
import com.jayway.restassured.path.json.JsonPath;
import com.jayway.restassured.response.Response;
import edu.harvard.iq.dataverse.authorization.DataverseRole;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CREATED;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;
import static org.hamcrest.CoreMatchers.equalTo;
import org.junit.BeforeClass;
import org.junit.Test;

public class DisableUsersIT {

    @BeforeClass
    public static void setUp() {
        RestAssured.baseURI = UtilIT.getRestAssuredBaseUri();
    }

    @Test
    public void testDisableUser() {

        Response createSuperuser = UtilIT.createRandomUser();
        createSuperuser.then().assertThat().statusCode(OK.getStatusCode());
        String superuserUsername = UtilIT.getUsernameFromResponse(createSuperuser);
        String superuserApiToken = UtilIT.getApiTokenFromResponse(createSuperuser);
        Response toggleSuperuser = UtilIT.makeSuperUser(superuserUsername);
        toggleSuperuser.then().assertThat()
                .statusCode(OK.getStatusCode());

        Response createDataverse = UtilIT.createRandomDataverse(superuserApiToken);
        createDataverse.then().assertThat().statusCode(CREATED.getStatusCode());
        String dataverseAlias = UtilIT.getAliasFromResponse(createDataverse);
        Integer dataverseId = UtilIT.getDataverseIdFromResponse(createDataverse);

        Response createDataset = UtilIT.createRandomDatasetViaNativeApi(dataverseAlias, superuserApiToken);
        createDataset.prettyPrint();
        createDataset.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        String datasetPersistentId = UtilIT.getDatasetPersistentIdFromResponse(createDataset);

        Response createUser = UtilIT.createRandomUser();
        createUser.then().assertThat().statusCode(OK.getStatusCode());
        String username = UtilIT.getUsernameFromResponse(createUser);
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);

        Response grantRoleBeforeDisable = UtilIT.grantRoleOnDataverse(dataverseAlias, DataverseRole.ADMIN.toString(), "@" + username, superuserApiToken);
        grantRoleBeforeDisable.prettyPrint();
        grantRoleBeforeDisable.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data.assignee", equalTo("@" + username))
                .body("data._roleAlias", equalTo("admin"));

        String aliasInOwner = "groupFor" + dataverseAlias;
        String displayName = "Group for " + dataverseAlias;
        String user2identifier = "@" + username;
        Response createGroup = UtilIT.createGroup(dataverseAlias, aliasInOwner, displayName, superuserApiToken);
        createGroup.prettyPrint();
        createGroup.then().assertThat()
                .statusCode(CREATED.getStatusCode());

        String groupIdentifier = JsonPath.from(createGroup.asString()).getString("data.identifier");

        List<String> roleAssigneesToAdd = new ArrayList<>();
        roleAssigneesToAdd.add(user2identifier);
        Response addToGroup = UtilIT.addToGroup(dataverseAlias, aliasInOwner, roleAssigneesToAdd, superuserApiToken);
        addToGroup.prettyPrint();
        addToGroup.then().assertThat()
                .statusCode(OK.getStatusCode());

        Response userTracesBeforeDisable = UtilIT.getUserTraces(username, superuserApiToken);
        userTracesBeforeDisable.prettyPrint();
        userTracesBeforeDisable.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data.traces.roleAssignments.items[0].definitionPointName", equalTo(dataverseAlias))
                .body("data.traces.roleAssignments.items[0].definitionPointId", equalTo(dataverseId))
                .body("data.traces.explicitGroups.items[0].name", equalTo("Group for " + dataverseAlias));

        Response disableUser = UtilIT.disableUser(username);
        disableUser.prettyPrint();
        disableUser.then().assertThat().statusCode(OK.getStatusCode());

        Response getUser = UtilIT.getAuthenticatedUser(username, superuserApiToken);
        getUser.prettyPrint();
        getUser.then().assertThat()
                .statusCode(OK.getStatusCode())
                .body("data.disabled", equalTo(true));

        Response getUserDisabled = UtilIT.getAuthenticatedUserByToken(apiToken);
        getUserDisabled.prettyPrint();
        getUserDisabled.then().assertThat().statusCode(BAD_REQUEST.getStatusCode());

        Response userTracesAfterDisable = UtilIT.getUserTraces(username, superuserApiToken);
        userTracesAfterDisable.prettyPrint();
        userTracesAfterDisable.then().assertThat()
                .statusCode(OK.getStatusCode())
                /**
                 * Here we are showing the the following were deleted:
                 *
                 * - role assignments
                 *
                 * - membership in explict groups.
                 */
                .body("data.traces", equalTo(Collections.EMPTY_MAP));

        Response grantRoleAfterDisable = UtilIT.grantRoleOnDataverse(dataverseAlias, DataverseRole.ADMIN.toString(), "@" + username, superuserApiToken);
        grantRoleAfterDisable.prettyPrint();
        grantRoleAfterDisable.then().assertThat()
                .statusCode(FORBIDDEN.getStatusCode())
                .body("message", equalTo("User " + username + " is disabled and cannot be given a role."));

        Response addToGroupAfter = UtilIT.addToGroup(dataverseAlias, aliasInOwner, roleAssigneesToAdd, superuserApiToken);
        addToGroupAfter.prettyPrint();
        addToGroupAfter.then().assertThat()
                .statusCode(FORBIDDEN.getStatusCode())
                .body("message", equalTo("User " + username + " is disabled and cannot be added to a group."));

        Response grantRoleOnDataset = UtilIT.grantRoleOnDataset(datasetPersistentId, DataverseRole.ADMIN.toString(), "@" + username, superuserApiToken);
        grantRoleOnDataset.prettyPrint();
        grantRoleOnDataset.then().assertThat()
                .statusCode(FORBIDDEN.getStatusCode())
                .body("message", equalTo("User " + username + " is disabled and cannot be given a role."));

    }

    @Test
    public void testDisableUserById() {

        Response createSuperuser = UtilIT.createRandomUser();
        createSuperuser.then().assertThat().statusCode(OK.getStatusCode());
        String superuserUsername = UtilIT.getUsernameFromResponse(createSuperuser);
        String superuserApiToken = UtilIT.getApiTokenFromResponse(createSuperuser);
        Response toggleSuperuser = UtilIT.makeSuperUser(superuserUsername);
        toggleSuperuser.then().assertThat()
                .statusCode(OK.getStatusCode());

        Response createUser = UtilIT.createRandomUser();
        createUser.prettyPrint();
        createUser.then().assertThat().statusCode(OK.getStatusCode());
        String username = UtilIT.getUsernameFromResponse(createUser);
        Long userId = JsonPath.from(createUser.body().asString()).getLong("data.user.id");
        String apiToken = UtilIT.getApiTokenFromResponse(createUser);

        Response disableUser = UtilIT.disableUser(userId);
        disableUser.prettyPrint();
        disableUser.then().assertThat().statusCode(OK.getStatusCode());
    }

    @Test
    public void testMergeDisabledIntoEnabledUser() {

        Response createSuperuser = UtilIT.createRandomUser();
        String superuserUsername = UtilIT.getUsernameFromResponse(createSuperuser);
        String superuserApiToken = UtilIT.getApiTokenFromResponse(createSuperuser);
        Response toggleSuperuser = UtilIT.makeSuperUser(superuserUsername);
        toggleSuperuser.then().assertThat()
                .statusCode(OK.getStatusCode());

        Response createUserMergeTarget = UtilIT.createRandomUser();
        createUserMergeTarget.prettyPrint();
        String usernameMergeTarget = UtilIT.getUsernameFromResponse(createUserMergeTarget);

        Response createUserToMerge = UtilIT.createRandomUser();
        createUserToMerge.prettyPrint();
        String usernameToMerge = UtilIT.getUsernameFromResponse(createUserToMerge);

        Response disableUser = UtilIT.disableUser(usernameToMerge);
        disableUser.prettyPrint();
        disableUser.then().assertThat().statusCode(OK.getStatusCode());

        // User accounts can only be merged if they are either both enabled or both disabled.
        Response mergeAccounts = UtilIT.mergeAccounts(usernameMergeTarget, usernameToMerge, superuserApiToken);
        mergeAccounts.prettyPrint();
        mergeAccounts.then().assertThat().statusCode(BAD_REQUEST.getStatusCode());
    }

    @Test
    public void testMergeEnabledIntoDisabledUser() {

        Response createSuperuser = UtilIT.createRandomUser();
        String superuserUsername = UtilIT.getUsernameFromResponse(createSuperuser);
        String superuserApiToken = UtilIT.getApiTokenFromResponse(createSuperuser);
        Response toggleSuperuser = UtilIT.makeSuperUser(superuserUsername);
        toggleSuperuser.then().assertThat()
                .statusCode(OK.getStatusCode());

        Response createUserMergeTarget = UtilIT.createRandomUser();
        createUserMergeTarget.prettyPrint();
        String usernameMergeTarget = UtilIT.getUsernameFromResponse(createUserMergeTarget);

        Response createUserToMerge = UtilIT.createRandomUser();
        createUserToMerge.prettyPrint();
        String usernameToMerge = UtilIT.getUsernameFromResponse(createUserToMerge);

        Response disableUser = UtilIT.disableUser(usernameMergeTarget);
        disableUser.prettyPrint();
        disableUser.then().assertThat().statusCode(OK.getStatusCode());

        // User accounts can only be merged if they are either both enabled or both disabled.
        Response mergeAccounts = UtilIT.mergeAccounts(usernameMergeTarget, usernameToMerge, superuserApiToken);
        mergeAccounts.prettyPrint();
        mergeAccounts.then().assertThat().statusCode(BAD_REQUEST.getStatusCode());
    }

    @Test
    public void testMergeDisabledIntoDisabledUser() {

        Response createSuperuser = UtilIT.createRandomUser();
        String superuserUsername = UtilIT.getUsernameFromResponse(createSuperuser);
        String superuserApiToken = UtilIT.getApiTokenFromResponse(createSuperuser);
        Response toggleSuperuser = UtilIT.makeSuperUser(superuserUsername);
        toggleSuperuser.then().assertThat()
                .statusCode(OK.getStatusCode());

        Response createUserMergeTarget = UtilIT.createRandomUser();
        createUserMergeTarget.prettyPrint();
        String usernameMergeTarget = UtilIT.getUsernameFromResponse(createUserMergeTarget);

        Response createUserToMerge = UtilIT.createRandomUser();
        createUserToMerge.prettyPrint();
        String usernameToMerge = UtilIT.getUsernameFromResponse(createUserToMerge);

        Response disableUserMergeTarget = UtilIT.disableUser(usernameMergeTarget);
        disableUserMergeTarget.prettyPrint();
        disableUserMergeTarget.then().assertThat().statusCode(OK.getStatusCode());

        Response disableUserToMerge = UtilIT.disableUser(usernameToMerge);
        disableUserToMerge.prettyPrint();
        disableUserToMerge.then().assertThat().statusCode(OK.getStatusCode());

        // User accounts can only be merged if they are either both enabled or both disabled.
        Response mergeAccounts = UtilIT.mergeAccounts(usernameMergeTarget, usernameToMerge, superuserApiToken);
        mergeAccounts.prettyPrint();
        mergeAccounts.then().assertThat().statusCode(OK.getStatusCode());
    }

}
