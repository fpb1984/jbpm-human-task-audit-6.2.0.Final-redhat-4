package com.mongodb.model;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

public class TaskSnapshot {
	Long processInstanceId;
	Long taskId;
	Long workItemId;
	String status;
	String actualOwner;
	String deploymentId;
	String processId;
	Integer processSessionId;
	String createdBy;
	String previousStatus;
	Date activationTime;
	Date expirationTime;
	Date createdOn;
	Date completed;
	Date delegated;

	Map<String, Object> taskVariables;

	
	public Long getProcessInstanceId() {
		return processInstanceId;
	}

	public void setProcessInstanceId(Long processInstanceId) {
		this.processInstanceId = processInstanceId;
	}

	public Long getTaskId() {
		return taskId;
	}

	public void setTaskId(Long taskId) {
		this.taskId = taskId;
	}
	
	public Map<String, Object> getTaskVariables() {
		return taskVariables;
	}

	public void setTaskVariables(Map<String, Object> taskVariables) {
		this.taskVariables = taskVariables;
	}

	public Date getCompleted() {
		return completed;
	}

	public void setCompleted(Date completed) {
		this.completed = completed;
	}
	
	public Long getWorkItemId() {
		return workItemId;
	}

	public void setWorkItemId(Long workItemId) {
		this.workItemId = workItemId;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
	
	public String getActualOwner() {
		return actualOwner;
	}

	public void setActualOwner(String actualOwner) {
		this.actualOwner = actualOwner;
	}
	
	public String getDeploymentId() {
		return deploymentId;
	}

	public void setDeploymentId(String deploymentId) {
		this.deploymentId = deploymentId;
	}

	public String getProcessId() {
		return processId;
	}

	public void setProcessId(String processId) {
		this.processId = processId;
	}

	public Integer getProcessSessionId() {
		return processSessionId;
	}

	public void setProcessSessionId(Integer processSessionId) {
		this.processSessionId = processSessionId;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public void setCreatedBy(String createdBy) {
		this.createdBy = createdBy;
	}

	public String getPreviousStatus() {
		return previousStatus;
	}

	public void setPreviousStatus(String previousStatus) {
		this.previousStatus = previousStatus;
	}

	public Date getActivationTime() {
		return activationTime;
	}

	public void setActivationTime(Date activationTime) {
		this.activationTime = activationTime;
	}

	public Date getExpirationTime() {
		return expirationTime;
	}

	public void setExpirationTime(Date expirationTime) {
		this.expirationTime = expirationTime;
	}

	public Date getCreatedOn() {
		return createdOn;
	}

	public void setCreatedOn(Date createdOn) {
		this.createdOn = createdOn;
	}

	public Date getDelegated() {
		return delegated;
	}

	public void setDelegated(Date delegated) {
		this.delegated = delegated;
	}

	private SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

	
	public String toJsonStr(){
		JSONObject json = null;
		try {
			
			json = new JSONObject();
			
			if(processInstanceId!=null)
				json.put("processInstanceId", processInstanceId);
			if(taskId!=null)
				json.put("taskId", taskId);
			if(workItemId!=null)
				json.put("workItemId", workItemId);
			if(status!=null)
				json.put("status", status);
			if(actualOwner!=null)
				json.put("actualOwner", actualOwner);
			if(deploymentId!=null)
				json.put("deploymentId", deploymentId);
			if(processId!=null)
				json.put("processId", processId);
			if(processSessionId!=null)	
				json.put("processSessionId", processSessionId);
			if(createdBy!=null)
				json.put("createdBy", createdBy);
			if(previousStatus!=null)
				json.put("previousStatus", previousStatus);
			if(activationTime!=null)
				json.put("activationTime", sdf.format(activationTime));
			if(expirationTime!=null)
				json.put("expirationTime", sdf.format(expirationTime));
			if(createdOn!=null)
				json.put("createdOn", sdf.format(createdOn));
			if(completed!=null)
				json.put("completed", sdf.format(completed));
			if(delegated!=null)
				json.put("delegated", sdf.format(delegated));
			
			JSONObject json2;
			if(taskVariables!=null)
				System.out.println("Intentando agregar variables: " + taskVariables.toString());
			if((taskVariables!=null)&&(!taskVariables.isEmpty())){
				json2 = new JSONObject(taskVariables);
				json.put("taskVariables", json2);
				System.out.println("variables cargadas correctamente: " + taskVariables.toString());
			}
			
			System.out.println("Objeto creado correctamente");
				
		} catch (JSONException e) {
			Exception ex = new Exception("Error in BEAN->JSON construction",e);
			System.out.println(ex);
		}
		
		System.out.println(json.toString());
		
		return json.toString();
	}

}
