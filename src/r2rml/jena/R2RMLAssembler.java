package r2rml.jena;

import org.apache.jena.assembler.Assembler;
import org.apache.jena.assembler.Mode;
import org.apache.jena.assembler.assemblers.ModelAssembler;
import org.apache.jena.assembler.exceptions.AssemblerException;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.util.MappingRegistry;
import r2rml.engine.Configuration;
import r2rml.engine.R2RMLProcessor;

import java.util.logging.Logger;

/**
 * R2RML Model Assembler
 * <p>
 *      Processes R2RML mapping and adds the output triples to the base model
 *      that is configured in the assembler configuration.
 * </p>
 *
 * Add the prefix to the top of the assembler configuration (/etc/fuseki/configuration/*.ttl)
 * This prefix matches the URI used below in the code
 * <pre>
 *     {@literal @}prefix r2rml: &lt;http://r2rml#&gt;.
 * </pre>
 *
 * Load this class and specify that the r2rml:Model is a sub class of a ja:NamedModel
 * <pre>
 *     [] ja:loadClass "r2rml.jena.R2RMLAssembler" .
 *     r2rml:Model rdfs:subClassOf ja:NamedModel .
 * </pre>
 *
 * Create a graph
 * The r2rml:baseModel is the graph that will be used to add the new triples to
 * The r2rml:connectionURL is the JDBC URL (relative to the launch path of the web container)
 * The r2rml:mappingFile is the r2rml mapping file (relative to the launch path of the web container)
 * <pre>
 *      :r2rml_dataset a ja:RDFDataset ;
 * 		        ja:defaultGraph :r2rml_model .
 *      :r2rml_model a r2rml:Model ;
 * 		        r2rml:baseModel :tdb_graph ;
 * 		        r2rml:connectionURL "jdbc:sqlite:D:/OIS/cinema.db" ;
 * 		        r2rml:mappingFile "D:/OIS/cinema-mapping.ttl" .
 *
 *      # Create a graph for the tdb_dataset.
 *      :tdb_graph a tdb:GraphTDB ;
 * 		        tdb:dataset :tdb_dataset_readwrite .
 * </pre>
 *
 * @author Maxim Van de Wynckel (mvdewync@vub.be)
 */
public class R2RMLAssembler extends ModelAssembler {
    private static Logger logger = Logger.getLogger(R2RMLAssembler.class.getName());
    private static boolean initialized = false;

    // R2RML URI
    private static String URI = "http://r2rml#";

    static {
        init();
    }

    static synchronized public void init() {
        if (initialized)
            return;
        logger.info("Initializing R2RML Assembler ...");
        // TODO: Load SQLite drivers in some different manner
        try { Class.forName("org.sqlite.JDBC").newInstance(); } catch(Exception e) {e.printStackTrace();};
        // Register prefix
        MappingRegistry.addPrefixMapping("r2rml", URI) ;
        // Register this model assembler as "http://r2rml#Model"
        Assembler.general.implementWith(ResourceFactory.createProperty(URI + "Model"), new R2RMLAssembler());
        logger.info("Registered Assembler model: " + URI + "Model");
        initialized = true;
    }

    @Override
    public Model open(Assembler a, Resource root, Mode mode) {
        logger.info("Processing R2RML Jena Assembler ...");
        // Get the r2rml:baseModel
        Resource rootModel = getUniqueResource(root, ResourceFactory.createProperty(URI + "baseModel"));
        if (rootModel == null) {
            throw new AssemblerException( root, "No r2rml:baseModel specified!");
        }
        Model baseModel = a.openModel(rootModel, Mode.ANY);
        // Create a new model (do not extend on the base model, else you would add it to the persistent database)
        Model newModel = ModelFactory.createDefaultModel();
        newModel.add(baseModel);

        // Get the configuration from the root resource
        Configuration configuration = new Configuration();
        Literal confMappingFile = getUniqueLiteral(root, ResourceFactory.createProperty(URI + "mappingFile"));
        if (confMappingFile == null){
            throw new AssemblerException( root, "No r2rml:mappingFile specified!");
        }
        Literal confConnectionURL = getUniqueLiteral(root, ResourceFactory.createProperty(URI + "connectionURL"));
        if (confConnectionURL == null){
            throw new AssemblerException( root, "No r2rml:connectionURL specified!");
        }
        Literal confUser = getUniqueLiteral(root, ResourceFactory.createProperty(URI + "user"));
        if (confUser != null){
            configuration.setUser(confUser.getString());
        }
        Literal confPassword = getUniqueLiteral(root, ResourceFactory.createProperty(URI + "password"));
        if (confPassword != null){
            configuration.setPassword(confPassword.getString());
        }
        configuration.setMappingFile(confMappingFile.getString());
        configuration.setConnectionURL(confConnectionURL.getString());

        // Run the R2RML processor
        R2RMLProcessor engine = new R2RMLProcessor(configuration);
        engine.execute();

        // Add the data model (R2RML processed results) to the base model
        newModel.add(engine.getDataset().getDefaultModel());
        return newModel;
    }

    @Override
    protected Model openEmptyModel(Assembler a, Resource root, Mode mode) {
        return open(a, root, mode);
    }
}
