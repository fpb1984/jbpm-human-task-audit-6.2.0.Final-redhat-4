/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jbpm.services.task.audit;

import java.io.FileWriter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.core.impl.EnvironmentFactory;
import org.jbpm.services.task.audit.impl.model.AuditTaskImpl;
import org.jbpm.services.task.audit.impl.model.TaskEventImpl;
import org.jbpm.services.task.lifecycle.listeners.TaskLifeCycleEventListener;
import org.jbpm.services.task.utils.ClassUtil;
import org.jbpm.services.task.utils.ContentMarshallerHelper;
import org.kie.api.runtime.Environment;
import org.kie.api.task.TaskEvent;
import org.kie.api.task.model.Content;
import org.kie.api.task.model.Status;
import org.kie.api.task.model.Task;
import org.kie.internal.task.api.TaskContext;
import org.kie.internal.task.api.TaskPersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoConectionManager;
import com.mongodb.model.TaskSnapshot;

/**
 *
 */
public class JPATaskLifeCycleEventListener implements TaskLifeCycleEventListener {
	
	
	private static short waitingTime = 2000;
	private static short retryTimes = 1200;
	private MongoConectionManager mcm;
	private static final String SERVER_CONF_URL = System.getProperty("jboss.server.config.dir");
	private static String version = "beta 0.5";

    public JPATaskLifeCycleEventListener() {
    }
	private static final Logger logger = LoggerFactory.getLogger(JPATaskLifeCycleEventListener.class);

    public JPATaskLifeCycleEventListener(boolean flag) {
    }

    @Override
    public void afterTaskStartedEvent(TaskEvent event) {
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.STARTED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId ));
        
// @TODO:     Update UserAuditTask to Lucene        

        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
            
        persistenceContext.merge(auditTaskImpl);
        
        System.out.println("afterTaskStartedEvent");
        Content a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
  		
  		System.out.println("ContentID: " +  ti.getTaskData().getDocumentContentId());
  		if(a!=null)
  			System.out.println("Content encontrado: " + a.getId());
  		else{
  			System.out.println("Content no encontrado: " + ti.getTaskData().getDocumentContentId());
  			System.out.println("Retrying");
  			for(int i=0;i<retryTimes;i++)
				try {
					Thread.sleep(waitingTime);
	  		        a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
	  		        if(a!=null)
	  		        	break;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
  			}
  		
  		if(a!=null){
  		
	  		TaskSnapshot ts = new TaskSnapshot();
	  		
	  		ts.setActivationTime(event.getTask().getTaskData().getActivationTime());
	  		if(event.getTask().getTaskData().getActualOwner()!=null)
	  			ts.setActualOwner(userId);
	  		ts.setCompleted(null);
	  		if(event.getTask().getTaskData().getCreatedBy()!=null)
	  			ts.setCreatedBy(event.getTask().getTaskData().getCreatedBy().getId());
	  		ts.setCreatedOn(event.getTask().getTaskData().getCreatedOn());
	  		ts.setDeploymentId(event.getTask().getTaskData().getDeploymentId());
	  		ts.setExpirationTime(event.getTask().getTaskData().getExpirationTime());
	  		ts.setPreviousStatus(event.getTask().getTaskData().getPreviousStatus().toString());
	  		ts.setProcessId(event.getTask().getTaskData().getProcessId());
	  		ts.setProcessInstanceId(event.getTask().getTaskData().getProcessInstanceId());
	  		ts.setProcessSessionId((int) event.getTask().getTaskData().getProcessSessionId());
	  		ts.setStatus("InProgress");
	  		ts.setTaskId(ti.getId());
	  		ts.setWorkItemId(event.getTask().getTaskData().getWorkItemId());
	  		
	  		Environment environment = EnvironmentFactory.newEnvironment();    		   
			
	  		ContentMarshallerHelper cMH = new ContentMarshallerHelper();

	  		Map vars = (Map) cMH.unmarshall(a.getContent(), environment);
	  		
	  		if(vars!=null)
	  			ts.setTaskVariables(vars);

	  		Task persistedActualEntity = persistenceContext.findTask(ti.getId());
	  		
	  		if(persistedActualEntity.getTaskData().getStatus().equals(Status.InProgress)){	
	  			
	  			deleteTaskMongo(ts.getTaskId());
	  		  	createOrUpdateTaskMongo(ts);
  		}else
  			createJSONFile(ts.toJsonStr());
    }else
    	createJSONFile("{\"ErrorMessage\":\"afterTaskStartedEvent- Error tratando de persistir en mongoDB debido a que la transaccion de JBPM demoro demasiado\", \"taskId\" : \""+ ti.getId() +"\"}");
    }
    
	public void createJSONFile(String jsonFile){
		System.out.println("Se encontro un error, archivo json generado: ");
				  
	        try {
	        	FileWriter file = new FileWriter(SERVER_CONF_URL + "/" + (new Date().getTime())+ ".json", true);
	            file.write(jsonFile);
	            file.close();
	        }catch(Exception e){
	        	e.printStackTrace();
	        }
	}

    @Override
    public void afterTaskActivatedEvent(TaskEvent event) {
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.ACTIVATED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
        
// @TODO:     Update UserAuditTask to Lucene        
        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
        auditTaskImpl.setDescription(ti.getDescription());    
        persistenceContext.merge(auditTaskImpl);
        System.out.println("afterTaskActivatedEvent");

		Content a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
		  		
  		System.out.println("ContentID: " +  ti.getTaskData().getDocumentContentId());
  		if(a!=null)
  			System.out.println("Content encontrado: " + a.getId());
  		else{
  			System.out.println("Content no encontrado: " + ti.getTaskData().getDocumentContentId());
  			System.out.println("Retrying");
  			for(int i=0;i<retryTimes;i++)
				try {
					Thread.sleep(waitingTime);
	  		        a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
	  		        if(a!=null)
	  		        	break;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
  		}
		  		
  		if(a!=null){
  		
	  		TaskSnapshot ts = new TaskSnapshot();
	  		ts.setActivationTime(event.getTask().getTaskData().getActivationTime());
	  		if(event.getTask().getTaskData().getActualOwner()!=null)
	  			ts.setActualOwner(userId);
	  		ts.setCompleted(null);
	  		if(event.getTask().getTaskData().getCreatedBy()!=null)
	  			ts.setCreatedBy(event.getTask().getTaskData().getCreatedBy().getId());
	  		ts.setCreatedOn(event.getTask().getTaskData().getCreatedOn());
	  		ts.setDeploymentId(event.getTask().getTaskData().getDeploymentId());
	  		ts.setExpirationTime(event.getTask().getTaskData().getExpirationTime());
	  		ts.setPreviousStatus(event.getTask().getTaskData().getPreviousStatus().toString());
	  		ts.setProcessId(event.getTask().getTaskData().getProcessId());
	  		ts.setProcessInstanceId(event.getTask().getTaskData().getProcessInstanceId());
	  		ts.setProcessSessionId((int)event.getTask().getTaskData().getProcessSessionId());
	  		ts.setStatus(org.kie.internal.task.api.model.TaskEvent.TaskEventType.ACTIVATED.toString());
	  		ts.setTaskId(ti.getId());
	  		ts.setWorkItemId(event.getTask().getTaskData().getWorkItemId());
	  		
	  		Environment environment = EnvironmentFactory.newEnvironment();    		   
			
	  		ContentMarshallerHelper cMH = new ContentMarshallerHelper();
			
			Map vars = (Map) cMH.unmarshall(a.getContent(), environment);
	  		
	  		if(vars!=null)
	  			ts.setTaskVariables(vars);

	  		Task persistedActualEntity = persistenceContext.findTask(ti.getId());
	  		if(persistedActualEntity.getTaskData().getStatus().equals(org.kie.internal.task.api.model.TaskEvent.TaskEventType.ACTIVATED)){
	  		
	  			deleteTaskMongo(ts.getTaskId());
	  		    createOrUpdateTaskMongo(ts);
	  		
	  		}else
	  			createJSONFile(ts.toJsonStr());
    }else
    	createJSONFile("{\"ErrorMessage\":\"afterTaskActivatedEvent - Error tratando de persistir en mongoDB debido a que la transaccion de JBPM demoro demasiado\", \"taskId\" : \""+ ti.getId() +"\"}");

    }

    @Override
    public void afterTaskClaimedEvent(TaskEvent event) {
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.CLAIMED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
        //@TODO:      Remove  GroupAuditTask to Lucene

        //@TODO:      Create new   UserAuditTask to Lucene

        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
        auditTaskImpl.setDescription(ti.getDescription());    
        persistenceContext.merge(auditTaskImpl);
        
        System.out.println("afterTaskClaimedEvent");

        Content a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
		  		
  		System.out.println("ContentID: " +  ti.getTaskData().getDocumentContentId());
  		if(a!=null)
  			System.out.println("Content encontrado: " + a.getId());
  		else{
  			System.out.println("Content no encontrado: " + ti.getTaskData().getDocumentContentId());
  			System.out.println("Retrying");
  			for(int i=0;i<retryTimes;i++)
				try {
					Thread.sleep(waitingTime);
	  		        a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
	  		        if(a!=null)
	  		        	break;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
  		}
  		
  		if(a!=null){
  		
	  		TaskSnapshot ts = new TaskSnapshot();
	  		
	  		ts.setActivationTime(event.getTask().getTaskData().getActivationTime());
	  		if(event.getTask().getTaskData().getActualOwner()!=null)
	  			ts.setActualOwner(userId);
	  		ts.setCompleted(null);
	  		if(event.getTask().getTaskData().getCreatedBy()!=null)
	  			ts.setCreatedBy(event.getTask().getTaskData().getCreatedBy().getId());
	  		ts.setCreatedOn(event.getTask().getTaskData().getCreatedOn());
	  		ts.setDeploymentId(event.getTask().getTaskData().getDeploymentId());
	  		ts.setExpirationTime(event.getTask().getTaskData().getExpirationTime());
	  		ts.setPreviousStatus(event.getTask().getTaskData().getPreviousStatus().toString());
	  		ts.setProcessId(event.getTask().getTaskData().getProcessId());
	  		ts.setProcessInstanceId(event.getTask().getTaskData().getProcessInstanceId());
	  		ts.setProcessSessionId((int)event.getTask().getTaskData().getProcessSessionId());
	  		ts.setStatus("Reserved");
	  		ts.setTaskId(ti.getId());
	  		ts.setWorkItemId(event.getTask().getTaskData().getWorkItemId());
	  		
	  		Environment environment = EnvironmentFactory.newEnvironment();    		   
			
	  		ContentMarshallerHelper cMH = new ContentMarshallerHelper();

	  		Map vars = (Map) cMH.unmarshall(a.getContent(), environment);
	  		
	  		if(vars!=null)
	  			ts.setTaskVariables(vars);

	  		Task persistedActualEntity = persistenceContext.findTask(ti.getId());

	  		if(persistedActualEntity.getTaskData().getStatus().equals(Status.Reserved)){
	  			
		  		deleteTaskMongo(ts.getTaskId());
		  		createOrUpdateTaskMongo(ts);
	  		}
	  		else
	  			createJSONFile(ts.toJsonStr());
    }else
    	createJSONFile("{\"ErrorMessage\":\"afterTaskClaimedEvent - Error tratando de persistir en mongoDB debido a que la transaccion de JBPM demoro demasiado\", \"taskId\" : \""+ ti.getId() +"\"}");

    }

    @Override
    public void afterTaskSkippedEvent(TaskEvent event) {
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.SKIPPED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
        //@TODO:  Find the UserAuditTask in the lucene index

      
            //@TODO: If the UserAuditTask is in lucene remove it
          
        
        //@TODO: Create the History Audit Task Impl, store it in the DB and also into lucene
        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
        auditTaskImpl.setDescription(ti.getDescription());    
        persistenceContext.merge(auditTaskImpl);
        //@TODO:        There is also the possibility that a GroupAuditTask exist in Lucene..
        //        make sure that you remove it as well  
        
        System.out.println("afterTaskSkippedEvent");

    	Content a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
		  		
  		System.out.println("ContentID: " +  ti.getTaskData().getDocumentContentId());
  		if(a!=null)
  			System.out.println("Content encontrado: " + a.getId());
  		else{
  			System.out.println("Content no encontrado: " + ti.getTaskData().getDocumentContentId());
  			System.out.println("Retrying");
  			for(int i=0;i<retryTimes;i++)
				try {
					Thread.sleep(waitingTime);
	  		        a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
	  		        if(a!=null)
	  		        	break;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
  		}
  		
  		if(a!=null){
  		
	  		TaskSnapshot ts = new TaskSnapshot();
	  		
	  		ts.setActivationTime(event.getTask().getTaskData().getActivationTime());
	  		if(event.getTask().getTaskData().getActualOwner()!=null)
	  			ts.setActualOwner(userId);
	  		ts.setCompleted(null);
	  		if(event.getTask().getTaskData().getCreatedBy()!=null)
	  			ts.setCreatedBy(event.getTask().getTaskData().getCreatedBy().getId());
	  		ts.setCreatedOn(event.getTask().getTaskData().getCreatedOn());
	  		ts.setDeploymentId(event.getTask().getTaskData().getDeploymentId());
	  		ts.setExpirationTime(event.getTask().getTaskData().getExpirationTime());
	  		ts.setPreviousStatus(event.getTask().getTaskData().getPreviousStatus().toString());
	  		ts.setProcessId(event.getTask().getTaskData().getProcessId());
	  		ts.setProcessInstanceId(event.getTask().getTaskData().getProcessInstanceId());
	  		ts.setProcessSessionId((int)event.getTask().getTaskData().getProcessSessionId());
	  		ts.setStatus(org.kie.internal.task.api.model.TaskEvent.TaskEventType.SKIPPED.toString());
	  		ts.setTaskId(ti.getId());
	  		ts.setWorkItemId(event.getTask().getTaskData().getWorkItemId());
	  		
	  		Environment environment = EnvironmentFactory.newEnvironment();    		   
			
	  		ContentMarshallerHelper cMH = new ContentMarshallerHelper();

			Map vars = (Map) cMH.unmarshall(a.getContent(), environment);
	  		
	  		if(vars!=null)
	  			ts.setTaskVariables(vars);
	  		
	        Task persistedActualEntity = persistenceContext.findTask(ti.getId());

	  		if(persistedActualEntity.getTaskData().getStatus().equals(org.kie.internal.task.api.model.TaskEvent.TaskEventType.SKIPPED)){
	  			deleteTaskMongo(ts.getTaskId());
	  		   	createOrUpdateTaskMongo(ts);
	  		}else
	  			createJSONFile(ts.toJsonStr());
        }else
        	createJSONFile("{\"ErrorMessage\":\"afterTaskSkippedEvent - Error tratando de persistir en mongoDB debido a que la transaccion de JBPM demoro demasiado\", \"taskId\" : \""+ ti.getId() +"\"}");
    
        
    }

    @Override
    public void afterTaskStoppedEvent(TaskEvent event) {
		System.out.println("Jar version: " + version);
        System.out.println("afterTaskStoppedEvent");

        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.STOPPED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
        
      
        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setDescription(ti.getDescription());
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
            
        persistenceContext.merge(auditTaskImpl);

    }

    @Override
    public void afterTaskCompletedEvent(TaskEvent event) {
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.COMPLETED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));

        //@TODO:      Make sure that you find and remove the USerAuditTask from lucene once it is completed    
        //@TODO: Create a new HistoryAuditTask to keep track about the task. 
        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
            
        persistenceContext.merge(auditTaskImpl);
        
        System.out.println("afterTaskCompletedEvent");

        Content a = null;
        
  		System.out.println("ContentID: " +  ti.getTaskData().getDocumentContentId());
  		if(a!=null)
  			System.out.println("Content encontrado: " + a.getId());
  		else{
  			Map params = new HashMap<String, String>();
  			params.put("content", ti.getTaskData().getDocumentContentId());
  			List b = (List) persistenceContext.queryStringWithParametersInTransaction("select a from ContentImpl a where a.id = :content", params, Content.class);
  				if(b!=null)
  					a = (Content) b.get(0);
//  			System.out.println("Content no encontrado: " + ti.getTaskData().getDocumentContentId());
//  			System.out.println("Retrying");
//  			long startTime = System.currentTimeMillis();
//  			long endTime = 0;
//  			for(int i=0;i<retryTimes;i++)
//				try {
//					System.out.println("retry n: " + i);
//					Thread.sleep(waitingTime);
//					System.out.println("ContentId:" + event.getTask().getTaskData().getDocumentContentId());
//	  		        a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
//	  		        if(a!=null){
//	  		        	endTime   = System.currentTimeMillis();
//	  		        	System.out.println("Se obtuvo respuesta, la cual tomo: " + (endTime-startTime)/60 + " segundos");
//	  		        	break;
//	  		        }
//				} catch (InterruptedException e) {
//					// TODO Auto-generated catch block
//					e.printStackTrace();
//				}
//  			if(a==null)
//	        	System.out.println("NO se obtuvo respuesta, despues de: " + (endTime-startTime)/60 + " segundos");
  		}
  		
  		if(a!=null){
  		
	  		TaskSnapshot ts = new TaskSnapshot();
	  		
	  		ts.setActivationTime(event.getTask().getTaskData().getActivationTime());
	  		if(event.getTask().getTaskData().getActualOwner()!=null)
	  			ts.setActualOwner(userId);
	  		ts.setCompleted(new Date());
	  		if(event.getTask().getTaskData().getCreatedBy()!=null)
	  			ts.setCreatedBy(event.getTask().getTaskData().getCreatedBy().getId());
	  		ts.setCreatedOn(event.getTask().getTaskData().getCreatedOn());
	  		ts.setDeploymentId(event.getTask().getTaskData().getDeploymentId());
	  		ts.setExpirationTime(event.getTask().getTaskData().getExpirationTime());
	  		ts.setPreviousStatus(event.getTask().getTaskData().getPreviousStatus().toString());
	  		ts.setProcessId(event.getTask().getTaskData().getProcessId());
	  		ts.setProcessInstanceId(event.getTask().getTaskData().getProcessInstanceId());
	  		ts.setProcessSessionId((int)event.getTask().getTaskData().getProcessSessionId());
	  		ts.setStatus("Completed");
	  		ts.setTaskId(ti.getId());
	  		ts.setWorkItemId(event.getTask().getTaskData().getWorkItemId());
	  		
	  		Environment environment = EnvironmentFactory.newEnvironment();    		   
			
	  		ContentMarshallerHelper cMH = new ContentMarshallerHelper();

			Map vars = (Map) cMH.unmarshall(a.getContent(), environment);
	  		
	  		if(vars!=null)
	  			ts.setTaskVariables(vars);

	  		Task persistedActualEntity = persistenceContext.findTask(ti.getId());
	  		if(persistedActualEntity!=null)
		  		if(persistedActualEntity.getTaskData().getStatus().equals(Status.Completed)){
		  			deleteTaskMongo(ts.getTaskId());
		  		    createMongoCompletedTask(ts);
		  		}else
	  			createJSONFile(ts.toJsonStr());
	  		else
	  			createJSONFile(ts.toJsonStr());
        }else
        	createJSONFile("{\"ErrorMessage\":\"afterTaskCompletedEvent - Error tratando de persistir en mongoDB debido a que la transaccion de JBPM demoro demasiado\", \"taskId\" : \""+ ti.getId() +"\"}");
	   
    }
    
   private void createMongoCompletedTask(TaskSnapshot ts){
    	
    	System.out.println("Abriendo conector MongoDB");	
    	mcm = MongoConectionManager.getInstance();
	    System.out.println("Persistiendo Objeto en MongoDB :" + ts.toJsonStr());
	    mcm.persistCompleted(ts.toJsonStr());
    	
    }
   
   private void createMongoAbortedTask(TaskSnapshot ts){
   		System.out.println("Abriendo conector MongoDB");	
   		mcm = MongoConectionManager.getInstance();
   		System.out.println("Persistiendo Objeto en MongoDB :" + ts.toJsonStr());
   		mcm.persistAborted(ts.toJsonStr());
   	
   }

    @Override
    public void afterTaskFailedEvent(TaskEvent event) {
		System.out.println("Jar version: " + version);
        System.out.println("afterTaskFailedEvent");

        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.FAILED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
        
        // Same as task skipped
        
        
        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
            
        persistenceContext.merge(auditTaskImpl);
    }

    @Override
    public void afterTaskAddedEvent(TaskEvent event) {
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if(ti.getTaskData().getProcessId() != null){
            userId = ti.getTaskData().getProcessId();
        }else if(ti.getTaskData().getActualOwner() != null){
            userId = ti.getTaskData().getActualOwner().getId();
        }
        AuditTaskImpl auditTaskImpl = new AuditTaskImpl( ti.getId(),ti.getName(),  ti.getTaskData().getStatus().name(),
                                                                                ti.getTaskData().getActivationTime() ,
                                                                                (ti.getTaskData().getActualOwner() != null)?ti.getTaskData().getActualOwner().getId():"",
                                                                                ti.getDescription(), ti.getPriority(),
                                                                                (ti.getTaskData().getCreatedBy() != null)?ti.getTaskData().getCreatedBy().getId():"",
                                                                                ti.getTaskData().getCreatedOn(), 
                                                                                ti.getTaskData().getExpirationTime(), ti.getTaskData().getProcessInstanceId(), 
                                                                                ti.getTaskData().getProcessId(), ti.getTaskData().getProcessSessionId(),
                                                                                ti.getTaskData().getDeploymentId(),
                                                                                ti.getTaskData().getParentId());
        persistenceContext.persist(auditTaskImpl);
        //@TODO: Create User or Group Task for Lucene
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.ADDED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
    
        System.out.println("afterTaskAddedEvent");

        Content a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());

        System.out.println("ContentID: " +  ti.getTaskData().getDocumentContentId());
  		if(a!=null)
  			System.out.println("Content encontrado: " + a.getId());
  		else{
  			System.out.println("Content no encontrado: " + ti.getTaskData().getDocumentContentId());
  			System.out.println("Retrying");
  			for(int i=0;i<retryTimes;i++)
				try {
					Thread.sleep(waitingTime);
	  		        a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
	  		        if(a!=null)
	  		        	break;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
  		}	
  			
  		if(a!=null){
  		
  		
	  		TaskSnapshot ts = new TaskSnapshot();
	  		
	  		
	  		ts.setActivationTime(event.getTask().getTaskData().getActivationTime());
	  		if(ti.getTaskData().getActualOwner()!=null)
	  			ts.setActualOwner(ti.getTaskData().getActualOwner().getId());
	  		ts.setCompleted(null);
	  		if(event.getTask().getTaskData().getCreatedBy()!=null)
	  			ts.setCreatedBy(event.getTask().getTaskData().getCreatedBy().getId());
	  		
	  		ts.setCreatedOn(event.getTask().getTaskData().getCreatedOn());
	  		ts.setDeploymentId(event.getTask().getTaskData().getDeploymentId());
	  		ts.setExpirationTime(event.getTask().getTaskData().getExpirationTime());
	  		ts.setPreviousStatus(event.getTask().getTaskData().getPreviousStatus().toString());
	  		ts.setProcessId(event.getTask().getTaskData().getProcessId());
	  		ts.setProcessInstanceId(event.getTask().getTaskData().getProcessInstanceId());
	  		ts.setProcessSessionId((int)event.getTask().getTaskData().getProcessSessionId());
	  		
	        Task persistedActualEntity = persistenceContext.findTask(ti.getId());

	  		if(persistedActualEntity.getTaskData().getStatus().equals(Status.Ready))
	  			ts.setStatus("Ready");
	  		else
	  			ts.setStatus("Reserved");
	  		ts.setTaskId(ti.getId());
	  		ts.setWorkItemId(event.getTask().getTaskData().getWorkItemId());
	  		
	  		
	  		Environment environment = EnvironmentFactory.newEnvironment();    		   
		
	  		ContentMarshallerHelper cMH = new ContentMarshallerHelper();

			Map vars = (Map) cMH.unmarshall(a.getContent(), environment);
	  		
	  		if(vars!=null)
	  			ts.setTaskVariables(vars);
	  	
	        if((persistedActualEntity.getTaskData().getStatus().equals(Status.Ready))||(persistedActualEntity.getTaskData().getStatus().equals(Status.Reserved))){
	    
	        	createOrUpdateTaskMongo(ts);
  			}else
  				createJSONFile(ts.toJsonStr());
        }else
        	createJSONFile("{\"ErrorMessage\":\"afterTaskAddedEvent - Error tratando de persistir en mongoDB debido a que la transaccion de JBPM demoro demasiado\", \"taskId\" : \""+ ti.getId() +"\"}");
   
    
    }

    @Override
    public void afterTaskExitedEvent(TaskEvent event) {		
    	System.out.println("Jar version: " + version);
        System.out.println("afterTaskExitedEvent");

        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.EXITED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
        
        //@TODO: Same as skipped

        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        
        auditTaskImpl.setDescription(ti.getDescription());
        auditTaskImpl.setName(ti.getName());  
        auditTaskImpl.setActivationTime(ti.getTaskData().getActivationTime());
        auditTaskImpl.setPriority(ti.getPriority());
        auditTaskImpl.setDueDate(ti.getTaskData().getExpirationTime());
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
            
        persistenceContext.merge(auditTaskImpl);
        
        Content a = null;
        
  		System.out.println("ContentID: " +  ti.getTaskData().getDocumentContentId());
  		if(a!=null)
  			System.out.println("Content encontrado: " + a.getId());
  		else{
  			Map params = new HashMap<String, String>();
  			params.put("content", ti.getTaskData().getDocumentContentId());
  			List b = (List) persistenceContext.queryStringWithParametersInTransaction("select a from ContentImpl a where a.id = :content", params, Content.class);
  				if(b!=null)
  					a = (Content) b.get(0);
  		}
  		
  		if(a!=null){
  		
	  		TaskSnapshot ts = new TaskSnapshot();
	  		
	  		ts.setActivationTime(event.getTask().getTaskData().getActivationTime());
	  		if(event.getTask().getTaskData().getActualOwner()!=null)
	  			ts.setActualOwner(userId);
	  		ts.setCompleted(new Date());
	  		if(event.getTask().getTaskData().getCreatedBy()!=null)
	  			ts.setCreatedBy(event.getTask().getTaskData().getCreatedBy().getId());
	  		ts.setCreatedOn(event.getTask().getTaskData().getCreatedOn());
	  		ts.setDeploymentId(event.getTask().getTaskData().getDeploymentId());
	  		ts.setExpirationTime(event.getTask().getTaskData().getExpirationTime());
	  		ts.setPreviousStatus(event.getTask().getTaskData().getPreviousStatus().toString());
	  		ts.setProcessId(event.getTask().getTaskData().getProcessId());
	  		ts.setProcessInstanceId(event.getTask().getTaskData().getProcessInstanceId());
	  		ts.setProcessSessionId((int)event.getTask().getTaskData().getProcessSessionId());
	  		ts.setStatus("Exited");
	  		ts.setTaskId(ti.getId());
	  		ts.setWorkItemId(event.getTask().getTaskData().getWorkItemId());
	  		
	  		Environment environment = EnvironmentFactory.newEnvironment();    		   
			
	  		ContentMarshallerHelper cMH = new ContentMarshallerHelper();

			Map vars = (Map) cMH.unmarshall(a.getContent(), environment);
	  		
	  		if(vars!=null)
	  			ts.setTaskVariables(vars);

	  		Task persistedActualEntity = persistenceContext.findTask(ti.getId());
	  		if(persistedActualEntity!=null)
		  		if(persistedActualEntity.getTaskData().getStatus().equals(Status.Exited)){
		  			deleteTaskMongo(ts.getTaskId());
		  		    createMongoAbortedTask(ts);
		  		}else
	  			createJSONFile(ts.toJsonStr());
	  		else
	  			createJSONFile(ts.toJsonStr());
        }else
        	createJSONFile("{\"ErrorMessage\":\"afterTaskCompletedEvent - Error tratando de persistir en mongoDB debido a que la transaccion de JBPM demoro demasiado\", \"taskId\" : \""+ ti.getId() +"\"}");
	   
        
    }

    @Override
    public void afterTaskReleasedEvent(TaskEvent event) {
        
		System.out.println("Jar version: " + version);
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        //@TODO: Remove UserAuditTask and create a new GroupAuditTask for lucene  
        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setDescription(ti.getDescription());
        auditTaskImpl.setName(ti.getName());  
        auditTaskImpl.setActivationTime(ti.getTaskData().getActivationTime());
        auditTaskImpl.setPriority(ti.getPriority());
        auditTaskImpl.setDueDate(ti.getTaskData().getExpirationTime());
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner("");
            
        persistenceContext.merge(auditTaskImpl);

    }

    @Override
    public void afterTaskResumedEvent(TaskEvent event) {
    	
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.RESUMED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
       //@TODO: Update Lucene UserAudit Task

        
        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setDescription(ti.getDescription());
        auditTaskImpl.setName(ti.getName());  
        auditTaskImpl.setActivationTime(ti.getTaskData().getActivationTime());
        auditTaskImpl.setPriority(ti.getPriority());
        auditTaskImpl.setDueDate(ti.getTaskData().getExpirationTime());
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
            
        persistenceContext.merge(auditTaskImpl);
        
        System.out.println("afterTaskResumedEvent");

        Content a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
  		
  		System.out.println("ContentID: " +  ti.getTaskData().getDocumentContentId());
  		if(a!=null)
  			System.out.println("Content encontrado: " + a.getId());
  		else{
  			System.out.println("Content no encontrado: " + ti.getTaskData().getDocumentContentId());
  			System.out.println("Retrying");
  			for(int i=0;i<retryTimes;i++)
				try {
					Thread.sleep(waitingTime);
	  		        a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
	  		        if(a!=null)
	  		        	break;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
  			}
  		
  		if(a!=null){
  		
	  		TaskSnapshot ts = new TaskSnapshot();
	  		
	  		ts.setActivationTime(event.getTask().getTaskData().getActivationTime());
	  		if(event.getTask().getTaskData().getActualOwner()!=null)
	  			ts.setActualOwner(userId);
	  		ts.setCompleted(null);
	  		if(event.getTask().getTaskData().getCreatedBy()!=null)
	  			ts.setCreatedBy(event.getTask().getTaskData().getCreatedBy().getId());
	  		ts.setCreatedOn(event.getTask().getTaskData().getCreatedOn());
	  		ts.setDeploymentId(event.getTask().getTaskData().getDeploymentId());
	  		ts.setExpirationTime(event.getTask().getTaskData().getExpirationTime());
	  		ts.setPreviousStatus(event.getTask().getTaskData().getPreviousStatus().toString());
	  		ts.setProcessId(event.getTask().getTaskData().getProcessId());
	  		ts.setProcessInstanceId(event.getTask().getTaskData().getProcessInstanceId());
	  		ts.setProcessSessionId((int)event.getTask().getTaskData().getProcessSessionId());
	  		ts.setStatus(org.kie.internal.task.api.model.TaskEvent.TaskEventType.RESUMED.toString());
	  		ts.setTaskId(ti.getId());
	  		ts.setWorkItemId(event.getTask().getTaskData().getWorkItemId());
	  		
	  		Environment environment = EnvironmentFactory.newEnvironment();    		   
			
	  		ContentMarshallerHelper cMH = new ContentMarshallerHelper();

			Map vars = (Map) cMH.unmarshall(a.getContent(), environment);
	  		
	  		if(vars!=null)
	  			ts.setTaskVariables(vars);
	  		
	  		deleteTaskMongo(ts.getTaskId());
	  		    
	  		createOrUpdateTaskMongo(ts);
  		}else
        	createJSONFile("{\"ErrorMessage\":\"afterTaskResumedEvent - Error tratando de persistir en mongoDB debido a que la transaccion de JBPM demoro demasiado\", \"taskId\" : \""+ ti.getId() +"\"}");
    
    }

    @Override
    public void afterTaskSuspendedEvent(TaskEvent event) {
    	
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.SUSPENDED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
        
        //@TODO: Update Lucene Audit Task

        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
          auditTaskImpl.setDescription(ti.getDescription());
        auditTaskImpl.setName(ti.getName());  
        auditTaskImpl.setActivationTime(ti.getTaskData().getActivationTime());
        auditTaskImpl.setPriority(ti.getPriority());
        auditTaskImpl.setDueDate(ti.getTaskData().getExpirationTime());
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
            
        persistenceContext.merge(auditTaskImpl);
        
        System.out.println("afterTaskSuspendedEvent");

        Content a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
  		
  		System.out.println("ContentID: " +  ti.getTaskData().getDocumentContentId());
  		if(a!=null)
  			System.out.println("Content encontrado: " + a.getId());
  		else{
  			System.out.println("Content no encontrado: " + ti.getTaskData().getDocumentContentId());
  			System.out.println("Retrying");
  			for(int i=0;i<retryTimes;i++)
				try {
					Thread.sleep(waitingTime);
	  		        a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
	  		        if(a!=null)
	  		        	break;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
  		}	
  			
  		if(a!=null){
  		
	  		TaskSnapshot ts = new TaskSnapshot();
	  		
	  		ts.setActivationTime(event.getTask().getTaskData().getActivationTime());
	  		if(event.getTask().getTaskData().getActualOwner()!=null)
	  			ts.setActualOwner(userId);
	  		ts.setCompleted(null);
	  		if(event.getTask().getTaskData().getCreatedBy()!=null)
	  			ts.setCreatedBy(event.getTask().getTaskData().getCreatedBy().getId());
	  		ts.setCreatedOn(event.getTask().getTaskData().getCreatedOn());
	  		ts.setDeploymentId(event.getTask().getTaskData().getDeploymentId());
	  		ts.setExpirationTime(event.getTask().getTaskData().getExpirationTime());
	  		ts.setPreviousStatus(event.getTask().getTaskData().getPreviousStatus().toString());
	  		ts.setProcessId(event.getTask().getTaskData().getProcessId());
	  		ts.setProcessInstanceId(event.getTask().getTaskData().getProcessInstanceId());
	  		ts.setProcessSessionId((int)event.getTask().getTaskData().getProcessSessionId());
	  		ts.setStatus(org.kie.internal.task.api.model.TaskEvent.TaskEventType.SUSPENDED.toString());
	  		ts.setTaskId(ti.getId());
	  		ts.setWorkItemId(event.getTask().getTaskData().getWorkItemId());
	  		
	  		Environment environment = EnvironmentFactory.newEnvironment();    		   
			
	  		ContentMarshallerHelper cMH = new ContentMarshallerHelper();

			Map vars = (Map) cMH.unmarshall(a.getContent(), environment);
	  		
	  		if(vars!=null)
	  			ts.setTaskVariables(vars);
	  		
	  		deleteTaskMongo(ts.getTaskId());
	  		    
	  		createOrUpdateTaskMongo(ts);
  		}else
        	createJSONFile("{\"ErrorMessage\":\"afterTaskSuspendedEvent - Error tratando de persistir en mongoDB debido a que la transaccion de JBPM demoro demasiado\", \"taskId\" : \""+ ti.getId() +"\"}");
    
    }

    @Override
    public void afterTaskForwardedEvent(TaskEvent event) {
    	
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.FORWARDED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
        //@TODO: Update Lucene Audit Task

        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setDescription(ti.getDescription());
        auditTaskImpl.setName(ti.getName());  
        auditTaskImpl.setActivationTime(ti.getTaskData().getActivationTime());
        auditTaskImpl.setPriority(ti.getPriority());
        auditTaskImpl.setDueDate(ti.getTaskData().getExpirationTime());
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
            
        persistenceContext.merge(auditTaskImpl);
    }

    @Override
    public void afterTaskDelegatedEvent(TaskEvent event) {
    	
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.DELEGATED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
        
        //@TODO: Do I need to remove the USerAuditTask and create a GroupAuditTask in lucene???

        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setDescription(ti.getDescription());
        auditTaskImpl.setName(ti.getName());  
        auditTaskImpl.setActivationTime(ti.getTaskData().getActivationTime());
        auditTaskImpl.setPriority(ti.getPriority());
        auditTaskImpl.setDueDate(ti.getTaskData().getExpirationTime());
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
            
        persistenceContext.merge(auditTaskImpl);
    }
    
    @Override
    public void afterTaskNominatedEvent(TaskEvent event) {
    	
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.NOMINATED, userId, new Date()));
        //@TODO: Update Lucene Audit Task

        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setDescription(ti.getDescription());
        auditTaskImpl.setName(ti.getName());  
        auditTaskImpl.setActivationTime(ti.getTaskData().getActivationTime());
        auditTaskImpl.setPriority(ti.getPriority());
        auditTaskImpl.setDueDate(ti.getTaskData().getExpirationTime());
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId);
            
        persistenceContext.merge(auditTaskImpl);
    }
    
    /*
     * helper methods - start
     */
    
    protected AuditTaskImpl getAuditTask(TaskEvent event, TaskPersistenceContext persistenceContext, Task ti) {
    	AuditTaskImpl auditTaskImpl = persistenceContext.queryWithParametersInTransaction("getAuditTaskById", true, 
				persistenceContext.addParametersToMap("taskId", ti.getId()),
				ClassUtil.<AuditTaskImpl>castClass(AuditTaskImpl.class));
        
        return auditTaskImpl;
    }

	/*
     * helper methods - end
     */
	
    @Override
    public void beforeTaskActivatedEvent(TaskEvent event) {

    }

    @Override
    public void beforeTaskClaimedEvent(TaskEvent event) {

    }

    @Override
    public void beforeTaskSkippedEvent(TaskEvent event) {

    }

    @Override
    public void beforeTaskStartedEvent(TaskEvent event) {

    }

    @Override
    public void beforeTaskStoppedEvent(TaskEvent event) {

    }

    @Override
    public void beforeTaskCompletedEvent(TaskEvent event) {

    }

    @Override
    public void beforeTaskFailedEvent(TaskEvent event) {

    }

    @Override
    public void beforeTaskAddedEvent(TaskEvent event) {
        
    }

    @Override
    public void beforeTaskExitedEvent(TaskEvent event) {

    }

    @Override
    public void beforeTaskReleasedEvent(TaskEvent event) {
    	
		System.out.println("Jar version: " + version);
        String userId = "";
        Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
        if (ti.getTaskData().getActualOwner() != null) {
            userId = ti.getTaskData().getActualOwner().getId();
        }
        persistenceContext.persist(new TaskEventImpl(ti.getId(), org.kie.internal.task.api.model.TaskEvent.TaskEventType.RELEASED, ti.getTaskData().getProcessInstanceId(), ti.getTaskData().getWorkItemId(), userId));
      
        AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setDescription(ti.getDescription());
        auditTaskImpl.setName(ti.getName());  
        auditTaskImpl.setActivationTime(ti.getTaskData().getActivationTime());
        auditTaskImpl.setPriority(ti.getPriority());
        auditTaskImpl.setDueDate(ti.getTaskData().getExpirationTime());
        auditTaskImpl.setStatus(ti.getTaskData().getStatus().name());
        auditTaskImpl.setActualOwner(userId); 
        persistenceContext.merge(auditTaskImpl);
        
        System.out.println("beforeTaskReleasedEvent");

        Content a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
  		
  		System.out.println("ContentID: " +  ti.getTaskData().getDocumentContentId());
  		if(a!=null)
  			System.out.println("Content encontrado: " + a.getId());
  		else{
  			System.out.println("Content no encontrado: " + ti.getTaskData().getDocumentContentId());
  			System.out.println("Retrying");
  			for(int i=0;i<retryTimes;i++)
				try {
					Thread.sleep(waitingTime);
	  		        a = persistenceContext.findContent(event.getTask().getTaskData().getDocumentContentId());
	  		        if(a!=null)
	  		        	break;
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
  			
  			}
  		
  		if(a!=null){
  		
  		
	  		TaskSnapshot ts = new TaskSnapshot();
	  		
	  		
	  		ts.setActivationTime(event.getTask().getTaskData().getActivationTime());
	  		ts.setCompleted(null);
	  		if(event.getTask().getTaskData().getCreatedBy()!=null)
	  			ts.setCreatedBy(event.getTask().getTaskData().getCreatedBy().getId());
	  		
	  		ts.setCreatedOn(event.getTask().getTaskData().getCreatedOn());
	  		ts.setDeploymentId(event.getTask().getTaskData().getDeploymentId());
	  		ts.setExpirationTime(event.getTask().getTaskData().getExpirationTime());
	  		ts.setPreviousStatus(event.getTask().getTaskData().getPreviousStatus().toString());
	  		ts.setProcessId(event.getTask().getTaskData().getProcessId());
	  		ts.setProcessInstanceId(event.getTask().getTaskData().getProcessInstanceId());
	  		ts.setProcessSessionId((int)event.getTask().getTaskData().getProcessSessionId());
	  		
	  		ts.setStatus("Ready");
	  		ts.setTaskId(ti.getId());
	  		ts.setWorkItemId(event.getTask().getTaskData().getWorkItemId());
	  		
	  		
	  		Environment environment = EnvironmentFactory.newEnvironment();    		   
		
	  		ContentMarshallerHelper cMH = new ContentMarshallerHelper();

			Map vars = (Map) cMH.unmarshall(a.getContent(), environment);
	  		
	  		if(vars!=null)
	  			ts.setTaskVariables(vars);
	       
	  		Task persistedActualEntity = persistenceContext.findTask(ti.getId());

	        if((persistedActualEntity.getTaskData().getStatus().equals(Status.InProgress))||(persistedActualEntity.getTaskData().getStatus().equals(Status.Reserved))){
	        	
	        	deleteTaskMongo(ts.getTaskId());
	  			createOrUpdateTaskMongo(ts);
  			}else
  				createJSONFile(ts.toJsonStr());
        }else
        	createJSONFile("{\"ErrorMessage\":\"beforeTaskReleasedEvent - Error tratando de persistir en mongoDB debido a que la transaccion de JBPM demoro demasiado\", \"taskId\" : \""+ ti.getId() +"\"}");
        
        
    }

    @Override
    public void beforeTaskResumedEvent(TaskEvent event) {

    }

    @Override
    public void beforeTaskSuspendedEvent(TaskEvent event) {

    }

    @Override
    public void beforeTaskForwardedEvent(TaskEvent event) {

    }

    @Override
    public void beforeTaskDelegatedEvent(TaskEvent event) {

    }
    
    @Override
    public void beforeTaskNominatedEvent(TaskEvent event) {

    }

	@Override
	public boolean equals(Object obj) {
		if ( this == obj ) 
			return true;
        if ( obj == null ) 
        	return false;
        if ( (obj instanceof JPATaskLifeCycleEventListener) ) 
        	return true;
        
        return false;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
        int result = 1;
        result = prime * result + this.getClass().getName().hashCode();
        
        return result;
	}

	@Override
	public void beforeTaskUpdatedEvent(TaskEvent event) {
		
		
	}

	@Override
	public void afterTaskUpdatedEvent(TaskEvent event) {
		
		System.out.println("Jar version: " + version);
		Task ti = event.getTask();
        TaskPersistenceContext persistenceContext = ((TaskContext)event.getTaskContext()).getPersistenceContext();
		AuditTaskImpl auditTaskImpl = getAuditTask(event, persistenceContext, ti);
        if (auditTaskImpl == null) {
        	logger.warn("Unable find audit task entry for task id {} '{}', skipping audit task update", ti.getId(), ti.getName());
        	return;
        }
        auditTaskImpl.setDescription(ti.getDescription());
        auditTaskImpl.setName(ti.getName());
        auditTaskImpl.setPriority(ti.getPriority());
        auditTaskImpl.setDueDate(ti.getTaskData().getExpirationTime());
            
        persistenceContext.merge(auditTaskImpl);
	}
	
    private void deleteTaskMongo(Long taskId){
        
    	System.out.println("Abriendo conector MongoDB");
    	mcm = MongoConectionManager.getInstance();
	    System.out.println("Removing task: " + taskId);
	    mcm.deleteTask(taskId);

    	
    }
    
    private void createOrUpdateTaskMongo(TaskSnapshot ts){
    	
    	System.out.println("Abriendo conector MongoDB");	
    	mcm = MongoConectionManager.getInstance();
	    System.out.println("Persistiendo Objeto en MongoDB :" + ts.toJsonStr());
	    mcm.persist(ts.toJsonStr());
    }
    
}
