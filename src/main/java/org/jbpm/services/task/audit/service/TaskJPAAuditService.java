package org.jbpm.services.task.audit.service;

import static org.kie.internal.query.QueryParameterIdentifiers.*;

import java.util.Date;

import javax.persistence.EntityManagerFactory;

import org.jbpm.process.audit.JPAAuditLogService;
import org.kie.internal.runtime.manager.audit.query.AuditTaskInstanceLogDeleteBuilder;
import org.kie.internal.runtime.manager.audit.query.AuditTaskInstanceLogQueryBuilder;
import org.kie.internal.runtime.manager.audit.query.TaskEventInstanceLogDeleteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskJPAAuditService extends JPAAuditLogService {

	private static final Logger logger = LoggerFactory.getLogger(TaskJPAAuditService.class);

	static { 

        addCriteria(CREATED_ON_ID, "l.createdOn", Date.class);
        addCriteria(DEPLOYMENT_ID_LIST, "l.deploymentId", String.class);
        addCriteria(TASK_EVENT_DATE_ID, "l.logTime", Date.class);
        addCriteria(TASK_NAME_LIST, "l.name", String.class);
        addCriteria(TASK_DESCRIPTION_LIST, "l.description", String.class);
        addCriteria(TASK_AUDIT_STATUS_LIST, "l.status", String.class);
	}
	
	public TaskJPAAuditService() {
		super();
	}
	
	public TaskJPAAuditService(EntityManagerFactory emf) {
		super(emf);
	}

	public AuditTaskInstanceLogDeleteBuilder auditTaskInstanceLogDelete(){
		return new AuditTaskInstanceLogDeleteBuilderImpl(this);
	}
	
	public TaskEventInstanceLogDeleteBuilder taskEventInstanceLogDelete(){
		return new TaskEventInstanceLogDeleteBuilderImpl(this);
	}
	
	public AuditTaskInstanceLogQueryBuilder auditTaskInstanceLogQuery() {
		return new AuditTaskInstanceLogQueryBuilderImpl(this);
	}

	@Override
	public void clear() {
		try {
			super.clear();
		} catch (Exception e) {
			logger.warn("Unable to clear using {} due to {}", super.getClass().getName(), e.getMessage());
		}
		auditTaskInstanceLogDelete().build().execute();
		
		taskEventInstanceLogDelete().build().execute();
	}
}
