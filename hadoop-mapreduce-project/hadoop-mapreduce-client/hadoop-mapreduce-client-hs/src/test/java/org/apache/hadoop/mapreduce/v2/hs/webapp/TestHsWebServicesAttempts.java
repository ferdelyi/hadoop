/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.mapreduce.v2.hs.webapp;

import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static org.apache.hadoop.yarn.webapp.WebServicesTestUtils.assertResponseStatusCode;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.http.JettyUtils;
import org.apache.hadoop.mapreduce.v2.api.records.JobId;
import org.apache.hadoop.mapreduce.v2.api.records.TaskAttemptId;
import org.apache.hadoop.mapreduce.v2.api.records.TaskType;
import org.apache.hadoop.mapreduce.v2.app.AppContext;
import org.apache.hadoop.mapreduce.v2.app.job.Job;
import org.apache.hadoop.mapreduce.v2.app.job.Task;
import org.apache.hadoop.mapreduce.v2.app.job.TaskAttempt;
import org.apache.hadoop.mapreduce.v2.hs.HistoryContext;
import org.apache.hadoop.mapreduce.v2.hs.MockHistoryContext;
import org.apache.hadoop.mapreduce.v2.util.MRApps;
import org.apache.hadoop.util.XMLUtils;
import org.apache.hadoop.yarn.api.ApplicationClientProtocol;
import org.apache.hadoop.yarn.webapp.GenericExceptionHandler;
import org.apache.hadoop.yarn.webapp.JerseyTestBase;
import org.apache.hadoop.yarn.webapp.WebApp;
import org.apache.hadoop.yarn.webapp.WebServicesTestUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import org.glassfish.jersey.internal.inject.AbstractBinder;
import org.glassfish.jersey.jettison.JettisonFeature;
import org.glassfish.jersey.server.ResourceConfig;

/**
 * Test the history server Rest API for getting task attempts, a
 * specific task attempt, and task attempt counters
 *
 * /ws/v1/history/mapreduce/jobs/{jobid}/tasks/{taskid}/attempts
 * /ws/v1/history/mapreduce/jobs/{jobid}/tasks/{taskid}/attempts/{attemptid}
 * /ws/v1/history/mapreduce/jobs/{jobid}/tasks/{taskid}/attempts/{attemptid}/
 * counters
 */
public class TestHsWebServicesAttempts extends JerseyTestBase {

  private static Configuration conf = new Configuration();
  private static HistoryContext appContext;
  private static HsWebApp webApp;
  private static ApplicationClientProtocol acp = mock(ApplicationClientProtocol.class);

  @Override
  protected Application configure() {
    ResourceConfig config = new ResourceConfig();
    config.register(new JerseyBinder());
    config.register(HsWebServices.class);
    config.register(GenericExceptionHandler.class);
    config.register(new JettisonFeature()).register(JAXBContextResolver.class);
    return config;
  }

  private static class JerseyBinder extends AbstractBinder {
    @Override
    protected void configure() {
      appContext = new MockHistoryContext(0, 1, 2, 1);
      webApp = mock(HsWebApp.class);
      when(webApp.name()).thenReturn("hsmockwebapp");

      bind(webApp).to(WebApp.class).named("hsWebApp");
      bind(appContext).to(AppContext.class);
      bind(appContext).to(HistoryContext.class).named("ctx");
      bind(conf).to(Configuration.class).named("conf");
      bind(acp).to(ApplicationClientProtocol.class).named("appClient");
      final HttpServletResponse response = mock(HttpServletResponse.class);
      bind(response).to(HttpServletResponse.class);
      final HttpServletRequest request = mock(HttpServletRequest.class);
      bind(request).to(HttpServletRequest.class);
    }
  }

  @Test
  public void testTaskAttempts() throws JSONException, Exception{
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);
      for (Task task : jobsMap.get(id).getTasks().values()) {
        String tid = MRApps.toString(task.getID());
        Response response = r.path("ws").path("v1").path("history")
            .path("mapreduce").path("jobs").path(jobId).path("tasks").path(tid)
            .path("attempts").request(MediaType.APPLICATION_JSON)
            .get(Response.class);
        assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
            response.getMediaType().toString());
        JSONObject json = response.readEntity(JSONObject.class);
        verifyHsTaskAttempts(json, task);
      }
    }
  }

  @Test
  public void testTaskAttemptsSlash() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);
      for (Task task : jobsMap.get(id).getTasks().values()) {

        String tid = MRApps.toString(task.getID());
        Response response = r.path("ws").path("v1").path("history")
            .path("mapreduce").path("jobs").path(jobId).path("tasks").path(tid)
            .path("attempts/").request(MediaType.APPLICATION_JSON)
            .get(Response.class);
        assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
            response.getMediaType().toString());
        JSONObject json = response.readEntity(JSONObject.class);
        verifyHsTaskAttempts(json, task);
      }
    }
  }

  @Test
  public void testTaskAttemptsDefault() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);
      for (Task task : jobsMap.get(id).getTasks().values()) {

        String tid = MRApps.toString(task.getID());
        Response response = r.path("ws").path("v1").path("history")
            .path("mapreduce").path("jobs").path(jobId).path("tasks").path(tid)
            .path("attempts").request(MediaType.APPLICATION_JSON).get(Response.class);
        assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
            response.getMediaType().toString());
        JSONObject json = response.readEntity(JSONObject.class);
        verifyHsTaskAttempts(json, task);
      }
    }
  }

  @Test
  public void testTaskAttemptsXML() throws JSONException, Exception {
    WebTarget r = target();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);
      for (Task task : jobsMap.get(id).getTasks().values()) {

        String tid = MRApps.toString(task.getID());
        Response response = r.path("ws").path("v1").path("history")
            .path("mapreduce").path("jobs").path(jobId).path("tasks").path(tid)
            .path("attempts").request(MediaType.APPLICATION_XML)
            .get(Response.class);

        assertEquals(MediaType.APPLICATION_XML_TYPE + ";" + JettyUtils.UTF_8,
            response.getMediaType().toString());
        String xml = response.readEntity(String.class);
        DocumentBuilderFactory dbf = XMLUtils.newSecureDocumentBuilderFactory();
        DocumentBuilder db = dbf.newDocumentBuilder();
        InputSource is = new InputSource();
        is.setCharacterStream(new StringReader(xml));
        Document dom = db.parse(is);
        NodeList attempts = dom.getElementsByTagName("taskAttempts");
        assertEquals("incorrect number of elements", 1, attempts.getLength());

        NodeList nodes = dom.getElementsByTagName("taskAttempt");
        verifyHsTaskAttemptsXML(nodes, task);
      }
    }
  }

  @Test
  public void testTaskAttemptId() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();

    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);

      for (Task task : jobsMap.get(id).getTasks().values()) {
        String tid = MRApps.toString(task.getID());

        for (TaskAttempt att : task.getAttempts().values()) {
          TaskAttemptId attemptid = att.getID();
          String attid = MRApps.toString(attemptid);

          Response response = r.path("ws").path("v1").path("history")
              .path("mapreduce").path("jobs").path(jobId).path("tasks")
              .path(tid).path("attempts").path(attid)
              .request(MediaType.APPLICATION_JSON).get(Response.class);
          assertEquals(MediaType.APPLICATION_JSON_TYPE + ";"
                  + JettyUtils.UTF_8, response.getMediaType().toString());
          JSONObject json = response.readEntity(JSONObject.class);
          assertEquals("incorrect number of elements", 1, json.length());
          JSONObject info = json.getJSONObject("taskAttempt");
          verifyHsTaskAttempt(info, att, task.getType());
        }
      }
    }
  }

  @Test
  public void testTaskAttemptIdSlash() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();

    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);

      for (Task task : jobsMap.get(id).getTasks().values()) {
        String tid = MRApps.toString(task.getID());

        for (TaskAttempt att : task.getAttempts().values()) {
          TaskAttemptId attemptid = att.getID();
          String attid = MRApps.toString(attemptid);

          Response response = r.path("ws").path("v1").path("history")
              .path("mapreduce").path("jobs").path(jobId).path("tasks")
              .path(tid).path("attempts").path(attid + "/")
              .request(MediaType.APPLICATION_JSON).get(Response.class);
          assertEquals(MediaType.APPLICATION_JSON_TYPE + ";"
              + JettyUtils.UTF_8, response.getMediaType().toString());
          JSONObject json = response.readEntity(JSONObject.class);
          assertEquals("incorrect number of elements", 1, json.length());
          JSONObject info = json.getJSONObject("taskAttempt");
          verifyHsTaskAttempt(info, att, task.getType());
        }
      }
    }
  }

  @Test
  public void testTaskAttemptIdDefault() throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();

    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);

      for (Task task : jobsMap.get(id).getTasks().values()) {
        String tid = MRApps.toString(task.getID());

        for (TaskAttempt att : task.getAttempts().values()) {
          TaskAttemptId attemptid = att.getID();
          String attid = MRApps.toString(attemptid);

          Response response = r.path("ws").path("v1").path("history")
              .path("mapreduce").path("jobs").path(jobId).path("tasks")
              .path(tid).path("attempts").path(attid).request(MediaType.APPLICATION_JSON)
              .get(Response.class);
          assertEquals(MediaType.APPLICATION_JSON_TYPE + ";" + JettyUtils.UTF_8,
              response.getMediaType().toString());
          JSONObject json = response.readEntity(JSONObject.class);
          assertEquals("incorrect number of elements", 1, json.length());
          JSONObject info = json.getJSONObject("taskAttempt");
          verifyHsTaskAttempt(info, att, task.getType());
        }
      }
    }
  }

  @Test
  public void testTaskAttemptIdXML() throws JSONException, Exception {
    WebTarget r = target();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);
      for (Task task : jobsMap.get(id).getTasks().values()) {

        String tid = MRApps.toString(task.getID());
        for (TaskAttempt att : task.getAttempts().values()) {
          TaskAttemptId attemptid = att.getID();
          String attid = MRApps.toString(attemptid);

          Response response = r.path("ws").path("v1").path("history")
              .path("mapreduce").path("jobs").path(jobId).path("tasks")
              .path(tid).path("attempts").path(attid)
              .request(MediaType.APPLICATION_XML).get(Response.class);

          assertEquals(MediaType.APPLICATION_XML_TYPE + ";" + JettyUtils.UTF_8,
              response.getMediaType().toString());
          String xml = response.readEntity(String.class);
          DocumentBuilderFactory dbf = XMLUtils.newSecureDocumentBuilderFactory();
          DocumentBuilder db = dbf.newDocumentBuilder();
          InputSource is = new InputSource();
          is.setCharacterStream(new StringReader(xml));
          Document dom = db.parse(is);
          NodeList nodes = dom.getElementsByTagName("taskAttempt");
          for (int i = 0; i < nodes.getLength(); i++) {
            Element element = (Element) nodes.item(i);
            verifyHsTaskAttemptXML(element, att, task.getType());
          }
        }
      }
    }
  }

  @Test
  public void testTaskAttemptIdBogus() throws JSONException, Exception {

    testTaskAttemptIdErrorGeneric("bogusid",
        "TaskAttemptId string : bogusid is not properly formed");
  }

  @Test
  public void testTaskAttemptIdNonExist() throws JSONException, Exception {
    testTaskAttemptIdErrorGeneric("attempt_0_1234_m_000000_0",
        "Error getting info on task attempt id attempt_0_1234_m_000000_0");
  }

  @Test
  public void testTaskAttemptIdInvalid() throws JSONException, Exception {
    testTaskAttemptIdErrorGeneric("attempt_0_1234_d_000000_0",
        "Bad TaskType identifier. TaskAttemptId string : "
         + "attempt_0_1234_d_000000_0 is not properly formed.");
  }

  @Test
  public void testTaskAttemptIdInvalid2() throws JSONException, Exception {
    testTaskAttemptIdErrorGeneric("attempt_1234_m_000000_0",
        "TaskAttemptId string : attempt_1234_m_000000_0 is not properly formed");
  }

  @Test
  public void testTaskAttemptIdInvalid3() throws JSONException, Exception {

    testTaskAttemptIdErrorGeneric("attempt_0_1234_m_000000",
        "TaskAttemptId string : attempt_0_1234_m_000000 is not properly formed");
  }

  private void testTaskAttemptIdErrorGeneric(String attid, String error)
      throws JSONException, Exception {
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();

    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);

      for (Task task : jobsMap.get(id).getTasks().values()) {
        String tid = MRApps.toString(task.getID());

        try {
          r.path("ws").path("v1").path("history").path("mapreduce")
              .path("jobs").path(jobId).path("tasks").path(tid)
              .path("attempts").path(attid).request(MediaType.APPLICATION_JSON)
              .get(JSONObject.class);
          fail("should have thrown exception on invalid uri");
        } catch (NotFoundException nfe) {
          Response response = nfe.getResponse();
          assertResponseStatusCode(NOT_FOUND, response.getStatusInfo());
          assertEquals(MediaType.APPLICATION_JSON_TYPE + ";"
              + JettyUtils.UTF_8, response.getMediaType().toString());
          JSONObject msg = response.readEntity(JSONObject.class);
          JSONObject exception = msg.getJSONObject("RemoteException");
          assertEquals("incorrect number of elements", 3, exception.length());
          String message = exception.getString("message");
          String type = exception.getString("exception");
          String classname = exception.getString("javaClassName");
          WebServicesTestUtils.checkStringMatch("exception message", error, message);
          WebServicesTestUtils.checkStringMatch("exception type", "NotFoundException", type);
          WebServicesTestUtils.checkStringMatch("exception classname",
              "org.apache.hadoop.yarn.webapp.NotFoundException", classname);
        }
      }
    }
  }

  public void verifyHsTaskAttemptXML(Element element, TaskAttempt att,
      TaskType ttype) {
    verifyTaskAttemptGeneric(att, ttype,
        WebServicesTestUtils.getXmlString(element, "id"),
        WebServicesTestUtils.getXmlString(element, "state"),
        WebServicesTestUtils.getXmlString(element, "type"),
        WebServicesTestUtils.getXmlString(element, "rack"),
        WebServicesTestUtils.getXmlString(element, "nodeHttpAddress"),
        WebServicesTestUtils.getXmlString(element, "diagnostics"),
        WebServicesTestUtils.getXmlString(element, "assignedContainerId"),
        WebServicesTestUtils.getXmlLong(element, "startTime"),
        WebServicesTestUtils.getXmlLong(element, "finishTime"),
        WebServicesTestUtils.getXmlLong(element, "elapsedTime"),
        WebServicesTestUtils.getXmlFloat(element, "progress"));

    if (ttype == TaskType.REDUCE) {
      verifyReduceTaskAttemptGeneric(att,
          WebServicesTestUtils.getXmlLong(element, "shuffleFinishTime"),
          WebServicesTestUtils.getXmlLong(element, "mergeFinishTime"),
          WebServicesTestUtils.getXmlLong(element, "elapsedShuffleTime"),
          WebServicesTestUtils.getXmlLong(element, "elapsedMergeTime"),
          WebServicesTestUtils.getXmlLong(element, "elapsedReduceTime"));
    }
  }

  public void verifyHsTaskAttempt(JSONObject info, TaskAttempt att,
      TaskType ttype) throws JSONException {
    if (ttype == TaskType.REDUCE) {
      assertEquals("incorrect number of elements", 17, info.length());
    } else {
      assertEquals("incorrect number of elements", 12, info.length());
    }

    verifyTaskAttemptGeneric(att, ttype, info.getString("id"),
        info.getString("state"), info.getString("type"),
        info.getString("rack"), info.getString("nodeHttpAddress"),
        info.getString("diagnostics"), info.getString("assignedContainerId"),
        info.getLong("startTime"), info.getLong("finishTime"),
        info.getLong("elapsedTime"), (float) info.getDouble("progress"));

    if (ttype == TaskType.REDUCE) {
      verifyReduceTaskAttemptGeneric(att, info.getLong("shuffleFinishTime"),
          info.getLong("mergeFinishTime"), info.getLong("elapsedShuffleTime"),
          info.getLong("elapsedMergeTime"), info.getLong("elapsedReduceTime"));
    }
  }

  public void verifyHsTaskAttempts(JSONObject json, Task task)
      throws JSONException {
    assertEquals("incorrect number of elements", 1, json.length());
    JSONObject attempts = json.getJSONObject("taskAttempts");
    assertEquals("incorrect number of elements", 1, json.length());
    JSONObject taskAttempt = attempts.getJSONObject("taskAttempt");
    JSONArray arr = new JSONArray();
    arr.put(taskAttempt);
    for (TaskAttempt att : task.getAttempts().values()) {
      TaskAttemptId id = att.getID();
      String attid = MRApps.toString(id);
      Boolean found = false;

      for (int i = 0; i < arr.length(); i++) {
        JSONObject info = arr.getJSONObject(i);
        if (attid.matches(info.getString("id"))) {
          found = true;
          verifyHsTaskAttempt(info, att, task.getType());
        }
      }
      assertTrue("task attempt with id: " + attid
          + " not in web service output", found);
    }
  }

  public void verifyHsTaskAttemptsXML(NodeList nodes, Task task) {
    assertEquals("incorrect number of elements", 1, nodes.getLength());

    for (TaskAttempt att : task.getAttempts().values()) {
      TaskAttemptId id = att.getID();
      String attid = MRApps.toString(id);
      Boolean found = false;
      for (int i = 0; i < nodes.getLength(); i++) {
        Element element = (Element) nodes.item(i);

        if (attid.matches(WebServicesTestUtils.getXmlString(element, "id"))) {
          found = true;
          verifyHsTaskAttemptXML(element, att, task.getType());
        }
      }
      assertTrue("task with id: " + attid + " not in web service output", found);
    }
  }

  public void verifyTaskAttemptGeneric(TaskAttempt ta, TaskType ttype,
      String id, String state, String type, String rack,
      String nodeHttpAddress, String diagnostics, String assignedContainerId,
      long startTime, long finishTime, long elapsedTime, float progress) {

    TaskAttemptId attid = ta.getID();
    String attemptId = MRApps.toString(attid);

    WebServicesTestUtils.checkStringMatch("id", attemptId, id);
    WebServicesTestUtils.checkStringMatch("type", ttype.toString(), type);
    WebServicesTestUtils.checkStringMatch("state", ta.getState().toString(),
        state);
    WebServicesTestUtils.checkStringMatch("rack", ta.getNodeRackName(), rack);
    WebServicesTestUtils.checkStringMatch("nodeHttpAddress",
        ta.getNodeHttpAddress(), nodeHttpAddress);

    String expectDiag = "";
    List<String> diagnosticsList = ta.getDiagnostics();
    if (diagnosticsList != null && !diagnostics.isEmpty()) {
      StringBuilder b = new StringBuilder();
      for (String diag : diagnosticsList) {
        b.append(diag);
      }
      expectDiag = b.toString();
    }
    WebServicesTestUtils.checkStringMatch("diagnostics", expectDiag,
        diagnostics);
    WebServicesTestUtils.checkStringMatch("assignedContainerId",
        ta.getAssignedContainerID().toString(),
        assignedContainerId);

    assertEquals("startTime wrong", ta.getLaunchTime(), startTime);
    assertEquals("finishTime wrong", ta.getFinishTime(), finishTime);
    assertEquals("elapsedTime wrong", finishTime - startTime, elapsedTime);
    assertEquals("progress wrong", ta.getProgress() * 100, progress, 1e-3f);
  }

  public void verifyReduceTaskAttemptGeneric(TaskAttempt ta,
      long shuffleFinishTime, long mergeFinishTime, long elapsedShuffleTime,
      long elapsedMergeTime, long elapsedReduceTime) {

    assertEquals("shuffleFinishTime wrong", ta.getShuffleFinishTime(),
        shuffleFinishTime);
    assertEquals("mergeFinishTime wrong", ta.getSortFinishTime(),
        mergeFinishTime);
    assertEquals("elapsedShuffleTime wrong",
        ta.getShuffleFinishTime() - ta.getLaunchTime(), elapsedShuffleTime);
    assertEquals("elapsedMergeTime wrong",
        ta.getSortFinishTime() - ta.getShuffleFinishTime(), elapsedMergeTime);
    assertEquals("elapsedReduceTime wrong",
        ta.getFinishTime() - ta.getSortFinishTime(), elapsedReduceTime);
  }

  @Test
  public void testTaskAttemptIdCounters() throws Exception {
    WebTarget r = targetWithJsonObject();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();

    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);

      for (Task task : jobsMap.get(id).getTasks().values()) {
        String tid = MRApps.toString(task.getID());

        for (TaskAttempt att : task.getAttempts().values()) {
          TaskAttemptId attemptid = att.getID();
          String attid = MRApps.toString(attemptid);

          Response response = r.path("ws").path("v1").path("history")
              .path("mapreduce").path("jobs").path(jobId).path("tasks")
              .path(tid).path("attempts").path(attid).path("counters")
              .request(MediaType.APPLICATION_JSON).get(Response.class);
          assertEquals(MediaType.APPLICATION_JSON_TYPE + ";"
              + JettyUtils.UTF_8, response.getMediaType().toString());
          JSONObject json = response.readEntity(JSONObject.class);
          assertEquals("incorrect number of elements", 1, json.length());
          JSONObject info = json.getJSONObject("jobTaskAttemptCounters");
          verifyHsJobTaskAttemptCounters(info, att);
        }
      }
    }
  }

  @Test
  public void testTaskAttemptIdXMLCounters() throws Exception {
    WebTarget r = target();
    Map<JobId, Job> jobsMap = appContext.getAllJobs();
    for (JobId id : jobsMap.keySet()) {
      String jobId = MRApps.toString(id);
      for (Task task : jobsMap.get(id).getTasks().values()) {

        String tid = MRApps.toString(task.getID());
        for (TaskAttempt att : task.getAttempts().values()) {
          TaskAttemptId attemptid = att.getID();
          String attid = MRApps.toString(attemptid);

          Response response = r.path("ws").path("v1").path("history")
              .path("mapreduce").path("jobs").path(jobId).path("tasks")
              .path(tid).path("attempts").path(attid).path("counters")
              .request(MediaType.APPLICATION_XML).get(Response.class);

          assertEquals(MediaType.APPLICATION_XML_TYPE + ";" + JettyUtils.UTF_8,
              response.getMediaType().toString());
          String xml = response.readEntity(String.class);
          DocumentBuilderFactory dbf = XMLUtils.newSecureDocumentBuilderFactory();
          DocumentBuilder db = dbf.newDocumentBuilder();
          InputSource is = new InputSource();
          is.setCharacterStream(new StringReader(xml));
          Document dom = db.parse(is);
          NodeList nodes = dom.getElementsByTagName("jobTaskAttemptCounters");

          verifyHsTaskCountersXML(nodes, att);
        }
      }
    }
  }

  public void verifyHsJobTaskAttemptCounters(JSONObject info, TaskAttempt att)
      throws JSONException {

    assertEquals("incorrect number of elements", 2, info.length());

    WebServicesTestUtils.checkStringMatch("id", MRApps.toString(att.getID()),
        info.getString("id"));

    // just do simple verification of fields - not data is correct
    // in the fields
    JSONArray counterGroups = info.getJSONArray("taskAttemptCounterGroup");
    for (int i = 0; i < counterGroups.length(); i++) {
      JSONObject counterGroup = counterGroups.getJSONObject(i);
      String name = counterGroup.getString("counterGroupName");
      assertTrue("name not set", (name != null && !name.isEmpty()));
      JSONArray counters = counterGroup.getJSONArray("counter");
      for (int j = 0; j < counters.length(); j++) {
        JSONObject counter = counters.getJSONObject(j);
        String counterName = counter.getString("name");
        assertTrue("name not set",
            (counterName != null && !counterName.isEmpty()));
        long value = counter.getLong("value");
        assertTrue("value  >= 0", value >= 0);
      }
    }
  }

  public void verifyHsTaskCountersXML(NodeList nodes, TaskAttempt att) {

    for (int i = 0; i < nodes.getLength(); i++) {

      Element element = (Element) nodes.item(i);
      WebServicesTestUtils.checkStringMatch("id", MRApps.toString(att.getID()),
          WebServicesTestUtils.getXmlString(element, "id"));
      // just do simple verification of fields - not data is correct
      // in the fields
      NodeList groups = element.getElementsByTagName("taskAttemptCounterGroup");

      for (int j = 0; j < groups.getLength(); j++) {
        Element counters = (Element) groups.item(j);
        assertNotNull("should have counters in the web service info", counters);
        String name = WebServicesTestUtils.getXmlString(counters,
            "counterGroupName");
        assertTrue("name not set", (name != null && !name.isEmpty()));
        NodeList counterArr = counters.getElementsByTagName("counter");
        for (int z = 0; z < counterArr.getLength(); z++) {
          Element counter = (Element) counterArr.item(z);
          String counterName = WebServicesTestUtils.getXmlString(counter,
              "name");
          assertTrue("counter name not set",
              (counterName != null && !counterName.isEmpty()));

          long value = WebServicesTestUtils.getXmlLong(counter, "value");
          assertTrue("value not >= 0", value >= 0);

        }
      }
    }
  }

}
