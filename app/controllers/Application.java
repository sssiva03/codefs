package controllers;

import java.io.File;

import models.Artifact;
import models.Project;
import models.User;

import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.json.JSONObject;

import play.Logger;
import play.mvc.Controller;
import play.mvc.Http.MultipartFormData;
import play.mvc.Http.MultipartFormData.FilePart;
import play.mvc.Result;
import plugins.FindbugsPlugin;
import plugins.MongoPlugin;
import plugins.S3Plugin;
import utils.CodeFSConstants;
import utils.Mailer;
import views.html.upload;
import views.html.emails.artifactadded;
import views.html.emails.projectcreated;
import views.html.emails.useradded;

import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.PutObjectRequest;

public class Application extends Controller {

	// Views

	public static Result index() throws Exception {
		return ok(views.html.index.render());
	}

	public static Result dashboard(String projectId) throws Exception {
		Project project = MongoPlugin.ds.find(Project.class).field("_id")
				.equal(new ObjectId(projectId)).get();

		return ok(views.html.dashboard.render(project));
	}

	// APIs

	public static Result create(String projectName, String email)
			throws Exception {

		User u = MongoPlugin.ds.find(User.class).field("emailId").equal(email)
				.get();
		if (u == null) {
			u = new User(email);
			MongoPlugin.ds.save(u);
		}

		Project project = new Project(projectName, u);
		MongoPlugin.ds.save(project);

		Mailer.sendMail(CodeFSConstants.project_created,
				projectcreated.render(project).toString(), "Project Created", u);

		return ok(project.toString());
	}

	public static Result userList(String projectId) throws Exception {
		Project project = MongoPlugin.ds.find(Project.class).field("_id")
				.equal(new ObjectId(projectId)).get();

		JSONObject response = new JSONObject();
		response.put("users", new JSONArray(project.users.toString()));

		return ok(response.toString());
	}

	public static Result userAdd(String projectId, String emailId,
			String message) throws Exception {

		User u = MongoPlugin.ds.find(User.class).field("emailId")
				.equal(emailId).get();
		if (u == null) {
			u = new User(emailId);
			MongoPlugin.ds.save(u);
		}

		Project project = MongoPlugin.ds.find(Project.class).field("_id")
				.equal(new ObjectId(projectId)).get();

		project.users.add(u);
		MongoPlugin.ds.save(project);

		Mailer.sendMail(CodeFSConstants.user_added, useradded
				.render(project, u).toString(), "User Added", u);

		JSONObject response = new JSONObject();
		response.put("users", new JSONArray(project.users.toString()));

		return ok(response.toString());
	}

	public static Result artifactList(String projectId) throws Exception {
		Project project = MongoPlugin.ds.find(Project.class).field("_id")
				.equal(new ObjectId(projectId)).get();

		JSONObject response = new JSONObject();
		response.put("artifacts", new JSONArray(project.artifacts.toString()));

		return ok(response.toString());
	}

	public static Result artifactAdd(String projectId) throws Exception {

		MultipartFormData body = request().body().asMultipartFormData();
		FilePart filePart = body.getFile("file");
		String fileName = filePart.getFilename();
		File fileFromWeb = filePart.getFile();

		System.out.println("Received File : " + fileName);

		Artifact a = new Artifact(fileName);
		MongoPlugin.ds.save(a);

		Project project = MongoPlugin.ds.find(Project.class).field("_id")
				.equal(new ObjectId(projectId)).get();

		project.artifacts.add(a);
		MongoPlugin.ds.save(project);

		System.out.println("Uploading to S3 : " + a.id);

		if (S3Plugin.amazonS3 == null) {
			Logger.error("Could not save because amazonS3 was null");
			throw new RuntimeException("Could not save");
		} else {
			PutObjectRequest putObjectRequest = new PutObjectRequest(
					S3Plugin.s3Bucket, a.id.toString() + "/" + a.artifactName,
					fileFromWeb);
			putObjectRequest.withCannedAcl(CannedAccessControlList.PublicRead);
			S3Plugin.amazonS3.putObject(putObjectRequest);
		}

		System.out.println("FindBugs analyze start : " + a.id);

		FindbugsPlugin.put(a, project);

		System.out.println("Responding to Add Artifact : " + a.id);

		// Mailer.sendMail(CodeFSConstants.artifact_added,
		// artifactadded.render(project, a).toString(), "Project Created",
		// project.users);

		return ok(upload.render());
	}

}