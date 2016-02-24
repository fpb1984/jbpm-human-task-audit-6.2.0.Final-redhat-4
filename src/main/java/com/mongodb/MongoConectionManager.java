package com.mongodb;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Properties;






//import org.apache.log4j.Logger;
import org.bson.types.ObjectId;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;
import com.mongodb.WriteResult;
import com.mongodb.util.JSON;
//import com.mongodb.util.JSONParseException;

public class MongoConectionManager {

	//private static Logger log = Logger.getLogger(MongoConectionManager.class);
	
	public enum PropertyKeys {
		HOST("host","localhost"),PORT("port","27017"),USER("user",null),PASS("pass",null),DATABASE("database",null);
		
		private String key;
		private String value;
		
		PropertyKeys(String key,String value){
			this.key = key;
			this.value=value;
		}
		
		public String getKey(){
			return key;
		}
		
		public String getDefault(){
			return value;
		}
	}
	
	
	public static final String DEFAULT_FILE_NAME = "mongo_config.properties";
	
	private static final String SERVER_CONF_URL = System.getProperty("jboss.server.config.dir");
	
	private static Properties config = new Properties();
	
	private DB db;
	
	private DBCollection collection;
	
	private static MongoConectionManager instance;
	
	private Mongo m;
	
	private String lastErrorMsg;
	
	private Boolean hasError = false;
	
	private boolean authenticate = false;

	private boolean authenticated = false;
	
	private String[] credentials = new String[2];
	
	private ObjectId id = null;
	
	private InputStream elInput = null;
	
	public MongoConectionManager(){
		System.out.println("Creating MongoGateway Instance");
		String host = null;
		Integer port = null;
		String user = null;
		String pass = null;
		String database = null;
		try {
			
			File externalFile = getExternalConfig();
			
			if (externalFile==null) {
				System.out.println("No se encontr\u00F3 archivo de configuraci\u00F3n "+DEFAULT_FILE_NAME+ " en "+SERVER_CONF_URL+". Se usar\u00E1 el que se incluye en el paquete");
				System.out.println("Usando archivo de configuraci\u00F3n interno incluido en el paquete");
				elInput  = this.getClass().getClassLoader().getResourceAsStream(DEFAULT_FILE_NAME);
			} else {
				System.out.println("Usando archivo de configuraci\u00F3n externo "+externalFile.getAbsolutePath());
				elInput = new FileInputStream(externalFile);
			}
			
			try {
				config.load(elInput);
			} catch (IOException e) {
				try {
					System.out.println("Error loading file.");
					throw new Exception(e);
				} catch (Exception e1) {
					System.out.println("Error loading file.");
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
			
			System.out.println("Retrieving host value");
			host = config.getProperty(PropertyKeys.HOST.getKey(),PropertyKeys.HOST.getDefault());
			System.out.println("Retrieving port value");
			port = Integer.valueOf(config.getProperty(PropertyKeys.PORT.getKey(),PropertyKeys.PORT.getDefault()));
			System.out.println("Retrieving user value");
			user = config.getProperty(PropertyKeys.USER.getKey(),PropertyKeys.USER.getDefault());
			System.out.println("Retrieving pass value");
			pass = config.getProperty(PropertyKeys.PASS.getKey(),PropertyKeys.PASS.getDefault());
			if((user!=null && pass!=null) && (!user.isEmpty() && !pass.isEmpty())){
				authenticate = true;
				credentials[0] = user;
				credentials[1] = pass;
			}
			System.out.println("Retrieving database name");
			database = config.getProperty(PropertyKeys.DATABASE.getKey(),PropertyKeys.DATABASE.getDefault());

		} catch (MissingResourceException e) {
			System.out.println("No MongoDB configuration file found, missing resource, using default values");
			host = PropertyKeys.HOST.getDefault();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			System.out.println("No MongoDB configuration file found, using default values");
		}
		
		try {
			if(port!=null && port.intValue()!=0){
				m = new Mongo(host,port);
			}else m = new Mongo(host);
		} catch (Exception e) {
			System.out.println("Mongo Exception");
		}
		//useDefaultDB();
		useDB(database);
		useCollection("taskSnapshots");
	}
	
	private File getExternalConfig() {
		if (SERVER_CONF_URL == null)
			return null;
		
		System.out.println("Tratando de levantar archivo externo "+SERVER_CONF_URL+"/"+DEFAULT_FILE_NAME);
		
		File ret = new File(SERVER_CONF_URL, DEFAULT_FILE_NAME);

		if (!ret.exists())
			return null;

		return ret;

	}
	
	
	
	/**
	 * Retrieve MongoGateway instance
	 * @return Singleton instance of MongoGateway
	 */
	public static MongoConectionManager getInstance(){
		System.out.println("Retrieving MongoGateway Instance");
		
		if(instance == null){
			instance = new MongoConectionManager();
		}
		
		return instance;
	}
	
	private DB getDB(){
		if(db==null)
			try {
				throw new Exception("Gateway is not connected", new NullPointerException("Null DB attribute"));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		return db;
	}
	
	private DBCollection getCollection(){
		if(collection==null)
			try {
				throw new Exception("No collection selected", new NullPointerException("Null Collection attribute"));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		return collection;
	}
	
	public String getLastErrorMsg(){
		return lastErrorMsg;
	}
	
	public boolean hasError(){
		return hasError;
	}
	
	private void processWriteResult(WriteResult wr){
		lastErrorMsg = wr.getError();
		hasError = !(lastErrorMsg == null);
	}
	
	private void reset(){
		lastErrorMsg = null;
		hasError = false;
	}
	
	private DBObject getDBO(Object obj) {
		return (DBObject) obj;
	}
	
	/*------GATEWAY ACTION METHODS------*/
	
	/**
	 * Connects to <b>&quot;pathfinder&quot;</b> shard database
	 * @see com.leafnoise.pathfinder.mongo.MongoGateway#connect(String)
	 * @return the current instance of the MongoGateway
	 */
	public MongoConectionManager useDefaultDB(){
		return useDB("tasks");
	}
	
	/**
	 * Connects to a given mongo shard database
	 * @param dbStr The name of the mongo DB to retrieve, if none is found, mongo creates one with specified name.
	 * @return the current instance of the MongoGateway
	 */
	public MongoConectionManager useDB(String dbStr){
		if(dbStr == null || dbStr.trim().isEmpty()){
			useDefaultDB();
		}else{
			try {
				db = m.getDB(dbStr);
				if(authenticate && !authenticated){
					db.authenticate(credentials[0], credentials[1].toCharArray());
					authenticated = true;
				}
			} catch (Exception e) {
				System.out.println(e);
				try {
					throw new Exception("Unable to use \""+dbStr+"\" DB.",e);
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}
		}
		System.out.println("Using DB: \""+db.getName()+"\"");
		return this;
	}
	
	/**
	 * Retrieves the specified collection.
	 * @param col the name of the collection to retrieve
	 * @return the current instance of the MongoGateway
	 */
	public MongoConectionManager useCollection(String col){
		if(col == null) return this;
		collection = getDB().getCollection(col);
		System.out.println("Using collection: \""+collection.getName()+"\"");
		return this;
	}
	
	/**
	 * Persists a Map&lt;String,Object&gt; under a key of value &quot:taskSnapshot&quot;
	 * @param jsonMap the mapped valid JSON
	 * @return the current instance of the MongoGateway
	 */
	public MongoConectionManager persist(String taskSnapshot){
		return persist("taskSnapshots",taskSnapshot);
	}
	
	public MongoConectionManager persistCompleted(String taskSnapshot){
		return persistCompleted("taskSnapshots",taskSnapshot);
	}
	
	public MongoConectionManager persistAborted(String taskSnapshot){
		return persistAbortedTasks("taskSnapshots",taskSnapshot);
	}
	
	public MongoConectionManager persistCompleted(String key, String taskSnapshot){
		useCollection("completedTaskSnapshots");
		reset();
		BasicDBObject dbo = new BasicDBObject();		
		Object task;
		try {
			task = JSON.parse(taskSnapshot);
			dbo.put(key,task);
			WriteResult wr = getCollection().insert(dbo);	
			this.setLastId(new ObjectId(dbo.get("_id").toString()));
			processWriteResult(wr);
		} catch (Exception e) {
			lastErrorMsg = e.getMessage();
			hasError = true;
		}
		useCollection("taskSnapshots");
		return this;
	}
	
	public MongoConectionManager persistAbortedTasks(String key, String taskSnapshot){
		useCollection("abordedTaskSnapshots");
		reset();
		BasicDBObject dbo = new BasicDBObject();		
		Object task;
		try {
			task = JSON.parse(taskSnapshot);
			dbo.put(key,task);
			WriteResult wr = getCollection().insert(dbo);	
			this.setLastId(new ObjectId(dbo.get("_id").toString()));
			processWriteResult(wr);
		} catch (Exception e) {
			lastErrorMsg = e.getMessage();
			hasError = true;
		}
		useCollection("taskSnapshots");
		return this;
	}
	
	/**
	 * Persists a Map&lt;String,Object&gt; under a given key
	 * @param jsonMap the mapped valid JSON
	 * @return the current instance of the MongoGateway
	 */
	public MongoConectionManager persist(String key, String taskSnapshot){
		reset();
		BasicDBObject dbo = new BasicDBObject();		
		Object task;
		try {
			task = JSON.parse(taskSnapshot);
			dbo.put(key,task);
			WriteResult wr = getCollection().insert(dbo);	
			this.setLastId(new ObjectId(dbo.get("_id").toString()));
			processWriteResult(wr);
		} catch (Exception e) {
			lastErrorMsg = e.getMessage();
			hasError = true;
		}
		return this;
	}
	
	/**
	 * Find all tasks
	 * @return List&lt;PFtasks&gt;
	 */
	public List<BasicDBObject> findAll(){
		return find(null);
	}
	
	/**
	 * Find tasks according to filter object specifications<br/>
	 * If query map is null a generic find() will be executed.
	 * @param query the Map&lt;String,Object&gt; with the filters for the specific search
	 * @return List&lt;PFTaks&gt;
	 */
	public List<BasicDBObject> find(Map<String,Object> query){
		List<BasicDBObject> tasks = new ArrayList<BasicDBObject>();
		DBCursor cursor = null;
		if(query==null){//find all
			 cursor = getCollection().find();
		}else{//find with filters
			cursor = getCollection().find(new BasicDBObject(query));
		}
		if(cursor!=null){
			try {
				while(cursor.hasNext()) {
					DBObject obj = cursor.next();
					DBObject evtIns = getDBO(obj.get("taskSnapshots"));
					BasicDBObject evt = new BasicDBObject(obj.get("_id").toString(),evtIns);
					tasks.add(evt);
				}
			}catch(Exception e){
				System.out.println(e);
				try {
					throw new Exception(e);
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}finally {
				cursor.close();
			}
		}
		return tasks;
	}
	
	public int countQuery(Map<String,Object> query){
		int rowsQ;
		if(query==null){//find all
			rowsQ = getCollection().find().count();
		}else{//find with filters
			rowsQ = getCollection().find(new BasicDBObject(query)).count();
		}
		
		return rowsQ;
	}
	
	public List<BasicDBObject> findPaginate(Map<String,Object> query, int pageNumber, int nPerPage){
		List<BasicDBObject> tasks = new ArrayList<BasicDBObject>();
		DBCursor cursor = null;
		if(query==null){//find all
			cursor = getCollection().find().skip((pageNumber)*nPerPage).limit(nPerPage);
		}else{//find with filters
			cursor = getCollection().find(new BasicDBObject(query)).skip((pageNumber)*nPerPage).limit(nPerPage);
		}
		if(cursor!=null){
			try {
				while(cursor.hasNext()) {
					DBObject obj = cursor.next();
					DBObject evtIns = getDBO(obj.get("taskSnapshots"));
					BasicDBObject evt = new BasicDBObject(obj.get("_id").toString(),evtIns);
					tasks.add(evt);
				}
			}catch(Exception e){
				System.out.println(e);
				try {
					throw new Exception(e);
				} catch (Exception e1) {
					// TODO Auto-generated catch block
					e1.printStackTrace();
				}
			}finally {
				cursor.close();
			}
		}
		return tasks;
	}
	
	public DBCursor findSkipped(Map<String,Object> query, int nPerPage, int lastFound){
		DBCursor cursor = null;
		cursor = getCollection().find(new BasicDBObject(query)).skip(lastFound + 1).limit(nPerPage);
		return cursor;
	}
	
	public void deleteTask(Long taskId){
		Map<String,Object> query = new HashMap<String,Object>();
		query.put("taskSnapshots.taskId", taskId);
		deleteDocument(query);
		
	}
	
	public void deleteDocument(Map<String,Object> query){
		DBObject cursor = null;
		cursor = getCollection().findOne(new BasicDBObject(query));
		System.out.println("Find Query: " + query.toString());
		if(cursor!=null){
			System.out.println("Removing object: " + cursor.toString());
			getCollection().remove(cursor);
		} else
			try {
				System.out.println("Not found Obj");
				throw new Exception();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}

	public ObjectId getLastId() {
		return id;
	}

	public void setLastId(ObjectId id) {
		this.id = id;
	}
	
}