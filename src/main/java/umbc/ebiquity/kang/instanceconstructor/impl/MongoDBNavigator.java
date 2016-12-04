package umbc.ebiquity.kang.instanceconstructor.impl;

import static umbc.ebiquity.kang.instanceconstructor.impl.RepositorySchemaConfiguration.IS_FROM_INSTANCE;
import static umbc.ebiquity.kang.instanceconstructor.impl.RepositorySchemaConfiguration.NORMALIZED_TRIPLE_OBJECT;
import static umbc.ebiquity.kang.instanceconstructor.impl.RepositorySchemaConfiguration.NORMALIZED_TRIPLE_SUBJECT;
import static umbc.ebiquity.kang.instanceconstructor.impl.RepositorySchemaConfiguration.TRIPLE_OBJECT;
import static umbc.ebiquity.kang.instanceconstructor.impl.RepositorySchemaConfiguration.TRIPLE_OBJECT_AS_CONCEPT_SCORE;
import static umbc.ebiquity.kang.instanceconstructor.impl.RepositorySchemaConfiguration.TRIPLE_PREDICATE;
import static umbc.ebiquity.kang.instanceconstructor.impl.RepositorySchemaConfiguration.TRIPLE_RECORD_TYPE;
import static umbc.ebiquity.kang.instanceconstructor.impl.RepositorySchemaConfiguration.TRIPLE_RECORD_TYPE_CONCEPT_OF_INSTANCE;
import static umbc.ebiquity.kang.instanceconstructor.impl.RepositorySchemaConfiguration.TRIPLE_RECORD_TYPE_META_DATA;
import static umbc.ebiquity.kang.instanceconstructor.impl.RepositorySchemaConfiguration.TRIPLE_RECORD_TYPE_RELATION_VALUE;
import static umbc.ebiquity.kang.instanceconstructor.impl.RepositorySchemaConfiguration.TRIPLE_SUBJECT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.bson.Document;

import com.mongodb.client.MongoDatabase;

import umbc.ebiquity.kang.entityframework.object.Concept;
import umbc.ebiquity.kang.instanceconstructor.IDescribedInstance;
import umbc.ebiquity.kang.instanceconstructor.IStorage;
import umbc.ebiquity.kang.instanceconstructor.IStorageNavigator;

public class MongoDBNavigator implements IStorageNavigator {

	protected static final org.apache.logging.log4j.Logger LOGGER = LogManager.getLogger();

	private final MongoDatabase database;

	public MongoDBNavigator(MongoDatabase database) {
		this.database = database;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * umbc.ebiquity.kang.instanceconstructor.IStorageNavigator#listStorageNames
	 * ()
	 */
	@Override
	public List<String> listStorageNames() {
		return database.listCollectionNames().into(new ArrayList<String>());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see umbc.ebiquity.kang.instanceconstructor.IInstanceQuerier#findAll()
	 */
	@Override
	public List<IStorage> retrieveStorages() {
		Map<String, IStorage> storagelbl2obj = new HashMap<String, IStorage>();
		for (String collectionName : database.listCollectionNames()) {
			LOGGER.debug("Retrieving instances from " + collectionName + " collection");

			Storage storage = new Storage(collectionName);
			storagelbl2obj.put(collectionName, storage);
			doRetrieve(storage, collectionName);
		}
		return new ArrayList<IStorage>(storagelbl2obj.values());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * umbc.ebiquity.kang.instanceconstructor.IInstanceNavigator#getStorage(java
	 * .lang.String)
	 */
	@Override
	public IStorage retrieveStorage(String collectionName) {
		Storage storage = new Storage(collectionName);
		doRetrieve(storage, collectionName);
		return storage;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see umbc.ebiquity.kang.instanceconstructor.IInstanceNavigator#
	 * retrieveInstance(java.lang.String, java.lang.String)
	 */
	@Override
	public IDescribedInstance retrieveInstance(String instanceName, String collectionName) {
		Document doc = database.getCollection(collectionName).find(new Document(TRIPLE_SUBJECT, instanceName)).first();
		if (doc != null) {
			return getInstance(doc, null);
		}
		return null;
	}

	private void doRetrieve(Storage storage, String collectionName) {
		Map<String, DescribedInstance> instancelbl2obj = new HashMap<String, DescribedInstance>();
		for (Document doc : database.getCollection(collectionName).find()) {
			String recordType = (String) doc.get(TRIPLE_RECORD_TYPE);
			if (isMetaData(recordType)) {
				processMetaData(doc, storage);
				continue;
			}
			DescribedInstance instance = getInstance(doc, instancelbl2obj);
			instance.setStorageName(collectionName);
			instancelbl2obj.put(instance.getName(), instance);
			storage.addInstance(instance);
		}
	}

	private void processMetaData(Document doc, Storage storage) {
		// do nothing here for now
	}

	private DescribedInstance getInstance(Document doc, Map<String, DescribedInstance> instancelbl2obj) {

		String instancelbl = (String) doc.get(TRIPLE_SUBJECT);
		// check if the instance with the given label already created. If
		// not, create a new one and add it to the current storage.
		DescribedInstance instance = instancelbl2obj == null ? null : instancelbl2obj.get(instancelbl);
		if (instance == null) {
			instance = new DescribedInstance(instancelbl);
		}

		String recordType = (String) doc.get(TRIPLE_RECORD_TYPE);
		if (isConcept(recordType)) {
			instance.addConcept(createConcept(doc));
		} else if (isRelation(recordType)) {
			instance.addRelationalTriple(createTriple(doc));
		}
		return instance;
	}

	private Triple createTriple(Document doc) {
		String subject = (String) doc.get(TRIPLE_SUBJECT);
		String object = (String) doc.get(TRIPLE_OBJECT);
		String predicate = (String) doc.get(TRIPLE_PREDICATE);
		String nSubject = (String) doc.get(NORMALIZED_TRIPLE_SUBJECT);
		String nObject = (String) doc.get(NORMALIZED_TRIPLE_OBJECT);
		return new Triple(subject, nSubject, predicate, object, nObject);
	}

	private Concept createConcept(Document doc) {
		String object = (String) doc.get(TRIPLE_OBJECT);
		String nObject = (String) doc.get(NORMALIZED_TRIPLE_OBJECT);
		String isFromInstance = (String) doc.get(IS_FROM_INSTANCE);
		String score = (String) doc.get(TRIPLE_OBJECT_AS_CONCEPT_SCORE);
		Concept concept = new Concept(object, Boolean.valueOf(isFromInstance));
		concept.setScore(Double.valueOf(score));
		concept.updateProcessedLabel(nObject);
		return concept;
	}

	private boolean isConcept(String recordType) {
		return TRIPLE_RECORD_TYPE_CONCEPT_OF_INSTANCE.equals(recordType) ? true : false;
	}

	private boolean isRelation(String recordType) {
		return TRIPLE_RECORD_TYPE_RELATION_VALUE.equals(recordType) ? true : false;
	}

	private boolean isMetaData(String recordType) {
		return TRIPLE_RECORD_TYPE_META_DATA.equals(recordType) ? true : false;
	}

}
